package com.earalarm.app.service

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.earalarm.app.R
import com.earalarm.app.MainActivity
import com.earalarm.app.appContainer
import com.earalarm.app.audio.EarphonePlayback
import com.earalarm.app.data.model.*
import com.earalarm.app.ui.ringing.AlarmRingingActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class RingingAlarm(val id: Long, val token: String)

class AlarmForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val playback by lazy { EarphonePlayback(this) }
    @Suppress("DEPRECATION") private val vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }
    private var ringJob: Job? = null
    private var ring: RingingAlarm? = null
    private var settings = AlarmSettings()
    private var wake: PowerManager.WakeLock? = null
    private var title = "알람"

    override fun onCreate() {
        super.onCreate()
        channels(this)
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            RING -> {
                val id = intent.getLongExtra("id", -1)
                if (id >= 0) startRinging(id) else if (ring == null) stopSelf()
            }
            STOP, SNOOZE -> {
                val current = ring
                if (current != null && active.value == current && intent.data == current.commandUri()) {
                    if (intent.action == STOP) finishRinging()
                    else snooze(current)
                } else if (current == null) {
                    stopSelf()
                }
            }
            else -> if (ring == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(id: Long) {
        clearRinging()
        val current = RingingAlarm(id, UUID.randomUUID().toString())
        ring = current
        settings = AlarmSettings()
        title = if (id == 0L) "타이머가 끝났습니다" else "알람"
        _active.value = current
        startForeground(NOTIFICATION, notification(current, allowSnooze = false))

        ringJob = scope.launch {
            try {
                settings = appContainer.settingsRepository.current()
                val alarm = if (id == 0L) null else appContainer.alarmRepository.get(id)
                if (ring != current) return@launch
                if (id != 0L && alarm == null) {
                    finishRinging()
                    return@launch
                }
                title = alarm?.label?.takeIf { it.isNotBlank() } ?: title
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION, notification(current, allowSnooze = settings.snooze.minutes > 0))
                val autoStop = settings.autoStop.minutes
                wake = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "earalarm:ringing").apply {
                        if (autoStop == null) acquire() else acquire(autoStop * 60_000L + 30_000)
                    }
                val config = settings.playbackFor(alarm)
                playback.play(config.sound, config.volumePercent, settings.fadeIn.seconds * 1_000L,
                    onReady = { if (settings.vibrateWithSound) vibrate() },
                    onFailure = { failure -> handlePlaybackFailure(current, failure) })
                if (autoStop != null) {
                    delay(autoStop * 60_000L)
                    if (ring == current) finishRinging()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (ring == current) {
                    reportFailure(this@AlarmForegroundService, "알람을 시작하지 못했어요. 알람 설정을 확인해 주세요.")
                    finishRinging()
                }
            }
        }
    }

    private fun handlePlaybackFailure(current: RingingAlarm, failure: EarphonePlayback.Failure) {
        if (ring != current) return
        vibrator.cancel()
        val missingRoute = failure == EarphonePlayback.Failure.NO_EARPHONE ||
            failure == EarphonePlayback.Failure.ROUTE_LOST
        if (missingRoute && settings.noEarphoneAction == NoEarphoneAction.VIBRATE_ONLY) {
            vibrate()
            return
        }
        val message = when (failure) {
            EarphonePlayback.Failure.NO_EARPHONE -> "확인된 이어폰이 없어 소리 없이 알렸어요."
            EarphonePlayback.Failure.ROUTE_LOST -> "이어폰 출력 경로를 확인할 수 없어 소리를 중단했어요."
            EarphonePlayback.Failure.FOCUS_LOST -> "다른 오디오 또는 통화로 알람 소리를 중단했어요."
            EarphonePlayback.Failure.SOUND_UNAVAILABLE -> "알람음 파일을 재생할 수 없어요."
        }
        reportFailure(this, message)
        finishRinging()
    }

    private fun snooze(current: RingingAlarm) {
        val minutes = settings.snooze.minutes
        if (minutes <= 0) return
        clearRinging()
        ring = current
        scope.launch {
            try {
                appContainer.scheduler.snooze(current.id, minutes)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                reportFailure(this@AlarmForegroundService, "다시 울림을 예약하지 못했어요. 정확한 알람 권한을 확인해 주세요.")
            } finally {
                if (ring == current) finishRinging()
            }
        }
    }

    private fun vibrate() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
    }
    private fun clearRinging() {
        playback.stop()
        vibrator.cancel()
        ringJob?.cancel()
        ringJob = null
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        ring = null
        _active.value = null
    }

    private fun finishRinging() {
        clearRinging()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(current: RingingAlarm, allowSnooze: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, AlarmRingingActivity::class.java).setData(current.commandUri()),
            PendingIntent.FLAG_IMMUTABLE)
        fun command(action: String) = PendingIntent.getService(this, 0,
            controlIntent(this, action, current), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText("눌러서 알람 끄기 또는 다시 울림")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true).setSilent(true).setContentIntent(open).setFullScreenIntent(open, true)
            .addAction(0, "알람 끄기", command(STOP))
        if (allowSnooze) builder.addAction(0, "다시 울림", command(SNOOZE))
        return builder.build()
    }
    override fun onDestroy() {
        clearRinging()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private fun RingingAlarm.commandUri() = Uri.parse("earalarm://ring/$token")

        fun controlIntent(context: Context, action: String, alarm: RingingAlarm): Intent =
            Intent(context, AlarmForegroundService::class.java).setAction(action).setData(alarm.commandUri())


        const val RING = "com.earalarm.RING"
        const val STOP = "com.earalarm.STOP"
        const val SNOOZE = "com.earalarm.SNOOZE"
        private const val CHANNEL = "ringing_v1"
        private const val SILENT = "silent_v1"
        private const val NOTIFICATION = 10
        private val _active = MutableStateFlow<RingingAlarm?>(null)
        val active = _active.asStateFlow()
        private fun channels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "알람", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false) })
            manager.createNotificationChannel(NotificationChannel(SILENT, "무음 알림", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(false) })
        }
        fun reportFailure(context: Context, message: String) {
            channels(context)
            val open = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            try { NotificationManagerCompat.from(context).notify(11, NotificationCompat.Builder(context, SILENT)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("이어폰 알람").setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setSilent(true).setAutoCancel(true).setContentIntent(open).build()) }
            catch (_: SecurityException) { /* Notification permission is controlled by the user. */ }
        }
    }
}
