package com.earalarm.app.alarm

import java.time.ZonedDateTime

/** Calendar alarms follow local wall time; java.time resolves DST gaps and overlaps. */
fun nextOccurrence(hour: Int, minute: Int, repeatMask: Int, now: ZonedDateTime): ZonedDateTime {
    require(hour in 0..23 && minute in 0..59 && repeatMask in 0..127)
    for (offset in 0L..7L) {
        val date = now.toLocalDate().plusDays(offset)
        val candidate = date.atTime(hour, minute).atZone(now.zone)
        val selected = repeatMask == 0 || repeatMask and (1 shl (date.dayOfWeek.value - 1)) != 0
        if (selected && candidate.toInstant() > now.toInstant()) return candidate
    }
    error("No next occurrence")
}
