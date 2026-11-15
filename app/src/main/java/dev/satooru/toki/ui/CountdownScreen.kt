package dev.satooru.toki.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satooru.toki.alarm.AlarmScheduler
import dev.satooru.toki.data.AlarmRepository
import kotlinx.coroutines.delay

/** 「N分後」タブ: 設定中のN分後タイマーの残り時間表示と、新規設定フォーム */
@Composable
fun CountdownScreen() {
    val context = LocalContext.current
    val alarms by AlarmRepository.alarms.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val active = alarms.firstOrNull { it.isCountdown && it.enabled && (it.atMillis ?: 0) > now }

    if (active == null || editing) {
        // 設定フォーム(有効なタイマーがなければ新規、編集中なら既存を編集)
        AlarmEditScreen(
            alarmId = if (editing) active?.id else null,
            countdown = true,
            showCancel = editing,
            onClose = { editing = false },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("N分後タイマー", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(48.dp))
        Text(
            formatCountdown(active.atMillis!! - now),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            "${formatTime(context, active.hour, active.minute)} に鳴ります(${active.minutes}分後)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("編集") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { AlarmScheduler.delete(context, active.id) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("タイマーをキャンセル", color = Color(0xFFFF5252)) }
    }
}
