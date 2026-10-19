package com.earalarm.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earalarm.app.audio.EarphoneState
import com.earalarm.app.data.local.AlarmEntity
import com.earalarm.app.data.model.AlarmSettings
import com.earalarm.app.service.AlarmForegroundService
import com.earalarm.app.ui.alarm.AlarmEditorSheet
import com.earalarm.app.ui.alarm.AlarmListScreen
import com.earalarm.app.ui.alarm.timeUntil
import com.earalarm.app.ui.components.EarphoneStatusChip
import com.earalarm.app.ui.components.formatDuration
import com.earalarm.app.ui.ringing.AlarmRingingActivity
import com.earalarm.app.ui.settings.SettingsScreen
import com.earalarm.app.ui.settings.openExactAlarmSettings
import com.earalarm.app.ui.settings.openNotificationSettings
import com.earalarm.app.ui.settings.rememberPermissionStatus
import com.earalarm.app.ui.theme.AppIcons
import com.earalarm.app.ui.theme.EarAlarmColors
import com.earalarm.app.ui.theme.EarAlarmTheme
import com.earalarm.app.ui.timer.TimerScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { EarAlarmTheme { Home() } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Home() {
    val context = LocalContext.current
    val app = context.appContainer
    val scope = rememberCoroutineScope()
    val earphone by app.earphoneState.collectAsStateWithLifecycle()
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(AlarmSettings())
    val alarms by app.alarmRepository.alarms.collectAsStateWithLifecycle(emptyList())
    val sounds by app.soundRepository.customSounds.collectAsStateWithLifecycle(emptyList())
    val active by AlarmForegroundService.active.collectAsStateWithLifecycle()
    val permissions by rememberPermissionStatus()
    val pager = rememberPagerState { 2 }
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<AlarmEntity?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val run: (suspend () -> Unit) -> Unit = { block -> scope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "작업을 완료하지 못했어요" }
    }; Unit }
    fun showMessage(message: String, action: String? = null, onAction: (suspend () -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, action, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && onAction != null) run(onAction)
        }
    }
    fun announce(alarm: AlarmEntity) = showMessage("${formatDuration(timeUntil(alarm))} 후에 울려요")
    fun delete(alarm: AlarmEntity) = run {
        app.scheduler.delete(alarm)
        showMessage("알람을 삭제했어요", "실행 취소") { app.scheduler.save(alarm.copy(id = 0)) }
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) showMessage("알림이 꺼져 있으면 알람 화면이 뜨지 않을 수 있어요", "설정") { openNotificationSettings(context) }
    }
    LaunchedEffect(Unit) {
        run { app.scheduler.restore() }
        if (Build.VERSION.SDK_INT >= 33 && !permissions.notifications) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = EarAlarmColors.Background,
            snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, containerColor = Color(0xFF3D3D3D), contentColor = EarAlarmColors.Text,
                actionColor = EarAlarmColors.TealBright) } },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().height(72.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EarphoneStatusChip(earphone, onClick = { showMessage(earphoneMessage(context, earphone, settings)) }, Modifier.widthIn(max = 240.dp))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showSettings = true }, Modifier.size(56.dp)) {
                        BadgedBox(badge = { if (permissions.critical) Badge(containerColor = EarAlarmColors.DisconnectedContent) }) {
                            Icon(AppIcons.Settings, "설정", Modifier.size(32.dp))
                        }
                    }
                }
                Tabs(pager) { page -> scope.launch { pager.animateScrollToPage(page) } }
                active?.let { id ->
                    Banner("알람이 울리고 있어요", EarAlarmColors.ConnectedContainer, EarAlarmColors.ConnectedContent) {
                        TextButton(onClick = { context.startService(Intent(context, AlarmForegroundService::class.java)
                            .setAction(AlarmForegroundService.STOP).putExtra("id", id)) }) { Text("끄기", color = EarAlarmColors.Text) }
                        TextButton(onClick = { context.startActivity(Intent(context, AlarmRingingActivity::class.java).putExtra("id", id)) }) {
                            Text("열기", color = EarAlarmColors.TealBright)
                        }
                    }
                }
                if (active == null && permissions.critical) {
                    val notifications = permissions.notifications
                    Banner(if (!notifications) "알림이 꺼져 있어 알람 화면이 안 뜰 수 있어요" else "정확한 알람 권한이 필요해요",
                        EarAlarmColors.DisconnectedContainer, EarAlarmColors.DisconnectedContent, AppIcons.Warning) {
                        TextButton(onClick = {
                            when {
                                notifications -> openExactAlarmSettings(context)
                                Build.VERSION.SDK_INT >= 33 -> notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> openNotificationSettings(context)
                            }
                        }) { Text("허용", color = EarAlarmColors.DisconnectedContent) }
                    }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                    when (page) {
                        0 -> TimerScreen(earphone, settings, run)
                        else -> AlarmListScreen(
                            alarms = alarms,
                            settings = settings,
                            onAdd = { val now = LocalTime.now(); editing = AlarmEntity(hour = now.hour, minute = now.minute) },
                            onEdit = { editing = it },
                            onToggle = { alarm, on -> run { app.scheduler.save(alarm.copy(enabled = on)); if (on) announce(alarm) } },
                            onDelete = ::delete,
                            onToggleSort = {
                                val byTime = !settings.sortAlarmsByTime
                                run { app.settingsRepository.setSortAlarmsByTime(byTime) }
                                showMessage(if (byTime) "시간순으로 정렬했어요" else "추가한 순서로 정렬했어요")
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(showSettings, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            SettingsScreen(settings, sounds, earphone, run, onBack = { showSettings = false })
        }
    }
    editing?.let { alarm ->
        AlarmEditorSheet(alarm, settings, sounds,
            onDismiss = { editing = null },
            onSave = { value -> editing = null; run { app.scheduler.save(value); announce(value) } },
            onDelete = if (alarm.id == 0L) null else ({ editing = null; delete(alarm) }))
    }
    error?.let { message ->
        val needsExact = !app.scheduler.canSchedule()
        AlertDialog(onDismissRequest = { error = null }, containerColor = EarAlarmColors.Sheet, title = { Text("확인해 주세요") }, text = { Text(message) },
            confirmButton = {
                if (needsExact) TextButton(onClick = { error = null; openExactAlarmSettings(context) }) { Text("권한 허용", color = EarAlarmColors.TealBright) }
                else TextButton(onClick = { error = null }) { Text("확인", color = EarAlarmColors.TealBright) }
            },
            dismissButton = { if (needsExact) TextButton(onClick = { error = null }) { Text("닫기", color = EarAlarmColors.TextMuted) } })
    }
}

private fun earphoneMessage(context: android.content.Context, state: EarphoneState, settings: AlarmSettings): String = when (state) {
    is EarphoneState.Connected -> "지금 울리면: ${state.deviceName ?: context.getString(state.type.labelRes)} 이어폰으로 재생"
    EarphoneState.Disconnected -> "지금 울리면: ${context.getString(settings.noEarphoneAction.labelRes)}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tabs(pager: PagerState, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tabWidth = maxWidth / 2
        Row(Modifier.fillMaxWidth()) {
            listOf(AppIcons.Hourglass to "타이머", AppIcons.Alarm to "알람").forEachIndexed { index, (icon, label) ->
                val selected = pager.currentPage == index
                val color = if (selected) EarAlarmColors.TealBright else EarAlarmColors.Text
                Row(
                    Modifier.weight(1f).height(64.dp).selectable(selected, role = Role.Tab) { onSelect(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, Modifier.size(30.dp), tint = color)
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = color, fontSize = 20.sp)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).offset(x = tabWidth * (pager.currentPage + pager.currentPageOffsetFraction))
            .width(tabWidth).height(3.dp).background(EarAlarmColors.Teal))
    }
}

@Composable
private fun Banner(text: String, container: Color, content: Color, icon: ImageVector? = null, actions: @Composable RowScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(12.dp), color = container, contentColor = content) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, Modifier.weight(1f), fontSize = 14.sp)
            actions()
        }
    }
}
