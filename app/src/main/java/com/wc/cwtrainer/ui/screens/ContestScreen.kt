package com.wc.cwtrainer.ui.screens

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.AudioGate
import com.wc.cwtrainer.core.ContestCheck
import com.wc.cwtrainer.core.ContestGen
import com.wc.cwtrainer.core.ContestMode
import com.wc.cwtrainer.core.ContestQso
import com.wc.cwtrainer.core.VoiceSpec
import com.wc.cwtrainer.ui.KeepScreenOn
import com.wc.cwtrainer.ui.WpmAdjuster
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random

// —— 短波面板专用色板 ——
private val PanelBg = Color(0xFF12161C)
private val PanelRaised = Color(0xFF1E242D)
private val PanelRecessed = Color(0xFF0A0D11)
private val PanelLine = Color(0xFF3B4656)
private val LedAmber = Color(0xFFFFC857)
private val LedGreen = Color(0xFF3DDC97)
private val LedRed = Color(0xFFFF5555)
private val LedCyan = Color(0xFF00D4FF)
// 标签 / 说明文字：提亮到清晰可读的灰蓝（原 0xFF3A4250 在深色底上对比度过低）
private val LedDim = Color(0xFF9BA8BC)
// 熄灭的灯珠 / 未点亮状态：比标签暗一档，但仍能与背景区分
private val LedOff = Color(0xFF4A5568)

// —— 瀑布参数：96 个频率 bin × 40 行历史 ——
private const val WF_BINS = 96
private const val WF_ROWS = 40

// 热力图色标：能量 0→1 连续渐变（蓝黑 → 深蓝 → 青 → 绿 → 黄绿 → 琥珀）
private val HeatStops: List<Pair<Float, Color>> = listOf(
    0.00f to Color(0xFF04060A),
    0.14f to Color(0xFF08213A),
    0.30f to Color(0xFF0B3D58),
    0.44f to Color(0xFF0E6B62),
    0.58f to Color(0xFF20A96B),
    0.74f to Color(0xFF93CE4A),
    0.90f to Color(0xFFFFD24A),
    1.00f to LedAmber
)

private fun mixColor(a: Color, b: Color, f: Float) = Color(
    red = a.red + (b.red - a.red) * f,
    green = a.green + (b.green - a.green) * f,
    blue = a.blue + (b.blue - a.blue) * f,
    alpha = a.alpha + (b.alpha - a.alpha) * f
)

/** 预生成 96 级连续热力色，避免绘制时反复插值 */
private fun buildHeatLut(levels: Int = 96): Array<Color> = Array(levels) { idx ->
    val e = idx / (levels - 1).toFloat()
    var hi = 1
    while (hi < HeatStops.size - 1 && HeatStops[hi].first < e) hi++
    val lo = hi - 1
    val span = HeatStops[hi].first - HeatStops[lo].first
    val f = if (span <= 0f) 1f else ((e - HeatStops[lo].first) / span).coerceIn(0f, 1f)
    mixColor(HeatStops[lo].second, HeatStops[hi].second, f)
}

@Composable
fun ContestScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    val context = LocalContext.current
    // 本次进入赛事页的时间点：复盘 / 导出只统计这一次模拟赛
    val sessionStart = remember { System.currentTimeMillis() }
    var showReview by remember { mutableStateOf(false) }
    var exportMsg by remember { mutableStateOf<String?>(null) }

    val adifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val text = app.contestLog.toAdif(app.prefs.data.callsign, sessionStart)
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.isSuccess
            exportMsg = if (ok) "ADIF 已导出" else "导出失败，请重试"
        }
    }

    var station by remember { mutableStateOf(ContestGen.station()) }
    var playing by remember { mutableStateOf(false) }
    var loopSession by remember { mutableStateOf(0) }
    var nextReplayIn by remember { mutableStateOf(0) }
    var showScript by remember { mutableStateOf(false) }   // 默认关闭！真实比赛盲听

    var callIn by remember { mutableStateOf(TextFieldValue("")) }
    var serialIn by remember { mutableStateOf(TextFieldValue("")) }
    var check by remember { mutableStateOf<ContestCheck?>(null) }

    // CW / SSB 模式（SSB 用系统 TTS 模仿各国口音播报）
    var mode by remember { mutableStateOf(s.contestMode) }
    // SSB 解释法风格：口语（各国习惯解释法）/ 标准（规范 NATO，仅语气差异）
    var stdMode by remember { mutableStateOf(s.ssbStandard) }
    // 底噪电平：独立通道实时可调，松开滑块时落盘
    var noiseLv by remember { mutableStateOf(s.contestNoise) }
    // 语速：拖动只改本地值，松手落盘，下一轮播报生效
    var wpmLv by remember(mode) { mutableStateOf(s.contestWpm) }
    // 虚拟赛场：同频多台（pileup）与传播波动（QSB），仅 CW 生效
    var pileup by remember { mutableStateOf(false) }
    var qsb by remember { mutableStateOf(false) }
    // 干扰台随目标台一起换：每个目标台配两个固定干扰台，重播时听感一致
    val pileupStations = remember(station) { List(2) { ContestGen.station() } }
    val script = remember(station, mode, stdMode) { ContestGen.script(station, mode, stdMode) }
    // SSB 模式额外给出明文版（非字母解释法），显示在报文内容之后
    val plainScript = remember(station, mode) {
        if (mode == ContestMode.SSB) ContestGen.ssbPlain(station) else null
    }

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
        app.voice.stop()
        app.radio.stop()
        app.voiceBank.stop()
        app.speaker.stop()
    }
    fun newStation() {
        stop()
        station = ContestGen.station()
        callIn = TextFieldValue(""); serialIn = TextFieldValue("")
        check = null
    }
    fun play() {
        check = null
        app.audio.stop()
        app.radio.stop()
        app.voiceBank.stop()
        app.speaker.stop()
        // 被来电等打断时底噪也会停：再次播放时重新拉起
        if (!app.noise.isPlaying) {
            app.noise.start(app.prefs.data.volume, app.prefs.data.contestNoise)
        }
        playing = true
        loopSession++
    }

    // —— 自动循环播放：一遍播完静默 5 秒后自动重播，直至点"停止"或换台/离开 ——
    LaunchedEffect(loopSession, mode, stdMode) {
        if (loopSession == 0) return@LaunchedEffect
        // 模式切换会重启本协程，先掐掉上一种模式的残留声音（底噪是独立通道，不动）
        app.audio.stop()
        app.voice.stop()
        app.radio.stop()
        app.voiceBank.stop()
        app.speaker.stop()
        while (playing) {
            // script/station 是 state 委托，循环内读取始终拿到当前台站的报文
            // 码速/音调/音量每轮重播实时读取，用户调节后下一轮自动生效，无需停止重开
            val now = app.prefs.data
            if (mode == ContestMode.CW) {
                // 目标台：满增益居中；开启同频多台时叠加两个弱干扰台（不同音调/码速/起始时刻）
                val voices = mutableListOf(
                    VoiceSpec(
                        text = script,
                        fist = station.cwFist.copy(wpm = now.contestWpm.toDouble()),
                        toneHz = now.toneHz.toDouble()
                    )
                )
                if (pileup) {
                    pileupStations.forEachIndexed { i, st ->
                        voices += VoiceSpec(
                            text = ContestGen.script(st, ContestMode.CW),
                            fist = st.cwFist.copy(
                                wpm = (now.contestWpm + if (i == 0) -4 else 3)
                                    .coerceIn(8, 40).toDouble()
                            ),
                            // 音调错开 50~90Hz：同频但可分辨，接近真实 pileup 的"几个人叠在一起"
                            toneHz = (now.toneHz + if (i == 0) -70 else 55).toDouble(),
                            startMs = if (i == 0) 1100 else 2600,
                            gain = if (i == 0) 0.42f else 0.34f
                        )
                    }
                }
                app.audio.play(
                    voices = voices,
                    volume = now.volume, noiseLevel = 0f,
                    title = "赛事听抄",
                    subtitle = if (pileup) "CW · ${now.contestWpm} WPM · PILEUP"
                        else "CW · ${now.contestWpm} WPM",
                    // 传播波动：深衰落；关闭时保持稳定信号，便于先分辨再进阶
                    fadeDepth = if (qsb) 1f else 0.35f
                )
                while (app.audio.isPlaying && playing) kotlinx.coroutines.delay(100)
            } else {
                // SSB：内置真人语音库（各国母语音色，天然口音），不加底噪
                // 台站自身的 rate 是随机的（0.85~1.2），这里只做大范围收口，
                // 保留每台之间的语速差异，避免每个台听起来一个速度
                val baseRate = ((0.78f + now.contestWpm / 140f) * station.rate)
                    .coerceIn(0.88f, 1.12f)
                // 标准模式：用词一律规范 NATO 解释法，语气（语速/停顿）按台站分平稳/急促/拖长三档
                val rate = if (stdMode) (baseRate * ContestGen.toneRateMul(station))
                    .coerceIn(0.85f, 1.18f) else baseRate
                val pauseMul = if (stdMode) ContestGen.tonePauseMul(station) else 1f
                val bank = app.voiceBank.play(
                    ContestGen.ssbWords(station, stdMode), station.voiceKey, rate, now.volume, pauseMul
                )
                if (bank.isFailure) {
                    // 词库缺词：退回系统 TTS 合成（可带各国口音）
                    val radio = app.radio.play(script, station.localeTag, rate, station.pitch, now.volume)
                    if (radio.isFailure) {
                        // 引擎不支持合成到文件：直接播系统 TTS 原声
                        val spoken = app.speaker.speak(script, station.localeTag, rate, station.pitch)
                        if (spoken.isFailure) {
                            // 设备完全没有语音包：改用内置人声合成（无需下载任何组件）
                            val local = app.voice.speak(script, station.pitch, rate)
                            if (local.isFailure) {
                                // 仍失败：字母解释法用 CW 拍发兜底
                                app.audio.play(
                                    voices = listOf(VoiceSpec(
                                        text = ContestGen.ssbPhoneticCwScript(station),
                                        fist = station.cwFist.copy(wpm = now.contestWpm.toDouble()),
                                        toneHz = now.toneHz.toDouble()
                                    )),
                                    volume = now.volume, noiseLevel = 0f,
                                    title = "赛事听抄",
                                    subtitle = "SSB 兜底拍发"
                                )
                                while (app.audio.isPlaying && playing) kotlinx.coroutines.delay(100)
                            }
                        }
                    }
                }
            }
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
        // RST 固定 599，不参与判分输入
        check = ContestGen.check(station, callIn.text, "599", serialIn.text)
        check?.let { c ->
            app.stats.reportContest(c.score)
            app.contestLog.add(
                ContestQso(
                    timeMs = System.currentTimeMillis(),
                    call = station.call,
                    country = station.country,
                    mode = if (mode == ContestMode.CW) "CW" else "SSB",
                    serial = station.serial,
                    report = station.report,
                    callOk = c.callOk,
                    reportOk = c.reportOk,
                    serialOk = c.serialOk
                )
            )
        }
    }

    // 播放时保持常亮；被来电等打断后立即停播，避免通话中继续出声
    KeepScreenOn(playing)
    val interruption = AudioGate.interruption
    LaunchedEffect(interruption) {
        if (interruption > 0 && playing) stop()
    }

    DisposableEffect(Unit) {
        // 底噪是独立通道：进入赛事页就开始实时播放，与是否在发报/播报无关
        app.noise.start(app.prefs.data.volume, app.prefs.data.contestNoise)
        onDispose {
            app.sidetone.stop()
            app.audio.stop()
            app.noise.stop()
            app.voice.stop()
            app.radio.stop()
            app.voiceBank.stop()
            app.speaker.stop()
        }
    }

    Column(Modifier.fillMaxSize().background(PanelBg)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(Brush.verticalGradient(listOf(PanelBg, Color(0xFF0E1116))))
        ) {
            Spacer(Modifier.height(8.dp))
            MetalHeader(
                onBack = { onBack() },
                title = "CW / SSB 赛事听抄",
                subtitle = "模拟电台面板",
                onReview = { exportMsg = null; showReview = true }
            )
            Spacer(Modifier.height(8.dp))

            // 频率面板（只显示频率 + 模式，不显示对方呼号 —— 真实电台你不知道对方是谁）
            FrequencyPanel(
                pulse = pulse, mode = mode,
                tone = if (mode == ContestMode.SSB && stdMode) ContestGen.toneName(station) else null
            )
            Spacer(Modifier.height(8.dp))

            // 模式 + 状态：合并成一条紧凑灯条，为下方抄收区腾出纵向空间
            ModeStatusBar(mode = mode, playing = playing)
            Spacer(Modifier.height(8.dp))

            // CW/SSB 切换 + 码速 / 解释法风格；SSB 时右侧一列放底噪、语速两块滑块
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeToggle(mode = mode, onMode = { m ->
                    if (m != mode) {
                        mode = m
                        app.prefs.update { p -> p.copy(contestMode = m) }
                    }
                })
                if (mode == ContestMode.CW) {
                    Spacer(Modifier.width(10.dp))
                    WpmAdjuster(
                        wpm = s.contestWpm,
                        onWpm = { app.prefs.update { p -> p.copy(contestWpm = it) } },
                        label = "对方码速"
                    )
                } else {
                    // SSB：口语（各国习惯解释法）/ 标准（规范 NATO）切换
                    Spacer(Modifier.width(8.dp))
                    StdToggle(std = stdMode, onStd = { v ->
                        stdMode = v
                        app.prefs.update { p -> p.copy(ssbStandard = v) }
                    })
                    Spacer(Modifier.width(8.dp))
                    // 底噪、语速上下排列：标签与数值同宽，两块滑块左右完全对齐；
                    // 一个拖动中时另一个禁用，避免误触
                    Column(modifier = Modifier.weight(1f)) {
                        val noiseSrc = remember { MutableInteractionSource() }
                        val noiseDragging by noiseSrc.collectIsDraggedAsState()
                        val wpmSrc = remember { MutableInteractionSource() }
                        val wpmDragging by wpmSrc.collectIsDraggedAsState()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("底噪", color = LedDim, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                            Slider(
                                value = noiseLv,
                                onValueChange = { v ->
                                    noiseLv = v
                                    app.noise.setLevel(v)
                                },
                                onValueChangeFinished = {
                                    app.prefs.update { p -> p.copy(contestNoise = noiseLv) }
                                },
                                valueRange = 0f..0.3f,
                                enabled = !wpmDragging,
                                interactionSource = noiseSrc,
                                modifier = Modifier.weight(1f).height(22.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = LedAmber,
                                    activeTrackColor = LedAmber,
                                    inactiveTrackColor = PanelLine,
                                    disabledThumbColor = LedOff,
                                    disabledActiveTrackColor = LedOff,
                                    disabledInactiveTrackColor = PanelLine
                                )
                            )
                            Text(
                                "${(noiseLv / 0.3f * 100).roundToInt()}%",
                                color = LedAmber, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(28.dp), textAlign = TextAlign.End
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("语速", color = LedDim, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                            Slider(
                                value = wpmLv.toFloat(),
                                onValueChange = { v -> wpmLv = v.roundToInt() },
                                onValueChangeFinished = {
                                    app.prefs.update { p -> p.copy(contestWpm = wpmLv) }
                                },
                                valueRange = 5f..40f,
                                steps = 34,
                                enabled = !noiseDragging,
                                interactionSource = wpmSrc,
                                modifier = Modifier.weight(1f).height(22.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = LedCyan,
                                    activeTrackColor = LedCyan,
                                    inactiveTrackColor = PanelLine,
                                    disabledThumbColor = LedOff,
                                    disabledActiveTrackColor = LedOff,
                                    disabledInactiveTrackColor = PanelLine
                                )
                            )
                            Text(
                                "$wpmLv",
                                color = LedCyan, fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(28.dp), textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            // 赛场场景开关（仅 CW）：同频多台 / 传播波动。切换即重播，立即生效
            if (mode == ContestMode.CW) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SceneChip("同频多台", pileup) {
                        pileup = !pileup
                        if (playing) loopSession++
                    }
                    Spacer(Modifier.width(8.dp))
                    SceneChip("传播波动", qsb) {
                        qsb = !qsb
                        if (playing) loopSession++
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (pileup) "抄收最强目标台" else "",
                        color = LedDim, fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1, softWrap = false
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // 频谱瀑布 + 信号条：融合在同一面板内
            SpectrumWaterfall(
                playing = playing, keying = keying, pulse = pulse,
                level = if (playing) smeter else 0f
            )

            // —— 抄收填写：呼号 / RST / 序号 三栏同行，位于报文之上 ——
            Spacer(Modifier.height(8.dp))
            CopyPanel(
                callIn = callIn, serialIn = serialIn,
                onCall = { v ->
                    // 呼号统一大写：用户输入小写自动转大写显示，光标跟随到末尾
                    val up = v.text.uppercase()
                    callIn = if (up == v.text) v
                        else v.copy(text = up, selection = TextRange(up.length), composition = null)
                },
                onSerial = { serialIn = it }
            )

            // —— 报文内容（默认关闭，真实比赛盲听）——
            Spacer(Modifier.height(8.dp))
            ScriptPanel(script = script, plain = plainScript, show = showScript, onToggle = { showScript = !showScript })

            if (check != null) {
                Spacer(Modifier.height(8.dp))
                ResultPanel(check!!, station)
            }
            Spacer(Modifier.height(16.dp))
        }

        BottomPanelBar(
            playing = playing, hasResult = check != null, nextReplayIn = nextReplayIn,
            onNewStation = { newStation(); play() }, onPlay = { play() },
            onStop = { stop() }, onCheck = { checkIt() },
            modifier = Modifier.padding(12.dp)
        )
    }

    if (showReview) {
        ContestReviewDialog(
            app = app,
            sinceMs = sessionStart,
            message = exportMsg,
            onExport = {
                val name = "cw_contest_" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".adi"
                adifLauncher.launch(name)
            },
            onClose = { showReview = false }
        )
    }
}

// ============ 子组件 ============

@Composable
private fun MetalHeader(onBack: () -> Unit, title: String, subtitle: String, onReview: () -> Unit) {
    // 与其他板块保持同样的宽度：左右各留 14dp、10dp 圆角、1dp 描边
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(PanelRaised, PanelBg, PanelRaised)))
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ 返回", color = LedDim.copy(alpha = 0.8f), fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }
                    .padding(horizontal = 4.dp, vertical = 4.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = LedAmber, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Text(subtitle, color = LedDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Text("复盘", color = LedCyan, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onReview() }
                    .padding(horizontal = 6.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun FrequencyPanel(pulse: Float, mode: ContestMode, tone: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("7.040.00",
                color = LedAmber.copy(alpha = 0.92f * pulse + 0.08f),
                fontSize = 30.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (mode == ContestMode.CW) "CW" else "USB", color = LedCyan,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("VFO  A", color = LedDim, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("●  -0 dBm", color = LedGreen,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                // 标准解释法模式下显示该台站的随机语气（平稳 / 急促 / 拖长）
                if (tone != null) {
                    Text("语气 $tone", color = LedDim, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 1.dp))
                }
            }
        }
    }
}

/** CW / SSB 切换开关：与码速调节并排的紧凑分段控件 */
@Composable
private fun ModeToggle(mode: ContestMode, onMode: (ContestMode) -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ModeSegment("CW", mode == ContestMode.CW) { onMode(ContestMode.CW) }
        ModeSegment("SSB", mode == ContestMode.SSB) { onMode(ContestMode.SSB) }
    }
}

@Composable
private fun ModeSegment(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (on) LedAmber.copy(alpha = 0.20f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            color = if (on) LedAmber else LedDim,
            fontSize = 11.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** SSB 解释法风格切换：口语（各国习惯）/ 标准（规范 NATO） */
@Composable
private fun StdToggle(std: Boolean, onStd: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ModeSegment("口语", !std) { onStd(false) }
        ModeSegment("标准", std) { onStd(true) }
    }
}

/** 赛场场景开关：点亮=开启，轻量胶囊样式，放在 CW 控制区下方 */
@Composable
private fun SceneChip(text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) LedCyan.copy(alpha = 0.18f) else PanelRecessed)
            .border(1.dp, if (on) LedCyan else PanelLine, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (on) LedCyan else LedOff)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text, color = if (on) LedCyan else LedDim, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * 模式 + 状态灯条：把原来的"模式"卡片和"状态"卡片压成一条，
 * 高度约 28dp（原两块卡片合计约 62dp），为抄收填写区腾出纵向空间。
 */
@Composable
private fun ModeStatusBar(mode: ContestMode, playing: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(8.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniLamp("CW", LedGreen, mode == ContestMode.CW)
        Spacer(Modifier.width(10.dp))
        MiniLamp("SSB", LedGreen, mode == ContestMode.SSB)
        Spacer(Modifier.weight(1f))
        MiniLamp("RX", LedGreen, playing)
        Spacer(Modifier.width(10.dp))
        MiniLamp("TX", LedAmber, false)
        Spacer(Modifier.width(10.dp))
        MiniLamp("B", LedRed, playing)
    }
}

/** 紧凑灯珠：直径 8dp + 右侧标签，整体一行高 */
@Composable
private fun MiniLamp(label: String, color: Color, on: Boolean) {
    // 未点亮也保持可读：用提亮后的标签色，而不是几乎看不见的熄灭色
    val c = if (on) color else LedDim
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c)
            .border(1.dp, PanelLine, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, color = c, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * 模拟频谱瀑布（Canvas 绘制）。
 * 真实电台的 waterfall 是 FFT + 滚动，这里用简化模型：
 * - 96 个频率 bin，左到右对应频谱低端到高端
 * - 每一帧都是一行新采样的噪声，旧行整体下移 → 纹理持续向下流动
 * - 发码时，目标频率附近叠加一条高斯亮带
 * - 噪声底用连续热力图配色（不是几档离散色），细微能量差也能看出颗粒感
 * - 每 50ms 刷新一次
 */
@Composable
private fun SpectrumWaterfall(
    playing: Boolean, keying: Boolean, pulse: Float, level: Float
) {
    // 缓存频谱能量分布（96 个 bin × 40 行历史）
    var waterfall by remember { mutableStateOf(List(WF_ROWS) { List(WF_BINS) { 0f } }) }
    // 预生成的连续热力色表，绘制时按能量查表
    val heatLut = remember { buildHeatLut() }

    // —— 永远在动：不管 playing 与否都刷 ——
    LaunchedEffect(Unit) {
        var simulateOn = false
        var nextSwitchAt = SystemClock.elapsedRealtime() + Random.nextLong(40, 120)
        var frameCount = 0L
        var driftOffset = 0.0   // 频率漂移：±3 bin 慢慢晃
        // 上一行的噪声底，用于给噪声留一点向下拖尾
        val prevNoise = FloatArray(WF_BINS) { 0.2f }

        while (true) {
            kotlinx.coroutines.delay(50)   // 20 行/秒，40 行历史 = 2 秒滚动全程
            frameCount++
            val now = SystemClock.elapsedRealtime()

            // —— 控制"发码"节奏 ——
            if (now >= nextSwitchAt) {
                simulateOn = !simulateOn
                nextSwitchAt = now + if (simulateOn) {
                    if (Random.nextBoolean()) Random.nextLong(100, 250) else Random.nextLong(300, 600)
                } else {
                    Random.nextLong(100, 400)
                }
            }

            // —— 频率漂移：正弦缓慢左右晃 ±3 bin ——
            driftOffset = kotlin.math.sin(frameCount / 30.0) * 3.0

            // —— 生成频谱 ——
            // 关键：噪声必须"每行都不一样"，滚动才有向下流动的动感。
            // 之前只用缓慢演化的正弦场，相邻行几乎一样，再被离散色档压成同一个颜色，
            // 整块看起来是静止的单色，只剩中间一条亮带。
            // 现在改为：散粒噪声（每行重采样）为主 + 慢变起伏做底噪包络，
            // 并保留 30% 的上一行值形成向下拖尾。
            val t = frameCount * 0.08
            val baseBin = (WF_BINS / 2).toDouble() + driftOffset
            val intensityMul = if (playing) 1.0f else 0.4f
            val newBins = FloatArray(WF_BINS)

            for (i in 0 until WF_BINS) {
                val x = i.toDouble()
                // 慢变底噪包络：三个空间频率的正弦叠加，让噪声成片起伏
                val smooth = (kotlin.math.sin(x * 0.35 + t) * 0.5 +
                              kotlin.math.sin(x * 0.17 - t * 0.7) * 0.3 +
                              kotlin.math.sin(x * 0.55 + t * 0.35) * 0.2 + 1.0) / 2.0
                // 散粒噪声：每行、每个 bin 重新采样，行间差异大 → 肉眼看到纹理在流
                val grain = Random.nextFloat()
                // 偶发强噪声尖峰（天电/静电）
                val burst = if (Random.nextFloat() < 0.02f) Random.nextFloat() * 0.25f else 0f
                val raw = 0.05f + smooth.toFloat() * 0.12f + grain * 0.34f

                prevNoise[i] = prevNoise[i] * 0.30f + raw * 0.70f
                var energy = (prevNoise[i] + burst).toDouble()

                // 信号亮带：高斯包络，峰值固定（只留极小抖动）。
                // 之前峰值随机 0.55~1.0，强帧会顶到色标上限而"饱和变宽"，
                // 看起来就是亮带宽度忽宽忽窄地跳。
                if (simulateOn) {
                    val dist = kotlin.math.abs(i - baseBin)
                    val g = kotlin.math.exp(-dist * dist / 22.0)
                    energy += (0.62 + Random.nextFloat() * 0.06) * g * intensityMul
                }
                newBins[i] = energy.coerceIn(0.0, 1.0).toFloat()
            }

            // —— 瀑布滚动：新帧在顶部，旧帧下移 ——
            // 注意：新帧追加到末尾（末尾=最新=画在顶部），必须丢头部最旧的一行。
            // 之前误写成 dropLast(1)，把刚追加的新帧又丢掉，历史全是初始的 0（纯黑），
            // 所以除顶部一行外整块画布是黑的。
            waterfall = waterfall.drop(1) + listOf(newBins.toList())
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("频谱 WATERFALL", color = LedDim, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
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
                .height(94.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF05080C))
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val w = size.width
                val h = size.height
                val binW = w / WF_BINS.toFloat()
                val rowH = h / WF_ROWS.toFloat()

                // 画瀑布：最新行在顶部，旧行向下滚动
                for (rowIdx in 0 until WF_ROWS) {
                    val row = waterfall[WF_ROWS - 1 - rowIdx]
                    for (binIdx in 0 until WF_BINS) {
                        val e = row[binIdx]
                        val lutIdx = (e * (heatLut.size - 1)).toInt().coerceIn(0, heatLut.size - 1)
                        drawRect(
                            color = heatLut[lutIdx],
                            topLeft = Offset(binIdx * binW, rowIdx * rowH),
                            size = androidx.compose.ui.geometry.Size(binW + 1f, rowH + 1f)  // +1 消除亚像素缝隙
                        )
                    }
                }

                // 中心频率参考线
                val centerX = (WF_BINS / 2) * binW + binW / 2f
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
                Text("LSB", color = LedDim.copy(alpha = 0.9f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text("USB", color = LedDim.copy(alpha = 0.9f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        // —— 信号强度条：与频谱融合在同一面板，不再单独占用一个面板 ——
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp).padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("S", color = LedDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(4.dp))
            // 用 Canvas 按固定几何绘制 12 段：电平变化只换颜色，
            // 不再经过布局测量，避免格数增减时整条（乃至面板）宽度抖动
            Canvas(
                modifier = Modifier.weight(1f).height(9.dp)
                    .clip(RoundedCornerShape(2.dp)).background(PanelBg)
                    .border(1.dp, PanelLine, RoundedCornerShape(2.dp))
            ) {
                val gap = 1f
                val segW = ((size.width - 2f - gap * 11f) / 12f).coerceAtLeast(1f)
                for (i in 0 until 12) {
                    val isOn = level >= (i + 1) / 12f
                    val color = when {
                        isOn && i < 9 -> LedGreen
                        isOn && i < 11 -> LedAmber
                        isOn -> LedRed
                        else -> LedOff
                    }
                    drawRect(
                        color = if (isOn) color.copy(alpha = 0.9f) else LedOff.copy(alpha = 0.55f),
                        topLeft = Offset(1f + i * (segW + gap), 1f),
                        size = androidx.compose.ui.geometry.Size(segW, size.height - 2f)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (level <= 0.01f) "—" else {
                    val labels = listOf("S1", "S3", "S5", "S7", "S9", "+20", "+40", "+60")
                    labels[((level * 8).toInt()).coerceIn(0, 7)]
                },
                color = LedGreen.copy(alpha = 0.95f), fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(28.dp), textAlign = TextAlign.End,
                maxLines = 1, softWrap = false
            )
        }

        // 频率刻度（下方）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (label in listOf("7.000", "7.020", "7.040", "7.060", "7.080")) {
                Text(label, color = LedDim.copy(alpha = 0.85f), fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ScriptPanel(script: String, plain: String?, show: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRecessed)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("报文内容 SCRIPT", color = LedDim, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
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
                Column {
                    Text(script, color = LedAmber, fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp, lineHeight = 18.sp)
                    // SSB 模式：报文内容之后附明文版（非字母解释法），方便对照学习
                    if (plain != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(plain, color = LedCyan, fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp, lineHeight = 18.sp)
                    }
                }
            }
        } else {
            Text("（已隐藏报文，专心盲听）",
                color = LedDim.copy(alpha = 0.9f),
                fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CopyPanel(
    callIn: TextFieldValue, serialIn: TextFieldValue,
    onCall: (TextFieldValue) -> Unit, onSerial: (TextFieldValue) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp)).background(PanelRaised)
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp)).padding(14.dp)
    ) {
        Text("抄收填写 COPY LOG", color = LedDim, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))

        // 呼号 / RST / 序号：同一行三栏，节省纵向空间
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1.7f)) {
                Text("呼号  CALL", color = LedAmber.copy(alpha = 0.95f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                MetalField(value = callIn, onValueChange = onCall, fontSize = 18)
            }
            Column(Modifier.weight(1f)) {
                Text("RST", color = LedAmber.copy(alpha = 0.95f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                // RST 固定 599，只读展示，不用输入
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(PanelRecessed)
                        .border(1.dp, PanelLine, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("599", color = LedDim, fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("序号  NR", color = LedAmber.copy(alpha = 0.95f),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                MetalField(value = serialIn, onValueChange = onSerial, fontSize = 16)
            }
        }
    }
}

@Composable
private fun MetalField(
    value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit,
    fontSize: Int, placeholder: String? = null
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
                if (value.text.isEmpty() && placeholder != null) Text(
                    placeholder, color = LedDim.copy(alpha = 0.7f),
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
            Text("判分 RESULT", color = LedDim, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp)
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
                text = if (nextReplayIn > 0) "停 ${nextReplayIn}s" else "停止",
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
            // 播放中也可直接提交，听完多少算多少，不必等停止
            enabled = !hasResult,
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
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(1.dp, if (filled) color else color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ============ 赛事复盘 ============

@Composable
private fun ContestReviewDialog(
    app: AppState,
    sinceMs: Long,
    message: String?,
    onExport: () -> Unit,
    onClose: () -> Unit
) {
    val review = app.contestLog.review(sinceMs)
    val session = app.contestLog.items.filter { it.timeMs >= sinceMs }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("赛事复盘", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (review.total == 0) {
                    Text(
                        text = "本次会话还没有判分记录。先「接收呼叫」，填写呼号与序号后点「提交」。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                } else {
                    Text(
                        text = "本次会话 ${review.total} 次抄收",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "呼号正确率 ${reviewPct(review.callRate)} · 序号正确率 ${reviewPct(review.serialRate)}",
                        fontSize = 12.sp,
                        color = LedDim,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "平均得分 ${"%.2f".format(review.avgScore)} / 3 · ${reviewGrade(review.avgScore)}",
                        fontSize = 12.sp,
                        color = LedDim,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    // —— 失误分布：一眼看出是呼号还是序号拖后腿 ——
                    val callErr = session.count { !it.callOk }
                    val serialErr = session.count { !it.serialOk }
                    Spacer(Modifier.height(10.dp))
                    ErrorBar("呼号失误", callErr, session.size)
                    Spacer(Modifier.height(5.dp))
                    ErrorBar("序号失误", serialErr, session.size)
                    Spacer(Modifier.height(10.dp))
                    session.takeLast(12).reversed().forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = q.call,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${reviewMark(q.callOk)}呼号 ${reviewMark(q.serialOk)}序号  ${reviewTime(q.timeMs)}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = LedDim
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "日志累计 ${app.contestLog.items.size} 条 · 导出为标准 ADIF 3.1.4，可导入 N1MM / Log4OM 等软件。",
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = LedDim
                )
                message?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = LedGreen,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExport, enabled = session.isNotEmpty()) {
                Text("导出 ADIF")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        }
    )
}

private fun reviewPct(v: Float): String = "%.0f%%".format(v * 100f)

/** 赛后评级：按平均得分三档 */
private fun reviewGrade(avg: Float): String = when {
    avg >= 2.7f -> "优秀，可上真台"
    avg >= 2.0f -> "良好，注意细节"
    else -> "需加强，建议降速"
}

/** 失误条形：宽度按失误占比，0 次显示绿色 0 */
@Composable
private fun ErrorBar(label: String, errors: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = LedDim, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.width(58.dp)
        )
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(PanelLine)
        ) {
            if (errors > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(errors.toFloat() / total.coerceAtLeast(1))
                        .fillMaxHeight()
                        .background(LedRed)
                )
            }
        }
        Text(
            "$errors", color = if (errors == 0) LedGreen else LedRed,
            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp), textAlign = TextAlign.End
        )
    }
}

private fun reviewMark(ok: Boolean): String = if (ok) "✓" else "✗"

private fun reviewTime(ms: Long): String = java.time.Instant.ofEpochMilli(ms)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

