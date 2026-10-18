package com.earalarm.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.earalarm.app.data.model.RepeatDays

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0~23 */
    val hour: Int,
    /** 0~59 */
    val minute: Int,
    /** [RepeatDays.mask]. 0이면 한 번만 울리고 꺼진다. */
    val repeatMask: Int = 0,
    /** 알람 메모 */
    val label: String = "",
    val enabled: Boolean = true,
    /** false면 '기본 설정 따름', true면 아래 개별 알람음·음량 사용 */
    val useCustomSound: Boolean = false,
    /** [com.earalarm.app.data.model.SoundRef.encode] 값. null이면 기본 알람음 */
    val soundRef: String? = null,
    /** 0~100. null이면 기본 음량 */
    val volumePercent: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Room이 컬럼으로 오인하지 않도록 엔티티 밖의 확장 프로퍼티로 둔다. */
val AlarmEntity.repeatDays: RepeatDays get() = RepeatDays(repeatMask)
