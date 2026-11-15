package dev.satooru.toki.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.IntentCompat
import dev.satooru.toki.alarm.AlarmScheduler
import dev.satooru.toki.alarm.Vibration
import dev.satooru.toki.data.Alarm
import dev.satooru.toki.data.AlarmRepository
import java.time.LocalTime

private val SNOOZE_ITEMS = listOf("なし") + (1..15).map { "${it}分" }
private const val COUNTDOWN_MAX = 180
private val COUNTDOWN_ITEMS = (1..COUNTDOWN_MAX).map { "${it}分" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: Int?,
    onClose: () -> Unit,
    /** true なら N分後タイマーの設定画面として動作する */
    countdown: Boolean = false,
    /** 「N分後」タブに埋め込む場合は false(キャンセルを出さない) */
    showCancel: Boolean = true,
) {
    val context = LocalContext.current
    val existing = remember(alarmId) { alarmId?.let { AlarmRepository.get(it) } }
    val nowTime = remember { LocalTime.now() }

    val countdownMode = countdown || existing?.isCountdown == true
    var hour by remember { mutableIntStateOf(existing?.hour ?: nowTime.hour) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: nowTime.minute) }
    var countdownMinutes by remember {
        mutableIntStateOf((existing?.minutes?.takeIf { it > 0 } ?: 10).coerceIn(1, COUNTDOWN_MAX))
    }
    var repeatDays by remember { mutableIntStateOf(existing?.repeatDays ?: 0) }
    var soundUri by remember { mutableStateOf(existing?.soundUri) }
    var vibrate by remember { mutableStateOf(existing?.vibrate ?: true) }
    var snooze by remember { mutableStateOf(existing?.snooze ?: true) }
    var snoozeMinutes by remember { mutableIntStateOf((existing?.snoozeMinutes ?: 9).coerceIn(1, 15)) }
    var vibrationPattern by remember { mutableIntStateOf(existing?.vibrationPattern ?: 0) }
    var patternMenu by remember { mutableStateOf(false) }

    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            // 「なし」を選ぶと URI が null で返る
            soundUri = uri?.toString() ?: Alarm.SOUND_NONE
        }
    }

    val canSave = true

    fun save() {
        var alarm = (existing ?: Alarm(hour = 0, minute = 0)).copy(
            soundUri = soundUri,
            snooze = snooze,
            vibrate = vibrate,
            snoozeMinutes = snoozeMinutes,
            vibrationPattern = vibrationPattern,
            enabled = true,
            snoozeUntil = null,
        )
        alarm = if (countdownMode) {
            // 保存した時点から N 分後を絶対時刻で確定する
            AlarmScheduler.withCountdownFrom(alarm.copy(repeatDays = 0, minutes = countdownMinutes), System.currentTimeMillis())
        } else {
            alarm.copy(
                hour = hour, minute = minute,
                repeatDays = repeatDays, atMillis = null, minutes = 0,
            )
        }
        val saved = AlarmScheduler.save(context, alarm)
        AlarmScheduler.nextTrigger(saved, System.currentTimeMillis())?.let {
            Toast.makeText(context, remainingText(System.currentTimeMillis(), it), Toast.LENGTH_SHORT).show()
        }
        onClose()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (showCancel) TextButton(onClick = onClose) { Text("キャンセル") } else Spacer(Modifier.width(48.dp))
            Text(
                when {
                    countdownMode -> "N分後タイマー"
                    existing == null -> "アラームを追加"
                    else -> "アラームを編集"
                },
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = ::save, enabled = canSave) { Text("保存") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(vertical = 16.dp)) {
                if (countdownMode) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 「分」ホイールをスヌーズのホイールの隣に並べる
                        Row(verticalAlignment = Alignment.Top) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("分", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                WheelPicker(
                                    items = COUNTDOWN_ITEMS,
                                    selectedIndex = countdownMinutes - 1,
                                    onSelected = { countdownMinutes = it + 1 },
                                    itemWidth = 96.dp,
                                )
                            }
                            Spacer(Modifier.width(24.dp))
                            SnoozeWheel(snooze, snoozeMinutes) { on, m -> snooze = on; snoozeMinutes = m }
                        }
                        val at = System.currentTimeMillis() + countdownMinutes * 60_000L
                        val t = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                        Text(
                            "${formatTime(context, t.hour, t.minute)} に鳴ります",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                } else {
                    // 「時間」「スヌーズ」を同じ高さのラベルにして、ホイールの高さを揃える
                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("時間", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            TimeWheelPicker(
                                hour = hour, minute = minute,
                                is24Hour = DateFormat.is24HourFormat(context),
                                onChange = { h, m -> hour = h; minute = m },
                            )
                        }
                        Spacer(Modifier.width(24.dp))
                        SnoozeWheel(snooze, snoozeMinutes) { on, m -> snooze = on; snoozeMinutes = m }
                    }
                }
            }

            HorizontalDivider()
            if (!countdownMode) {
                Text("繰り返し", Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DAY_NAMES.forEachIndexed { i, name ->
                        DayToggle(name, repeatDays and (1 shl i) != 0) { repeatDays = repeatDays xor (1 shl i) }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 12.dp))
            }
            Row(
                Modifier.fillMaxWidth().clickable {
                    soundLauncher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                if (soundUri == Alarm.SOUND_NONE) null
                                else soundUri?.let { Uri.parse(it) }
                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            )
                    )
                }.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("サウンド")
                Text(soundTitle(context, soundUri), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("バイブレーション")
                Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            }
            if (vibrate) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { patternMenu = true }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("バイブレーションの種類")
                        Text(Vibration.patternName(vibrationPattern), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = patternMenu, onDismissRequest = { patternMenu = false }) {
                        Vibration.patterns.forEachIndexed { i, p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    vibrationPattern = i
                                    patternMenu = false
                                    // 選んだパターンを1回だけ試し振動する
                                    Vibration.stop(context)
                                    Vibration.start(context, i, loop = false)
                                },
                            )
                        }
                    }
                }
            }
            if (existing != null) {
                OutlinedButton(
                    onClick = {
                        AlarmScheduler.delete(context, existing.id)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) { Text("アラームを削除", color = Color(0xFFFF5252)) }
            }
        }
    }
}

@Composable
private fun DayToggle(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name, fontSize = 14.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun soundTitle(context: android.content.Context, uri: String?): String {
    if (uri == Alarm.SOUND_NONE) return "なし"
    val u = uri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    return runCatching { RingtoneManager.getRingtone(context, u).getTitle(context) }.getOrNull()
        ?: "既定のアラーム音"
}

/** スヌーズ間隔のホイール。「なし」= スヌーズOFF、1〜15分 */
@Composable
private fun SnoozeWheel(snooze: Boolean, minutes: Int, onChange: (Boolean, Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("スヌーズ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        WheelPicker(
            items = SNOOZE_ITEMS,
            selectedIndex = if (snooze) minutes.coerceIn(1, 15) else 0,
            onSelected = { i -> if (i == 0) onChange(false, minutes) else onChange(true, i) },
            itemWidth = 72.dp,
            fontSize = 20.sp,
        )
    }
}
