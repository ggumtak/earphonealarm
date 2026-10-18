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
            try { context.appContainer.scheduler.restore(intent.action == Intent.ACTION_BOOT_COMPLETED) }
            catch (_: Exception) { com.earalarm.app.service.AlarmForegroundService.reportFailure(context, "알람 재예약 실패: 앱을 열어 권한을 확인해 주세요.") }
            finally { pending.finish() }
        }
    }
}
