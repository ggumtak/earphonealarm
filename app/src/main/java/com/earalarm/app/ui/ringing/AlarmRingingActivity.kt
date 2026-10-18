package com.earalarm.app.ui.ringing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earalarm.app.appContainer
import com.earalarm.app.data.model.AlarmSettings
import com.earalarm.app.service.AlarmForegroundService
import com.earalarm.app.ui.theme.EarAlarmTheme

class AlarmRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { EarAlarmTheme {
            val active by AlarmForegroundService.active.collectAsStateWithLifecycle()
            val settings by appContainer.settingsRepository.settings.collectAsStateWithLifecycle(AlarmSettings())
            LaunchedEffect(active) { if (active == null) finish() }
            var label by remember { mutableStateOf("알람") }
            LaunchedEffect(active) { label = if (active == 0L) "타이머가 끝났습니다" else active?.let { appContainer.alarmRepository.get(it)?.label?.takeIf(String::isNotBlank) } ?: "알람" }
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                    Text(label, style = MaterialTheme.typography.headlineLarge)
                    Button(onClick = { command(AlarmForegroundService.STOP, active) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("알람 끄기") }
                    if (settings.snooze.minutes > 0) OutlinedButton(onClick = { command(AlarmForegroundService.SNOOZE, active) }, modifier = Modifier.fillMaxWidth()) { Text("${settings.snooze.minutes}분 뒤 다시 울림") }
                }
            }
        } }
    }
    private fun command(action: String, id: Long?) {
        if (id != null) startService(Intent(this, AlarmForegroundService::class.java).setAction(action).putExtra("id", id))
        finish()
    }
}
