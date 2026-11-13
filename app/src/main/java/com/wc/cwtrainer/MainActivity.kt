package com.wc.cwtrainer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import com.wc.cwtrainer.core.UiSound
import com.wc.cwtrainer.core.UiSounds
import com.wc.cwtrainer.core.Speaker
import com.wc.cwtrainer.core.ExamManager
import com.wc.cwtrainer.core.MemberStore
import com.wc.cwtrainer.core.StatsStore
import com.wc.cwtrainer.core.UpdateManager
import com.wc.cwtrainer.widget.CwWidgetProvider
import com.wc.cwtrainer.ui.SplashScreen
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import com.wc.cwtrainer.ui.WcTheme
import com.wc.cwtrainer.ui.screens.AccentScreen
import com.wc.cwtrainer.ui.screens.AccountScreen
import com.wc.cwtrainer.ui.screens.ContestScreen
import com.wc.cwtrainer.ui.screens.CallsignScreen
import com.wc.cwtrainer.ui.screens.CoachScreen
import com.wc.cwtrainer.ui.screens.CopyScreen
import com.wc.cwtrainer.ui.screens.ExamScreen
import com.wc.cwtrainer.ui.screens.HomeScreen
import com.wc.cwtrainer.ui.screens.LearnScreen
import com.wc.cwtrainer.ui.screens.MemberScreen
import com.wc.cwtrainer.ui.screens.OnboardingScreen
import com.wc.cwtrainer.ui.screens.SettingsScreen
import com.wc.cwtrainer.ui.screens.SkinStoreScreen
import com.wc.cwtrainer.ui.screens.SyncScreen
import com.wc.cwtrainer.ui.screens.ThemePreviewScreen
import com.wc.cwtrainer.ui.screens.TranslatorScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // 回到前台时刷新小组件上的码速与最高正确率
        CwWidgetProvider.refresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ 通知权限：拒绝时仍可前台播放，只是通知栏不显示控制
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            // AppState 提到主题外层：主题选项变化时整个界面按新色板重组
            val app = remember { AppState(applicationContext) }
            DisposableEffect(Unit) {
                onDispose { app.shutdown() }
            }
            WcTheme(app.prefs.data.theme, app.prefs.data.fontScale) {
                // 小组件可带 screen 参数直达最近科目，冷启动时读取一次
                var screen by remember { mutableStateOf(screenFromIntent(intent)) }

                // 冷启动先展示 2 秒品牌启动图，之后淡出（旋转等重建不再重复展示）
                var showSplash by rememberSaveable { mutableStateOf(true) }
                var onboarded by remember { mutableStateOf(app.prefs.data.onboarded) }
                LaunchedEffect(Unit) {
                    delay(2000)
                    showSplash = false
                }

                // 页面切换时统一停掉所有声音源，避免串扰。
                // 注意：底噪（noise）不停——它由赛事页 DisposableEffect 全权管理。
                // 本协程体在 Compose 应用阶段之后才执行，若在这里停 noise，
                // 会把赛事页刚启动的底噪立刻掐掉（stop 在 start 之后跑）。
                LaunchedEffect(screen) {
                    // 记住最近一次训练科目，供首页「继续上次训练」使用
                    if (screen != AppScreen.HOME && screen != AppScreen.SETTINGS &&
                        screen != AppScreen.THEME_PREVIEW && screen != AppScreen.SKIN_STORE &&
                        screen != AppScreen.CALLSIGN && screen != AppScreen.SYNC &&
                        screen != AppScreen.COACH && screen != AppScreen.ACCOUNT &&
                        screen != AppScreen.MEMBER
                    ) {
                        app.prefs.recordScreen(screen.name)
                    }
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
                // 弹窗在下载/安装/出错期间保持打开，便于展示进度与重试
                val uStatus = updateFlow.status
                val showUpdateDialog = newVersion != null && newVersion != promptedVersion && !showSplash &&
                    (uStatus == "new" || uStatus == "downloading" || uStatus == "installing" || uStatus == "error")
                if (showUpdateDialog && newVersion != null) {
                    val busy = uStatus == "downloading" || uStatus == "installing"
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
                                when (uStatus) {
                                    "downloading" -> {
                                        Spacer(Modifier.height(14.dp))
                                        if (updateFlow.progress >= 0f) {
                                            LinearProgressIndicator(
                                                progress = { updateFlow.progress },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = "下载中 ${(updateFlow.progress * 100).roundToInt()}%",
                                                color = TextDim,
                                                fontSize = 12.sp
                                            )
                                        } else {
                                            // 服务器未给总长：不定态进度条
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            Spacer(Modifier.height(6.dp))
                                            Text(text = "下载中…", color = TextDim, fontSize = 12.sp)
                                        }
                                    }
                                    "installing" -> {
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                            text = "📦 下载完成，正在调起安装器…",
                                            color = TextMain,
                                            fontSize = 13.sp
                                        )
                                    }
                                    "error" -> {
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                            text = updateFlow.message,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp
                                        )
                                    }
                                    else -> {
                                        Spacer(Modifier.height(14.dp))
                                        Text(
                                            text = "是否立即下载并安装？",
                                            color = TextMain,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { UpdateManager.downloadAndInstallAsync(context.applicationContext) },
                                enabled = !busy
                            ) {
                                Text(
                                    when (uStatus) {
                                        "downloading", "installing" -> "下载中…"
                                        "error" -> "重试下载"
                                        else -> "下载更新"
                                    }
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { promptedVersion = newVersion }) {
                                Text(if (busy) "后台下载" else "稍后")
                            }
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
                                AppScreen.ACCENT -> AccentScreen(app) { screen = AppScreen.HOME }
                                AppScreen.COACH -> CoachScreen(
                                    app = app,
                                    onBack = { screen = AppScreen.HOME },
                                    onOpen = { screen = it }
                                )
                                AppScreen.TRANSLATOR -> TranslatorScreen(app) { screen = AppScreen.HOME }
                                AppScreen.EXAM -> ExamScreen(app) { screen = AppScreen.HOME }
                                AppScreen.SETTINGS -> SettingsScreen(
                                    app = app,
                                    onBack = { screen = AppScreen.HOME },
                                    onPreviewTheme = { screen = AppScreen.THEME_PREVIEW },
                                    onOpenSkins = { screen = AppScreen.SKIN_STORE },
                                    onOpenSync = { screen = AppScreen.SYNC },
                                    onOpenAccount = { screen = AppScreen.ACCOUNT },
                                    onOpenMember = { screen = AppScreen.MEMBER }
                                )
                                AppScreen.THEME_PREVIEW -> ThemePreviewScreen(app) {
                                    screen = AppScreen.SETTINGS
                                }
                                AppScreen.SKIN_STORE -> SkinStoreScreen(app) {
                                    screen = AppScreen.SETTINGS
                                }
                                AppScreen.CALLSIGN -> CallsignScreen(app) {
                                    screen = AppScreen.HOME
                                }
                                AppScreen.SYNC -> SyncScreen(app) {
                                    screen = AppScreen.SETTINGS
                                }
                                AppScreen.ACCOUNT -> AccountScreen(app) {
                                    screen = AppScreen.SETTINGS
                                }
                                AppScreen.MEMBER -> MemberScreen(
                                    app = app,
                                    onBack = { screen = AppScreen.SETTINGS },
                                    onOpenAccount = { screen = AppScreen.ACCOUNT }
                                )
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

                    // 首启引导：启动图之后盖在界面上，完成后直接进入第一次训练
                    if (!showSplash && !onboarded) {
                        OnboardingScreen(app, onFinish = { target ->
                            onboarded = true
                            screen = target
                        })
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
    val member = MemberStore(context)
    val srs = com.wc.cwtrainer.core.SrsStore(context)
    val contestLog = com.wc.cwtrainer.core.ContestLogStore(context)
    val accent = com.wc.cwtrainer.core.AccentStore(context)
    val audio = CwAudio(context)
    val noise = NoiseAudio(context)
    val voice = VoiceSynth(context)
    val speaker = Speaker(context)
    val radio = RadioVoice(context, speaker)
    val voiceBank = VoiceBank(context)
    val sidetone = Sidetone(context)
    val exam = ExamManager(context)
    val uiSound = UiSound(context).also {
        UiSounds.enabled = prefs.data.uiSound
        UiSounds.attach(it)
    }

    fun shutdown() {
        uiSound.release()
        audio.stop()
        noise.stop()
        voice.stop()
        radio.stop()
        voiceBank.stop()
        sidetone.stop()
        speaker.shutdown()
    }
}

enum class AppScreen {
    HOME, COPY, LEARN, CONTEST, ACCENT, COACH, TRANSLATOR, EXAM, SETTINGS, THEME_PREVIEW, SKIN_STORE,
    CALLSIGN, SYNC, ACCOUNT, MEMBER
}

/** 从小组件 Intent 解析目标页，非法值回落首页 */
private fun screenFromIntent(intent: android.content.Intent?): AppScreen {
    val name = intent?.getStringExtra("screen") ?: return AppScreen.HOME
    return runCatching { AppScreen.valueOf(name) }.getOrDefault(AppScreen.HOME)
}
