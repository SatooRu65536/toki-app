package dev.satooru.toki.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF9F0A)

// iPhoneの時計アプリに合わせた黒基調 + オレンジのアクセント(ダークテーマ固定)
private val TokiColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF9A9AA0),
)

@Composable
fun TOKITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TokiColorScheme,
        typography = Typography,
        content = content,
    )
}
