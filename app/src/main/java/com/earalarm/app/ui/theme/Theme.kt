package com.earalarm.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 이어폰 상태 배지 등 Material 색상표에 없는 포인트 색 */
object EarAlarmColors {
    val Teal = Color(0xFF5EE6D4)
    val ConnectedContainer = Color(0xFF0E3B36)
    val ConnectedContent = Teal
    val DisconnectedContainer = Color(0xFF3A2A17)
    val DisconnectedContent = Color(0xFFFFB86B)
}

private val DarkColors = darkColorScheme(
    primary = EarAlarmColors.Teal,
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFF9FF2E7),
    secondary = Color(0xFFB1CCC7),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0F1413),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF1C2422),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainer = Color(0xFF1A201F),
    surfaceContainerHigh = Color(0xFF242B29),
    outline = Color(0xFF889390),
    outlineVariant = Color(0xFF3F4947),
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
