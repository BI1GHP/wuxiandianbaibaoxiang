package com.wc.cwtrainer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wc.cwtrainer.core.AppPrefs
import com.wc.cwtrainer.core.CwAudio
import com.wc.cwtrainer.core.Sidetone
import com.wc.cwtrainer.core.Speaker
import com.wc.cwtrainer.core.StatsStore
import com.wc.cwtrainer.core.UpdateManager
import com.wc.cwtrainer.ui.WcTheme
import com.wc.cwtrainer.ui.screens.ContestScreen
import com.wc.cwtrainer.ui.screens.CopyScreen
import com.wc.cwtrainer.ui.screens.HomeScreen
import com.wc.cwtrainer.ui.screens.LearnScreen
import com.wc.cwtrainer.ui.screens.SettingsScreen
import com.wc.cwtrainer.ui.screens.TranslatorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WcTheme {
                val app = remember { AppState(applicationContext) }
                DisposableEffect(Unit) {
                    onDispose { app.shutdown() }
                }
                var screen by remember { mutableStateOf(AppScreen.HOME) }

                // 页面切换时统一停掉所有声音源，避免串扰
                LaunchedEffect(screen) {
                    app.audio.stop()
                    app.speaker.stop()
                    app.sidetone.gated = false
                    app.sidetone.stop()
                }

                BackHandler(enabled = screen != AppScreen.HOME) {
                    screen = AppScreen.HOME
                }

                // 启动时自动检查更新一次（GitHub → Gitee 双源），有新版本弹窗询问
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    UpdateManager.checkUpdateAsync(BuildConfig.VERSION_CODE)
                }
                val updateFlow by UpdateManager.flow.collectAsState()
                var promptedVersion by remember { mutableStateOf<String?>(null) }
                val newVersion = updateFlow.newVersion
                if (updateFlow.status == "new" && newVersion != null && newVersion != promptedVersion) {
                    AlertDialog(
                        onDismissRequest = { promptedVersion = newVersion },
                        title = { Text("发现新版本 $newVersion") },
                        text = {
                            Text(
                                "来源 ${updateFlow.source} · " +
                                    "%.1f MB\n\n是否立即下载并安装？".format(updateFlow.apkSizeBytes / 1048576.0)
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                promptedVersion = newVersion
                                UpdateManager.downloadAndInstallAsync(context.applicationContext)
                            }) { Text("下载更新") }
                        },
                        dismissButton = {
                            TextButton(onClick = { promptedVersion = newVersion }) { Text("稍后") }
                        }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        when (screen) {
                            AppScreen.HOME -> HomeScreen(app) { screen = it }
                            AppScreen.COPY -> CopyScreen(app) { screen = AppScreen.HOME }
                            AppScreen.LEARN -> LearnScreen(app) { screen = AppScreen.HOME }
                            AppScreen.CONTEST -> ContestScreen(app) { screen = AppScreen.HOME }
                            AppScreen.TRANSLATOR -> TranslatorScreen(app) { screen = AppScreen.HOME }
                            AppScreen.SETTINGS -> SettingsScreen(app) { screen = AppScreen.HOME }
                        }
                    }
                }
            }
        }
    }
}

class AppState(context: Context) {
    val prefs = AppPrefs(context)
    val stats = StatsStore(context)
    val audio = CwAudio(context)
    val speaker = Speaker(context)
    val sidetone = Sidetone()

    fun shutdown() {
        audio.stop()
        sidetone.stop()
        speaker.shutdown()
    }
}

enum class AppScreen { HOME, COPY, LEARN, CONTEST, TRANSLATOR, SETTINGS }
