package com.earalarm.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import com.earalarm.app.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectionType(@StringRes val labelRes: Int) {
    WIRED(R.string.connection_wired),
    USB(R.string.connection_usb),
    BLUETOOTH(R.string.connection_bluetooth),
}

sealed interface EarphoneState {
    data object Disconnected : EarphoneState

    /** [deviceName]은 블루투스 기기 이름(예: Galaxy Buds2). 유선·USB는 null */
    data class Connected(val type: ConnectionType, val deviceName: String?) : EarphoneState
}

/**
 * AudioManager의 출력 장치 목록으로 이어폰 연결 여부를 판단한다.
 *
 * - [currentState] : 지금 이 순간의 상태 (서비스·리시버에서 알람을 울릴지 판단할 때)
 * - [state]        : 연결/해제될 때마다 새 값을 내보내는 Flow (상단 배지 실시간 표시)
 * - [preferredOutputDevice] : 소리를 보낼 이어폰 장치. Phase 2에서 MediaPlayer.setPreferredDevice()에 넘긴다.
 *
 * 블루투스 스피커나 차량 오디오도 A2DP로 잡히기 때문에 '이어폰'으로 판단된다는 점에 유의.
 */
class EarphoneDetector(context: Context) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(AudioManager::class.java)

    /** 우선순위 순서 (앞일수록 먼저 선택) */
    private val earphoneTypes: List<Int> = buildList {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AudioDeviceInfo.TYPE_HEARING_AID)
        // USB-C 오디오 젠더(DAC) 중 일부는 USB_HEADSET 대신 USB_DEVICE로 잡힌다.
        add(AudioDeviceInfo.TYPE_USB_DEVICE)
    }

    fun preferredOutputDevice(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink && it.type in earphoneTypes }
            .minByOrNull { earphoneTypes.indexOf(it.type) }

    fun isConnected(): Boolean = preferredOutputDevice() != null

    fun currentState(): EarphoneState {
        val device = preferredOutputDevice() ?: return EarphoneState.Disconnected
        val type = device.connectionType()
        val name = if (type == ConnectionType.BLUETOOTH) {
            runCatching { device.productName?.toString()?.takeIf { it.isNotBlank() } }.getOrNull()
        } else {
            null
        }
        return EarphoneState.Connected(type, name)
    }

    val state: Flow<EarphoneState> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(currentState())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(currentState())
            }
        }
        trySend(currentState())
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()

    private fun AudioDeviceInfo.connectionType(): ConnectionType = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> ConnectionType.WIRED
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE -> ConnectionType.USB
        else -> ConnectionType.BLUETOOTH
    }
}
