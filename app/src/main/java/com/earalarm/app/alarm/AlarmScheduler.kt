package com.earalarm.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.earalarm.app.MainActivity
import com.earalarm.app.data.local.AlarmEntity
import com.earalarm.app.data.repository.AlarmRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

class AlarmScheduler(private val context: Context, private val repository: AlarmRepository) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("scheduled", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    fun canSchedule() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun operation(key: String, at: Long = 0L) = PendingIntent.getBroadcast(context, 0,
        Intent(context, AlarmReceiver::class.java).setData(Uri.parse("earalarm://event/$key"))
            .putExtra("key", key).putExtra("at", at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancelKey(key: String) {
        manager.cancel(operation(key))
        prefs.edit().remove(key).commit()
    }
    private fun register(key: String, at: Long, elapsed: Boolean = false) {
        check(canSchedule()) { "정확한 알람 권한을 먼저 허용해 주세요" }
        // Commit before registering; delivery verifies this generation to reject stale intents.
        check(prefs.edit().putLong(key, at).commit())
        try {
            if (elapsed) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, operation(key, at))
            else {
                val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation(key, at))
            }
        } catch (e: Exception) { prefs.edit().remove(key).commit(); throw e }
    }
    private fun schedule(alarm: AlarmEntity) {
        if (alarm.enabled) register("alarm/${alarm.id}", nextOccurrence(alarm.hour, alarm.minute, alarm.repeatMask,
            ZonedDateTime.now()).toInstant().toEpochMilli())
    }
    suspend fun save(alarm: AlarmEntity) = mutex.withLock {
        require(alarm.hour in 0..23 && alarm.minute in 0..59 && alarm.repeatMask in 0..127)
        if (alarm.enabled) check(canSchedule()) { "정확한 알람 권한을 먼저 허용해 주세요" }
        val id = repository.save(alarm)
        cancelKey("alarm/$id"); cancelKey("snooze/$id")
        try { schedule(alarm.copy(id = id)) }
        catch (e: Exception) { repository.setEnabled(id, false); throw e }
    }
    suspend fun delete(alarm: AlarmEntity) = mutex.withLock {
        cancelKey("alarm/${alarm.id}"); cancelKey("snooze/${alarm.id}")
        repository.delete(alarm)
    }
    suspend fun restore(afterBoot: Boolean = false) = mutex.withLock {
        if (afterBoot) {
            prefs.edit().remove("timer/0").remove("timerPaused").remove("timerTotal").commit()
            prefs.all.keys.filter { it.startsWith("snooze/") }.forEach { cancelKey(it) }
        }
        if (!canSchedule()) return@withLock
        repository.enabledAlarms().forEach { schedule(it) }
    }
    fun timerRemaining(): Long = if (prefs.contains("timer/0"))
        (prefs.getLong("timer/0", 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        else prefs.getLong("timerPaused", 0)
    fun timerRunning() = prefs.contains("timer/0")
    /** 진행 표시용 전체 길이. 일시정지 후 계속하거나 시간을 더해도 유지된다. */
    fun timerTotal(): Long = maxOf(prefs.getLong("timerTotal", 0), timerRemaining())
    suspend fun startTimer(duration: Long) = mutex.withLock {
        require(duration in 1..MAX_TIMER) { "타이머는 1분부터 7일 이내로 입력해 주세요" }
        val resuming = prefs.getLong("timerPaused", 0) > 0
        register("timer/0", SystemClock.elapsedRealtime() + duration, true)
        prefs.edit().remove("timerPaused").putLong("timerTotal", if (resuming) maxOf(prefs.getLong("timerTotal", 0), duration) else duration).commit()
    }
    /** 진행 중이거나 일시정지된 타이머에 시간을 더하거나 뺀다. 1초 미만으로 줄어들면 무시한다. */
    suspend fun adjustTimer(delta: Long) = mutex.withLock {
        val remaining = timerRemaining() + delta
        if (remaining < 1000 || remaining > MAX_TIMER) return@withLock
        when {
            timerRunning() -> register("timer/0", SystemClock.elapsedRealtime() + remaining, true)
            prefs.getLong("timerPaused", 0) > 0 -> prefs.edit().putLong("timerPaused", remaining).commit()
            else -> return@withLock
        }
        prefs.edit().putLong("timerTotal", (prefs.getLong("timerTotal", 0) + delta).coerceAtLeast(remaining)).commit()
    }
    suspend fun pauseTimer() = mutex.withLock {
        val remaining = timerRemaining()
        cancelKey("timer/0")
        prefs.edit().putLong("timerPaused", remaining).commit()
    }
    suspend fun resetTimer() = mutex.withLock {
        cancelKey("timer/0"); cancelKey("snooze/0")
        prefs.edit().remove("timerPaused").remove("timerTotal").commit()
    }
    suspend fun snooze(id: Long, minutes: Int) = mutex.withLock {
        if (minutes > 0 && (id == 0L || repository.get(id) != null))
            register("snooze/$id", System.currentTimeMillis() + minutes * 60000L)
    }
    /** Returns null for duplicate, cancelled, deleted or superseded broadcasts. */
    suspend fun consume(key: String, at: Long): Long? = mutex.withLock {
        if (at <= 0 || prefs.getLong(key, 0) != at) return@withLock null
        val id = key.substringAfterLast('/').toLongOrNull() ?: return@withLock null
        prefs.edit().remove(key).commit()
        if (key.startsWith("timer/")) return@withLock 0L
        val alarm = repository.get(id)
        if (key.startsWith("snooze/")) return@withLock if (id == 0L || alarm != null) id else null
        if (alarm == null || !alarm.enabled) return@withLock null
        if (alarm.repeatMask == 0) repository.setEnabled(id, false) else schedule(alarm)
        id
    }
    private companion object { const val MAX_TIMER = 604800000L }
}
