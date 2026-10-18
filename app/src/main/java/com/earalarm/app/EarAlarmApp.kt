package com.earalarm.app

import android.app.Application
import android.content.Context
import com.earalarm.app.audio.EarphoneDetector
import com.earalarm.app.audio.EarphoneState
import com.earalarm.app.data.local.AppDatabase
import com.earalarm.app.data.repository.AlarmRepository
import com.earalarm.app.data.repository.SoundRepository
import com.earalarm.app.data.settings.SettingsRepository
import com.earalarm.app.data.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class EarAlarmApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 리시버·서비스·액티비티 어디서든 같은 객체를 쓰기 위한 간단한 수동 DI */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 화면 수명과 무관하게 돌아가야 하는 작업용 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { AppDatabase.create(appContext) }

    val settingsRepository by lazy { SettingsRepository(appContext.settingsDataStore) }
    val alarmRepository by lazy { AlarmRepository(database.alarmDao()) }
    val scheduler by lazy { com.earalarm.app.alarm.AlarmScheduler(appContext, alarmRepository) }
    val soundRepository by lazy {
        SoundRepository(appContext, database.customSoundDao(), database.alarmDao(), settingsRepository)
    }

    val earphoneDetector by lazy { EarphoneDetector(appContext) }

    /** 화면이 보고 있을 때만 감지 콜백을 등록하고, 떠난 뒤 5초 지나면 해제한다. */
    val earphoneState: StateFlow<EarphoneState> by lazy {
        earphoneDetector.state.stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = earphoneDetector.currentState(),
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as EarAlarmApp).container
