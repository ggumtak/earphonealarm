package com.earalarm.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.earalarm.app.R
import com.earalarm.app.audio.EarphoneState
import com.earalarm.app.ui.theme.EarAlarmColors

/** 상단 이어폰 연결 상태 배지. 연결되면 청록, 미연결이면 주황으로 바뀐다. */
@Composable
fun EarphoneStatusChip(state: EarphoneState, modifier: Modifier = Modifier) {
    val connected = state is EarphoneState.Connected
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(50),
        color = if (connected) EarAlarmColors.ConnectedContainer else EarAlarmColors.DisconnectedContainer,
        contentColor = if (connected) EarAlarmColors.ConnectedContent else EarAlarmColors.DisconnectedContent,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(if (connected) R.string.earphone_connected else R.string.earphone_disconnected),
                style = MaterialTheme.typography.labelLarge,
            )
            if (state is EarphoneState.Connected) {
                val typeLabel = stringResource(state.type.labelRes)
                Text(
                    text = state.deviceName
                        ?.let { stringResource(R.string.earphone_detail_with_name, typeLabel, it) }
                        ?: typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
