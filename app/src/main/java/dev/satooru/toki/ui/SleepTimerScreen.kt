package dev.satooru.toki.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satooru.toki.sleep.SleepTimer
import kotlinx.coroutines.delay

/** 選択できる分数 */
private val CHOICES = listOf(10, 15, 30, 45, 60, 90)
private val CHOICE_LABELS = CHOICES.map { "${it}分" }

@Composable
fun SleepTimerScreen() {
    val context = LocalContext.current
    val end by SleepTimer.end.collectAsState()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var choice by remember { mutableIntStateOf(CHOICES.indexOf(30)) }
    LaunchedEffect(end) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            // 画面表示中に満了した場合に備え、状態を再同期する
            if (end != null && now >= end!!) SleepTimer.init(context)
            delay(500)
        }
    }
    val active = end != null && end!! > now

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "スリープタイマー", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp),
            )
            if (active) {
                Text(formatCountdown(end!! - now), fontSize = 64.sp, fontWeight = FontWeight.Light)
                Text("後に再生を停止します", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = { SleepTimer.cancel(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("タイマーをキャンセル")
                }
            } else {
                WheelPicker(
                    items = CHOICE_LABELS,
                    selectedIndex = choice,
                    onSelected = { choice = it },
                    itemWidth = 120.dp,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { SleepTimer.start(context, CHOICES[choice]) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("開始") }
            }
        }
    }
}
