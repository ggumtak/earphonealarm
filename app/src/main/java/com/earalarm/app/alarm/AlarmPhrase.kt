package com.earalarm.app.alarm

import com.earalarm.app.data.model.RepeatDays
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal data class AlarmPhrase(val time: LocalTime, val repeatDays: RepeatDays, val onceAtMillis: Long? = null)

private val requestEnding = Regex("""\s*(?:알람(?:을)?\s*(?:(?:맞춰|설정해)\s*줘)?|깨워\s*줘|알려\s*줘)[.!?]?$""")
private val relativeTime = Regex("""(?:지금부터\s*)?(?:(\d{1,3})\s*시간\s*)?(?:(\d{1,5})\s*분\s*)?(?:뒤|후)(?:에)?""")
private val dayPrefix = Regex("""^(오늘|내일|모레|매일|평일|주말)\s*""")
private val weeklyPrefix = Regex("""^매주\s*([월화수목금토일](?:요일)?(?:(?:\s*[,·/]\s*|\s+)[월화수목금토일](?:요일)?)*)\s*""")
private val timeOfDay = Regex("""(?:(오전|오후|아침|저녁)\s*)?(\d{1,2})(?:\s*시(?:\s*(\d{1,2})\s*분)?|:(\d{2}))\s*에?""")
private val weekdays = "월화수목금토일"

internal fun parseAlarmPhrase(input: String, now: ZonedDateTime): AlarmPhrase {
    require(input.length <= 120) { "알람 문장을 120자 이내로 입력해 줘." }
    val text = input.trim().replace(requestEnding, "").trim()
    relativeTime.matchEntire(text)?.destructured?.let { (hours, minutes) ->
        val duration = (hours.toIntOrNull() ?: 0) * 60L + (minutes.toIntOrNull() ?: 0)
        require(duration in 1..10_080) { "1분부터 7일 이내로 입력해 줘." }
        val target = now.plusMinutes(duration)
        val minute = target.truncatedTo(ChronoUnit.MINUTES)
        return onceAt(if (target == minute) minute else minute.plusMinutes(1))
    }

    val prefix = dayPrefix.find(text)
    val weekly = weeklyPrefix.find(text)
    val clock = text.drop((prefix?.value ?: weekly?.value).orEmpty().length)
    val (period, hourText, minuteText, colonMinute) = requireNotNull(timeOfDay.matchEntire(clock)) {
        "시간을 알아보지 못했어. ‘내일 오전 7시’처럼 입력해 줘."
    }.destructured
    val hour = hourText.toInt()
    val minute = minuteText.ifEmpty { colonMinute }.ifEmpty { "0" }.toInt()
    require(minute in 0..59 && (if (period.isEmpty()) hour in 0..23 else hour in 1..12)) {
        "입력한 시각을 확인해 줘."
    }
    val afternoon = period in listOf("오후", "저녁")
    val clockHour = if (period.isEmpty()) hour else hour % 12 + (if (afternoon) 12 else 0)
    val time = LocalTime.of(clockHour, minute)
    val dayPrefixText = prefix?.groupValues?.get(1)
    val days = when (dayPrefixText) {
        "매일" -> RepeatDays.EVERYDAY
        "평일" -> RepeatDays.WEEKDAYS
        "주말" -> RepeatDays.WEEKEND
        "오늘", "내일", "모레" -> {
            val offset = listOf("오늘", "내일", "모레").indexOf(dayPrefixText).toLong()
            val target = now.toLocalDate().plusDays(offset).atTime(time).atZone(now.zone)
            require(target.isAfter(now)) { "이미 지난 시각이야. 새 날짜나 시간을 입력해 줘." }
            return onceAt(target)
        }
        else -> weekly?.groupValues?.get(1)?.let { dayText ->
            val mask = dayText.filter { it in weekdays }.fold(0) { bits, day ->
                bits or RepeatDays.bit(DayOfWeek.of(weekdays.indexOf(day) + 1))
            }
            RepeatDays(mask)
        } ?: RepeatDays.NONE
    }
    return AlarmPhrase(time, days)
}

private fun onceAt(target: ZonedDateTime) =
    AlarmPhrase(target.toLocalTime(), RepeatDays.NONE, target.toInstant().toEpochMilli())
