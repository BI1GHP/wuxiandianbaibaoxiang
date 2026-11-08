package com.wc.cwtrainer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.core.AppPrefs
import com.wc.cwtrainer.core.CwAudio
import com.wc.cwtrainer.core.NoiseAudio
import com.wc.cwtrainer.core.RadioVoice
import com.wc.cwtrainer.core.VoiceBank
import com.wc.cwtrainer.core.VoiceSynth
import com.wc.cwtrainer.core.Sidetone
import com.wc.cwtrainer.core.Speaker
import com.wc.cwtrainer.core.ExamManager
import com.wc.cwtrainer.core.StatsStore
import com.wc.cwtrainer.core.UpdateManager
import com.wc.cwtrainer.ui.SplashScreen
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import com.wc.cwtrainer.ui.WcTheme
import com.wc.cwtrainer.ui.screens.ContestScreen
import com.wc.cwtrainer.ui.screens.CopyScreen
import com.wc.cwtrainer.ui.screens.ExamScreen
import com.wc.cwtrainer.ui.screens.HomeScreen
import com.wc.cwtrainer.ui.screens.LearnScreen
import com.wc.cwtrainer.ui.screens.SettingsScreen
import com.wc.cwtrainer.ui.screens.TranslatorScreen
import kotlinx.coroutines.delay

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

                // 冷启动先展示 2 秒品牌启动图，之后淡出（旋转等重建不再重复展示）
                var showSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(2000)
                    showSplash = false
                }

                // 页面切换时统一停掉所有声音源，避免串扰。
                // 注意：底噪（noise）不停——它由赛事页 DisposableEffect 全权管理。
                // 本协程体在 Compose 应用阶段之后才执行，若在这里停 noise，
                // 会把赛事页刚启动的底噪立刻掐掉（stop 在 start 之后跑）。
                LaunchedEffect(screen) {
                    app.audio.stop()
                    app.voice.stop()
                    app.radio.stop()
                    app.voiceBank.stop()
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
                val changeLog = remember(updateFlow.body) {
                    UpdateManager.displayNotes(updateFlow.body)
                }
                if (updateFlow.status == "new" && newVersion != null &&
                    newVersion != promptedVersion && !showSplash
                ) {
                    AlertDialog(
                        onDismissRequest = { promptedVersion = newVersion },
                        title = { Text("发现新版本 $newVersion") },
                        text = {
                            Column {
                                Text(
                                    text = "来源 ${updateFlow.source} · " +
                                        "%.1f MB".format(updateFlow.apkSizeBytes / 1048576.0),
                                    color = TextDim,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                if (changeLog.isNotBlank()) {
                                    Text(
                                        text = "更新内容",
                                        color = TextMain,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Column(
                                        modifier = Modifier
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = changeLog,
                                            color = TextMain,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "暂无更新说明",
                                        color = TextDim,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "是否立即下载并安装？",
                                    color = TextMain,
                                    fontSize = 13.sp
                                )
                            }
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

                Box(modifier = Modifier.fillMaxSize()) {
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
                                AppScreen.EXAM -> ExamScreen(app) { screen = AppScreen.HOME }
                                AppScreen.SETTINGS -> SettingsScreen(app) { screen = AppScreen.HOME }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(420))
                    ) {
                        SplashScreen()
                    }
                }
            }
        }
    }
}

class AppState(context: Context) {
    val appContext = context.applicationContext
    val prefs = AppPrefs(context)
    val stats = StatsStore(context)
    val audio = CwAudio(context)
    val noise = NoiseAudio(context)
    val voice = VoiceSynth(context)
    val speaker = Speaker(context)
    val radio = RadioVoice(context, speaker)
    val voiceBank = VoiceBank(context)
    val sidetone = Sidetone()
    val exam = ExamManager(context)

    fun shutdown() {
        audio.stop()
        noise.stop()
        voice.stop()
        radio.stop()
        voiceBank.stop()
        sidetone.stop()
        speaker.shutdown()
    }
}

enum class AppScreen { HOME, COPY, LEARN, CONTEST, TRANSLATOR, EXAM, SETTINGS }
