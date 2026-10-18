package com.earalarm.app.service

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.earalarm.app.R
import com.earalarm.app.MainActivity
import com.earalarm.app.appContainer
import com.earalarm.app.data.model.*
import com.earalarm.app.ui.ringing.AlarmRingingActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    @Suppress("DEPRECATION") private val vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var job: Job? = null
    private var fadeJob: Job? = null
    private var id = 0L
    private var settings = AlarmSettings()
    private var config = PlaybackConfig(SoundRef.DEFAULT, 70)
    private var wake: PowerManager.WakeLock? = null
    private var expectedDevice: Int? = null
    private var ready = false
    private var title = "알람"
    private val handler = Handler(Looper.getMainLooper())
    private val disconnected = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Stop immediately, before any asynchronous settings/database work.
            if (player == null) return
            stopAudio()
            applyDisconnected()
        }
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.id == expectedDevice }) { stopAudio(); applyDisconnected() }
        }
    }
    override fun onCreate() {
        super.onCreate()
        channels(this)
        ContextCompat.registerReceiver(this, disconnected, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        audio.registerAudioDeviceCallback(devices, handler)
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent?.action == SNOOZE) {
            if (intent.getLongExtra("id", -1) != id || active.value == null) return START_NOT_STICKY
            job?.cancel()
            stopAudio()
            if (intent.action == SNOOZE) {
                job?.cancel()
                scope.launch {
                    try { appContainer.scheduler.snooze(id, settings.snooze.minutes) }
                    catch (_: Exception) { reportFailure(this@AlarmForegroundService, "다시 울림 예약 실패: 정확한 알람 권한을 확인해 주세요.") }
                    finally { stopSelf() }
                }
            } else stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != RING) { stopSelf(); return START_NOT_STICKY }
        job?.cancel(); stopAudio()
        id = intent.getLongExtra("id", 0)
        title = if (id == 0L) "타이머가 끝났습니다" else "알람"
        _active.value = id
        startForeground(NOTIFICATION, notification())
        job = scope.launch {
            try {
                settings = appContainer.settingsRepository.current()
                val alarm = if (id == 0L) null else appContainer.alarmRepository.get(id)
                title = alarm?.label?.takeIf { it.isNotBlank() } ?: title
                config = settings.playbackFor(alarm)
                notifyCurrent()
                wake?.let { if (it.isHeld) it.release() }
                wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "earalarm:ringing").apply {
                    val duration = settings.autoStop.minutes
                    if (duration == null) acquire() else acquire(duration * 60000L + 30000)
                }
                val output = appContainer.earphoneDetector.preferredOutputDevice()
                if (output == null) applyDisconnected() else play(output.id)
                settings.autoStop.minutes?.let { delay(it * 60000L); stopSelf() }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { reportFailure(this@AlarmForegroundService, "알람음 재생 실패: 음악 파일과 권한을 확인해 주세요."); stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun applyDisconnected() {
        when (settings.noEarphoneAction) {
            NoEarphoneAction.SILENT_NOTIFICATION -> {
                reportFailure(this, "이어폰이 연결되지 않아 소리 없이 알려 드립니다.")
                stopSelf()
            }
            NoEarphoneAction.VIBRATE_ONLY -> vibrate()
            NoEarphoneAction.SPEAKER -> {
                val speaker = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) play(speaker.id) else { reportFailure(this, "스피커를 찾을 수 없습니다."); stopSelf() }
            }
        }
    }
    private fun play(deviceId: Int, fallback: Boolean = false) {
        stopAudio()
        val device = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == deviceId }
            ?: run { applyDisconnected(); return }
        expectedDevice = deviceId
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener({ change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) {
                    stopAudio(); reportFailure(this, "다른 오디오 또는 통화로 알람 소리가 중단됐습니다."); stopSelf()
                }
            }, handler).build()
        if (audio.requestAudioFocus(focus!!) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            reportFailure(this, "오디오를 사용할 수 없어 무음으로 알려 드립니다."); stopSelf(); return
        }
        val media = MediaPlayer()
        player = media
        try {
            media.setAudioAttributes(attrs)
            media.setVolume(0f, 0f)
            if (!media.setPreferredDevice(device)) { stopAudio(); stopSelf(); return }
            media.setDataSource(this, if (fallback) systemDefaultAlarmUri() else config.sound.resolveUri(this))
            media.isLooping = true
            media.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener { routing ->
                if (player === media && ready && routing.routedDevice?.id != expectedDevice) {
                    stopAudio(); reportFailure(this, "출력 경로가 변경되어 소리를 중단했습니다."); stopSelf()
                }
            }, handler)
            media.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                it.start() // Muted until actual route is confirmed.
                fadeJob = scope.launch {
                    var confirmed = false
                    repeat(20) {
                        if (player !== media) return@launch
                        if (media.routedDevice?.id == deviceId) confirmed = true
                        if (!confirmed) delay(50)
                    }
                    if (!confirmed) { stopAudio(); reportFailure(this@AlarmForegroundService, "출력 경로를 확인할 수 없어 소리를 중단했습니다."); stopSelf(); return@launch }
                    ready = true
                    if (settings.vibrateWithSound) vibrate()
                    val steps = (settings.fadeIn.seconds * 10).coerceAtLeast(1)
                    for (step in 1..steps) {
                        if (player !== media || media.routedDevice?.id != deviceId) { stopAudio(); applyDisconnected(); return@launch }
                        val gain = config.volumePercent.coerceIn(0, 100) / 100f * step / steps
                        media.setVolume(gain, gain)
                        delay(100)
                    }
                }
            }
            media.setOnErrorListener { _, _, _ ->
                if (!fallback) play(deviceId, true)
                else { stopAudio(); reportFailure(this, "알람음을 읽을 수 없습니다."); stopSelf() }
                true
            }
            media.prepareAsync()
        } catch (_: Exception) {
            if (!fallback) play(deviceId, true)
            else { stopAudio(); reportFailure(this, "알람음을 읽을 수 없습니다."); stopSelf() }
        }
    }
    private fun vibrate() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
    }
    private fun stopAudio() {
        ready = false
        fadeJob?.cancel(); fadeJob = null
        player?.let { runCatching { it.setVolume(0f, 0f) }; runCatching { it.release() } }
        player = null; expectedDevice = null
        vibrator.cancel()
        focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, AlarmRingingActivity::class.java).putExtra("id", id)
            .putExtra("title", title), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun command(action: String) = PendingIntent.getService(this, if (action == STOP) 1 else 2,
            Intent(this, AlarmForegroundService::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText("눌러서 알람 끄기 또는 다시 울림")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true).setSilent(true).setContentIntent(open).setFullScreenIntent(open, true)
            .addAction(0, "알람 끄기", command(STOP))
        if (settings.snooze.minutes > 0) builder.addAction(0, "다시 울림", command(SNOOZE))
        return builder.build()
    }
    private fun notifyCurrent() { getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification()) }
    override fun onDestroy() {
        job?.cancel(); stopAudio(); scope.cancel()
        audio.unregisterAudioDeviceCallback(devices)
        unregisterReceiver(disconnected)
        wake?.let { if (it.isHeld) it.release() }
        _active.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        const val RING = "com.earalarm.RING"
        const val STOP = "com.earalarm.STOP"
        const val SNOOZE = "com.earalarm.SNOOZE"
        private const val CHANNEL = "ringing_v1"
        private const val SILENT = "silent_v1"
        private const val NOTIFICATION = 10
        private val _active = MutableStateFlow<Long?>(null)
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
