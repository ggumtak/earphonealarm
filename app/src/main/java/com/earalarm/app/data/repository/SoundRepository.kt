package com.earalarm.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.earalarm.app.R
import com.earalarm.app.data.local.AlarmDao
import com.earalarm.app.data.local.CustomSoundDao
import com.earalarm.app.data.local.CustomSoundEntity
import com.earalarm.app.data.model.SoundRef
import com.earalarm.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** '내 음악' 목록 관리. SAF로 고른 파일의 영구 읽기 권한을 얻고 반납한다. */
class SoundRepository(
    private val context: Context,
    private val soundDao: CustomSoundDao,
    private val alarmDao: AlarmDao,
    private val settingsRepository: SettingsRepository,
) {

    val customSounds: Flow<List<CustomSoundEntity>> = soundDao.observeAll()

    /**
     * ActivityResultContracts.OpenDocument 로 받은 Uri를 등록한다.
     * 영구 권한을 얻지 못하면(일시 Uri 등) null을 돌려준다.
     */
    suspend fun addFromDevice(uri: Uri): CustomSoundEntity? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            return@withContext null
        }
        val name = queryDisplayName(uri)?.substringBeforeLast('.')
            ?: context.getString(R.string.sound_unknown_name)
        val entity = CustomSoundEntity(uri = uri.toString(), displayName = name)
        val id = soundDao.insert(entity)
        if (id == -1L) soundDao.getByUri(entity.uri) else entity.copy(id = id)
    }

    /** 목록에서 지우고, 이 음악을 쓰던 알람과 기본 알람음 설정을 기본값으로 되돌린다. */
    suspend fun remove(sound: CustomSoundEntity) = withContext(Dispatchers.IO) {
        val ref = SoundRef.Custom(sound.uri)
        alarmDao.clearSoundRef(ref.encode())
        if (settingsRepository.current().defaultSound == ref) {
            settingsRepository.setDefaultSound(SoundRef.DEFAULT)
        }
        soundDao.delete(sound)
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(sound.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // 이미 권한이 없어진 경우(파일 삭제 등)는 무시
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}
