package com.earalarm.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.earalarm.app.R
import com.earalarm.app.audio.EarphoneState
import com.earalarm.app.ui.theme.AppIcons
import com.earalarm.app.ui.theme.EarAlarmColors

/** 상단 왼쪽의 작은 이어폰 상태 배지. 연결되면 청록, 미연결이면 주황 */
@Composable
fun EarphoneStatusChip(state: EarphoneState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val connected = state is EarphoneState.Connected
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(50),
        color = if (connected) EarAlarmColors.ConnectedContainer else EarAlarmColors.DisconnectedContainer,
        contentColor = if (connected) EarAlarmColors.ConnectedContent else EarAlarmColors.DisconnectedContent,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (connected) AppIcons.Headphones else AppIcons.SpeakerOff, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            val text = when (state) {
                is EarphoneState.Connected -> state.deviceName ?: stringResource(R.string.earphone_connected)
                EarphoneState.Disconnected -> stringResource(R.string.earphone_disconnected)
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
