package com.earalarm.app.ui.alarm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earalarm.app.alarm.nextOccurrence
import com.earalarm.app.data.local.AlarmEntity
import com.earalarm.app.data.local.repeatDays
import com.earalarm.app.data.model.AlarmSettings
import com.earalarm.app.data.model.summary
import com.earalarm.app.ui.components.CircleIconButton
import com.earalarm.app.ui.components.appSwitchColors
import com.earalarm.app.ui.components.formatDuration
import com.earalarm.app.ui.components.splitTime
import com.earalarm.app.ui.theme.AppIcons
import com.earalarm.app.ui.theme.EarAlarmColors
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** 다음 울림까지 남은 시간. 스낵바와 목록에서 같이 쓴다. */
fun timeUntil(alarm: AlarmEntity, now: ZonedDateTime = ZonedDateTime.now()): Duration =
    Duration.between(now, nextOccurrence(alarm.hour, alarm.minute, alarm.repeatMask, now))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmListScreen(
    alarms: List<AlarmEntity>,
    settings: AlarmSettings,
    onAdd: () -> Unit,
    onEdit: (AlarmEntity) -> Unit,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    onToggleSort: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val now by produceState(ZonedDateTime.now()) { while (true) { delay(15_000); value = ZonedDateTime.now() } }
    val sorted = if (settings.sortAlarmsByTime) alarms.sortedWith(compareBy<AlarmEntity> { it.hour }.thenBy { it.minute }) else alarms
    Column(Modifier.fillMaxSize()) {
        if (sorted.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(AppIcons.Alarm, null, Modifier.size(56.dp), tint = EarAlarmColors.TextMuted)
                Spacer(Modifier.height(16.dp))
                Text("등록된 알람이 없어요\n아래 + 버튼으로 추가해 보세요", color = EarAlarmColors.TextMuted, textAlign = TextAlign.Center, fontSize = 17.sp)
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(sorted, key = { it.id }) { alarm ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    AlarmRow(alarm, settings.use24Hour, now,
                        modifier = Modifier.combinedClickable(
                            onClickLabel = "알람 수정",
                            onLongClickLabel = "메뉴",
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                            onClick = { onEdit(alarm) },
                        ),
                        onToggle = { onToggle(alarm, it) })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(20.dp, 0.dp)) {
                        DropdownMenuItem(text = { Text("수정") }, onClick = { menu = false; onEdit(alarm) })
                        DropdownMenuItem(text = { Text(if (alarm.enabled) "끄기" else "켜기") }, onClick = { menu = false; onToggle(alarm, !alarm.enabled) })
                        DropdownMenuItem(text = { Text("삭제") }, leadingIcon = { Icon(AppIcons.Delete, null) }, onClick = { menu = false; onDelete(alarm) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
            CircleIconButton(AppIcons.Plus, "알람 추가", onAdd)
            CircleIconButton(AppIcons.Sort, if (settings.sortAlarmsByTime) "추가한 순서로 정렬" else "시간순 정렬", onToggleSort,
                highlighted = settings.sortAlarmsByTime)
        }
    }
}

@Composable
private fun AlarmRow(alarm: AlarmEntity, use24Hour: Boolean, now: ZonedDateTime, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val (time, period) = splitTime(LocalTime.of(alarm.hour, alarm.minute), use24Hour)
    val color = if (alarm.enabled) EarAlarmColors.Text else EarAlarmColors.Text.copy(alpha = 0.55f)
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(time, Modifier.alignByBaseline(), fontSize = 50.sp, color = color)
                if (period.isNotEmpty()) Text(" $period", Modifier.alignByBaseline(), fontSize = 30.sp, color = color)
            }
            val detail = listOfNotNull(alarm.repeatDays.summary(context.resources), alarm.label.takeIf { it.isNotBlank() }).joinToString(" · ")
            Text(detail, color = EarAlarmColors.TextMuted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (alarm.enabled) Text("${formatDuration(timeUntil(alarm, now))} 후", color = EarAlarmColors.TealBright, fontSize = 14.sp)
        }
        Switch(checked = alarm.enabled, onCheckedChange = onToggle, colors = appSwitchColors(), modifier = Modifier.scale(1.2f).padding(start = 8.dp))
    }
}
