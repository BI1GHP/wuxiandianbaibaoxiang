package com.wc.cwtrainer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wc.cwtrainer.core.AppPrefs
import com.wc.cwtrainer.core.CwAudio
import com.wc.cwtrainer.core.Sidetone
import com.wc.cwtrainer.core.Speaker
import com.wc.cwtrainer.core.StatsStore
import com.wc.cwtrainer.ui.WcTheme
import com.wc.cwtrainer.ui.screens.ContestScreen
import com.wc.cwtrainer.ui.screens.CopyScreen
import com.wc.cwtrainer.ui.screens.HomeScreen
import com.wc.cwtrainer.ui.screens.LearnScreen
import com.wc.cwtrainer.ui.screens.SendScreen
import com.wc.cwtrainer.ui.screens.SettingsScreen

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
                            AppScreen.SEND -> SendScreen(app) { screen = AppScreen.HOME }
                            AppScreen.CONTEST -> ContestScreen(app) { screen = AppScreen.HOME }
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

enum class AppScreen { HOME, COPY, LEARN, SEND, CONTEST, SETTINGS }
