package com.wc.cwtrainer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.Generators
import com.wc.cwtrainer.core.InputMode
import com.wc.cwtrainer.core.KeyMode
import com.wc.cwtrainer.core.MorseDecoder
import com.wc.cwtrainer.core.TextScore
import com.wc.cwtrainer.core.ToneKeyDetector
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Keypad
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.delay

private data class SendResult(
    val target: String, val sent: String,
    val accuracy: Float, val dotMs: Double, val wpm: Double
)

@Composable
fun SendScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    val ctx = LocalContext.current

    var target by remember { mutableStateOf(TextFieldValue("CQ CQ DE BG1WC K")) }
    var sent by remember { mutableStateOf("") }
    var session by remember { mutableStateOf(false) }
    var keying by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SendResult?>(null) }
    var showTargetInput by remember { mutableStateOf(false) }

    var dotHeld by remember { mutableStateOf(false) }
    var dashHeld by remember { mutableStateOf(false) }
    var paddleMemory by remember { mutableStateOf<Char?>(null) }
    var lastElem by remember { mutableStateOf<Char?>(null) }

    var listening by remember { mutableStateOf(false) }
    var micLevel by remember { mutableStateOf(0f) }
    var micDenied by remember { mutableStateOf(false) }

    val decoder = remember { MorseDecoder(s.wpm.toDouble()) }
    val sessionNow by rememberUpdatedState(session)

    fun startSession() {
        app.audio.stop()
        app.speaker.stop()
        decoder.reset()
        sent = ""
        result = null
        paddleMemory = null
        lastElem = null
        dotHeld = false
        dashHeld = false
        session = true
        val cur = app.prefs.data
        app.sidetone.frequency = cur.toneHz.toDouble()
        app.sidetone.volume = cur.volume
        if (cur.inputSource == InputMode.SCREEN) app.sidetone.start()
    }

    fun stopSession() {
        if (decoder.isKeyDown) decoder.keyUp(SystemClock.elapsedRealtime())
        decoder.commit()
        sent = decoder.text
        session = false
        keying = false
        app.sidetone.gated = false
        app.sidetone.stop()
    }

    fun evaluate() {
        if (session) stopSession()
        decoder.commit()
        val sentText = decoder.text
        sent = sentText
        result = SendResult(
            target = target.text, sent = sentText,
            accuracy = TextScore.accuracy(target.text, sentText),
            dotMs = decoder.avgDotMs, wpm = decoder.estimatedWpm
        )
    }

    fun regenerateTarget() {
        target = TextFieldValue(Generators.groups(Generators.MIXED, 3, 4))
        if (session) stopSession()
        sent = ""; result = null
    }

    fun leverDown(c: Char) {
        if (!session || app.prefs.data.inputSource != InputMode.SCREEN) return
        if (c == '.') dotHeld = true else dashHeld = true
    }
    fun leverUp(c: Char) {
        if (c == '.') dotHeld = false else dashHeld = false
        val modeB = app.prefs.data.iambicB
        if (lastElem == null || (modeB && lastElem != c)) paddleMemory = c
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { micDenied = false; listening = true } else { micDenied = true }
    }
    fun toggleListening() {
        if (listening) { listening = false; if (session) stopSession() }
        else {
            val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (ok) listening = true else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(session) {
        while (session) { delay(40); decoder.tick(SystemClock.elapsedRealtime()); sent = decoder.text }
    }
    LaunchedEffect(session, s.inputSource) {
        if (!session || s.inputSource != InputMode.SCREEN) return@LaunchedEffect
        while (true) {
            if (!dotHeld && !dashHeld && paddleMemory == null) { delay(15); continue }
            val next = when {
                dotHeld && dashHeld -> if (lastElem == '.') '-' else '.'
                dotHeld -> '.'; dashHeld -> '-'
                else -> paddleMemory!!
            }
            if (!dotHeld && !dashHeld) paddleMemory = null
            val unitMs = 1200.0 / app.prefs.data.wpm
            val durMs = if (next == '.') unitMs else unitMs * 3.0
            lastElem = next; keying = true; app.sidetone.gated = true
            decoder.keyDown(SystemClock.elapsedRealtime()); delay(durMs.toLong())
            app.sidetone.gated = false
            decoder.keyUp(SystemClock.elapsedRealtime()); keying = false; delay(unitMs.toLong())
        }
    }
    LaunchedEffect(listening) {
        if (listening) {
            if (app.prefs.data.inputSource != InputMode.AUDIO)
                app.prefs.update { it.copy(inputSource = InputMode.AUDIO) }
            if (!session) startSession()
        }
    }
    DisposableEffect(listening, s.extToneHz, s.micSensitivity) {
        val detector = ToneKeyDetector(
            detectorHz = s.extToneHz.toDouble(), sensitivity = s.micSensitivity,
            onDown = { t -> if (sessionNow) { decoder.keyDown(t); keying = true } },
            onUp = { t -> if (sessionNow) { decoder.keyUp(t); keying = false } },
            onLevel = { lvl -> micLevel = lvl }
        )
        if (listening) detector.start()
        onDispose { detector.stop() }
    }

    // ========== UI ==========

    val dotPulse by rememberInfiniteTransition(label = "snd-pulse").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(Modifier.fillMaxSize().background(Bg)) {
        // 顶部
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹ 返回", color = TextDim, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onBack() }
                    .padding(horizontal = 6.dp, vertical = 6.dp))
            Text("发码练习", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            val srcLabel = if (s.inputSource == InputMode.SCREEN) "屏幕" else "音频"
            ModeChipFast("左点右划", srcLabel)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 状态栏
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Surface1).border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(
                        when {
                            keying -> Green.copy(alpha = dotPulse)
                            session -> Amber.copy(alpha = dotPulse)
                            listening -> Green.copy(alpha = dotPulse)
                            else -> Line
                        }
                    )
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            listening -> "监听外部键中…"
                            session && keying -> "正在发码"
                            session -> "发码中（空闲）"
                            else -> "等待开始"
                        },
                        color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "估测 ${"%.0f".format(decoder.estimatedWpm)} WPM · 平均点长 ${"%.0f".format(decoder.avgDotMs)} ms",
                        color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 目标报文卡
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目标报文", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (showTargetInput) "收起" else "编辑",
                        color = Amber, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                            showTargetInput = !showTargetInput
                        }.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (showTargetInput) {
                    BasicTextField(
                        value = target,
                        onValueChange = { target = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TextMain
                        ),
                        cursorBrush = SolidColor(Amber),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { innerTextField() }
                        }
                    )
                } else {
                    Text(
                        text = target.text.ifEmpty { "点击右上角编辑目标报文" },
                        color = if (target.text.isEmpty()) TextDim else TextMain,
                        fontSize = 20.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, lineHeight = 26.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("屏幕", s.inputSource == InputMode.SCREEN) {
                        if (session) stopSession(); if (listening) listening = false
                        app.prefs.update { it.copy(inputSource = InputMode.SCREEN) }
                    }
                    Pill("音频接口", s.inputSource == InputMode.AUDIO) {
                        if (session) stopSession()
                        app.prefs.update { it.copy(inputSource = InputMode.AUDIO) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 发码按键区 或 外部音频区
            if (s.inputSource == InputMode.SCREEN) {
                ScreenKeyBlock(
                    session = session,
                    iambicB = s.iambicB,
                    onDownDot = { leverDown('.') },
                    onUpDot = { leverUp('.') },
                    onDownDash = { leverDown('-') },
                    onUpDash = { leverUp('-') }
                )
            } else {
                AudioInputBlock(
                    listening = listening,
                    micLevel = micLevel,
                    micDenied = micDenied,
                    onToggle = { toggleListening() }
                )
            }

            Spacer(Modifier.height(10.dp))

            // 收报显示
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(Surface1, Surface1.copy(alpha = 0.6f))))
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("收报识别", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    if (session) Text("实时识别中", color = Amber, fontSize = 10.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = sent.ifEmpty { "— 等待发码 —" },
                    color = if (sent.isEmpty()) TextDim else TextMain,
                    fontSize = 22.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, lineHeight = 30.sp
                )
            }

            // 结果卡
            if (result != null) {
                Spacer(Modifier.height(10.dp))
                ResultBlock(result!!, target.text) {
                    sent = ""; result = null
                }
            }

            Spacer(Modifier.height(18.dp))
        }

        // 底部操作条
        BottomSendBar(
            session = session,
            listening = listening,
            hasResult = result != null,
            onRegenerate = { regenerateTarget() },
            onStartStop = { if (session) stopSession() else startSession() },
            onEvaluate = { evaluate() },
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ============ 子组件 ============

@Composable
private fun ModeChipFast(a: String, b: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = a, color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.padding(horizontal = 6.dp).size(4.dp).clip(CircleShape).background(Line))
        Text(text = b, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun ScreenKeyBlock(
    session: Boolean,
    iambicB: Boolean,
    onDownDot: () -> Unit, onUpDot: () -> Unit,
    onDownDash: () -> Unit, onUpDash: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "固定左点右划 · 按住连续发",
                color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            if (iambicB) {
                Text("IAMBIC B", color = Green, fontSize = 10.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Green.copy(alpha = 0.15f))
                        .border(1.dp, Green.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Keypad(
                label = "·",
                sub = "点  按住连续发点",
                accent = Amber, modifier = Modifier.weight(1f).height(110.dp),
                enabled = session, onDown = onDownDot, onUp = onUpDot
            )
            Keypad(
                label = "—",
                sub = "划  按住连续发划",
                accent = Green, modifier = Modifier.weight(1f).height(110.dp),
                enabled = session, onDown = onDownDash, onUp = onUpDash
            )
        }
        Text(
            text = if (!session) "点击下方『开始发码』后按键才激活" else "",
            color = TextDim, fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun AudioInputBlock(
    listening: Boolean,
    micLevel: Float,
    micDenied: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("外部音频接口", color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (listening) "监听中" else "未启动",
                color = if (listening) Green else TextDim, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp).clip(CircleShape)
                    .background(if (listening) Green.copy(alpha = 0.15f) else Surface2)
                    .border(2.dp, if (listening) Green.copy(alpha = 0.6f) else Line, CircleShape)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (listening) "■" else "▶",
                    color = if (listening) Green else Amber, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (listening) "点击圆形按钮停止监听" else "点击圆形按钮开始监听",
                    color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                // 电平条
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

        if (micDenied) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "⚠ 未获得麦克风权限，请在系统设置中允许录音后重试。",
                color = Red, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "将外部键控音源（电台侧音 / USB 声卡 / 带侧音的键控器）接到手机麦克风输入。",
            color = TextDim, fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}

@Composable
private fun ResultBlock(r: SendResult, target: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Surface1).border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("发码评估", color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                Text(
                    text = "${"%.0f".format(r.accuracy * 100)}%",
                    color = when {
                        r.accuracy >= 0.9f -> Green
                        r.accuracy >= 0.6f -> Amber
                        else -> Red
                    },
                    fontSize = 40.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("平均点长", color = TextDim, fontSize = 10.sp)
                Text("${"%.0f".format(r.dotMs)} ms", color = TextMain, fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("估测速度", color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                Text("${"%.0f".format(r.wpm)} WPM", color = TextMain, fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Spacer(Modifier.height(12.dp))

        Text("目标", color = TextDim, fontSize = 10.sp)
        Text(target, color = Green, fontSize = 16.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("识别", color = TextDim, fontSize = 10.sp)
        Text(
            text = r.sent.ifEmpty { "—" },
            color = if (r.accuracy > 0.9f) Green else if (r.accuracy > 0.7f) Amber else Red,
            fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("清空重新发码", TextDim, Modifier.weight(1f)) { onReset() }
            SmallAction("换一组目标", Green, Modifier.weight(1f)) { /* handled by caller via onRegenerate - reuse */ }
        }
    }
}

@Composable
private fun BottomSendBar(
    session: Boolean,
    listening: Boolean,
    hasResult: Boolean,
    onRegenerate: () -> Unit,
    onStartStop: () -> Unit,
    onEvaluate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Surface1).border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionBtnS("↻", "换一组", TextDim, Modifier.padding(end = 4.dp)) { onRegenerate() }

        val mainColor = when {
            listening -> Red
            session -> Red
            else -> Amber
        }
        val mainLabel = when {
            listening -> "停止监听"
            session -> "结束发码"
            else -> "开始发码"
        }
        Box(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(mainColor).clickable { onStartStop() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (session || listening) "■" else "▶",
                    color = if (session || listening) Color.White else Color(0xFF1A1200),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = mainLabel,
                    color = if (session || listening) Color.White else Color(0xFF1A1200),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        if (!hasResult) {
            ActionBtnS("✓", "对答案", Green, Modifier.padding(start = 4.dp)) { onEvaluate() }
        } else {
            ActionBtnS("✓", "已评估", Green.copy(alpha = 0.4f), Modifier.padding(start = 4.dp), enabled = false) { }
        }
    }
}

@Composable
private fun ActionBtnS(
    icon: String, label: String, color: Color, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(Surface2).border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, color = if (enabled) color else color.copy(alpha = 0.5f),
            fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = if (enabled) color else color.copy(alpha = 0.5f), fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SmallAction(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
