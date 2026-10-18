package com.earalarm.app.data.repository

import com.earalarm.app.data.local.AlarmDao
import com.earalarm.app.data.local.AlarmEntity
import kotlinx.coroutines.flow.Flow

/** 알람 저장소. AlarmManager 등록/해제는 Phase 2의 AlarmScheduler가 이 위에서 처리한다. */
class AlarmRepository(private val dao: AlarmDao) {

    val alarms: Flow<List<AlarmEntity>> = dao.observeAll()

    suspend fun get(id: Long): AlarmEntity? = dao.getById(id)

    suspend fun enabledAlarms(): List<AlarmEntity> = dao.getEnabled()

    /** 새 알람이면 추가, 기존 알람이면 수정. 저장된 알람의 id를 돌려준다. */
    suspend fun save(alarm: AlarmEntity): Long =
        if (alarm.id == 0L) {
            dao.insert(alarm)
        } else {
            dao.update(alarm)
            alarm.id
        }

    suspend fun delete(alarm: AlarmEntity) = dao.delete(alarm)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
}
