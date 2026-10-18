package com.earalarm.app.data.model

import android.content.res.Resources
import androidx.annotation.StringRes
import com.earalarm.app.R
import com.earalarm.app.data.local.AlarmEntity

/** 다시 울림(스누즈) 간격 */
enum class SnoozeInterval(val minutes: Int) {
    OFF(0), MIN_3(3), MIN_5(5), MIN_10(10), MIN_15(15);

    fun label(res: Resources): String =
        if (this == OFF) res.getString(R.string.option_off) else res.getString(R.string.minutes_format, minutes)
}

/** 자동 종료 시간. [minutes]가 null이면 계속 울림 */
enum class AutoStopDuration(val minutes: Int?) {
    MIN_1(1), MIN_3(3), MIN_5(5), MIN_10(10), NEVER(null);

    fun label(res: Resources): String =
        minutes?.let { res.getString(R.string.minutes_format, it) } ?: res.getString(R.string.auto_stop_never)
}

/** 점점 크게 울리기 (페이드 인) */
enum class FadeInDuration(val seconds: Int) {
    OFF(0), SEC_10(10), SEC_30(30), MIN_1(60);

    fun label(res: Resources): String = when {
        this == OFF -> res.getString(R.string.option_off)
        seconds % 60 == 0 -> res.getString(R.string.fade_in_minutes, seconds / 60)
        else -> res.getString(R.string.fade_in_seconds, seconds)
    }
}

/** 이어폰 미연결 시 작동 방식 */
enum class NoEarphoneAction(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int?) {
    SILENT_NOTIFICATION(R.string.no_earphone_silent, null),
    VIBRATE_ONLY(R.string.no_earphone_vibrate, R.string.no_earphone_vibrate_desc),
    SPEAKER(R.string.no_earphone_speaker, null),
}

/** [설정] 탭의 전역 설정값 (DataStore에 저장) */
data class AlarmSettings(
    val defaultSound: SoundRef = SoundRef.DEFAULT,
    val volumePercent: Int = 70,
    val snooze: SnoozeInterval = SnoozeInterval.MIN_5,
    val autoStop: AutoStopDuration = AutoStopDuration.MIN_5,
    val fadeIn: FadeInDuration = FadeInDuration.OFF,
    val noEarphoneAction: NoEarphoneAction = NoEarphoneAction.SILENT_NOTIFICATION,
    val vibrateWithSound: Boolean = false,
    val use24Hour: Boolean = false,
    val sortAlarmsByTime: Boolean = false,
)

/** 실제로 울릴 알람음과 음량 */
data class PlaybackConfig(val sound: SoundRef, val volumePercent: Int)

/**
 * 알람별 개별 설정이 켜져 있으면 그 값을, 아니면 전역 설정을 쓴다.
 * 타이머처럼 개별 알람이 없는 경우 [alarm]에 null을 넘긴다.
 */
fun AlarmSettings.playbackFor(alarm: AlarmEntity?): PlaybackConfig =
    if (alarm != null && alarm.useCustomSound) {
        PlaybackConfig(
            sound = alarm.soundRef?.let(SoundRef::decode) ?: defaultSound,
            volumePercent = alarm.volumePercent ?: volumePercent,
        )
    } else {
        PlaybackConfig(defaultSound, volumePercent)
    }
