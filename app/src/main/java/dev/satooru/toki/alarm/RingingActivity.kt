package dev.satooru.toki.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.satooru.toki.data.AlarmRepository
import dev.satooru.toki.ui.formatTime
import dev.satooru.toki.ui.theme.TOKITheme
import kotlinx.coroutines.delay
import java.time.LocalTime

/** ロック画面上にも表示される鳴動画面 */
class RingingActivity : ComponentActivity() {
    /** 音量ボタンで鳴動をスヌーズする(電源ボタンは AlarmService が画面消灯で検知) */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            startService(AlarmService.snoozeIntent(this))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmRepository.init(this)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            TOKITheme {
                val ringing by AlarmService.ringingId.collectAsState()
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                val alarm = ringing?.let { AlarmRepository.get(it) }
                RingingScreen(
                    showSnooze = alarm?.snooze == true,
                    onSnooze = { startService(AlarmService.snoozeIntent(this)) },
                    onStop = { startService(AlarmService.stopIntent(this)) },
                )
            }
        }
    }
}

@Composable
private fun RingingScreen(
    showSnooze: Boolean,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("アラーム", color = Color.White, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            formatTime(androidx.compose.ui.platform.LocalContext.current, now.hour, now.minute),
            color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(64.dp))
        if (showSnooze) {
            Button(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("スヌーズ", fontSize = 20.sp) }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3A3A3C), contentColor = Color.White,
            ),
        ) { Text("停止", fontSize = 20.sp) }
    }
}
