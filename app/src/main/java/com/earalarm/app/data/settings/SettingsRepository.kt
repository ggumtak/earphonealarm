package com.earalarm.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.earalarm.app.data.model.AlarmSettings
import com.earalarm.app.data.model.AutoStopDuration
import com.earalarm.app.data.model.FadeInDuration
import com.earalarm.app.data.model.NoEarphoneAction
import com.earalarm.app.data.model.SnoozeInterval
import com.earalarm.app.data.model.SoundRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

const val MAX_TIMER_MINUTES = 999

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val DEFAULT_SOUND = stringPreferencesKey("default_sound")
        val VOLUME = intPreferencesKey("volume_percent")
        val SNOOZE = stringPreferencesKey("snooze")
        val AUTO_STOP = stringPreferencesKey("auto_stop")
        val FADE_IN = stringPreferencesKey("fade_in")
        val NO_EARPHONE = stringPreferencesKey("no_earphone_action")
        val VIBRATE_WITH_SOUND = booleanPreferencesKey("vibrate_with_sound")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val SORT_BY_TIME = booleanPreferencesKey("sort_alarms_by_time")
        val TIMER_MINUTES = intPreferencesKey("timer_minutes")
    }

    val settings: Flow<AlarmSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** 서비스·리시버처럼 한 번만 읽으면 되는 곳에서 사용 */
    suspend fun current(): AlarmSettings = settings.first()

    suspend fun setDefaultSound(sound: SoundRef) = edit { it[Keys.DEFAULT_SOUND] = sound.encode() }
    suspend fun setVolume(percent: Int) = edit { it[Keys.VOLUME] = percent.coerceIn(0, 100) }
    suspend fun setSnooze(value: SnoozeInterval) = edit { it[Keys.SNOOZE] = value.name }
    suspend fun setAutoStop(value: AutoStopDuration) = edit { it[Keys.AUTO_STOP] = value.name }
    suspend fun setFadeIn(value: FadeInDuration) = edit { it[Keys.FADE_IN] = value.name }
    suspend fun setNoEarphoneAction(value: NoEarphoneAction) = edit { it[Keys.NO_EARPHONE] = value.name }
    suspend fun setVibrateWithSound(enabled: Boolean) = edit { it[Keys.VIBRATE_WITH_SOUND] = enabled }
    suspend fun setUse24Hour(enabled: Boolean) = edit { it[Keys.USE_24_HOUR] = enabled }
    suspend fun setSortAlarmsByTime(enabled: Boolean) = edit { it[Keys.SORT_BY_TIME] = enabled }
    suspend fun setTimerMinutes(minutes: Int) = edit { it[Keys.TIMER_MINUTES] = minutes.coerceIn(1, MAX_TIMER_MINUTES) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings(): AlarmSettings {
        val defaults = AlarmSettings()
        return AlarmSettings(
            defaultSound = this[Keys.DEFAULT_SOUND]?.let(SoundRef::decode) ?: defaults.defaultSound,
            volumePercent = this[Keys.VOLUME] ?: defaults.volumePercent,
            snooze = this[Keys.SNOOZE].toEnumOr(defaults.snooze),
            autoStop = this[Keys.AUTO_STOP].toEnumOr(defaults.autoStop),
            fadeIn = this[Keys.FADE_IN].toEnumOr(defaults.fadeIn),
            noEarphoneAction = this[Keys.NO_EARPHONE].toEnumOr(defaults.noEarphoneAction),
            vibrateWithSound = this[Keys.VIBRATE_WITH_SOUND] ?: defaults.vibrateWithSound,
            use24Hour = this[Keys.USE_24_HOUR] ?: defaults.use24Hour,
            sortAlarmsByTime = this[Keys.SORT_BY_TIME] ?: defaults.sortAlarmsByTime,
            timerMinutes = this[Keys.TIMER_MINUTES] ?: defaults.timerMinutes,
        )
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
