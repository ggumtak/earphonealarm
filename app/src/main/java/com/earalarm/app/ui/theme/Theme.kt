package com.earalarm.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 기본 시계 앱처럼 짙은 회색 바탕 + 청록 포인트 */
object EarAlarmColors {
    val Background = Color(0xFF2B2B2B)
    val Sheet = Color(0xFF424242)
    val Dial = Color(0xFF4B4B4B)
    val Field = Color(0xFF505050)
    val Teal = Color(0xFF00897B)
    val TealDark = Color(0xFF1E6B63)
    val TealBright = Color(0xFF1DD8C3)
    val Text = Color(0xFFECE7EE)
    val TextMuted = Color(0xFF9E9E9E)
    val SwitchOff = Color(0xFF8E8C94)
    val ConnectedContainer = Color(0xFF173C38)
    val ConnectedContent = TealBright
    val DisconnectedContainer = Color(0xFF45351F)
    val DisconnectedContent = Color(0xFFFFB86B)
}

private val DarkColors = darkColorScheme(
    primary = EarAlarmColors.Teal,
    onPrimary = Color.White,
    primaryContainer = EarAlarmColors.TealDark,
    onPrimaryContainer = Color.White,
    secondary = EarAlarmColors.TealBright,
    onSecondary = Color(0xFF00201D),
    secondaryContainer = EarAlarmColors.TealDark,
    onSecondaryContainer = Color.White,
    background = EarAlarmColors.Background,
    onBackground = EarAlarmColors.Text,
    surface = EarAlarmColors.Background,
    onSurface = EarAlarmColors.Text,
    surfaceVariant = EarAlarmColors.Field,
    onSurfaceVariant = Color(0xFFC9C5CC),
    surfaceContainerLowest = Color(0xFF242424),
    surfaceContainerLow = Color(0xFF323232),
    surfaceContainer = Color(0xFF383838),
    surfaceContainerHigh = EarAlarmColors.Sheet,
    surfaceContainerHighest = EarAlarmColors.Dial,
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = EarAlarmColors.TealBright,
    outline = Color(0xFF8B8B8B),
    outlineVariant = Color(0xFF555555),
    error = Color(0xFFFFB4AB),
)

/** 다크 테마 고정 */
@Composable
fun EarAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
