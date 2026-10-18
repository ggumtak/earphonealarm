package com.earalarm.app.data.model

import android.content.res.Resources
import com.earalarm.app.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * 반복 요일을 비트마스크로 표현한다. 월요일 = 1번 비트(1), 일요일 = 7번 비트(64).
 * Room에는 [mask] 정수 그대로 저장한다.
 */
@JvmInline
value class RepeatDays(val mask: Int) {

    operator fun contains(day: DayOfWeek): Boolean = (mask and bit(day)) != 0

    fun toggle(day: DayOfWeek): RepeatDays = RepeatDays(mask xor bit(day))

    val isEmpty: Boolean get() = mask == 0

    /** 월요일부터 일요일 순서로 켜진 요일 */
    fun days(): List<DayOfWeek> = DayOfWeek.values().filter { it in this }

    companion object {
        val NONE = RepeatDays(0)
        val WEEKDAYS = RepeatDays(0b0011111)
        val WEEKEND = RepeatDays(0b1100000)
        val EVERYDAY = RepeatDays(0b1111111)

        fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}

/** 알람 카드에 보여줄 반복 요약: 한 번만 / 매일 / 평일 / 주말 / 월, 수, 금 */
fun RepeatDays.summary(resources: Resources): String = when (this) {
    RepeatDays.NONE -> resources.getString(R.string.repeat_once)
    RepeatDays.EVERYDAY -> resources.getString(R.string.repeat_everyday)
    RepeatDays.WEEKDAYS -> resources.getString(R.string.repeat_weekdays)
    RepeatDays.WEEKEND -> resources.getString(R.string.repeat_weekend)
    else -> days().joinToString(", ") { it.shortKoreanName() }
}

/** 월, 화, 수 … 한 글자 요일 이름 */
fun DayOfWeek.shortKoreanName(): String = getDisplayName(TextStyle.SHORT, Locale.KOREAN)
