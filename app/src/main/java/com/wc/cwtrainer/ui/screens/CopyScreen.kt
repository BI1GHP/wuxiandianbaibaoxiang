package com.wc.cwtrainer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.AudioGate
import com.wc.cwtrainer.core.Fist
import com.wc.cwtrainer.core.Generators
import com.wc.cwtrainer.core.Morse
import com.wc.cwtrainer.core.TextScore
import com.wc.cwtrainer.core.VoiceSpec
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Cyan
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.KeepScreenOn
import com.wc.cwtrainer.ui.LabeledSlider
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.NameplateCard
import com.wc.cwtrainer.ui.QslCardDialog
import com.wc.cwtrainer.ui.QslData
import com.wc.cwtrainer.ui.copyQsl
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import com.wc.cwtrainer.ui.WpmAdjuster
import com.wc.cwtrainer.core.UiSounds
import kotlinx.coroutines.delay

@Composable
fun CopyScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    var charset by remember { mutableStateOf(2) }
    var groupCount by remember { mutableStateOf(5) }
    var text by remember { mutableStateOf(Generators.groups(Generators.MIXED, 5, 5)) }
    var answer by remember { mutableStateOf(TextFieldValue("")) }
    var showContent by remember { mutableStateOf(false) }   // 报文内容默认隐藏
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var passCount by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var looping by remember { mutableStateOf(false) }
    var loopSession by remember { mutableStateOf(0) }
    var nextReplayIn by remember { mutableStateOf(0) }
    var qslCard by remember { mutableStateOf<QslData?>(null) }

    fun regenerate() {
        text = when (charset) {
            0 -> Generators.groups(Generators.LETTERS, groupCount, 5)
            1 -> Generators.groups(Generators.DIGITS, groupCount, 5)
            3 -> Generators.callsigns(groupCount.coerceAtMost(8))
            else -> Generators.groups(Generators.MIXED, groupCount, 5)
        }
        answer = TextFieldValue("")
        showContent = false
        accuracy = null
        passCount = 0
        looping = false
        nextReplayIn = 0
        app.audio.stop()
    }

    fun startLoop() {
        looping = true
        loopSession++
    }

    fun stopLoop() {
        looping = false
        nextReplayIn = 0
        app.audio.stop()
    }

    // 循环播放：一遍播完静默 5 秒后自动重播，直至点“停止”或离开页面
    LaunchedEffect(loopSession) {
        if (loopSession == 0) return@LaunchedEffect
        while (looping) {
            passCount++
            app.audio.play(
                voices = listOf(
                    VoiceSpec(
                        text = text,
                        fist = Fist(wpm = s.copyWpm.toDouble(), farnsworth = s.farnsworth.toDouble()),
                        toneHz = s.toneHz.toDouble()
                    )
                ),
                volume = s.volume,
                title = "听抄练习",
                subtitle = "${s.copyWpm} WPM · 循环听抄"
            )
            while (app.audio.isPlaying) delay(100)
            if (!looping) break
            for (sec in 5 downTo 1) {
                nextReplayIn = sec
                delay(1000)
                if (!looping) break
            }
            nextReplayIn = 0
        }
        nextReplayIn = 0
    }

    fun check() {
        val acc = TextScore.accuracy(text, answer.text)
        accuracy = acc
        showContent = true
        app.stats.reportCopy(acc, text, answer.text)
    }

    // 播放时保持常亮；被来电等打断后立即停循环，避免通话中继续出声
    KeepScreenOn(looping)
    val interruption = AudioGate.interruption
    LaunchedEffect(interruption) {
        if (interruption > 0 && looping) stopLoop()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .imePadding()
    ) {
        // 顶部：铭牌页头（返回 + 标题 + 设置入口）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            NameplateCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "‹",
                        color = TextDim,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    ) {
                        Text(
                            text = "听抄练习",
                            color = TextMain,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "COPY · RX",
                            color = TextDim,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.6.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    IconButton(
                        label = if (showSettings) "收起" else "设置",
                        active = showSettings
                    ) { showSettings = !showSettings }
                }
            }
        }

        // 码速调节（独立行，标题栏保持单行紧凑）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeChip(charsetText(charset), "${s.copyWpm} WPM")
            Spacer(Modifier.weight(1f))
            WpmAdjuster(
                wpm = s.copyWpm,
                onWpm = {
                    app.prefs.update { prefs ->
                        prefs.copy(copyWpm = it, farnsworth = if (prefs.farnsworth > it) 0 else prefs.farnsworth)
                    }
                }
            )
        }

        // 设置面板（可折叠）
        if (showSettings) {
            SettingsPanel(
                charset = charset,
                groupCount = groupCount,
                wpm = s.copyWpm,
                farnsworth = s.farnsworth,
                onCharset = { charset = it; regenerate() },
                onGroupCount = { groupCount = it; regenerate() },
                onWpm = { app.prefs.update { prefs -> prefs.copy(copyWpm = it, farnsworth = if (prefs.farnsworth > it) 0 else prefs.farnsworth) } },
                onFarnsworth = { app.prefs.update { prefs -> prefs.copy(farnsworth = it) } },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // 主区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            // 状态/播放动画区
            StatusBar(
                isPlaying = app.audio.isPlaying,
                looping = looping,
                nextReplayIn = nextReplayIn,
                passCount = passCount,
                accuracy = accuracy
            )

            Spacer(Modifier.height(14.dp))

            // 抄收输入框（主焦点）
            CopyInput(
                value = answer,
                onValueChange = { answer = it },
                enabled = accuracy == null
            )

            if (showContent) {
                Spacer(Modifier.height(14.dp))
                ContentPanel(
                    target = text,
                    answer = answer.text,
                    accuracy = accuracy,
                    passCount = passCount,
                    onPlayAgain = { startLoop() },
                    onRegenerate = { regenerate() },
                    onBackToInput = { showContent = false; accuracy = null }
                )
            } else {
                Spacer(Modifier.height(14.dp))
                HintBar(
                    hasInput = answer.text.isNotBlank(),
                    isPlaying = app.audio.isPlaying,
                    passCount = passCount
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // 底部操作条（固定）
        BottomActionBar(
            isPlaying = looping,
            hasContent = showContent,
            scored = accuracy != null,
            onRegenerate = { regenerate() },
            onPlay = { startLoop() },
            onStop = { stopLoop() },
            onToggleContent = { showContent = !showContent },
            onCheck = { check() },
            onQsl = {
                qslCard = copyQsl(text, answer.text, accuracy ?: 0f, passCount)
            },
            modifier = Modifier.padding(12.dp)
        )

        qslCard?.let { QslCardDialog(it) { qslCard = null } }
    }
}

private fun charsetText(cs: Int) = when (cs) {
    0 -> "字母"
    1 -> "数字"
    2 -> "混合"
    3 -> "呼号"
    else -> "混合"
}

// ============ 子组件 ============

@Composable
private fun ModeChip(charset: String, wpm: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = charset, color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(Line)
        )
        Text(text = wpm, color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun IconButton(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Amber.copy(alpha = 0.18f) else Surface2)
            .border(1.dp, if (active) Amber else Line, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (active) "收起" else "⚙ 设置",
            color = if (active) Amber else TextDim,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SettingsPanel(
    charset: Int,
    groupCount: Int,
    wpm: Int,
    farnsworth: Int,
    onCharset: (Int) -> Unit,
    onGroupCount: (Int) -> Unit,
    onWpm: (Int) -> Unit,
    onFarnsworth: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        SectionTitle("报文设置")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("字母", charset == 0) { onCharset(0) }
            Pill("数字", charset == 1) { onCharset(1) }
            Pill("混合", charset == 2) { onCharset(2) }
            Pill("呼号", charset == 3) { onCharset(3) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("5 组", groupCount == 5) { onGroupCount(5) }
            Pill("8 组", groupCount == 8) { onGroupCount(8) }
            Pill("12 组", groupCount == 12) { onGroupCount(12) }
        }
        Spacer(Modifier.height(10.dp))
        LabeledSlider(
            label = "发送速度",
            value = wpm.toFloat(),
            range = 5f..40f,
            valueText = "$wpm WPM",
            steps = 34,
            onValueChange = { onWpm(it.toInt()) }
        )
        LabeledSlider(
            label = "Farnsworth 有效速度",
            value = farnsworth.toFloat(),
            range = 0f..wpm.toFloat(),
            valueText = if (farnsworth == 0) "关闭" else "$farnsworth WPM",
            steps = 0,
            onValueChange = { onFarnsworth(it.toInt()) }
        )
    }
}

@Composable
private fun StatusBar(
    isPlaying: Boolean,
    looping: Boolean,
    nextReplayIn: Int,
    passCount: Int,
    accuracy: Float?
) {
    val dotPulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isPlaying -> Amber.copy(alpha = dotPulse)
                        looping -> Cyan
                        accuracy != null -> Green
                        passCount > 0 -> Cyan
                        else -> Line
                    }
                )
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = when {
                    isPlaying -> "正在播放电报…"
                    looping && nextReplayIn > 0 -> "第 $passCount 遍已播完 · ${nextReplayIn}s 后自动重播"
                    looping -> "循环播放中…"
                    accuracy != null -> "判分完成"
                    passCount == 0 -> "准备就绪，点击播放开始"
                    else -> "第 $passCount 遍已播完"
                },
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when {
                    isPlaying -> "循环播放：播完静默 5 秒后自动重播，点“停止”结束"
                    looping && nextReplayIn > 0 -> "点“停止”可随时终止循环"
                    accuracy != null -> "准确率 ${"%.0f".format(accuracy * 100)}%"
                    passCount == 0 -> "建议使用有线耳机，避免扬声器回授"
                    else -> "可以再听一遍或直接判分"
                },
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun CopyInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Surface1, Surface1.copy(alpha = 0.6f))
                )
            )
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "抄收内容",
            color = TextDim,
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = false,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            ),
            cursorBrush = SolidColor(Amber),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "在此输入听到的字母/数字…",
                            color = TextDim.copy(alpha = 0.5f),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val count = value.text.filter { it != ' ' }.length
            Text(
                text = "$count 字符",
                color = TextDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "支持字母/数字，空格分组",
                color = TextDim,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun HintBar(
    hasInput: Boolean,
    isPlaying: Boolean,
    passCount: Int
) {
    val text = when {
        !hasInput && passCount == 0 -> "💡 先点“播放”听电报，然后在上方输入框抄收"
        !hasInput -> "💡 还没输入内容，至少先尝试一遍"
        isPlaying -> "💡 可以边听边补写，按自己的节奏来"
        else -> "💡 听抄时不要逐点逐划数，先抓骨架再补细节"
    }
    Text(
        text = text,
        color = TextDim,
        fontSize = 11.sp,
        lineHeight = 17.sp
    )
}

@Composable
private fun ContentPanel(
    target: String,
    answer: String,
    accuracy: Float?,
    passCount: Int,
    onPlayAgain: () -> Unit,
    onRegenerate: () -> Unit,
    onBackToInput: () -> Unit
) {
    val normalizedTarget = TextScore.normalize(target)
    val normalizedAnswer = TextScore.normalize(answer)
    val correct = TextScore.correctCount(target, answer)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "报文内容",
                    color = TextDim,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "答案核对已融合，默认隐藏",
                    color = TextDim.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (accuracy != null) {
                Text(
                    text = "${"%.0f".format(accuracy * 100)}%",
                    color = when {
                        accuracy >= 0.9f -> Green
                        accuracy >= 0.6f -> Amber
                        else -> Red
                    },
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "未判分",
                    color = TextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 报文原文：纸带式逐字打印
        PaperTape(target)
        Spacer(Modifier.height(6.dp))
        MorseStrip(target = normalizedTarget)

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Line)
        )
        Spacer(Modifier.height(12.dp))

        // 答案核对（融入报文内容，随面板一起显示）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "答案核对",
                color = TextDim,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
            if (accuracy != null) {
                Text(
                    text = "${correct} / ${normalizedTarget.length}",
                    color = TextMain,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "第 $passCount 遍",
                color = TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        ComparisonStrip(target = normalizedTarget, answer = normalizedAnswer)

        Spacer(Modifier.height(16.dp))

        // 结果操作
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallAction("再听一遍", Cyan, Modifier.weight(1f)) { onPlayAgain() }
            SmallAction("重新抄收", Amber, Modifier.weight(1f)) { onBackToInput() }
            SmallAction("换一组", Green, Modifier.weight(1f)) { onRegenerate() }
        }
    }
}

// —— 纸带配色（纸张隐喻，独立于 App 主题）——
private val TapePaper = Color(0xFFFBF6E9)
private val TapeLine = Color(0xFFE2D7C1)
private val TapeHole = Color(0xFFD9CDB8)
private val TapeInk = Color(0xFF2B3A33)

/**
 * 纸带：报文像电传打字机一样逐字打印出来。
 * 上下两排走带孔用 Canvas 画，文字用等宽字体逐字揭示，末尾带闪烁光标。
 */
@Composable
private fun PaperTape(text: String) {
    var reveal by remember(text) { mutableIntStateOf(0) }
    val cursor = rememberInfiniteTransition(label = "tapeCursor").animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "tapeCursorAlpha"
    )
    LaunchedEffect(text) {
        reveal = 0
        val step = (900L / text.length.coerceAtLeast(1)).coerceIn(18L, 40L)
        for (i in 1..text.length) {
            delay(step)
            reveal = i
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TapePaper)
            .border(1.dp, TapeLine, RoundedCornerShape(8.dp))
    ) {
        // 走带孔
        Canvas(Modifier.matchParentSize()) {
            val r = 1.6.dp.toPx()
            val step = 16.dp.toPx()
            val top = 5.dp.toPx()
            val bottom = size.height - 5.dp.toPx()
            var x = 8.dp.toPx()
            while (x < size.width - 6.dp.toPx()) {
                drawCircle(TapeHole, r, Offset(x, top))
                drawCircle(TapeHole, r, Offset(x, bottom))
                x += step
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row {
                Text(
                    text = text.take(reveal),
                    color = TapeInk,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    lineHeight = 28.sp
                )
                if (reveal < text.length) {
                    Text(
                        text = "▌",
                        color = Amber.copy(alpha = cursor.value),
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "AUDIO TAPE · ${text.length} CH",
                color = TapeInk.copy(alpha = 0.45f),
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComparisonStrip(target: String, answer: String) {
    val maxLen = maxOf(target.length, answer.length)

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until maxLen) {
            val t = target.getOrNull(i)
            val a = answer.getOrNull(i)
            when {
                t == null -> CharCell(' ', TextDim, "多")
                a == null -> CharCell(t, Red, "缺")
                t == a -> CharCell(t, Green, null)
                else -> CharCell(t, Red, a.toString())
            }
        }
    }
}

@Composable
private fun CharCell(char: Char, color: Color, label: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 30.dp, minHeight = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.18f))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (char == ' ') "·" else char.toString(),
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        if (label != null) {
            Text(
                text = label,
                color = Red,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MorseStrip(target: String) {
    // FlowRow 自动换行：每个字符一个"字母+电码"小列，永远不会越出边框；
    // 空格变成固定宽度的间隔，保持字母与电码一一对应
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        target.forEach { c ->
            if (c == ' ') {
                Spacer(Modifier.width(10.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = c.toString(),
                        color = TextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = Morse.visualFor(c),
                        color = Amber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BottomActionBar(
    isPlaying: Boolean,
    hasContent: Boolean,
    scored: Boolean,
    onRegenerate: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onToggleContent: () -> Unit,
    onCheck: () -> Unit,
    onQsl: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playBtnColor by animateColorAsState(
        targetValue = if (isPlaying) Red else Amber,
        label = "playColor"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        // 主操作独占一行：大字号下不会被两侧图标按钮挤压
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(playBtnColor)
                .clickable { if (isPlaying) onStop() else onPlay() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isPlaying) "■" else "▶",
                    color = if (isPlaying) Color.White else Color(0xFF1A1200),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) "停止" else "播放报文",
                    color = if (isPlaying) Color.White else Color(0xFF1A1200),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 次要操作三等分，图标与文字横排，只占一行高度
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionBtn(
                icon = "↻",
                label = "换一组",
                color = TextDim,
                modifier = Modifier.weight(1f)
            ) { onRegenerate() }

            ActionBtn(
                icon = "📄",
                label = "报文",
                color = if (hasContent) Amber else Cyan,
                modifier = Modifier.weight(1f)
            ) { onToggleContent() }

            if (!scored) {
                ActionBtn(
                    icon = "✓",
                    label = "判分",
                    color = Green,
                    modifier = Modifier.weight(1f)
                ) { onCheck() }
            } else {
                ActionBtn(
                    icon = "★",
                    label = "成绩单",
                    color = Amber,
                    modifier = Modifier.weight(1f)
                ) { onQsl() }
            }
        }
    }
}

@Composable
private fun ActionBtn(
    icon: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) {
                if (enabled) UiSounds.click()
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = if (enabled) color else color.copy(alpha = 0.5f),
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}
