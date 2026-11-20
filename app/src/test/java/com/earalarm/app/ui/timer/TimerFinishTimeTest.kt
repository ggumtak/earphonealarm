package com.earalarm.app.ui.timer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TimerFinishTimeTest {
    @Test fun endTimeIsStableAsRemainingSecondsRoundAcrossBoundaries() {
        val startOfSecond = Instant.parse("2026-11-20T10:00:00Z")
        val expectedFinish = startOfSecond.plusMillis(30_200)
        val deadline = 40_200L

        for (offset in listOf(100L, 300L, 1_100L, 1_300L, 30_200L)) {
            val now = startOfSecond.plusMillis(offset)
            val elapsed = 10_000L + offset
            assertEquals(expectedFinish, finishInstant(now, elapsed, deadline))
        }
    }
}
