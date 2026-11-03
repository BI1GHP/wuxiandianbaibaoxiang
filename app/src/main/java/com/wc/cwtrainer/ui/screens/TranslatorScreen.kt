package com.wc.cwtrainer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.Fist
import com.wc.cwtrainer.core.Morse
import com.wc.cwtrainer.core.MorseDecoder
import com.wc.cwtrainer.core.ToneKeyDetector
import com.wc.cwtrainer.core.VoiceSpec
import com.wc.cwtrainer.core.buildSegments
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Keypad
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.delay

enum class TranslatorTab { DECODE, KEY, ENCODE }

@Composable
fun TranslatorScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    var tab by remember { mutableStateOf(TranslatorTab.DECODE) }

    DisposableEffect(tab) {
        app.sidetone.stop()
        onDispose { app.sidetone.stop() }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B1220))) {
        ScreenHeader("CW 翻译器", onBack)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1).border(1.dp, Line, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabBtn("🔍 解码", tab == TranslatorTab.DECODE, Modifier.weight(1f)) { tab = TranslatorTab.DECODE }
            TabBtn("⌨️ 发码", tab == TranslatorTab.KEY, Modifier.weight(1f)) { tab = TranslatorTab.KEY }
            TabBtn("📝 文本", tab == TranslatorTab.ENCODE, Modifier.weight(1f)) { tab = TranslatorTab.ENCODE }
        }

        Spacer(Modifier.height(10.dp))

        when (tab) {
            TranslatorTab.DECODE -> DecodeBlock(app)
            TranslatorTab.KEY -> KeyBlock(app)
            TranslatorTab.ENCODE -> EncodeBlock(app)
        }
    }
}

@Composable
private fun TabBtn(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Amber.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (selected) Amber.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = if (selected) Amber else TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ============ Tab 1: 实时解码 ============
@Composable
private fun DecodeBlock(app: AppState) {
    val ctx = LocalContext.current
    val s = app.prefs.data
    var listening by remember { mutableStateOf(false) }
    var micLevel by remember { mutableStateOf(0f) }
    var micDenied by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var estWpm by remember { mutableStateOf(20.0) }
    var lastDotMs by remember { mutableStateOf(60.0) }
    // 检测器实例：tick 需要用它的采样时钟，才能和按键事件严格对齐
    var detector by remember { mutableStateOf<ToneKeyDetector?>(null) }

    val decoder = remember { MorseDecoder(s.wpm.toDouble()) }
    // 音频线程的按键事件统一切到主线程处理，避免与 tick/清屏并发修改解码器
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // 滚动状态 + 自动到底部
    val scrollState = rememberScrollState()

    LaunchedEffect(text) {
        // 每次 text 变化自动滚到底部
        val max = scrollState.maxValue
        if (max > 0 && scrollState.value != max) {
            scrollState.animateScrollTo(max)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { micDenied = false; listening = true } else { micDenied = true }
    }
    fun toggle() {
        if (listening) {
            listening = false
            // 停止监听时把最后一个字母也解出来，避免末尾电码丢失。
            // 放到主线程消息队列末尾执行，让检测线程补发的 keyUp 先处理完再结算
            mainHandler.post {
                decoder.commit(SystemClock.elapsedRealtime())
                text = decoder.text
            }
        } else {
            val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (ok) listening = true else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun clearAll() {
        decoder.reset(); text = ""; estWpm = 20.0; lastDotMs = 60.0
    }
    DisposableEffect(listening, s.extToneHz, s.micSensitivity) {
        val det = ToneKeyDetector(
            detectorHz = s.extToneHz.toDouble(), sensitivity = s.micSensitivity,
            onDown = { t -> mainHandler.post { decoder.keyDown(t) } },
            onUp = { t -> mainHandler.post { decoder.keyUp(t) } },
            onLevel = { lvl -> mainHandler.post { micLevel = lvl } }
        )
        detector = det
        if (listening) det.start()
        onDispose {
            det.stop()
            if (detector === det) detector = null
        }
    }

    LaunchedEffect(listening) {
        while (listening) {
            // 25ms 结算一次：间隔判定更贴合实际节奏，快速电码不会被拆散
            delay(25)
            // 用检测端的采样时钟结算，主线程时钟超前缓冲区时间会把字母拆散
            val t = detector?.nowMs ?: 0L
            if (t > 0L) decoder.tick(t)
            text = decoder.text
            estWpm = decoder.estimatedWpm
            lastDotMs = decoder.avgDotMs
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (listening) Green.copy(alpha = 0.15f) else Surface2)
                        .border(2.dp, if (listening) Green.copy(alpha = 0.6f) else Line, CircleShape)
                        .clickable { toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (listening) "■" else "▶", color = if (listening) Green else Amber,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (listening) "监听中 · 自动码速匹配" else "点击开始监听外部电码",
                        color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp)).background(Surface2)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(micLevel.coerceIn(0.001f, 1f))
                                .height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(if (micLevel > 0.02f) Green else Line)
                        )
                    }
                }
                // 清屏按钮（监听中也能点）
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Surface2).border(1.dp, Line, RoundedCornerShape(8.dp))
                        .clickable { clearAll() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("清屏", color = TextDim, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip("估测 ${"%.0f".format(estWpm)} WPM", Green)
                StatChip("平均点 ${"%.0f".format(lastDotMs)} ms", Amber)
                StatChip("频率 ${s.extToneHz} Hz", TextDim)
            }
            if (micDenied) {
                Spacer(Modifier.height(6.dp))
                Text("⚠ 未获得麦克风权限，请在系统设置中允许录音。", color = Red, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A0D11)).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("解码结果", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (listening) "● 实时" else "○ 停止",
                    color = if (listening) Green else TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF06080C))
                    .border(1.dp, Line, RoundedCornerShape(8.dp))
                    .padding(12.dp).verticalScroll(scrollState)
            ) {
                Text(
                    text = text.ifEmpty { "等待接收电码…" },
                    color = if (listening) Green else TextDim,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    lineHeight = 34.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "将电台侧音 / 外部键控器 / USB 声卡输出接到手机麦克风输入。检测频率在设置中可改。",
            color = TextDim, fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}

// ============ Tab 2: 手键 / 自动键 ============
@Composable
private fun KeyBlock(app: AppState) {
    val s = app.prefs.data

    var dotHeld by remember { mutableStateOf(false) }
    var dashHeld by remember { mutableStateOf(false) }
    var keying by remember { mutableStateOf(false) }
    var currentElem by remember { mutableStateOf<Char?>(null) }
    // 点/划“记忆”：轻点后即使已经抬起，也会完整发出一个元素，避免快速连点时丢失
    var tapMemory by remember { mutableStateOf<Char?>(null) }

    // 发码速度由 App 自己决定，初始码速可信
    val decoder = remember { MorseDecoder(s.wpm.toDouble(), trustInitialWpm = true) }
    val scrollState = rememberScrollState()

    // —— 页面进入自动启动 SIDETONE ——
    LaunchedEffect(Unit) {
        app.audio.stop(); app.speaker.stop()
        app.sidetone.frequency = s.toneHz.toDouble()
        app.sidetone.volume = s.volume
        app.sidetone.start()
    }
    DisposableEffect(Unit) { onDispose { app.sidetone.stop() } }

    // —— paddling 核心：2ms tick ——
    LaunchedEffect(Unit) {
        var lastSentElem: Char? = null
        while (true) {
            val wpm = app.prefs.data.wpm
            val unitMs = 1200.0 / wpm.coerceAtLeast(5)

            // 当前应发的元素：优先按住的两个拨片，其次消费一次“轻点记忆”
            val heldTarget = when {
                dotHeld && dashHeld -> if (lastSentElem == '.') '-' else '.'
                dotHeld -> '.'
                dashHeld -> '-'
                else -> null
            }
            val target = heldTarget ?: tapMemory

            if (target != null) {
                if (heldTarget == null || tapMemory == target) tapMemory = null
                if (dotHeld && dashHeld) tapMemory = null
                lastSentElem = target
                val durMs = if (target == '.') unitMs.toLong() else (unitMs * 3.0).toLong()

                app.sidetone.gated = true
                decoder.keyDown(SystemClock.elapsedRealtime())

                // 元素必须发完整，不能被下一次点击截断
                delay(durMs)

                app.sidetone.gated = false
                decoder.keyUp(SystemClock.elapsedRealtime())

                // 元素间隔 1 单位，保证“点 + 划”能组成完整电码
                delay(unitMs.toLong())
            } else {
                lastSentElem = null
                delay(2)
            }
        }
    }

    // —— 解码 tick：40ms —— sent 只从这里写入 ——
    var sent by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            decoder.tick(SystemClock.elapsedRealtime())
            sent = decoder.text
        }
    }

    // —— 自动滚到底部（瞬时滚动，避免每 40ms 触发动画造成界面跳动）——
    LaunchedEffect(sent) {
        val max = scrollState.maxValue
        if (max > 0 && scrollState.value != max) {
            scrollState.scrollTo(max)
        }
    }

    // —— UI 节流 tick：60ms ——
    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            keying = app.sidetone.gated || dotHeld || dashHeld
            currentElem = when { dotHeld -> '.'; dashHeld -> '-'; else -> null }
        }
    }

    fun leverDown(c: Char) {
        tapMemory = c
        if (c == '.') dotHeld = true else dashHeld = true
    }
    fun leverUp(c: Char) { if (c == '.') dotHeld = false else dashHeld = false }
    fun clearAll() { decoder.reset(); sent = "" }

    val dotPulse by rememberInfiniteTransition(label = "k-led").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "p"
    )

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(6.dp))

            // 状态栏
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Surface1).border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (keying) Green.copy(alpha = dotPulse) else Line))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            keying -> "正在发码 ${if (currentElem != null) "→ ${if (currentElem == '.') "·" else "—"}" else ""}"
                            else -> "左点右划 · 点划可连续组合"
                        },
                        color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "估测 ${"%.0f".format(decoder.estimatedWpm)} WPM · 平均点长 ${"%.0f".format(decoder.avgDotMs)} ms",
                        color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = if (s.iambicB) "IAMBIC B" else "单点单划",
                    color = if (s.iambicB) Green else TextDim, fontSize = 10.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (s.iambicB) Green.copy(alpha = 0.15f) else Surface2)
                        .border(1.dp, if (s.iambicB) Green.copy(alpha = 0.5f) else Line, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // 收报显示 + 清屏
            Column(
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A0D11)).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("实时解码", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Surface2).border(1.dp, Line, RoundedCornerShape(8.dp))
                            .clickable { clearAll() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("清屏", color = TextDim, fontSize = 10.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF06080C))
                        .border(1.dp, Line, RoundedCornerShape(8.dp))
                        .padding(12.dp).verticalScroll(scrollState)
                ) {
                    Text(
                        text = sent.ifEmpty { "— 等待发码 —" },
                        color = if (sent.isEmpty()) TextDim else TextMain,
                        fontSize = 24.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, lineHeight = 32.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("提示：可连续轻点“·”“—”组合成完整电码；双指同时按 = IAMBIC B 交替发码。",
                color = TextDim, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))
        }

        // —— 固定按键区 ——
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Keypad(
                    label = "·", sub = "点  按住连续发点",
                    accent = Amber, modifier = Modifier.weight(1f).height(140.dp),
                    enabled = true, keypadKey = 1,
                    onDown = { leverDown('.') }, onUp = { leverUp('.') }
                )
                Keypad(
                    label = "—", sub = "划  按住连续发划",
                    accent = Green, modifier = Modifier.weight(1f).height(140.dp),
                    enabled = true, keypadKey = 2,
                    onDown = { leverDown('-') }, onUp = { leverUp('-') }
                )
            }
        }
    }
}

// ============ Tab 3: 文本转 CW ============
@Composable
private fun EncodeBlock(app: AppState) {
    val s = app.prefs.data
    var input by remember { mutableStateOf(TextFieldValue("CQ CQ DE BI1GHP K")) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(app.audio.isPlaying) { isPlaying = app.audio.isPlaying }

    val textOnly = remember(input.text) {
        input.text.uppercase().filter { Morse.code(it) != null }
    }
    val durationMs = remember(textOnly) {
        buildSegments(textOnly, Fist(wpm = s.wpm.toDouble(), farnsworth = s.farnsworth.toDouble()))
            .sumOf { it.ms }
    }

    fun play() {
        val txt = input.text.uppercase().trim().filter { Morse.code(it) != null }
        if (txt.isEmpty()) return
        val fist = Fist(wpm = s.wpm.toDouble(), farnsworth = s.farnsworth.toDouble())
        app.audio.play(
            voices = listOf(VoiceSpec(text = txt, fist = fist, toneHz = s.toneHz.toDouble())),
            volume = s.volume, noiseLevel = s.noiseLevel
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text("要转换的文本", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = input, onValueChange = { input = it },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 18.sp,
                    fontWeight = FontWeight.Medium, color = TextMain
                ),
                cursorBrush = SolidColor(Amber),
                modifier = Modifier.fillMaxWidth().height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2).border(1.dp, Line, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (input.text.isEmpty()) Text(
                        "输入字母 / 数字 / 常用标点…",
                        color = TextDim.copy(alpha = 0.5f), fontSize = 18.sp, fontFamily = FontFamily.Monospace
                    )
                    inner()
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A0D11)).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(14.dp)
        ) {
            Text("CW 预览 · 点划", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text(
                text = Morse.grouped(textOnly).joinToString("  ") { "${it.first} ${it.second}" },
                color = Amber, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("速度 ${s.wpm} WPM", Amber)
            StatChip("音调 ${s.toneHz} Hz", Green)
            StatChip("时长 ${"%.1f".format(durationMs / 1000.0)} s", TextDim)
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isPlaying) Red else Amber)
                .clickable { if (isPlaying) app.audio.stop() else play() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isPlaying) "■" else "▶",
                    color = if (isPlaying) Color.White else Color(0xFF1A1200),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) "停止播放 CW" else "播放 CW 电码",
                    color = if (isPlaying) Color.White else Color(0xFF1A1200),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatChip(text: String, color: Color) {
    Text(
        text = text, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(Surface2).border(1.dp, Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
