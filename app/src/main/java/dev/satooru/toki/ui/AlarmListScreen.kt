package dev.satooru.toki.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import dev.satooru.toki.data.Alarm
import dev.satooru.toki.data.AlarmRepository
import kotlinx.coroutines.delay

@Composable
fun AlarmListScreen(
    permissionBanners: @Composable () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
) {
    val context = LocalContext.current
    val alarms by AlarmRepository.alarms.collectAsState()
    var editMode by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val sorted = alarms.sortedWith(compareBy({ it.hour }, { it.minute }))
    val next = AlarmScheduler.nextAlarmMillis(alarms, now)

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { editMode = !editMode }, enabled = alarms.isNotEmpty() || editMode) {
                Text(if (editMode) "完了" else "編集")
            }
            TextButton(onClick = onAdd) { Text("＋", fontSize = 28.sp) }
        }
        Text(
            "アラーム",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            if (next != null) remainingText(now, next) else "有効なアラームはありません",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        permissionBanners()
        if (alarms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("アラームはありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sorted, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        now = now,
                        editMode = editMode,
                        onClick = { onEdit(alarm.id) },
                        onToggle = { enabled ->
                            val updated = AlarmScheduler.setEnabled(context, alarm, enabled)
                            if (enabled) {
                                AlarmScheduler.nextTrigger(updated, System.currentTimeMillis())?.let {
                                    Toast.makeText(context, remainingText(System.currentTimeMillis(), it), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = { AlarmScheduler.delete(context, alarm.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmRow(
    alarm: Alarm,
    now: Long,
    editMode: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(Color(0xFFD32F2F)).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("削除", color = Color.White) }
        },
    ) {
        val alpha = if (alarm.enabled) 1f else 0.4f
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
                // 例) 5:15 (スヌーズ 5分)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatTime(context, alarm.hour, alarm.minute),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Light,
                        color = color,
                    )
                    Text(
                        "(${snoozeText(alarm)})",
                        fontSize = 16.sp,
                        color = color,
                        modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                    )
                }
                // 例) 明日　音なし　バイブレーションなし(該当するものだけ表示)
                Text(
                    listOfNotNull(
                        repeatText(alarm, now),
                        "音なし".takeIf { alarm.soundUri == Alarm.SOUND_NONE },
                        "バイブレーションなし".takeIf { !alarm.vibrate },
                    ).joinToString("　"),
                    color = color,
                )
            }
            if (editMode) {
                TextButton(onClick = onDelete) { Text("削除", color = Color(0xFFFF5252)) }
            } else {
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
        }
    }
}
