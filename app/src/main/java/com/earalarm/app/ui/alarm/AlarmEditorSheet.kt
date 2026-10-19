package com.earalarm.app.ui.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earalarm.app.data.local.AlarmEntity
import com.earalarm.app.data.local.CustomSoundEntity
import com.earalarm.app.data.local.repeatDays
import com.earalarm.app.data.model.AlarmSettings
import com.earalarm.app.data.model.RepeatDays
import com.earalarm.app.data.model.SoundRef
import com.earalarm.app.data.model.shortKoreanName
import com.earalarm.app.data.model.summary
import com.earalarm.app.ui.components.SoundChoiceRow
import com.earalarm.app.ui.components.ToggleRow
import com.earalarm.app.ui.components.VolumeRow
import com.earalarm.app.ui.theme.AppIcons
import com.earalarm.app.ui.theme.EarAlarmColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

/** 시간 선택 시트. 스크린샷처럼 시계 다이얼 + 취소/OK, 아래에 반복·메모·알람음 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorSheet(
    initial: AlarmEntity,
    settings: AlarmSettings,
    sounds: List<CustomSoundEntity>,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var alarm by remember(initial) { mutableStateOf(initial) }
    // 메모·알람음·빠른 반복은 기본으로 접어 두어 시트를 낮게 유지한다. 이미 값이 있으면 펼쳐서 보여 준다.
    var more by remember(initial) { mutableStateOf(initial.label.isNotBlank() || initial.useCustomSound) }
    // '15분 뒤' 등을 누르면 키가 바뀌어 다이얼이 새 시각으로 다시 만들어진다.
    var base by remember(initial) { mutableStateOf(initial.hour to initial.minute) }
    val picker = key(base) { rememberTimePickerState(base.first, base.second, settings.use24Hour) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) { scope.launch { sheet.hide() }.invokeOnCompletion { then() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = EarAlarmColors.Sheet,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFBDBDBD)) },
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (onDelete != null) Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("알람 수정", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = EarAlarmColors.TextMuted)
                IconButton(onClick = { close(onDelete) }) { Icon(AppIcons.Delete, "알람 삭제", tint = EarAlarmColors.TextMuted) }
            }
            Spacer(Modifier.height(8.dp))
            TimePicker(state = picker, colors = timePickerColors())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15분 뒤", 30 to "30분 뒤", 60 to "1시간 뒤").forEach { (minutes, label) ->
                    SuggestionChip(
                        onClick = { val time = LocalTime.now().plusMinutes(minutes.toLong()); base = time.hour to time.minute },
                        label = { Text(label) },
                        border = BorderStroke(1.dp, EarAlarmColors.Teal),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("반복", Modifier.weight(1f), fontSize = 17.sp)
                    Text(alarm.repeatDays.summary(context.resources), color = EarAlarmColors.TealBright)
                }
                DayRow(alarm.repeatDays) { alarm = alarm.copy(repeatMask = it.mask) }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { more = !more }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("메모·알람음·빠른 반복", fontSize = 15.sp, color = EarAlarmColors.TextMuted)
                Spacer(Modifier.weight(1f))
                if (alarm.label.isNotBlank()) Text(alarm.label, Modifier.widthIn(max = 140.dp), color = EarAlarmColors.TealBright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(AppIcons.ChevronRight, if (more) "접기" else "펼치기", Modifier.size(24.dp).rotate(if (more) 90f else 0f), tint = EarAlarmColors.TextMuted)
            }
            AnimatedVisibility(visible = more) {
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("매일" to RepeatDays.EVERYDAY, "평일" to RepeatDays.WEEKDAYS, "주말" to RepeatDays.WEEKEND).forEach { (label, days) ->
                                val selected = alarm.repeatDays == days
                                FilterChip(selected = selected, onClick = { alarm = alarm.copy(repeatMask = if (selected) 0 else days.mask) }, label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EarAlarmColors.TealDark, selectedLabelColor = Color.White))
                            }
                        }
                        OutlinedTextField(
                            value = alarm.label, onValueChange = { alarm = alarm.copy(label = it.take(100)) },
                            label = { Text("알람 메모") }, placeholder = { Text("예: 출근 준비") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EarAlarmColors.TealBright, focusedLabelColor = EarAlarmColors.TealBright,
                                cursorColor = EarAlarmColors.TealBright),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ToggleRow("이 알람만 다른 알람음·음량", alarm.useCustomSound, description = if (alarm.useCustomSound) null else "꺼 두면 설정의 기본 알람음을 써요") {
                        alarm = alarm.copy(useCustomSound = it)
                    }
                    if (alarm.useCustomSound) {
                        SoundChoiceRow(alarm.soundRef?.let(SoundRef::decode) ?: settings.defaultSound, sounds) { alarm = alarm.copy(soundRef = it.encode()) }
                        VolumeRow(alarm.volumePercent ?: settings.volumePercent) { alarm = alarm.copy(volumePercent = it) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { close(onDismiss) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, EarAlarmColors.Teal),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EarAlarmColors.Text),
            ) { Text("취소", fontSize = 18.sp) }
            Button(
                // 시간을 고르고 OK를 누르면 꺼져 있던 알람도 켠다.
                onClick = { val result = alarm.copy(hour = picker.hour, minute = picker.minute, enabled = true); close { onSave(result) } },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EarAlarmColors.Teal, contentColor = Color.White),
            ) { Text("OK", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun DayRow(days: RepeatDays, onChange: (RepeatDays) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.values().forEach { day ->
            val selected = day in days
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (selected) EarAlarmColors.Teal else Color.Transparent)
                    .border(1.dp, if (selected) EarAlarmColors.Teal else EarAlarmColors.TextMuted, CircleShape)
                    .toggleable(value = selected, role = Role.Checkbox) { onChange(days.toggle(day)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(day.shortKoreanName(), color = when {
                    selected -> Color.White
                    day == DayOfWeek.SUNDAY -> Color(0xFFFF8A80)
                    day == DayOfWeek.SATURDAY -> Color(0xFF82B1FF)
                    else -> EarAlarmColors.Text
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun timePickerColors() = TimePickerDefaults.colors(
    clockDialColor = EarAlarmColors.Dial,
    clockDialSelectedContentColor = Color.White,
    clockDialUnselectedContentColor = EarAlarmColors.Text,
    selectorColor = EarAlarmColors.Teal,
    containerColor = EarAlarmColors.Sheet,
    periodSelectorBorderColor = EarAlarmColors.Field,
    periodSelectorSelectedContainerColor = EarAlarmColors.TealDark,
    periodSelectorUnselectedContainerColor = EarAlarmColors.Field,
    periodSelectorSelectedContentColor = Color.White,
    periodSelectorUnselectedContentColor = EarAlarmColors.Text,
    timeSelectorSelectedContainerColor = EarAlarmColors.TealDark,
    timeSelectorUnselectedContainerColor = EarAlarmColors.Field,
    timeSelectorSelectedContentColor = Color.White,
    timeSelectorUnselectedContentColor = EarAlarmColors.Text,
)
