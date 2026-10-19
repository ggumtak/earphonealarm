package com.earalarm.app.ui.ringing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earalarm.app.appContainer
import com.earalarm.app.data.model.AlarmSettings
import com.earalarm.app.service.AlarmForegroundService
import com.earalarm.app.ui.components.splitTime
import com.earalarm.app.ui.theme.AppIcons
import com.earalarm.app.ui.theme.EarAlarmColors
import com.earalarm.app.ui.theme.EarAlarmTheme
import kotlinx.coroutines.delay
import java.time.LocalTime

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
            val now by produceState(LocalTime.now()) { while (true) { delay(1000); value = LocalTime.now() } }
            Surface(Modifier.fillMaxSize(), color = EarAlarmColors.Background) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    Icon(if (active == 0L) AppIcons.Hourglass else AppIcons.Alarm, null, Modifier.size(56.dp), tint = EarAlarmColors.TealBright)
                    Spacer(Modifier.height(24.dp))
                    val (time, period) = splitTime(now, settings.use24Hour)
                    Row {
                        Text(time, Modifier.alignByBaseline(), fontSize = 76.sp, color = EarAlarmColors.Text)
                        if (period.isNotEmpty()) Text(" $period", Modifier.alignByBaseline(), fontSize = 36.sp, color = EarAlarmColors.Text)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(label, fontSize = 24.sp, color = EarAlarmColors.TextMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.weight(1.4f))
                    if (settings.snooze.minutes > 0) {
                        OutlinedButton(
                            onClick = { command(AlarmForegroundService.SNOOZE, active) },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.5.dp, EarAlarmColors.Teal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EarAlarmColors.Text),
                        ) { Text("${settings.snooze.minutes}분 뒤 다시 울림", fontSize = 18.sp) }
                        Spacer(Modifier.height(16.dp))
                    }
                    Button(
                        onClick = { command(AlarmForegroundService.STOP, active) },
                        modifier = Modifier.fillMaxWidth().height(76.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = EarAlarmColors.Teal, contentColor = Color.White),
                    ) { Text("알람 끄기", fontSize = 22.sp) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        } }
    }
    private fun command(action: String, id: Long?) {
        if (id != null) startService(Intent(this, AlarmForegroundService::class.java).setAction(action).putExtra("id", id))
        finish()
    }
}
