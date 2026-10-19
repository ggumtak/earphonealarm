package com.earalarm.app.ui.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.earalarm.app.appContainer
import com.earalarm.app.audio.EarphoneState
import com.earalarm.app.data.model.AlarmSettings
import com.earalarm.app.data.model.NoEarphoneAction
import com.earalarm.app.data.settings.MAX_TIMER_MINUTES
import com.earalarm.app.ui.components.CircleButton
import com.earalarm.app.ui.components.splitTime
import com.earalarm.app.ui.theme.AppIcons
import com.earalarm.app.ui.theme.EarAlarmColors
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun TimerScreen(earphone: EarphoneState, settings: AlarmSettings, run: (suspend () -> Unit) -> Unit) {
    val app = LocalContext.current.appContainer
    val scheduler = app.scheduler
    // -1: 저장된 마지막 시간을 아직 읽지 않음
    var minutes by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) { if (minutes < 0) minutes = app.settingsRepository.current().timerMinutes }
    var remaining by remember { mutableLongStateOf(scheduler.timerRemaining()) }
    var total by remember { mutableLongStateOf(scheduler.timerTotal()) }
    var running by remember { mutableStateOf(scheduler.timerRunning()) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            remaining = scheduler.timerRemaining(); total = scheduler.timerTotal(); running = scheduler.timerRunning()
            now = LocalTime.now()
            delay(200)
        }
    }
    val active = remaining > 0
    val shown = minutes.coerceAtLeast(0)
    var typing by remember { mutableStateOf(false) }
    fun add(delta: Int) {
        if (active) run { scheduler.adjustTimer(delta * 60_000L) }
        else { minutes = (shown + delta).coerceIn(0, MAX_TIMER_MINUTES) }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = min(min(maxWidth - 56.dp, maxHeight - 32.dp), 400.dp).coerceAtLeast(160.dp)
            val progress = if (active && total > 0) remaining.toFloat() / total else 1f
            val seconds = if (active) (remaining + 999) / 1000 else shown * 60L
            TimerRing(diameter, progress, dimTrack = active, onClick = if (active) null else ({ typing = true })) {
                TimerCenter(diameter, active, running, remaining, shown, now.plusSeconds(seconds), earphone, settings)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(10, 5, 1).forEach { value ->
                CircleButton(onClick = { add(value) }, contentDescription = "${value}분 더하기", onLongClick = { add(-value) }) {
                    Text("+$value", fontSize = 30.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CircleButton(
                onClick = { run {
                    when {
                        running -> scheduler.pauseTimer()
                        active -> scheduler.startTimer(remaining)
                        else -> { scheduler.startTimer(shown * 60_000L); app.settingsRepository.setTimerMinutes(shown) }
                    }
                } },
                contentDescription = if (running) "일시정지" else if (active) "계속" else "시작",
                filled = true, enabled = active || shown > 0,
            ) { Icon(if (running) AppIcons.Pause else AppIcons.Play, null, Modifier.size(32.dp)) }
            CircleButton(
                onClick = { if (active) run { scheduler.resetTimer() } else { minutes = 0 } },
                contentDescription = if (active) "타이머 취소" else "시간 지우기",
                enabled = active || shown > 0,
            ) { Icon(AppIcons.Close, null, Modifier.size(32.dp)) }
        }
        Text(
            if (active) "+ 버튼으로 시간 연장 · 길게 누르면 빼기" else "가운데를 누르면 직접 입력 · 길게 누르면 빼기",
            Modifier.padding(top = 16.dp, bottom = 20.dp), color = EarAlarmColors.TextMuted, fontSize = 13.sp,
        )
    }
    if (typing) MinutesDialog(shown, onDismiss = { typing = false }) { minutes = it; typing = false }
}

@Composable
private fun TimerRing(diameter: Dp, progress: Float, dimTrack: Boolean, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    Box(
        Modifier.size(diameter).clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "시간 직접 입력", role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (dimTrack) drawArc(EarAlarmColors.Teal.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(EarAlarmColors.Teal, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = if (progress < 1f) StrokeCap.Round else StrokeCap.Butt))
        }
        content()
    }
}

@Composable
private fun TimerCenter(diameter: Dp, active: Boolean, running: Boolean, remaining: Long, minutes: Int, end: LocalTime,
                        earphone: EarphoneState, settings: AlarmSettings) {
    // 원 크기에 맞춰 글자 크기를 정한다. 시스템 글꼴 크기와 무관하게 원 안에 들어가도록 dp 기준
    val unit = with(LocalDensity.current) { diameter.toSp() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (active) {
            val s = (remaining + 999) / 1000
            val text = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
            Text(text, fontSize = unit * if (s >= 3600) 0.16f else 0.2f, color = EarAlarmColors.Text)
        } else Row {
            Text("$minutes", Modifier.alignByBaseline(), fontSize = unit * if (minutes >= 100) 0.2f else 0.24f, color = EarAlarmColors.Text)
            Text(" min", Modifier.alignByBaseline(), fontSize = unit * 0.11f, color = EarAlarmColors.Text)
        }
        Spacer(Modifier.height(6.dp))
        val connected = earphone is EarphoneState.Connected
        val (time, period) = splitTime(end, settings.use24Hour, seconds = true)
        val alpha = if (active && !running) 0.45f else 1f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    connected -> AppIcons.Headphones
                    settings.noEarphoneAction == NoEarphoneAction.SPEAKER -> AppIcons.Speaker
                    else -> AppIcons.SpeakerOff
                },
                contentDescription = if (connected) "이어폰으로 울림" else "이어폰 없음",
                Modifier.size(with(LocalDensity.current) { (unit * 0.085f).toDp() }),
                tint = (if (connected) EarAlarmColors.Text else EarAlarmColors.DisconnectedContent).copy(alpha = alpha),
            )
            Spacer(Modifier.width(8.dp))
            Row {
                Text(time, Modifier.alignByBaseline(), fontSize = unit * 0.075f, color = EarAlarmColors.Text.copy(alpha = alpha))
                if (period.isNotEmpty()) Text(" $period", Modifier.alignByBaseline(), fontSize = unit * 0.055f, color = EarAlarmColors.Text.copy(alpha = alpha))
            }
        }
        if (active && !running) Text("일시정지됨", Modifier.padding(top = 4.dp), color = EarAlarmColors.TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun MinutesDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val start = if (initial > 0) initial.toString() else ""
    var text by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val value = text.text.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EarAlarmColors.Sheet,
        title = { Text("타이머 시간") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.text.length <= 3 && it.text.all(Char::isDigit)) text = it },
                    suffix = { Text("분") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (value > 0) onConfirm(value) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { preset ->
                        SuggestionChip(onClick = { onConfirm(preset) }, label = { Text("${preset}분") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value > 0) { Text("확인", color = EarAlarmColors.TealBright) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = EarAlarmColors.TextMuted) } },
    )
}
