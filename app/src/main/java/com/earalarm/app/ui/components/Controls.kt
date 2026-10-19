package com.earalarm.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earalarm.app.data.local.CustomSoundEntity
import com.earalarm.app.data.model.BuiltInSounds
import com.earalarm.app.data.model.SoundRef
import com.earalarm.app.ui.theme.EarAlarmColors
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 스크린샷의 둥근 원형 버튼. [filled]면 청록으로 채운 주 버튼 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    filled: Boolean = false,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    filled -> EarAlarmColors.Teal.copy(alpha = alpha)
                    highlighted -> EarAlarmColors.Teal.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .then(if (filled) Modifier else Modifier.border(BorderStroke(1.5.dp, EarAlarmColors.Teal.copy(alpha = alpha)), CircleShape))
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onLongClick = onLongClick?.let { action -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); action() } },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides EarAlarmColors.Text.copy(alpha = alpha)) { content() }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                     filled: Boolean = false, highlighted: Boolean = false, enabled: Boolean = true) {
    CircleButton(onClick, contentDescription, modifier, filled = filled, highlighted = highlighted, enabled = enabled) {
        Icon(icon, contentDescription, Modifier.size(32.dp))
    }
}

@Composable
fun appSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = EarAlarmColors.TealBright,
    checkedTrackColor = EarAlarmColors.Teal,
    checkedBorderColor = EarAlarmColors.Teal,
    uncheckedThumbColor = EarAlarmColors.SwitchOff,
    uncheckedTrackColor = Color(0xFF2E2D33),
    uncheckedBorderColor = EarAlarmColors.SwitchOff,
)

@Composable
fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        color = EarAlarmColors.TealBright, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

/** 한 줄 설정 항목. 누르면 [onClick] */
@Composable
fun SettingRow(title: String, modifier: Modifier = Modifier, value: String? = null, description: String? = null,
               valueColor: Color = EarAlarmColors.TealBright, leading: ImageVector? = null, onClick: (() -> Unit)? = null,
               trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { Icon(leading, null, Modifier.size(24.dp), tint = EarAlarmColors.TextMuted); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontSize = 17.sp)
            if (value != null) Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium)
            if (description != null) Text(description, color = EarAlarmColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    SettingRow(title, description = description, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange, colors = appSwitchColors())
    }
}

/** 현재 값을 보여 주고 누르면 라디오 목록 대화상자를 연다. */
@Composable
fun <T> ChoiceRow(title: String, selected: T, values: List<T>, label: (T) -> String, description: String? = null,
                  leading: ImageVector? = null, footer: (@Composable () -> Unit)? = null, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, value = label(selected), description = description, leading = leading, onClick = { open = true })
    if (open) ChoiceDialog(title, selected, values, label, onDismiss = { open = false }, footer = footer) { open = false; onSelect(it) }
}

@Composable
fun <T> ChoiceDialog(title: String, selected: T, values: List<T>, label: (T) -> String, onDismiss: () -> Unit,
                     footer: (@Composable () -> Unit)? = null, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EarAlarmColors.Sheet,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                values.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.small)
                            .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = EarAlarmColors.TealBright))
                        Spacer(Modifier.width(12.dp))
                        Text(label(value), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                footer?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기", color = EarAlarmColors.TealBright) } },
    )
}

@Composable
fun VolumeRow(value: Int, onSet: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("음량", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontSize = 17.sp)
            Text("${draft.toInt()}%", color = EarAlarmColors.TealBright)
        }
        Slider(value = draft, onValueChange = { draft = it }, valueRange = 0f..100f, onValueChangeFinished = { onSet(draft.toInt()) },
            colors = SliderDefaults.colors(thumbColor = EarAlarmColors.TealBright, activeTrackColor = EarAlarmColors.Teal,
                inactiveTrackColor = EarAlarmColors.Field))
    }
}

@Composable
fun SoundChoiceRow(selected: SoundRef, sounds: List<CustomSoundEntity>, footer: (@Composable () -> Unit)? = null, onSelect: (SoundRef) -> Unit) {
    val context = LocalContext.current
    val choices = listOf<SoundRef>(SoundRef.SystemDefault) + BuiltInSounds.all.map { SoundRef.BuiltIn(it.resName) } + sounds.map { SoundRef.Custom(it.uri) }
    ChoiceRow("알람음", selected, choices, { ref -> when (ref) {
        SoundRef.SystemDefault -> "시스템 기본 알람음"
        is SoundRef.BuiltIn -> BuiltInSounds.all.firstOrNull { it.resName == ref.resName }?.let { context.getString(it.labelRes) } ?: "기본 알람음"
        is SoundRef.Custom -> sounds.firstOrNull { it.uri == ref.uri }?.displayName ?: "내 음악"
    } }, footer = footer, onSelect = onSelect)
}

/** "6:00" 과 "AM"을 나눠서 돌려준다. 24시간 형식이면 두 번째 값은 빈 문자열 */
fun splitTime(time: LocalTime, use24Hour: Boolean, seconds: Boolean = false): Pair<String, String> =
    if (use24Hour) time.format(DateTimeFormatter.ofPattern(if (seconds) "HH:mm:ss" else "HH:mm")) to ""
    else time.format(DateTimeFormatter.ofPattern(if (seconds) "h:mm:ss" else "h:mm")) to
        time.format(DateTimeFormatter.ofPattern("a", Locale.US))

/** "3시간 20분" 처럼 남은 시간을 짧게 표시. 1분 미만은 "1분 이내" */
fun formatDuration(duration: Duration): String {
    val minutes = (duration.seconds + 59) / 60
    if (minutes < 1) return "1분 이내"
    val days = minutes / 1440; val hours = minutes / 60 % 24; val mins = minutes % 60
    return buildList {
        if (days > 0) add("${days}일")
        if (hours > 0) add("${hours}시간")
        if (mins > 0 && days == 0L) add("${mins}분")
    }.joinToString(" ")
}
