package com.earalarm.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 사용자가 기기에서 추가한 음악 파일 (SAF content:// Uri, 영구 읽기 권한 보유) */
@Entity(
    tableName = "custom_sounds",
    indices = [Index(value = ["uri"], unique = true)],
)
data class CustomSoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
)
