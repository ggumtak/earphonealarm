package com.earalarm.app.alarm

import com.earalarm.app.data.local.AlarmEntity
import com.earalarm.app.data.model.RepeatDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

class AlarmPhraseTest {
    private val now = ZonedDateTime.parse("2026-11-21T06:40:15+09:00[Asia/Seoul]")

    @Test fun tomorrowDoesNotFallBackToTodayBeforeSeven() {
        val phrase = parseAlarmPhrase("내일 오전 7시에 깨워줘", now)
        val expected = ZonedDateTime.parse("2026-11-22T07:00+09:00[Asia/Seoul]")
        assertEquals(expected.toInstant().toEpochMilli(), phrase.onceAtMillis)
        assertEquals(expected, AlarmEntity(hour = 7, minute = 0, onceAtMillis = phrase.onceAtMillis).scheduledOccurrence(now))
    }

    @Test fun understandsRecurringKoreanPhrases() {
        val weekday = parseAlarmPhrase("평일 아침 7시 30분", now)
        assertEquals(RepeatDays.WEEKDAYS, weekday.repeatDays)
        assertEquals(LocalTime.of(7, 30), weekday.time)
        assertEquals(0b0010101, parseAlarmPhrase("매주 월·수·금 오후 6:15", now).repeatDays.mask)
        assertNull(weekday.onceAtMillis)
    }

    @Test fun relativeAlarmKeepsTheExactDelay() {
        val phrase = parseAlarmPhrase("1시간 30분 뒤에 알려줘", now)
        assertEquals(Instant.parse("2026-11-20T23:10:15Z").toEpochMilli(), phrase.onceAtMillis)
        assertEquals(RepeatDays.NONE, phrase.repeatDays)
    }

    @Test fun understandsMidnightAndNoon() {
        assertEquals(LocalTime.MIDNIGHT, parseAlarmPhrase("오전 12시", now).time)
        assertEquals(LocalTime.NOON, parseAlarmPhrase("오후 12시", now).time)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnAlreadyPassedTimeToday() {
        parseAlarmPhrase("오늘 오전 6시", now)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnInvalidClockTime() {
        parseAlarmPhrase("오전 13시 70분", now)
    }
}
