package com.earalarm.app.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.earalarm.app.R
import com.earalarm.app.appContainer
import com.earalarm.app.audio.EarphoneState
import com.earalarm.app.data.local.CustomSoundEntity
import com.earalarm.app.data.model.*
import com.earalarm.app.ui.components.*
import com.earalarm.app.ui.theme.AppIcons
import com.earalarm.app.ui.theme.EarAlarmColors

/** 앱 동작에 필요한 권한 상태. 화면으로 돌아올 때마다 다시 확인한다. */
data class PermissionStatus(
    val notifications: Boolean,
    val exactAlarm: Boolean,
    val fullScreen: Boolean,
    val bluetooth: Boolean,
    val battery: Boolean,
) {
    /** 알람이 아예 안 보이거나 예약되지 않는 치명적인 문제 */
    val critical get() = !notifications || !exactAlarm

    companion object {
        fun read(context: Context): PermissionStatus {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            return PermissionStatus(
                notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarm = context.appContainer.scheduler.canSchedule(),
                fullScreen = Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent(),
                bluetooth = Build.VERSION.SDK_INT < 31 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED,
                battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            )
        }
    }
}

@Composable
fun rememberPermissionStatus(): State<PermissionStatus> {
    val context = LocalContext.current
    val status = remember { mutableStateOf(PermissionStatus.read(context)) }
    LifecycleResumeEffect(Unit) {
        status.value = PermissionStatus.read(context)
        onPauseOrDispose { }
    }
    return status
}

fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
    else openAppSettings(context)
}

@Composable
fun SettingsScreen(
    settings: AlarmSettings,
    sounds: List<CustomSoundEntity>,
    earphone: EarphoneState,
    run: (suspend () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val app = context.appContainer
    val repo = app.settingsRepository
    val permissions by rememberPermissionStatus()
    var refresh by remember { mutableIntStateOf(0) }
    val status = remember(permissions, refresh) { PermissionStatus.read(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) run {
        val added = app.soundRepository.addFromDevice(uri)
        check(added != null) { "이 파일의 지속적인 읽기 권한을 얻지 못했어요. 다른 파일을 선택해 주세요." }
        repo.setDefaultSound(SoundRef.Custom(added.uri))
    } }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openNotificationSettings(context) else refresh++
    }
    val bluetoothRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openAppSettings(context) else refresh++
    }

    // 아래 메인 화면으로 터치가 새지 않도록 pointerInput을 둔다. Surface로 기본 글자색(contentColor)도 밝게 맞춘다.
    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = EarAlarmColors.Background, contentColor = EarAlarmColors.Text) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, "뒤로", Modifier.size(26.dp)) }
                Text("설정", fontSize = 22.sp, modifier = Modifier.padding(start = 4.dp))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SectionTitle("이어폰")
                SettingRow(
                    title = when (earphone) {
                        is EarphoneState.Connected -> earphone.deviceName ?: stringResource(R.string.earphone_connected)
                        EarphoneState.Disconnected -> stringResource(R.string.earphone_disconnected)
                    },
                    value = when (earphone) {
                        is EarphoneState.Connected -> stringResource(earphone.type.labelRes) + " · 이 기기로 알람이 울려요"
                        EarphoneState.Disconnected -> "알람이 울릴 때 아래 설정대로 동작해요"
                    },
                    valueColor = if (earphone is EarphoneState.Connected) EarAlarmColors.TealBright else EarAlarmColors.DisconnectedContent,
                    leading = if (earphone is EarphoneState.Connected) AppIcons.Headphones else AppIcons.SpeakerOff,
                )
                ChoiceRow("이어폰이 없을 때", settings.noEarphoneAction, NoEarphoneAction.values().toList(), { context.getString(it.labelRes) },
                    description = settings.noEarphoneAction.descriptionRes?.let { context.getString(it) }) { run { repo.setNoEarphoneAction(it) } }

                SectionTitle("알람음")
                SoundChoiceRow(settings.defaultSound, sounds, footer = {
                    TextButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(AppIcons.Plus, null, Modifier.size(18.dp), tint = EarAlarmColors.TealBright)
                        Spacer(Modifier.width(8.dp))
                        Text("기기에서 음악 파일 추가", color = EarAlarmColors.TealBright)
                    }
                }) { run { repo.setDefaultSound(it) } }
                VolumeRow(settings.volumePercent) { run { repo.setVolume(it) } }
                SettingRow("내 음악 추가", description = "기기에 있는 음악 파일을 알람음으로 써요", leading = AppIcons.Music,
                    onClick = { picker.launch(arrayOf("audio/*")) })
                sounds.forEach { sound ->
                    SettingRow(sound.displayName, description = "내 음악", trailing = {
                        IconButton(onClick = { run { app.soundRepository.remove(sound) } }) { Icon(AppIcons.Delete, "${sound.displayName} 삭제", tint = EarAlarmColors.TextMuted) }
                    })
                }

                SectionTitle("울림")
                ChoiceRow("다시 울림 간격", settings.snooze, SnoozeInterval.values().toList(), { it.label(context.resources) }) { run { repo.setSnooze(it) } }
                ChoiceRow("자동 종료", settings.autoStop, AutoStopDuration.values().toList(), { it.label(context.resources) },
                    description = "이 시간 동안 울린 뒤 자동으로 꺼져요") { run { repo.setAutoStop(it) } }
                ChoiceRow("점점 크게 울리기", settings.fadeIn, FadeInDuration.values().toList(), { it.label(context.resources) }) { run { repo.setFadeIn(it) } }
                ToggleRow("진동 함께 사용", settings.vibrateWithSound, "이어폰으로 소리가 날 때 진동도 함께 울려요") { run { repo.setVibrateWithSound(it) } }

                SectionTitle("표시")
                ToggleRow("24시간 형식", settings.use24Hour) { run { repo.setUse24Hour(it) } }

                SectionTitle("권한")
                PermissionRow("알림 표시", "알람 화면과 종료 버튼을 띄우는 데 필요해요", status.notifications) {
                    if (Build.VERSION.SDK_INT >= 33) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
                }
                PermissionRow("정확한 알람", "정해진 시각에 정확히 울리게 해요", status.exactAlarm) { openExactAlarmSettings(context) }
                if (Build.VERSION.SDK_INT >= 34) PermissionRow("잠금 화면 알람", "잠긴 화면 위로 알람 화면을 띄워요", status.fullScreen) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                }
                if (Build.VERSION.SDK_INT >= 31) PermissionRow("블루투스 이름 표시", "연결된 이어폰 이름을 보여 줘요 (선택)", status.bluetooth) {
                    bluetoothRequest.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
                PermissionRow("배터리 최적화 제외", "절전 중에도 알람이 끊기지 않게 해요", status.battery) {
                    @Suppress("BatteryLife")
                    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(request) }.onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }

                SectionTitle("알아 두기")
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "이어폰 소리는 휴대폰 미디어 음량의 영향을 받아요. 미디어 음량이 0이면 소리가 나지 않아요.",
                        "블루투스 스피커·차량 오디오·일부 USB 장치도 이어폰으로 인식돼요. 처음에는 짧은 타이머로 확인해 주세요.",
                        "이어폰이 빠지면 재생을 멈추지만, 기기별 오디오 전환 지연으로 아주 짧은 스피커 출력까지 막을 수는 없어요.",
                        "앱을 닫아도 예약된 시간에 울려요. 기기를 재부팅하면 진행 중이던 타이머는 초기화돼요.",
                    ).forEach { Text("• $it", color = EarAlarmColors.TextMuted, fontSize = 14.sp) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onRequest: () -> Unit) {
    SettingRow(title, description = description, onClick = if (granted) null else onRequest) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (granted) EarAlarmColors.ConnectedContainer else EarAlarmColors.DisconnectedContainer,
            contentColor = if (granted) EarAlarmColors.ConnectedContent else EarAlarmColors.DisconnectedContent,
        ) {
            Text(if (granted) "허용됨" else "허용하기", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
        }
    }
}
