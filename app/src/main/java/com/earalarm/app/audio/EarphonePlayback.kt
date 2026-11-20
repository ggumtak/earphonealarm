package com.earalarm.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.earalarm.app.appContainer
import com.earalarm.app.data.model.SoundRef
import com.earalarm.app.data.model.resolveUri

/** Shared by alarm, timer, snooze and preview. All calls and callbacks run on the main thread. */
internal class EarphonePlayback(context: Context) {
    enum class Failure { NO_EARPHONE, ROUTE_LOST, FOCUS_LOST, SOUND_UNAVAILABLE }

    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val handler = Handler.createAsync(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var session: Session? = null
    val isPlaying: Boolean get() = session != null

    fun play(
        sound: SoundRef,
        volume: Int,
        fadeInMs: Long = 0,
        onReady: () -> Unit = {},
        onFailure: (Failure) -> Unit,
    ) {
        stop()
        val devices = app.appContainer.earphoneDetector.outputs()
        if (devices.isEmpty()) {
            onFailure(Failure.NO_EARPHONE)
            return
        }
        val next = Session(devices, fadeInMs, onReady, onFailure)
        session = next
        next.volume = volume.coerceIn(0, 100)
        next.start(sound)
    }

    fun setVolume(volume: Int) {
        // The polling loop checks the live route before applying each gain, including UI changes.
        session?.volume = volume.coerceIn(0, 100)
    }

    fun stop() {
        val previous = session ?: return
        session = null
        previous.close()
    }

    private inner class Session(
        private val devices: List<AudioDeviceInfo>,
        private val fadeInMs: Long,
        private val onReady: () -> Unit,
        private val onFailure: (Failure) -> Unit,
    ) {
        private val allowed = devices.map { it.identity() }.toSet()
        var volume = 0
        private var player: MediaPlayer? = null
        private var focus: AudioFocusRequest? = null
        private var receiverRegistered = false
        private var callbackRegistered = false
        private var deadline = 0L
        private var candidate: Set<Int>? = null
        private var confirmed: Set<Int>? = null
        private var confirmedAt = 0L
        private var lastGain = 0f
        private val tick = Runnable { checkRoute() }
        private val timeout = Runnable { fail(Failure.SOUND_UNAVAILABLE) }
        private val noisy = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { fail(Failure.ROUTE_LOST) }
        }
        private val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                val watch = confirmed ?: candidate ?: setOf(devices.first().id)
                if (removed.any { it.id in watch }) fail(Failure.ROUTE_LOST)
            }
        }

        fun start(sound: SoundRef) {
            try {
                ContextCompat.registerReceiver(app, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_NOT_EXPORTED)
                receiverRegistered = true
                audio.registerAudioDeviceCallback(deviceCallback, handler)
                callbackRegistered = true
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener({ change ->
                        if (change != AudioManager.AUDIOFOCUS_GAIN) fail(Failure.FOCUS_LOST)
                    }, handler).build()
                focus = request
                if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    fail(Failure.FOCUS_LOST)
                    return
                }
                load(sound)
            } catch (_: RuntimeException) {
                fail(Failure.SOUND_UNAVAILABLE)
            }
        }

        private fun load(sound: SoundRef) {
            clearPlayer()
            val media = MediaPlayer()
            player = media
            try {
                media.setAudioAttributes(attributes)
                media.setDataSource(app, sound.resolveUri(app))
                media.setVolume(0f, 0f)
                media.setPreferredDevice(devices.first())
                media.isLooping = true
                media.addOnRoutingChangedListener({ _ ->
                    val expected = confirmed
                    if (isCurrent(media) && expected != null && verifiedRoute(media) != expected) {
                        fail(Failure.ROUTE_LOST)
                    }
                }, handler)
                media.setOnPreparedListener {
                    if (!isCurrent(it)) return@setOnPreparedListener
                    handler.removeCallbacks(timeout)
                    try {
                        it.setVolume(0f, 0f)
                        it.start()
                        deadline = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS
                        handler.post(tick)
                    } catch (_: RuntimeException) {
                        handleMediaError(sound)
                    }
                }
                media.setOnErrorListener { failed, _, _ ->
                    if (isCurrent(failed)) handleMediaError(sound)
                    true
                }
                handler.postDelayed(timeout, PREPARE_TIMEOUT_MS)
                media.prepareAsync()
            } catch (_: Exception) {
                handleMediaError(sound)
            }
        }

        private fun handleMediaError(sound: SoundRef) {
            // A missing file can fall back to the bundled tone only before a route was confirmed.
            if (confirmed == null && sound != SoundRef.DEFAULT) load(SoundRef.DEFAULT)
            else fail(Failure.SOUND_UNAVAILABLE)
        }

        private fun isCurrent(media: MediaPlayer): Boolean = session === this && player === media

        private fun verifiedRoute(media: MediaPlayer): Set<Int>? {
            return try {
                if (!media.isPlaying) return null
                val actual = if (Build.VERSION.SDK_INT >= 36) media.routedDevices
                else listOfNotNull(media.routedDevice)
                val actualIds = allowedIds(actual) ?: return null
                if (Build.VERSION.SDK_INT in 33..35 &&
                    allowedIds(audio.getAudioDevicesForAttributes(attributes)) == null
                ) return null
                actualIds
            } catch (_: RuntimeException) {
                null
            }
        }

        private fun allowedIds(routes: List<AudioDeviceInfo>): Set<Int>? =
            verifiedOutputIds(routes.map { it.identity() }, allowed)

        private fun checkRoute() {
            val media = player ?: return
            if (!isCurrent(media)) return
            val route = verifiedRoute(media)
            val now = SystemClock.elapsedRealtime()
            if (confirmed == null) {
                // Require two matching reads before turning up the volume.
                if (route != null && route == candidate) {
                    confirmed = route
                    confirmedAt = now
                    onReady()
                } else {
                    candidate = route
                    if (now >= deadline) fail(Failure.ROUTE_LOST)
                    else handler.postDelayed(tick, ROUTE_CHECK_MS)
                    return
                }
            } else if (route != confirmed) {
                fail(Failure.ROUTE_LOST)
                return
            }
            val fraction = if (fadeInMs == 0L) 1f else ((now - confirmedAt) / fadeInMs.toFloat()).coerceAtMost(1f)
            val gain = volume / 100f * fraction
            if (gain != lastGain) {
                try {
                    media.setVolume(gain, gain)
                    lastGain = gain
                } catch (_: RuntimeException) {
                    fail(Failure.SOUND_UNAVAILABLE)
                    return
                }
            }
            handler.postDelayed(tick, ROUTE_CHECK_MS)
        }

        private fun clearPlayer() {
            handler.removeCallbacks(tick)
            handler.removeCallbacks(timeout)
            val previous = player
            player = null
            previous?.let {
                runCatching { it.setVolume(0f, 0f) }
                runCatching { it.release() }
            }
            candidate = null
            confirmed = null
            lastGain = 0f
        }

        fun close() {
            clearPlayer()
            if (receiverRegistered) app.unregisterReceiver(noisy)
            if (callbackRegistered) audio.unregisterAudioDeviceCallback(deviceCallback)
            focus?.let(audio::abandonAudioFocusRequest)
        }

        private fun fail(failure: Failure) {
            if (session !== this) return
            stop()
            onFailure(failure)
        }
    }

    private companion object {
        const val ROUTE_CHECK_MS = 75L
        const val ROUTE_TIMEOUT_MS = 3_000L
        const val PREPARE_TIMEOUT_MS = 8_000L
    }
}

internal data class OutputIdentity(val id: Int, val type: Int, val address: String)

private fun AudioDeviceInfo.identity() = OutputIdentity(id, type, address)

internal fun verifiedOutputIds(routes: List<OutputIdentity>, allowed: Set<OutputIdentity>): Set<Int>? =
    routes.takeIf { it.isNotEmpty() && allowed.containsAll(it) }?.mapTo(mutableSetOf()) { it.id }
