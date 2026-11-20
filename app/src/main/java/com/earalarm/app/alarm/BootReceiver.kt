package com.earalarm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.earalarm.app.appContainer
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                val action = intent.action
                context.appContainer.scheduler.restore(
                    afterBoot = action == Intent.ACTION_BOOT_COMPLETED,
                    recalculate = action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED,
                    reapply = action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                        action == android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                )
            }
            catch (_: Exception) { com.earalarm.app.service.AlarmForegroundService.reportFailure(context, "알람 재예약 실패: 앱을 열어 권한을 확인해 주세요.") }
            finally { pending.finish() }
        }
    }
}
