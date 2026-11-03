package com.wc.cwtrainer.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.R
import com.wc.cwtrainer.core.Fist
import com.wc.cwtrainer.core.Morse
import com.wc.cwtrainer.core.VoiceSpec
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class QuizQuestion(val answer: Char, val options: List<Char>)

@Composable
fun LearnScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var question by remember {
        mutableStateOf(QuizQuestion('E', listOf('E', 'T', 'A', 'O')))
    }
    var picked by remember { mutableStateOf<Char?>(null) }
    var feedback by remember { mutableStateOf<Boolean?>(null) }

    // —— 每日一听：内置音频播放器 ——
    val context = LocalContext.current
    var audioPlaying by remember { mutableStateOf(false) }
    var audioPosition by remember { mutableStateOf(0) }
    var audioDuration by remember { mutableStateOf(1) }
    val audioPlayer = remember { MediaPlayer.create(context, R.raw.daily_morse) }

    DisposableEffect(audioPlayer) {
        onDispose {
            audioPlaying = false
            audioPlayer?.release()
        }
    }

    LaunchedEffect(audioPlayer) {
        val mp = audioPlayer ?: return@LaunchedEffect
        audioDuration = mp.duration.coerceAtLeast(1)
        mp.setOnCompletionListener {
            audioPlaying = false
            audioPosition = 0
        }
    }

    // 播放期间刷新进度
    LaunchedEffect(audioPlaying, audioPlayer) {
        val mp = audioPlayer ?: return@LaunchedEffect
        while (audioPlaying) {
            try {
                audioPosition = mp.currentPosition
                audioDuration = mp.duration.coerceAtLeast(1)
            } catch (_: IllegalStateException) {
            }
            delay(200)
        }
    }

    fun toggleAudio() {
        val mp = audioPlayer ?: return
        if (audioPlaying) {
            mp.pause()
            audioPlaying = false
        } else {
            app.audio.stop()
            val total = mp.duration
            if (total > 0 && mp.currentPosition >= total - 200) mp.seekTo(0)
            mp.start()
            audioPlaying = true
        }
    }

    fun playChar(c: Char) {
        app.audio.play(
            voices = listOf(
                VoiceSpec(
                    text = c.toString(),
                    fist = Fist(wpm = s.wpm.toDouble()),
                    toneHz = s.toneHz.toDouble()
                )
            ),
            volume = s.volume
        )
    }

    fun newQuestion() {
        val pool = Morse.letters + Morse.digits
        val ans = pool.random()
        val options = (pool.filter { it != ans }.shuffled().take(3) + ans).shuffled()
        question = QuizQuestion(ans, options)
        picked = null
        feedback = null
    }

    fun choose(c: Char) {
        if (feedback != null) return
        picked = c
        val ok = c == question.answer
        feedback = ok
        app.stats.addLearn(ok)
        if (ok) {
            scope.launch {
                delay(700)
                newQuestion()
            }
        }
    }

    LaunchedEffect(tab) {
        if (tab > 0) {
            newQuestion()
            if (audioPlaying) {
                audioPlayer?.pause()
                audioPlaying = false
            }
        }
    }
    LaunchedEffect(question, tab) {
        if (tab == 1) playChar(question.answer)
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // 顶部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹ 返回",
                color = TextDim,
                fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onBack() }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            )
            Text(
                text = "背码训练",
                color = TextMain,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            val correctRate = if (app.stats.learnTotal == 0) "—" else
                String.format("%.0f%%", app.stats.learnCorrect.toFloat() / app.stats.learnTotal * 100f)
            ModeChip("正确率 $correctRate", "${app.stats.learnTotal} 题")
        }

        // Tab 切换
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("码表速查", tab == 0) { tab = 0 }
            Pill("听音辨码", tab == 1) { tab = 1 }
            Pill("看码辨字", tab == 2) { tab = 2 }
        }

        // 主区
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            DailyAudioCard(
                playing = audioPlaying,
                positionMs = audioPosition,
                durationMs = audioDuration,
                onToggle = { toggleAudio() }
            )
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> CodeTableContent(s, app) { playChar(it) }
                else -> QuizContent(
                    tab = tab,
                    question = question,
                    picked = picked,
                    feedback = feedback,
                    total = app.stats.learnTotal,
                    onPlay = { playChar(question.answer) },
                    onChoose = { choose(it) },
                    onRetry = { picked = null; feedback = null },
                    onNext = { newQuestion() }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============ 每日一听音频播放器 ============

@Composable
private fun DailyAudioCard(
    playing: Boolean,
    positionMs: Int,
    durationMs: Int,
    onToggle: () -> Unit
) {
    val progress = (positionMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // 进度条上方文案
        Text(
            text = "动感电码，每日一听，巩固记忆",
            color = Amber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Amber.copy(alpha = 0.16f))
                    .border(1.dp, Amber.copy(alpha = 0.55f), CircleShape)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (playing) "❚❚" else "▶",
                    color = Amber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Surface2)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Amber)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = fmtTime(positionMs),
                        color = TextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = fmtTime(durationMs),
                        color = TextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun fmtTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

// ============ Tab 0: 码表速查 ============

@Composable
private fun CodeTableContent(s: com.wc.cwtrainer.core.SettingsData, app: AppState, onPlay: (Char) -> Unit) {
    val letters = Morse.letters
    val digits = Morse.digits
    val punctuation = listOf('.', ',', '?', '=', '/')

    Text(
        text = "点击任意字符试听 · ${s.wpm} WPM / ${s.toneHz} Hz",
        color = TextDim, fontSize = 11.sp
    )
    Spacer(Modifier.height(10.dp))

    CodeGroup("字母 A–Z", letters, onPlay)
    Spacer(Modifier.height(10.dp))
    CodeGroup("数字 0–9", digits, onPlay)
    Spacer(Modifier.height(10.dp))
    CodeGroup("常用标点", punctuation, onPlay)
}

@Composable
private fun CodeGroup(title: String, chars: List<Char>, onPlay: (Char) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(text = title, color = TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        chars.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { c ->
                    CodeTile(c, Modifier.weight(1f)) { onPlay(c) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CodeTile(c: Char, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = c.toString(),
            color = TextMain,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = Morse.visualFor(c),
            color = Amber,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

// ============ Tab 1/2: 听音辨码 & 看码辨字 ============

@Composable
private fun QuizContent(
    tab: Int,
    question: QuizQuestion,
    picked: Char?,
    feedback: Boolean?,
    total: Int,
    onPlay: () -> Unit,
    onChoose: (Char) -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit
) {
    val dotPulse by rememberInfiniteTransition(label = "lrn-pulse").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // 大题目区
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            feedback == true -> Green.copy(alpha = dotPulse)
                            feedback == false -> Red
                            else -> Amber.copy(alpha = dotPulse)
                        }
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (tab == 1) "听音辨码" else "看码辨字",
                color = TextDim, fontSize = 12.sp, letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "第 ${total + 1} 题",
                color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(22.dp))

        if (tab == 1) {
            // 听音：大播放按钮
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Amber.copy(alpha = 0.15f))
                    .border(2.dp, Amber.copy(alpha = 0.5f), CircleShape)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "▶", color = Amber, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(text = "播放", color = Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "点击圆形按钮重听电报音",
                color = TextDim, fontSize = 11.sp
            )
        } else {
            // 看码：大·/—显示
            Text(
                text = Morse.visualFor(question.answer),
                color = Amber,
                fontSize = 38.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这串点划对应哪个字符？",
                color = TextDim, fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(22.dp))

        // 四选一
        question.options.chunked(2).forEach { rowOpts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowOpts.forEach { c ->
                    QuizOption(c, c == question.answer, picked, feedback, Modifier.weight(1f)) { onChoose(c) }
                }
                if (rowOpts.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        // 反馈
        if (feedback != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (feedback!!) "✓ 正确！" else "✗ 错误",
                    color = if (feedback!!) Green else Red,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                if (!feedback!!) {
                    Text(
                        text = "正确答案是 ${question.answer}  ${Morse.visualFor(question.answer)}",
                        color = Red, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!feedback!!) {
                    SmallAction("再试一次", TextDim, Modifier.weight(1f)) { onRetry() }
                }
                SmallAction("下一题", Green, Modifier.weight(1f)) { onNext() }
            }
        }
    }
}

@Composable
private fun QuizOption(
    c: Char, isAnswer: Boolean, picked: Char?, feedback: Boolean?,
    modifier: Modifier, onClick: () -> Unit
) {
    val border by animateColorAsState(
        targetValue = when {
            feedback != null && isAnswer -> Green
            feedback != null && picked == c && !isAnswer -> Red
            else -> Line
        }, label = "opt-border"
    )
    val bg by animateColorAsState(
        targetValue = when {
            feedback != null && isAnswer -> Green.copy(alpha = 0.20f)
            feedback != null && picked == c && !isAnswer -> Red.copy(alpha = 0.20f)
            else -> Surface2
        }, label = "opt-bg"
    )
    val fg = when {
        feedback != null && isAnswer -> Green
        feedback != null && picked == c && !isAnswer -> Red
        else -> TextMain
    }
    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = feedback == null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = c.toString(),
            color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ModeChip(a: String, b: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = a, color = Green, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.padding(horizontal = 6.dp).size(4.dp).clip(CircleShape).background(Line))
        Text(text = b, color = TextDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SmallAction(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
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
