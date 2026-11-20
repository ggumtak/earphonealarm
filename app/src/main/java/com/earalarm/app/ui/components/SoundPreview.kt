package com.earalarm.app.ui.components

import android.content.Context
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
import com.earalarm.app.audio.EarphonePlayback
import com.earalarm.app.data.model.SoundRef
import com.earalarm.app.service.AlarmForegroundService

class SoundPreview(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val playback = EarphonePlayback(app)
    private var sound: SoundRef? = null
    private var lastHint: Long? = null
    private val autoStop = Runnable { stop() }

    fun play(sound: SoundRef, volumePercent: Int): Boolean {
        stop()
        if (AlarmForegroundService.active.value != null) return false
        this.sound = sound
        playback.play(sound, volumePercent, onFailure = { failure ->
            stop()
            if (failure == EarphonePlayback.Failure.NO_EARPHONE) hint()
        })
        if (playback.isPlaying) handler.postDelayed(autoStop, AUTO_STOP_MS)
        return playback.isPlaying
    }

    fun playOrAdjust(sound: SoundRef, volumePercent: Int): Boolean {
        if (playback.isPlaying && this.sound == sound) {
            playback.setVolume(volumePercent)
            handler.removeCallbacks(autoStop)
            handler.postDelayed(autoStop, AUTO_STOP_MS)
            return true
        }
        return play(sound, volumePercent)
    }

    fun stop() {
        handler.removeCallbacks(autoStop)
        playback.stop()
        sound = null
    }

    private fun hint() {
        val now = SystemClock.elapsedRealtime()
        if (lastHint?.let { now - it < 3000 } == true) return
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
