package com.earalarm.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomSoundDao {

    @Query("SELECT * FROM custom_sounds ORDER BY addedAt")
    fun observeAll(): Flow<List<CustomSoundEntity>>

    @Query("SELECT * FROM custom_sounds WHERE uri = :uri")
    suspend fun getByUri(uri: String): CustomSoundEntity?

    /** 같은 파일을 두 번 추가하면 무시하고 -1을 돌려준다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sound: CustomSoundEntity): Long

    @Delete
    suspend fun delete(sound: CustomSoundEntity)
}
