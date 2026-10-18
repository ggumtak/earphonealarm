package com.earalarm.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    /** 추가한 순서. 시간순 정렬은 화면에서 설정값에 따라 처리한다. */
    @Query("SELECT * FROM alarms ORDER BY createdAt, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    /** 재부팅·시간 변경 후 다시 등록할 알람 */
    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabled(): List<AlarmEntity>

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** 삭제된 내 음악을 쓰던 알람은 기본 알람음으로 돌린다 (개별 음량은 유지). */
    @Query("UPDATE alarms SET soundRef = NULL WHERE soundRef = :soundRef")
    suspend fun clearSoundRef(soundRef: String)
}
