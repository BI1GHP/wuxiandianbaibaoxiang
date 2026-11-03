package com.wc.cwtrainer.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip

enum class TranslatorTab { DECODE, ENCODE }

@Composable
fun TranslatorScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    var tab by remember { mutableStateOf(TranslatorTab.DECODE) }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B1220))) {
        ScreenHeader("CW 翻译器", onBack)
        Spacer(Modifier.height(8.dp))

        // Tab 切换
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1).border(1.dp, Line, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabBtn("🔍 实时解码", tab == TranslatorTab.DECODE,
                Modifier.weight(1f)) { tab = TranslatorTab.DECODE }
            Spacer(Modifier.width(4.dp))
            TabBtn("📝 文本转 CW", tab == TranslatorTab.ENCODE,
                Modifier.weight(1f)) { tab = TranslatorTab.ENCODE }
        }

        Spacer(Modifier.height(10.dp))

        when (tab) {
            TranslatorTab.DECODE -> DecodeBlock(app)
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
            .border(
                1.dp,
                if (selected) Amber.copy(alpha = 0.6f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Amber else TextDim,
            fontSize = 13.sp, fontWeight = FontWeight.Medium
        )
    }
}

// ============ 实时解码 ============
@Composable
private fun DecodeBlock(app: AppState) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s = app.prefs.data
    var listening by remember { mutableStateOf(false) }
    var micLevel by remember { mutableStateOf(0f) }
    var micDenied by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var estWpm by remember { mutableStateOf(20.0) }
    var lastDotMs by remember { mutableStateOf(60.0) }

    val decoder = remember { MorseDecoder(s.wpm.toDouble()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { micDenied = false; listening = true } else { micDenied = true }
    }
    fun toggle() {
        if (listening) {
            listening = false
            decoder.reset(); text = ""; estWpm = 20.0; lastDotMs = 60.0
        } else {
            val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (ok) listening = true else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(listening, s.extToneHz, s.micSensitivity) {
        val detector = ToneKeyDetector(
            detectorHz = s.extToneHz.toDouble(), sensitivity = s.micSensitivity,
            onDown = { t -> decoder.keyDown(t) },
            onUp = { t -> decoder.keyUp(t) },
            onLevel = { lvl -> micLevel = lvl }
        )
        if (listening) detector.start()
        onDispose { detector.stop() }
    }

    LaunchedEffect(listening) {
        while (listening) {
            delay(40)
            decoder.tick(SystemClock.elapsedRealtime())
            text = decoder.text
            estWpm = decoder.estimatedWpm
            lastDotMs = decoder.avgDotMs
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (listening) Green.copy(alpha = 0.15f) else Surface2)
                        .border(2.dp, if (listening) Green.copy(alpha = 0.6f) else Line, CircleShape)
                        .clickable { toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (listening) "■" else "▶",
                        color = if (listening) Green else Amber,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
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
                            .padding(top = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(micLevel.coerceIn(0.001f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (micLevel > 0.05f) Green else Line)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("估测 ${"%.0f".format(estWpm)} WPM", color = Green, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Green.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
                Text("平均点 ${"%.0f".format(lastDotMs)} ms", color = Amber, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Amber.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
                Text("检测频率 ${s.extToneHz} Hz", color = TextDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(Surface2).border(1.dp, Line, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (micDenied) {
                Spacer(Modifier.height(6.dp))
                Text("⚠ 未获得麦克风权限", color = Red, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A0D11)).border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("解码结果", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (listening) "● 实时" else "○ 停止",
                    color = if (listening) Green else TextDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF06080C))
                    .border(1.dp, Line, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = text.ifEmpty { "等待接收电码…" },
                    color = if (listening) Green else TextDim,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "将电台侧音 / 外部键控器 / USB 声卡输出接到手机麦克风输入。APP 自动检测设定频率上的音调能量，自适应码速。",
            color = TextDim, fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}

// ============ 文本转 CW ============
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
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text("要转换的文本", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = input, onValueChange = { input = it },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 18.sp,
                    fontWeight = FontWeight.Medium, color = TextMain
                ),
                modifier = Modifier.fillMaxWidth().height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2).border(1.dp, Line, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (input.text.isEmpty()) Text(
                        "输入字母 / 数字 / 常用标点…",
                        color = TextDim.copy(alpha = 0.5f), fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    inner()
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A0D11)).border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text("CW 预览 · 点划", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text(
                text = Morse.grouped(textOnly).joinToString("  ") { "${it.first} ${it.second}" },
                color = Amber, fontSize = 16.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ParamChip("速度 ${"%.0f".format(s.wpm)} WPM", Amber)
            ParamChip("音调 ${s.toneHz} Hz", Green)
            ParamChip("时长 ${"%.1f".format(durationMs / 1000.0)} s", TextDim)
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
private fun ParamChip(text: String, color: Color) {
    Text(
        text = text, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(Surface2).border(1.dp, Line, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
