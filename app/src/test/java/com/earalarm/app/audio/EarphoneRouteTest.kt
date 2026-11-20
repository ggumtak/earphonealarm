package com.earalarm.app.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarphoneRouteTest {
    private val wired = OutputIdentity(1, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "")
    private val buds = OutputIdentity(2, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "12:34:56:78:90:AB")
    private val speaker = OutputIdentity(3, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "")
    private val allowed = setOf(wired, buds)

    @Test fun requiresAtLeastOneVerifiedRoute() {
        assertNull(verifiedOutputIds(emptyList(), allowed))
        assertEquals(setOf(buds.id), verifiedOutputIds(listOf(buds), allowed))
    }

    @Test fun rejectsSpeakerEvenWhenAnEarphoneIsAlsoRouted() {
        assertNull(verifiedOutputIds(listOf(buds, speaker), allowed))
    }

    @Test fun rejectsAReusedIdForAnUnverifiedBluetoothDevice() {
        assertNull(verifiedOutputIds(listOf(buds.copy(address = "AB:90:78:56:34:12")), allowed))
    }
}
