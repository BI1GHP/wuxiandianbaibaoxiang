package com.wc.cwtrainer.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.ContestCheck
import com.wc.cwtrainer.core.ContestGen
import com.wc.cwtrainer.core.ContestMode
import com.wc.cwtrainer.core.VoiceSpec
import com.wc.cwtrainer.ui.WpmAdjuster
import kotlin.math.cos
import kotlin.random.Random

// —— 短波面板专用色板 ——
private val PanelBg = Color(0xFF12161C)
private val PanelRaised = Color(0xFF1E242D)
private val PanelRecessed = Color(0xFF0A0D11)
private val PanelLine = Color(0xFF2A3240)
private val LedAmber = Color(0xFFFFC857)
private val LedGreen = Color(0xFF3DDC97)
private val LedRed = Color(0xFFFF4444)
private val LedCyan = Color(0xFF00D4FF)
private val LedDim = Color(0xFF3A4250)

@Composable
fun ContestScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data

    var station by remember { mutableStateOf(ContestGen.station()) }
    var playing by remember { mutableStateOf(false) }
    var loopSession by remember { mutableStateOf(0) }
    var nextReplayIn by remember { mutableStateOf(0) }
    var showScript by remember { mutableStateOf(false) }   // 默认关闭！真实比赛盲听

    var callIn by remember { mutableStateOf(TextFieldValue("")) }
    var rstIn by remember { mutableStateOf(TextFieldValue("")) }
    var serialIn by remember { mutableStateOf(TextFieldValue("")) }
    var check by remember { mutableStateOf<ContestCheck?>(null) }

    // 强制 CW 模式
    val mode = ContestMode.CW
    val script = remember(station) { ContestGen.script(station, mode) }

    // —— 侧音状态：监听 sidetone.gated 来驱动频谱瀑布的"发码亮带"效果 ——
    var keying by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        while (playing) {
            kotlinx.coroutines.delay(30)
            keying = app.sidetone.gated
        }
    }

    val pulse by rememberInfiniteTransition(label = "ct-led").animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val smeter by rememberInfiniteTransition(label = "smeter").animateFloat(
        initialValue = 0f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "sm"
    )

    fun stop() {
        playing = false; nextReplayIn = 0
        app.audio.stop()
    }
    fun newStation() {
        stop()
        station = ContestGen.station()
        callIn = TextFieldValue(""); rstIn = TextFieldValue(""); serialIn = TextFieldValue("")
        check = null
    }
    fun play() {
        check = null
        app.audio.stop()
        playing = true
        loopSession++
    }

    // —— 自动循环播放：一遍播完静默 5 秒后自动重播，直至点"停止"或换台/离开 ——
    LaunchedEffect(loopSession) {
        if (loopSession == 0) return@LaunchedEffect
        while (playing) {
            // script/station 是 state 委托，循环内读取始终拿到当前台站的报文
            // 码速用赛事板块独立设置覆盖台站随机手法，其余手法参数（划长/抖动等）保留真实感
            app.audio.play(
                voices = listOf(VoiceSpec(
                    text = script,
                    fist = station.cwFist.copy(wpm = s.contestWpm.toDouble()),
                    toneHz = s.toneHz.toDouble()
                )),
                volume = s.volume, noiseLevel = s.noiseLevel
            )
            while (app.audio.isPlaying && playing) kotlinx.coroutines.delay(100)
            if (!playing) break
            for (sec in 5 downTo 1) {
                nextReplayIn = sec
                kotlinx.coroutines.delay(1000)
                if (!playing) break
            }
            nextReplayIn = 0
        }
        nextReplayIn = 0
    }

    fun checkIt() {
        check = ContestGen.check(station, callIn.text, rstIn.text, serialIn.text)
        check?.let { app.stats.reportContest(it.score) }
    }

    DisposableEffect(Unit) {
        onDispose { app.sidetone.stop() }
    }

    Column(Modifier.fillMaxSize().background(PanelBg)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(Brush.verticalGradient(listOf(PanelBg, Color(0xFF0E1116))))
        ) {
            Spacer(Modifier.height(10.dp))
            MetalHeader(onBack = { onBack() }, title = "CW 赛事听抄", subtitle = "模拟电台面板")
            Spacer(Modifier.height(10.dp))

            // 频率面板（只显示频率 + 模式，不显示对方呼号 —— 真实电台你不知道对方是谁）
            FrequencyPanel(pulse)
            Spacer(Modifier.height(10.dp))

            // 模式灯 + 状态灯：独立一行，左右平铺
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.weight(1f)) { FixedModeLight(pulse = pulse) }
                Box(Modifier.weight(1f)) { StatusLeds(playing = playing, pulse = pulse) }
            }
            Spacer(Modifier.height(8.dp))

            // 赛事码速调节（独立于全局设置）
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                WpmAdjuster(
                    wpm = s.contestWpm,
                    onWpm = { app.prefs.update { p -> p.copy(contestWpm = it) } },
                    label = "对方码速"
                )
            }
            Spacer(Modifier.height(10.dp))

            // 频谱瀑布：独占一行，全宽平铺填满
            SpectrumWaterfall(playing = playing, keying = keying, pulse = pulse)

            Spacer(Modifier.height(10.dp))
            SMeterPanel(level = if (playing) smeter else 0f)

            // —— 报文内容（默认关闭，真实比赛盲听）——
            Spacer(Modifier.height(10.dp))
            ScriptPanel(script = script, show = showScript, onToggle = { showScript = !showScript })

            Spacer(Modifier.height(10.dp))
            CopyPanel(
                callIn = callIn, rstIn = rstIn, serialIn = serialIn,
                onCall = { callIn = it }, onRst = { rstIn = it }, onSerial = { serialIn = it }
            )

            if (check != null) {
                Spacer(Modifier.height(10.dp))
                ResultPanel(check!!, station)
            }
            Spacer(Modifier.height(16.dp))
        }

        BottomPanelBar(
            playing = playing, hasResult = check != null, nextReplayIn = nextReplayIn,
            onNewStation = { newStation() }, onPlay = { play() },
            onStop = { stop() }, onCheck = { checkIt() },
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ============ 子组件 ============

@Composable
private fun MetalHeader(onBack: () -> Unit, title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(PanelRaised, PanelBg, PanelRaised)))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 返回", color = LedDim.copy(alpha = 0.7f), fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }
                    .padding(horizontal = 6.dp, vertical = 4.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, color = LedAmber, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text(subtitle, color = LedDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Screw(); Spacer(Modifier.width(10.dp)); Screw()
        }
    }
}

@Composable
private fun Screw() {
    Box(
        Modifier.size(14.dp).clip(CircleShape)
            .background(Brush.radialGradient(colors = listOf(Color(0xFF4A5464), Color(0xFF1C222C))))
            .border(1.dp, Color(0xFF0A0D11), CircleShape),
        contentAlignment = Alignment.Center
    ) { Box(Modifier.size(9.dp).background(Color(0xFF0A0D11)).height(1.dp)) }
}

@Composable
private fun FrequencyPanel(pulse: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("7.040.00",
                color = LedAmber.copy(alpha = 0.92f * pulse + 0.08f),
                fontSize = 36.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("CW", color = LedCyan.copy(alpha = 0.9f),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("VFO  A", color = LedDim, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 1.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("●  -0 dBm", color = LedGreen.copy(alpha = 0.8f),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun FixedModeLight(pulse: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(10.dp)
    ) {
        Text("模式 MODE", color = LedDim, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(LedGreen)
                        .border(1.dp, PanelLine, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f))) }
                Text("CW", color = LedGreen, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(LedDim)
                        .border(1.dp, PanelLine, CircleShape)
                )
                Text("SSB", color = LedDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun StatusLeds(playing: Boolean, pulse: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(10.dp)
    ) {
        Text("状态 STATUS", color = LedDim, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusDot("RX", LedGreen, playing)
            StatusDot("TX", LedAmber, false)
            StatusDot("B", LedRed, playing)
        }
    }
}

@Composable
private fun StatusDot(label: String, color: Color, on: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (on) color else LedDim)
            .border(1.dp, PanelLine, CircleShape))
        Text(label, color = if (on) color else LedDim, fontSize = 8.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 3.dp))
    }
}

/**
 * 模拟频谱瀑布（Canvas 绘制）。
 * 真实电台的 waterfall 是 FFT + 滚动，这里用简化模型：
 * - 64 个频率 bin，左到右对应频谱低端到高端
 * - 播放 + 侧音(keying)时，目标频率附近有一条亮带
 * - 噪声底用暗绿色，侧音时变成琥珀色 + 绿色混合
 * - 每 50ms 刷新一次，加入随机扰动让画面"活起来"
 */
@Composable
private fun SpectrumWaterfall(playing: Boolean, keying: Boolean, pulse: Float) {
    // 缓存频谱能量分布（64 个 bin × 40 行历史）
    var waterfall by remember { mutableStateOf(List(40) { List(64) { 0f } }) }

    // —— 永远在动：不管 playing 与否都刷 ——
    LaunchedEffect(Unit) {
        var simulateOn = false
        var nextSwitchAt = SystemClock.elapsedRealtime() + Random.nextLong(40, 120)
        var frameCount = 0L
        var driftOffset = 0.0   // 频率漂移：±3 bin 慢慢晃

        while (true) {
            kotlinx.coroutines.delay(40)
            frameCount++
            val now = SystemClock.elapsedRealtime()

            // —— 控制"发码"节奏 ——
            if (now >= nextSwitchAt) {
                simulateOn = !simulateOn
                nextSwitchAt = now + if (simulateOn) {
                    if (Random.nextBoolean()) Random.nextLong(60, 130) else Random.nextLong(160, 320)
                } else {
                    Random.nextLong(50, 260)
                }
            }

            // —— 频率漂移：正弦缓慢左右晃 ±2 bin ——
            driftOffset = kotlin.math.sin(frameCount / 25.0) * 2.0

            // —— 生成频谱 ——
            val baseBin = 32 + driftOffset
            val intensityMul = if (playing) 1.2f else 0.45f   // 播放时更亮
            val newBins = FloatArray(64)

            for (i in 0 until 64) {
                val dist = kotlin.math.abs(i - baseBin)
                val noise = (Random.nextFloat() * 0.09f + 0.04f).toDouble()

                val signal: Double = if (simulateOn) {
                    val g = kotlin.math.exp(-dist * dist / 8.0)
                    (0.65 + Random.nextFloat() * 0.35f) * g * intensityMul
                } else if (playing && dist <= 2) {
                    (Random.nextFloat() * 0.06f).toDouble()
                } else {
                    0.0
                }

                newBins[i] = (noise + signal).coerceIn(0.0, 1.0).toFloat()
            }

            // —— 瀑布滚动：新帧在底部，旧帧上移 ——
            waterfall = (waterfall.dropLast(1) + listOf(newBins.toList())).toList()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("频谱 WATERFALL", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (playing) "● 接收中" else "○ 待机",
                color = if (playing) LedGreen else LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF05080C))
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val w = size.width
                val h = size.height
                val binW = w / 64f
                val rowH = h / 40f

                // 画瀑布：从底部（最新）到顶部（最旧）
                for (rowIdx in 0 until 40) {
                    val row = waterfall[39 - rowIdx]  // 旧的在上
                    // 越旧越暗，但最旧仍保留 ~53% 亮度 —— 之前 0.025 衰减到 0.025 导致底部几乎全黑
                    val rowAlpha = 1f - rowIdx * 0.012f
                    for (binIdx in 0 until 64) {
                        val energy = row[binIdx]
                        if (energy > 0.005f) {
                            val color = when {
                                energy > 0.75f -> LedAmber.copy(alpha = (energy * rowAlpha).coerceAtMost(1f))
                                energy > 0.45f -> LedGreen.copy(alpha = (energy * rowAlpha).coerceAtMost(1f))
                                // 噪声底加一个最低 alpha，保证每个 bin 都可见，瀑布填满整框
                                else -> LedGreen.copy(alpha = ((energy * 1.5f).coerceAtMost(0.6f) * rowAlpha).coerceAtLeast(0.05f))
                            }
                            drawRect(
                                color = color,
                                topLeft = Offset(binIdx * binW, rowIdx * rowH),
                                size = androidx.compose.ui.geometry.Size(binW + 0.5f, rowH + 0.5f)
                            )
                        }
                    }
                }

                // 中心频率参考线
                val centerX = 32 * binW + binW / 2f
                drawLine(
                    color = LedCyan.copy(alpha = 0.3f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, h),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }
            // 叠加 LSB / USB 标签（用 Compose Text 而不是 Canvas drawText）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LSB", color = LedDim.copy(alpha = 0.6f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Text("USB", color = LedDim.copy(alpha = 0.6f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // 频率刻度（下方）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (label in listOf("7.000", "7.020", "7.040", "7.060", "7.080")) {
                Text(label, color = LedDim.copy(alpha = 0.5f), fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun SMeterPanel(level: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("S METER", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("RF  SIGNAL", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(20.dp)
                .clip(RoundedCornerShape(4.dp)).background(PanelBg)
                .border(1.dp, PanelLine, RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 12) {
                val segLevel = (i + 1) / 12f
                val isOn = level >= segLevel
                val color = when {
                    isOn && i < 9 -> LedGreen
                    isOn && i < 11 -> LedAmber
                    isOn -> LedRed
                    else -> LedDim
                }
                Box(Modifier.weight(1f).fillMaxSize().padding(1.dp)
                    .background(if (isOn) color.copy(alpha = 0.85f) else LedDim.copy(alpha = 0.3f)))
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (label in listOf("S1", "S3", "S5", "S7", "S9", "+20", "+40", "+60")) {
                Text(label, color = LedDim, fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ScriptPanel(script: String, show: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("报文内容 SCRIPT", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    .background(if (show) LedAmber.copy(alpha = 0.15f) else PanelBg)
                    .border(1.dp, if (show) LedAmber.copy(alpha = 0.5f) else PanelLine, RoundedCornerShape(4.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape)
                    .background(if (show) LedAmber else LedDim))
                Spacer(Modifier.width(4.dp))
                Text(if (show) "开" else "关（盲听）",
                    color = if (show) LedAmber else LedDim,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (show) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(PanelBg)
                    .border(1.dp, PanelLine, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(script, color = LedAmber, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp, lineHeight = 18.sp)
            }
        } else {
            Text("（已隐藏报文，专心盲听）",
                color = LedDim.copy(alpha = 0.6f),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CopyPanel(
    callIn: TextFieldValue, rstIn: TextFieldValue, serialIn: TextFieldValue,
    onCall: (TextFieldValue) -> Unit, onRst: (TextFieldValue) -> Unit,
    onSerial: (TextFieldValue) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRaised)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(14.dp)
    ) {
        Text("抄收填写 COPY LOG", color = LedDim, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))

        Column {
            Text("呼号  CALLSIGN", color = LedAmber.copy(alpha = 0.7f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            MetalField(value = callIn, onValueChange = onCall,
                placeholder = "对方呼号…", fontSize = 24)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("RST", color = LedAmber.copy(alpha = 0.7f),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                MetalField(value = rstIn, onValueChange = onRst,
                    placeholder = "599", fontSize = 18)
            }
            Column(Modifier.weight(1f)) {
                Text("序号  SERIAL", color = LedAmber.copy(alpha = 0.7f),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                MetalField(value = serialIn, onValueChange = onSerial,
                    placeholder = "001", fontSize = 18)
            }
        }
    }
}

@Composable
private fun MetalField(
    value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit,
    placeholder: String, fontSize: Int
) {
    BasicTextField(
        value = value, onValueChange = onValueChange,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace, fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold, color = LedGreen
        ),
        singleLine = true, cursorBrush = SolidColor(LedAmber),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(PanelRecessed)
                    .border(1.dp, PanelLine, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (value.text.isEmpty()) Text(
                    placeholder, color = LedDim.copy(alpha = 0.45f),
                    fontSize = fontSize.sp, fontFamily = FontFamily.Monospace)
                inner()
            }
        }
    )
}

@Composable
private fun ResultPanel(check: ContestCheck, station: com.wc.cwtrainer.core.ContestStation) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("判分 RESULT", color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${check.score} / 3",
                color = when { check.score == 3 -> LedGreen; check.score == 2 -> LedAmber; else -> LedRed },
                fontSize = 32.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(PanelLine))
        Spacer(Modifier.height(10.dp))
        CheckLine("呼号 CALL", check.callOk, station.call)
        Spacer(Modifier.height(6.dp))
        CheckLine("RST", check.reportOk, station.report)
        Spacer(Modifier.height(6.dp))
        CheckLine("序号 SERIAL", check.serialOk, station.serial)
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean, correct: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LedDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(18.dp).clip(CircleShape)
                .background(if (ok) LedGreen.copy(alpha = 0.2f) else LedRed.copy(alpha = 0.2f))
                .border(1.dp, if (ok) LedGreen else LedRed, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(if (ok) "✓" else "✗",
            color = if (ok) LedGreen else LedRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Text(correct, color = if (ok) LedGreen else LedRed,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BottomPanelBar(
    playing: Boolean, hasResult: Boolean, nextReplayIn: Int,
    onNewStation: () -> Unit, onPlay: () -> Unit,
    onStop: () -> Unit, onCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(PanelRaised, PanelBg, PanelRaised)))
            .border(1.dp, PanelLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PanelButton(
            text = "下一组", color = LedAmber, filled = false,
            modifier = Modifier.weight(1f)
        ) { onNewStation() }

        if (playing) {
            PanelButton(
                text = if (nextReplayIn > 0) "停止 ${nextReplayIn}s" else "停止",
                color = LedRed, filled = true,
                modifier = Modifier.weight(1f)
            ) { onStop() }
        } else {
            PanelButton(
                text = "接收呼叫", color = LedAmber, filled = true,
                modifier = Modifier.weight(1f)
            ) { onPlay() }
        }

        PanelButton(
            text = if (hasResult) "已提交" else "提交",
            color = if (hasResult) LedDim else LedGreen, filled = false,
            enabled = !playing && !hasResult,
            modifier = Modifier.weight(1f)
        ) { onCheck() }
    }
}

@Composable
private fun PanelButton(
    text: String, color: Color, filled: Boolean,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = when {
        !enabled -> color.copy(alpha = 0.5f)
        filled && color == LedRed -> Color.White
        filled -> Color(0xFF1A1200)
        else -> color
    }
    Box(
        modifier = modifier.height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(1.dp, if (filled) color else color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1, softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}
