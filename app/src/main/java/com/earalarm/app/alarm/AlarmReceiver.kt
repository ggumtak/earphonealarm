package com.earalarm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.earalarm.app.appContainer
import com.earalarm.app.service.AlarmForegroundService
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        val pending = goAsync()
        context.appContainer.appScope.launch {
            try {
                val delivery = context.appContainer.scheduler.consume(
                    key, intent.getLongExtra("at", 0), intent.data?.getQueryParameter("token"),
                ) ?: return@launch
                if (delivery.scheduleUpdateFailed) {
                    AlarmForegroundService.reportFailure(context, "다음 알람 예약을 갱신하지 못했어요. 앱을 열어 확인해 주세요.")
                }
                ContextCompat.startForegroundService(context, Intent(context, AlarmForegroundService::class.java)
                    .setAction(AlarmForegroundService.RING).putExtra("id", delivery.id))
            } catch (e: Exception) {
                AlarmForegroundService.reportFailure(context, "알람을 시작하지 못했어요. 앱에서 권한을 확인해 주세요.")
            } finally { pending.finish() }
        }
    }
}
