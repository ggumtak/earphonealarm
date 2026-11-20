package com.earalarm.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

/** Identifies personal audio outputs from Android's device types and Bluetooth class. */
class EarphoneDetector(context: Context) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val earphoneTypes = buildList {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_HEARING_AID)
    }

    fun outputs(): List<AudioDeviceInfo> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { device ->
                device.isSink && device.type in earphoneTypes &&
                    (device.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || isBluetoothHeadphone(device.address))
            }
            .sortedBy { earphoneTypes.indexOf(it.type) }
    }

    @SuppressLint("MissingPermission") // Checked below, and revocation during lookup is handled.
    private fun isBluetoothHeadphone(address: String): Boolean {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val deviceClass = app.getSystemService(BluetoothManager::class.java)
                ?.adapter?.getRemoteDevice(address)?.bluetoothClass?.deviceClass
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET
        } catch (_: SecurityException) {
            false
        }
    }

    fun refresh() { refreshes.tryEmit(Unit) }

    fun currentState(): EarphoneState {
        val device = outputs().firstOrNull() ?: return EarphoneState.Disconnected
        val type = device.connectionType()
        val name = if (type == ConnectionType.BLUETOOTH) {
            runCatching { device.productName?.toString()?.takeIf { it.isNotBlank() } }.getOrNull()
        } else {
            null
        }
        return EarphoneState.Connected(type, name)
    }

    private val deviceChanges = callbackFlow<Unit> {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(Unit)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    val state: Flow<EarphoneState> = merge(deviceChanges, refreshes)
        .map { currentState() }
        .distinctUntilChanged()

    private fun AudioDeviceInfo.connectionType(): ConnectionType = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> ConnectionType.WIRED
        AudioDeviceInfo.TYPE_USB_HEADSET -> ConnectionType.USB
        else -> ConnectionType.BLUETOOTH
    }
}
