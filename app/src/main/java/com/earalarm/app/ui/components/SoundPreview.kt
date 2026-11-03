package com.earalarm.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.earalarm.app.appContainer
import com.earalarm.app.data.model.SoundRef
import com.earalarm.app.data.model.resolveUri
import com.earalarm.app.data.model.systemDefaultAlarmUri
import com.earalarm.app.service.AlarmForegroundService

/**
 * 알람음 미리 듣기. 알람과 같은 안전 규칙으로 '이어폰으로만' 짧게 재생한다.
 * 음량 0으로 시작해 실제 출력 장치가 이어폰으로 확인된 뒤에만 소리를 올린다(스피커 누출 방지).
 * 메인 스레드에서만 사용한다.
 */
class SoundPreview(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var sound: SoundRef? = null
    private var volume = 0
    private var routed = false
    private var lastHint = 0L
    private val autoStop = Runnable { stop() }
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    /** 처음부터 다시 재생한다. 재생을 시작하지 못하면 false (이어폰 없음, 알람 울리는 중 등) */
    fun play(sound: SoundRef, volumePercent: Int): Boolean {
        stop()
        if (AlarmForegroundService.active.value != null) return false
        val device = app.appContainer.earphoneDetector.preferredOutputDevice()
        if (device == null) { hint(); return false }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
            }, handler).build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        this.sound = sound
        volume = volumePercent.coerceIn(0, 100)
        start(sound, device, false)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
        return player != null
    }

    /** 이미 같은 소리가 재생 중이면 음량만 바꾸고(슬라이더 드래그용), 아니면 새로 재생한다. */
    fun playOrAdjust(sound: SoundRef, volumePercent: Int): Boolean {
        if (player != null && this.sound == sound) {
            setVolume(volumePercent)
            handler.removeCallbacks(autoStop); handler.postDelayed(autoStop, AUTO_STOP_MS)
            return true
        }
        return play(sound, volumePercent)
    }

    fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, 100)
        val media = player ?: return
        if (routed) { val gain = volume / 100f; runCatching { media.setVolume(gain, gain) } }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        sound = null
        focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
    }

    private fun releasePlayer() {
        routed = false
        player?.let { runCatching { it.setVolume(0f, 0f) }; runCatching { it.release() } }
        player = null
    }

    private fun start(sound: SoundRef, device: AudioDeviceInfo, fallback: Boolean) {
        val media = MediaPlayer()
        player = media
        routed = false
        try {
            media.setAudioAttributes(attrs)
            media.setVolume(0f, 0f)
            media.setDataSource(app, if (fallback) systemDefaultAlarmUri() else sound.resolveUri(app))
            media.setPreferredDevice(device) // setDataSource 뒤에 지정해야 한다. 실패해도 무음으로 두고 경로 확인으로 거른다.
            media.isLooping = true
            media.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener { routing ->
                if (player === media && routed && !app.appContainer.earphoneDetector.isEarphone(routing.routedDevice)) stop()
            }, handler)
            media.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                it.start() // 경로가 확인될 때까지 무음
                awaitRoute(it, 0)
            }
            media.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    if (!fallback) { releasePlayer(); start(sound, device, true) } else stop()
                }
                true
            }
            media.prepareAsync()
        } catch (_: Exception) {
            if (player !== media) return
            if (!fallback) { releasePlayer(); start(sound, device, true) } else stop()
        }
    }

    /** 블루투스는 경로가 잡히기까지 걸리므로 최대 약 3초 기다린다. 확인되지 않으면 소리 없이 끝낸다. */
    private fun awaitRoute(media: MediaPlayer, tries: Int) {
        if (player !== media) return
        if (app.appContainer.earphoneDetector.isEarphone(media.routedDevice)) {
            routed = true
            val gain = volume / 100f
            media.setVolume(gain, gain)
            return
        }
        if (tries >= 60) { stop(); return }
        handler.postDelayed({ awaitRoute(media, tries + 1) }, 50)
    }

    private fun hint() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHint < 3000) return
        lastHint = now
        Toast.makeText(app, "이어폰을 연결하면 미리 들을 수 있어요", Toast.LENGTH_SHORT).show()
    }

    private companion object { const val AUTO_STOP_MS = 6000L }
}

/** 화면을 벗어나거나 앱이 멈추면(ON_PAUSE/ON_STOP) 미리 듣기를 끝낸다. */
@Composable
fun rememberSoundPreview(): SoundPreview {
    val context = LocalContext.current
    val preview = remember { SoundPreview(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, preview) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) preview.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); preview.stop() }
    }
    return preview
}
