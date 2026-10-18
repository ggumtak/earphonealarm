package com.earalarm.app.alarm

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class NextOccurrenceTest {
    @Test fun oneShotToday() {
        val now = ZonedDateTime.parse("2026-10-18T10:00:00+09:00[Asia/Seoul]")
        assertEquals("2026-10-18T11:30+09:00[Asia/Seoul]", nextOccurrence(11, 30, 0, now).toString())
    }
    @Test fun exactMinuteMovesToTomorrow() {
        val now = ZonedDateTime.parse("2026-10-18T10:00:00+09:00[Asia/Seoul]")
        assertEquals(19, nextOccurrence(10, 0, 0, now).dayOfMonth)
    }
    @Test fun weekdaysSkipWeekend() {
        val now = ZonedDateTime.parse("2026-10-16T18:00:00+09:00[Asia/Seoul]")
        assertEquals(19, nextOccurrence(7, 0, 31, now).dayOfMonth)
    }
    @Test fun sameWeekdayWrapsToNextWeek() {
        val now = ZonedDateTime.parse("2026-10-18T10:00:00+09:00[Asia/Seoul]")
        assertEquals(25, nextOccurrence(9, 0, 64, now).dayOfMonth)
    }
    @Test fun dstGapResolvesForward() {
        val now = ZonedDateTime.parse("2026-03-08T00:00:00-05:00[America/New_York]")
        val next = nextOccurrence(2, 30, 0, now)
        assertEquals(3, next.hour)
        assertTrue(next.toInstant() > now.toInstant())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidRepeatMask() {
        nextOccurrence(1, 0, 128, ZonedDateTime.now())
    }
}
