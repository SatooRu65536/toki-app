package dev.satooru.toki

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.satooru.toki.alarm.AlarmScheduler
import dev.satooru.toki.alarm.Notifications
import dev.satooru.toki.data.AlarmRepository
import dev.satooru.toki.sleep.SleepTimer
import dev.satooru.toki.ui.AlarmEditScreen
import dev.satooru.toki.ui.AlarmListScreen
import dev.satooru.toki.ui.CountdownScreen
import dev.satooru.toki.ui.SleepTimerScreen
import dev.satooru.toki.ui.theme.TOKITheme

private enum class Screen(val tabLabel: String?, @DrawableRes val tabIcon: Int) {
    LIST("アラーム", R.drawable.ic_nav_alarm),
    COUNTDOWN("N分後", R.drawable.ic_nav_timer),
    SLEEP("スリープ", R.drawable.ic_nav_sleep),
    EDIT(null, 0), // タブではない(編集画面)
}

class MainActivity : ComponentActivity() {
    /** onResume ごとに増やし、権限状態の再チェックを促す */
    private var resumeTick by mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmRepository.init(this)
        SleepTimer.init(this)
        Notifications.ensureChannels(this)
        AlarmScheduler.rescheduleAll(this)
        // ステータスバー(時刻・電池などの表示部分)は白背景 + 黒アイコン、ナビゲーションバーは黒
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TOKITheme {
                var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
                var editId by rememberSaveable { mutableIntStateOf(0) } // 0 = 新規
                BackHandler(enabled = screen == Screen.EDIT) { screen = Screen.LIST }
                val showNavBar = screen != Screen.EDIT

                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // ステータスバー部分だけを白くする
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(androidx.compose.ui.graphics.Color.White)
                    )
                    var content = Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)
                    if (showNavBar) content = content.consumeWindowInsets(WindowInsets.navigationBars)
                    Surface(content, color = MaterialTheme.colorScheme.background) {
                        when (screen) {
                            Screen.LIST -> AlarmListScreen(
                                permissionBanners = { PermissionBanners(resumeTick) },
                                onAdd = { editId = 0; screen = Screen.EDIT },
                                onEdit = { editId = it; screen = Screen.EDIT },
                            )

                            Screen.EDIT -> AlarmEditScreen(
                                alarmId = editId.takeIf { it != 0 },
                                onClose = { screen = Screen.LIST },
                            )

                            Screen.COUNTDOWN -> CountdownScreen()
                            Screen.SLEEP -> SleepTimerScreen()
                        }
                    }
                    if (showNavBar) {
                        NavigationBar(containerColor = Color(0xFF1C1C1E)) {
                            Screen.entries.filter { it.tabLabel != null }.forEach { tab ->
                                NavigationBarItem(
                                    selected = screen == tab,
                                    onClick = { screen = tab },
                                    icon = { Icon(painterResource(tab.tabIcon), contentDescription = null) },
                                    label = { Text(tab.tabLabel!!) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    @Composable
    private fun PermissionBanners(tick: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        @Suppress("UNUSED_EXPRESSION") tick
        if (!nm.areNotificationsEnabled()) {
            Banner("通知が許可されていません。アラームが表示されない場合があります。") {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            Banner("ロック画面でのアラーム表示が許可されていません。") {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            }
        }
    }

    @Composable
    private fun Banner(text: String, onOpen: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onOpen) { Text("設定") }
        }
    }
}
