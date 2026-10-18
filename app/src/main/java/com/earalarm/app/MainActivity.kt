package com.earalarm.app

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.earalarm.app.data.local.AlarmEntity
import com.earalarm.app.data.local.CustomSoundEntity
import com.earalarm.app.data.local.repeatDays
import com.earalarm.app.data.model.*
import com.earalarm.app.ui.components.EarphoneStatusChip
import com.earalarm.app.ui.theme.EarAlarmTheme
import com.earalarm.app.service.AlarmForegroundService
import com.earalarm.app.ui.ringing.AlarmRingingActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EarAlarmTheme { Home() } }
    }
}

@Composable
private fun Home() {
    val context = LocalContext.current
    val app = context.appContainer
    val scope = rememberCoroutineScope()
    val state by app.earphoneState.collectAsStateWithLifecycle()
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(AlarmSettings())
    val alarms by app.alarmRepository.alarms.collectAsStateWithLifecycle(emptyList())
    val sounds by app.soundRepository.customSounds.collectAsStateWithLifecycle(emptyList())
    val active by AlarmForegroundService.active.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<AlarmEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val run: (suspend () -> Unit) -> Unit = { block -> scope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "작업을 완료하지 못했어요" }
    }; Unit }
    LaunchedEffect(Unit) { run { app.scheduler.restore() } }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("이어폰 알람", style = MaterialTheme.typography.headlineLarge)
            EarphoneStatusChip(state = state)
            if (active != null) Button(onClick = { context.startActivity(Intent(context, AlarmRingingActivity::class.java).putExtra("id", active!!)) }) { Text("울리는 알람 열기") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("타이머", "알람", "설정").forEachIndexed { index, text ->
                    FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(text) })
                }
            }
            when (tab) {
                0 -> TimerPanel(run)
                1 -> {
                    Button(onClick = { val now = LocalTime.now().plusMinutes(1); editing = AlarmEntity(hour = now.hour, minute = now.minute) }, modifier = Modifier.fillMaxWidth()) { Text("+ 알람 추가") }
                    Toggle("시간순 정렬", settings.sortAlarmsByTime) { run { app.settingsRepository.setSortAlarmsByTime(it) } }
                    val sorted = if (settings.sortAlarmsByTime) alarms.sortedWith(compareBy<AlarmEntity> { it.hour }.thenBy { it.minute }) else alarms
                    if (sorted.isEmpty()) Text("등록된 알람이 없어요. 알람을 추가해 주세요.")
                    sorted.forEach { alarm ->
                        Card(onClick = { editing = alarm }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(formatTime(alarm.hour, alarm.minute, settings.use24Hour), style = MaterialTheme.typography.headlineMedium)
                                    Text(alarm.repeatDays.summary(context.resources))
                                    if (alarm.label.isNotBlank()) Text(alarm.label)
                                }
                                Switch(checked = alarm.enabled, onCheckedChange = { run { app.scheduler.save(alarm.copy(enabled = it)) } })
                            }
                        }
                    }
                }
                2 -> SettingsPanel(settings, sounds, run)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    editing?.let { alarm ->
        AlarmEditor(alarm, settings, sounds, onDismiss = { editing = null },
            onSave = { value -> run { app.scheduler.save(value); editing = null } },
            onDelete = { run { app.scheduler.delete(alarm); editing = null } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("확인해 주세요") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } }) }
}

@Composable
private fun TimerPanel(run: (suspend () -> Unit) -> Unit) {
    val scheduler = LocalContext.current.appContainer.scheduler
    var minutes by rememberSaveable { mutableStateOf("35") }
    var remaining by remember { mutableLongStateOf(scheduler.timerRemaining()) }
    var running by remember { mutableStateOf(scheduler.timerRunning()) }
    LaunchedEffect(Unit) { while (true) { remaining = scheduler.timerRemaining(); running = scheduler.timerRunning(); delay(250) } }
    val seconds = (remaining + 999) / 1000
    Text(if (remaining > 0) "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "${minutes.ifEmpty { "0" }} min", style = MaterialTheme.typography.displayMedium)
    if (running && remaining > 0) Text("종료 예정: " + java.time.LocalDateTime.now().plusSeconds(seconds).format(DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")))
    OutlinedTextField(value = minutes, onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) minutes = it }, label = { Text("타이머 (분)") }, enabled = !running, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(5, 15, 35, 60).forEach { value -> OutlinedButton(onClick = { minutes = value.toString() }, enabled = !running) { Text("$value") } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { run {
            if (running) scheduler.pauseTimer()
            else scheduler.startTimer(if (remaining > 0) remaining else ((minutes.toLongOrNull() ?: 0) * 60000))
        } }) { Text(if (running) "일시정지" else if (remaining > 0) "계속" else "시작") }
        OutlinedButton(onClick = { run { scheduler.resetTimer() } }) { Text("초기화") }
    }
    Text("앱을 닫아도 예약된 시간에 알려 줘요. 기기를 재부팅하면 타이머는 초기화돼요.", style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(initial: AlarmEntity, settings: AlarmSettings, sounds: List<CustomSoundEntity>, onDismiss: () -> Unit, onSave: (AlarmEntity) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var alarm by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (alarm.id == 0L) "알람 추가" else "알람 수정", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> alarm = alarm.copy(hour = h, minute = m) }, alarm.hour, alarm.minute, settings.use24Hour).show() }) {
                Text(formatTime(alarm.hour, alarm.minute, settings.use24Hour), style = MaterialTheme.typography.headlineMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { value -> TextButton(onClick = { val time = LocalTime.now().plusMinutes(value.toLong()); alarm = alarm.copy(hour = time.hour, minute = time.minute) }) { Text("+$value 분") } }
            }
            Text("반복: ${alarm.repeatDays.summary(context.resources)}")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.values().forEach { day ->
                    FilterChip(selected = day in alarm.repeatDays, onClick = { alarm = alarm.copy(repeatMask = alarm.repeatDays.toggle(day).mask) }, label = { Text(day.shortKoreanName()) }, modifier = Modifier.width(43.dp))
                }
            }
            OutlinedTextField(value = alarm.label, onValueChange = { alarm = alarm.copy(label = it.take(100)) }, label = { Text("알람 메모") }, modifier = Modifier.fillMaxWidth())
            Toggle("이 알람만 다른 알람음·음량 사용", alarm.useCustomSound) { alarm = alarm.copy(useCustomSound = it) }
            if (alarm.useCustomSound) {
                SoundPicker(alarm.soundRef?.let(SoundRef::decode) ?: settings.defaultSound, sounds) { alarm = alarm.copy(soundRef = it.encode()) }
                Volume(alarm.volumePercent ?: settings.volumePercent) { alarm = alarm.copy(volumePercent = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSave(alarm) }) { Text("저장") }
                if (alarm.id != 0L) TextButton(onClick = onDelete) { Text("삭제") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsPanel(settings: AlarmSettings, sounds: List<CustomSoundEntity>, run: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    val app = context.appContainer
    val repo = app.settingsRepository
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) run {
        check(app.soundRepository.addFromDevice(uri) != null) { "이 파일의 지속적인 읽기 권한을 얻지 못했어요. 다른 파일을 선택해 주세요." }
    } }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    Text("알람 기본 설정", style = MaterialTheme.typography.titleLarge)
    SoundPicker(settings.defaultSound, sounds) { run { repo.setDefaultSound(it) } }
    OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("+ 기기에서 음악 파일 추가") }
    sounds.forEach { sound -> Row(Modifier.fillMaxWidth()) {
        Text(sound.displayName, Modifier.weight(1f))
        TextButton(onClick = { run { app.soundRepository.remove(sound) } }) { Text("삭제") }
    } }
    Volume(settings.volumePercent) { run { repo.setVolume(it) } }
    Choice("다시 울림 간격", settings.snooze, SnoozeInterval.values().toList(), { it.label(context.resources) }) { run { repo.setSnooze(it) } }
    Choice("자동 종료 시간", settings.autoStop, AutoStopDuration.values().toList(), { it.label(context.resources) }) { run { repo.setAutoStop(it) } }
    Choice("점점 크게 울리기", settings.fadeIn, FadeInDuration.values().toList(), { it.label(context.resources) }) { run { repo.setFadeIn(it) } }
    Choice("이어폰 미연결 시", settings.noEarphoneAction, NoEarphoneAction.values().toList(), { context.getString(it.labelRes) }) { run { repo.setNoEarphoneAction(it) } }
    Toggle("진동 함께 사용", settings.vibrateWithSound) { run { repo.setVibrateWithSound(it) } }
    Toggle("24시간 형식 사용", settings.use24Hour) { run { repo.setUse24Hour(it) } }
    HorizontalDivider()
    Text("권한 및 시스템", style = MaterialTheme.typography.titleLarge)
    Text("이어폰 소리는 휴대폰의 미디어 음량에도 영향을 받아요. 미디어 음량이 0이면 소리가 나지 않아요.")
    Text("블루투스 스피커·차량 오디오·일부 USB 장치도 이어폰으로 인식돼요. 처음에는 짧은 타이머로 확인해 주세요.")
    Text("이어폰이 빠지면 재생을 중단하지만 기기별 오디오 전환 지연으로 순간적인 스피커 출력까지 보장할 수는 없어요.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { permissions.launch(buildList {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
    }.toTypedArray()) }) { Text("알림·블루투스 권한 허용") }
    OutlinedButton(onClick = { run {
        if (Build.VERSION.SDK_INT >= 31 && !app.scheduler.canSchedule()) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        else Toast.makeText(context, "정확한 알람이 허용되어 있어요", Toast.LENGTH_SHORT).show()
    } }) { Text("정확한 알람 권한 확인") }
    if (Build.VERSION.SDK_INT >= 34) OutlinedButton(onClick = { run { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) } }) { Text("잠금 화면 알람 허용") }
    OutlinedButton(onClick = { run { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }) { Text("배터리 최적화 설정") }
    OutlinedButton(onClick = { run { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } }) { Text("앱 알림·권한 설정") }
}

@Composable
private fun SoundPicker(selected: SoundRef, sounds: List<CustomSoundEntity>, onSelect: (SoundRef) -> Unit) {
    val context = LocalContext.current
    val choices = listOf<SoundRef>(SoundRef.SystemDefault) + BuiltInSounds.all.map { SoundRef.BuiltIn(it.resName) } + sounds.map { SoundRef.Custom(it.uri) }
    Choice("알람음", selected, choices, { ref -> when (ref) {
        SoundRef.SystemDefault -> "시스템 기본 알람음"
        is SoundRef.BuiltIn -> BuiltInSounds.all.firstOrNull { it.resName == ref.resName }?.let { context.getString(it.labelRes) } ?: "기본 알람음"
        is SoundRef.Custom -> sounds.firstOrNull { it.uri == ref.uri }?.displayName ?: "내 음악"
    } }, onSelect)
}

@Composable
private fun <T> Choice(title: String, selected: T, values: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(label(selected)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEach { value -> DropdownMenuItem(text = { Text(label(value)) }, onClick = { expanded = false; onSelect(value) }) }
            }
        }
    }
}

@Composable
private fun Volume(value: Int, onSet: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Text("음량 ${draft.toInt()}%")
    Slider(value = draft, onValueChange = { draft = it }, valueRange = 0f..100f, onValueChangeFinished = { onSet(draft.toInt()) })
}
@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
private fun formatTime(hour: Int, minute: Int, use24Hour: Boolean) = LocalTime.of(hour, minute)
    .format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "a h:mm", java.util.Locale.KOREAN))
