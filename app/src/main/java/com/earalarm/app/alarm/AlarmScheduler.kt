package com.earalarm.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.earalarm.app.MainActivity
import com.earalarm.app.data.local.AlarmEntity
import com.earalarm.app.data.repository.AlarmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.UUID

class AlarmScheduler(private val context: Context, private val repository: AlarmRepository) {
    data class Delivery(val id: Long, val scheduleUpdateFailed: Boolean = false)
    data class TimerState(val remaining: Long, val running: Boolean, val total: Long)

    // A null token identifies a pending intent created by an older app version.
    private data class Schedule(val at: Long, val token: String?)

    private val manager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("scheduled", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    fun canSchedule() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    private fun operation(key: String, schedule: Schedule, existingOnly: Boolean = false): PendingIntent? {
        val uri = Uri.parse("earalarm://event/$key").buildUpon()
        schedule.token?.let { uri.appendQueryParameter("token", it) }
        val intent = Intent(context, AlarmReceiver::class.java)
            .setData(uri.build()).putExtra("key", key).putExtra("at", schedule.at)
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or (if (existingOnly) PendingIntent.FLAG_NO_CREATE else 0))
    }

    private fun read(key: String): Schedule? =
        prefs.getLong(key, 0).takeIf { it > 0 }?.let { Schedule(it, prefs.getString("token/$key", null)) }

    private fun SharedPreferences.Editor.write(key: String, schedule: Schedule?) = apply {
        if (schedule == null) remove(key) else putLong(key, schedule.at)
        putString("token/$key", schedule?.token)
    }

    private fun cancelKey(key: String) {
        val previous = read(key) ?: return
        if (!prefs.edit().write(key, null).commit()) {
            prefs.edit().write(key, previous).commit()
            error("알람 예약을 삭제하지 못했어요")
        }
        cancelOperation(key, previous)
    }

    private fun cancelOperation(key: String, schedule: Schedule) {
        operation(key, schedule, existingOnly = true)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    private fun scheduleOperation(key: String, schedule: Schedule, operation: PendingIntent) {
        if (key == TIMER) {
            manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, schedule.at, operation)
        } else {
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(schedule.at, show), operation)
        }
    }

    private fun register(key: String, at: Long, timerTotal: Long? = null) {
        check(canSchedule()) { "정확한 알람 권한을 먼저 허용해 주세요" }
        val previous = read(key)
        val next = Schedule(at, UUID.randomUUID().toString())
        val pending = checkNotNull(operation(key, next))
        val timerBefore = if (timerTotal != null) prefs.all else emptyMap()
        try {
            // Each generation has a separate PendingIntent. The old alarm stays registered
            // until its replacement succeeds. consume() waits for the same mutex.
            scheduleOperation(key, next, pending)
            val editor = prefs.edit().write(key, next)
            if (timerTotal != null) editor.remove("timerPaused").putLong("timerTotal", timerTotal)
            if (!editor.commit()) {
                val rollback = prefs.edit().write(key, previous)
                if (timerTotal != null) {
                    for (field in listOf("timerPaused", "timerTotal")) {
                        val value = timerBefore[field] as? Long
                        if (value == null) rollback.remove(field) else rollback.putLong(field, value)
                    }
                }
                rollback.commit()
                error("알람 예약을 저장하지 못했어요")
            }
        } catch (e: Exception) {
            cancelOperation(key, next)
            throw e
        }
        previous?.let { cancelOperation(key, it) }
    }

    private fun schedule(alarm: AlarmEntity) {
        val at = nextOccurrence(alarm.hour, alarm.minute, alarm.repeatMask, ZonedDateTime.now())
            .toInstant().toEpochMilli()
        register("alarm/${alarm.id}", at)
    }

    suspend fun save(alarm: AlarmEntity) = mutate {
        require(alarm.hour in 0..23 && alarm.minute in 0..59 && alarm.repeatMask in 0..127)
        if (alarm.enabled) check(canSchedule()) { "정확한 알람 권한을 먼저 허용해 주세요" }
        val previous = if (alarm.id == 0L) null else checkNotNull(repository.get(alarm.id)) {
            "이미 삭제된 알람이에요"
        }
        val id = repository.save(alarm)
        val saved = alarm.copy(id = id)
        try {
            if (saved.enabled) schedule(saved) else cancelKey("alarm/$id")
        } catch (e: Exception) {
            if (previous == null) repository.delete(saved) else repository.save(previous)
            throw e
        }
        cancelKey("snooze/$id")
    }

    suspend fun delete(alarm: AlarmEntity) = mutate {
        cancelKey("alarm/${alarm.id}")
        cancelKey("snooze/${alarm.id}")
        repository.delete(alarm)
    }

    suspend fun restore(afterBoot: Boolean = false, recalculate: Boolean = false, reapply: Boolean = false) = mutate {
        if (afterBoot) {
            cancelKey(TIMER)
            prefs.edit().remove("timerPaused").remove("timerTotal").commit()
            prefs.all.keys.filter { it.startsWith("snooze/") }.forEach { cancelKey(it) }
        }
        if (!canSchedule()) return@mutate
        var firstFailure: Exception? = null
        for (alarm in repository.enabledAlarms()) {
            try {
                val key = "alarm/${alarm.id}"
                val previous = read(key)
                if (afterBoot || recalculate || previous == null) {
                    schedule(alarm)
                } else if (reapply && previous.at > System.currentTimeMillis()) {
                    scheduleOperation(key, previous, checkNotNull(operation(key, previous)))
                } else if (operation(key, previous, existingOnly = true) == null) {
                    schedule(alarm)
                }
            } catch (e: Exception) {
                if (firstFailure == null) firstFailure = e
            }
        }
        firstFailure?.let { throw it }
    }

    fun timerState(): TimerState {
        val values = prefs.all
        val at = values[TIMER] as? Long
        val remaining = if (at != null) (at - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            else (values["timerPaused"] as? Long ?: 0L)
        return TimerState(remaining, at != null, maxOf(values["timerTotal"] as? Long ?: 0L, remaining))
    }

    suspend fun startTimer(duration: Long) = mutate {
        require(duration in 1..MAX_TIMER) { "타이머는 1분부터 7일 이내로 입력해 주세요" }
        val timer = timerState()
        if (timer.running) return@mutate
        val total = if (timer.remaining > 0) maxOf(timer.total, duration) else duration
        register(TIMER, SystemClock.elapsedRealtime() + duration, total)
    }

    /** An expired timer belongs to its pending delivery, even if it hasn't reached the receiver yet. */
    suspend fun adjustTimer(delta: Long) = mutate {
        val timer = timerState()
        if (timer.remaining <= 0 || delta !in -MAX_TIMER..MAX_TIMER) return@mutate
        val remaining = timer.remaining + delta
        if (remaining !in 1_000..MAX_TIMER) return@mutate
        val total = (timer.total + delta).coerceAtLeast(remaining)
        if (timer.running) {
            register(TIMER, SystemClock.elapsedRealtime() + remaining, total)
        } else {
            check(prefs.edit().putLong("timerPaused", remaining).putLong("timerTotal", total).commit())
        }
    }

    suspend fun pauseTimer() = mutate {
        val timer = timerState()
        if (!timer.running || timer.remaining <= 0) return@mutate
        val current = read(TIMER) ?: return@mutate
        check(prefs.edit().write(TIMER, null).putLong("timerPaused", timer.remaining).commit())
        cancelOperation(TIMER, current)
    }

    suspend fun resetTimer() = mutate {
        cancelKey(TIMER)
        cancelKey("snooze/0")
        check(prefs.edit().remove("timerPaused").remove("timerTotal").commit())
    }

    suspend fun snooze(id: Long, minutes: Int) = mutate {
        if (minutes > 0 && (id == 0L || repository.get(id) != null)) {
            register("snooze/$id", System.currentTimeMillis() + minutes * 60_000L)
        }
    }

    /** Rejects deliveries from cancelled or superseded PendingIntents, even for the same timestamp. */
    suspend fun consume(key: String, at: Long, token: String?): Delivery? = mutate {
        val current = read(key) ?: return@mutate null
        if (current != Schedule(at, token)) return@mutate null
        val id = key.substringAfterLast('/').toLongOrNull() ?: return@mutate null
        check(prefs.edit().write(key, null).commit()) { "알람 예약 상태를 저장하지 못했어요" }
        cancelOperation(key, current)
        if (key == TIMER) return@mutate Delivery(0L)
        val alarm = if (id != 0L) repository.get(id) else null
        if (key.startsWith("snooze/")) return@mutate if (id == 0L || alarm != null) Delivery(id) else null
        if (alarm == null || !alarm.enabled) return@mutate null
        try {
            if (alarm.repeatMask == 0) repository.setEnabled(id, false) else schedule(alarm)
            Delivery(id)
        } catch (_: Exception) {
            // A failure to update the next occurrence must not suppress the one due now.
            Delivery(id, scheduleUpdateFailed = true)
        }
    }

    private suspend fun <T> mutate(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { withContext(NonCancellable) { action() } }
    }

    private companion object {
        const val TIMER = "timer/0"
        const val MAX_TIMER = 604_800_000L
    }
}
