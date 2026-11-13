package com.wc.cwtrainer.ui.screens

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.ContestGen
import com.wc.cwtrainer.core.PhoneticStyle
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.NameplateCard
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.launch

private data class AccentCountry(
    val locale: String,
    val name: String,
    val style: PhoneticStyle
)

private val COUNTRIES = listOf(
    AccentCountry("en-US", "美国", PhoneticStyle.NATO),
    AccentCountry("en-GB", "英国", PhoneticStyle.NATO),
    AccentCountry("en-AU", "澳大利亚", PhoneticStyle.NATO),
    AccentCountry("de-DE", "德国", PhoneticStyle.GERMAN),
    AccentCountry("fr-FR", "法国", PhoneticStyle.NATO),
    AccentCountry("it-IT", "意大利", PhoneticStyle.ITALIAN),
    AccentCountry("es-ES", "西班牙", PhoneticStyle.SPANISH),
    AccentCountry("ru-RU", "俄罗斯", PhoneticStyle.NATO),
    AccentCountry("ja-JP", "日本", PhoneticStyle.NATO),
    AccentCountry("ko-KR", "韩国", PhoneticStyle.NATO),
    AccentCountry("zh-CN", "中国", PhoneticStyle.NATO),
    AccentCountry("pt-BR", "巴西", PhoneticStyle.NATO),
    AccentCountry("sv-SE", "瑞典", PhoneticStyle.NATO),
    AccentCountry("pl-PL", "波兰", PhoneticStyle.NATO),
    AccentCountry("nl-NL", "荷兰", PhoneticStyle.NATO)
)

private data class AccentQuestion(
    val call: String,
    val options: List<String>,
    val words: List<String>,
    val voiceKey: String,
    val rate: Float,
    val pitch: Float
)

/** 一轮 5 题，按国家出题并记录最佳命中率 */
private fun makeQuestion(locale: String, style: PhoneticStyle): AccentQuestion? {
    val base = ContestGen.stationFor(locale) ?: return null
    val calls = linkedSetOf(base.call)
    var guard = 0
    while (calls.size < 4 && guard++ < 60) {
        ContestGen.stationFor(locale)?.let { calls.add(it.call) }
    }
    val words = ContestGen.spellCall(base.call, style).split(" ").filter { it.isNotBlank() }
    return AccentQuestion(
        call = base.call,
        options = calls.toList().shuffled(),
        words = words,
        voiceKey = base.voiceKey,
        rate = 0.90f + kotlin.random.Random.nextFloat() * 0.15f,
        pitch = base.pitch
    )
}

@Composable
fun AccentScreen(app: AppState, onBack: () -> Unit) {
    val s = app.prefs.data
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<AccentCountry?>(null) }
    var question by remember { mutableStateOf<AccentQuestion?>(null) }
    var qIndex by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun playQuestion(q: AccentQuestion) {
        playing = true
        error = null
        val r = app.voiceBank.play(q.words, q.voiceKey, q.rate, s.volume)
        if (r.isFailure) {
            val fallback = app.voice.speak(q.words.joinToString(" "), q.pitch, q.rate)
            if (fallback.isFailure) error = "语音包缺失，无法播放"
        }
        playing = false
    }

    fun replay() {
        val q = question ?: return
        scope.launch { playQuestion(q) }
    }

    fun start(c: AccentCountry) {
        app.voiceBank.stop()
        app.voice.stop()
        selected = c
        qIndex = 0
        correctCount = 0
        picked = null
        finished = false
        error = null
        question = makeQuestion(c.locale, c.style)
    }

    fun next() {
        val c = selected ?: return
        if (qIndex >= 4) {
            app.accent.report(c.locale, correctCount / 5f)
            app.stats.reportAccent()
            finished = true
        } else {
            qIndex++
            picked = null
            question = makeQuestion(c.locale, c.style)
        }
    }

    LaunchedEffect(question) {
        question?.let { playQuestion(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            app.voiceBank.stop()
            app.voice.stop()
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
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
                            text = if (selected == null) "口音适应" else "口音适应 · ${selected!!.name}",
                            color = TextMain,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "ACCENT · COPY",
                            color = TextDim,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.6.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (selected != null) {
                        Text(
                            text = "换国家",
                            color = Amber,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    app.voiceBank.stop()
                                    app.voice.stop()
                                    selected = null
                                    question = null
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            val c = selected
            if (c == null) {
                CountryList(app) { start(it) }
            } else if (finished) {
                ResultPanel(
                    country = c,
                    correct = correctCount,
                    best = app.accent.bestOf(c.locale) ?: 0f,
                    onAgain = { start(c) },
                    onSwitch = { selected = null; question = null }
                )
            } else {
                QuestionPanel(
                    country = c,
                    index = qIndex,
                    correct = correctCount,
                    question = question,
                    picked = picked,
                    playing = playing,
                    error = error,
                    onReplay = { replay() },
                    onPick = { opt ->
                        if (picked == null && question != null) {
                            picked = opt
                            if (opt == question!!.call) correctCount++
                        }
                    },
                    onNext = { next() }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CountryList(app: AppState, onPick: (AccentCountry) -> Unit) {
    SectionCard {
        SectionTitle("选择口音")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "用各国母语音色朗读字母解释法呼号，练「听懂口音」而不是背电码。每轮 5 题，按国家记录最佳命中率。",
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        COUNTRIES.forEach { c ->
            val best = app.accent.bestOf(c.locale)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface1)
                    .border(1.dp, Line, RoundedCornerShape(10.dp))
                    .clickable { onPick(c) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = c.name,
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (best != null) "最佳 ${(best * 100).toInt()}%" else "未挑战",
                    color = if (best != null) Green else TextDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "›",
                    color = Amber,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun QuestionPanel(
    country: AccentCountry,
    index: Int,
    correct: Int,
    question: AccentQuestion?,
    picked: String?,
    playing: Boolean,
    error: String?,
    onReplay: () -> Unit,
    onPick: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${country.name}口音",
                color = Amber,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "第 ${index + 1}/5 题 · 命中 $correct",
                color = TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Amber.copy(alpha = if (playing) 0.28f else 0.15f))
                .border(2.dp, Amber.copy(alpha = 0.55f), CircleShape)
                .clickable { onReplay() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (playing) "◉" else "▶",
                    color = Amber,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (playing) "播放中" else "重听",
                    color = Amber,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "听呼号的字母解释法，选出完整呼号",
            color = TextDim,
            fontSize = 11.sp
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Red, fontSize = 11.sp)
        }
        Spacer(Modifier.height(18.dp))
        val q = question
        if (q != null) {
            q.options.chunked(2).forEach { rowOpts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowOpts.forEach { opt ->
                        OptionCell(
                            text = opt,
                            isAnswer = opt == q.call,
                            picked = picked,
                            modifier = Modifier.weight(1f)
                        ) { onPick(opt) }
                    }
                    if (rowOpts.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            if (picked != null) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (picked == q.call) "✓ 正确" else "✗ 正确呼号 ${q.call}",
                        color = if (picked == q.call) Green else Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Green.copy(alpha = 0.15f))
                            .border(1.dp, Green.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { onNext() }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (index >= 4) "查看成绩" else "下一题",
                            color = Green,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            Text("语音包缺失，无法出题", color = Red, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OptionCell(
    text: String,
    isAnswer: Boolean,
    picked: String?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val border = when {
        picked != null && isAnswer -> Green
        picked == text -> Red
        else -> Line
    }
    val bg = when {
        picked != null && isAnswer -> Green.copy(alpha = 0.18f)
        picked == text -> Red.copy(alpha = 0.18f)
        else -> Surface2
    }
    val fg = when {
        picked != null && isAnswer -> Green
        picked == text -> Red
        else -> TextMain
    }
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = picked == null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultPanel(
    country: AccentCountry,
    correct: Int,
    best: Float,
    onAgain: () -> Unit,
    onSwitch: () -> Unit
) {
    val rate = correct / 5f
    SectionCard {
        SectionTitle("本轮成绩")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${country.name}口音 · $correct / 5",
            color = TextMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "命中率 ${(rate * 100).toInt()}% · 最佳 ${(best * 100).toInt()}%",
            color = if (rate >= best) Green else TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Amber)
                    .clickable { onAgain() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "再来一轮",
                    color = Color(0xFF1A1200),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .clickable { onSwitch() },
                contentAlignment = Alignment.Center
            ) {
                Text("换国家", color = TextMain, fontSize = 14.sp)
            }
        }
    }
}
