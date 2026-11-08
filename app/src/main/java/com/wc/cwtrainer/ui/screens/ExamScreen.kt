package com.wc.cwtrainer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.ExamChapters
import com.wc.cwtrainer.core.ExamLevel
import com.wc.cwtrainer.core.ExamRecord
import com.wc.cwtrainer.core.Question
import com.wc.cwtrainer.core.QuestionBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
//  纸质护眼 · 课堂学习风配色
// ============================================================

private val PaperBg = Color(0xFFF6F1E4)        // 米黄护眼纸
private val PaperCard = Color(0xFFFFFDF6)      // 白纸卡片
private val PaperInk = Color(0xFF3A362E)       // 墨色正文
private val PaperDim = Color(0xFF8E8776)       // 铅笔灰
private val PaperLine = Color(0xFFE3DAC4)      // 纸边框线
private val PaperRule = Color(0xFFEFE8D6)      // 信纸横线
private val PaperBlue = Color(0xFF4A6FA5)      // 钢笔蓝（选中）
private val PaperRed = Color(0xFFC7503F)       // 红笔批改
private val PaperGreen = Color(0xFF4E8C4A)     // 绿笔批改
private val PaperAmber = Color(0xFFC8862D)     // 无线电琥珀装饰

private val PaperShape = RoundedCornerShape(12.dp)
private val ExamFont = FontFamily.Serif         // 课本衬线体

// ============================================================
//  无线电小装饰
// ============================================================

/** 天线 + 两侧电波弧线 */
@Composable
private fun AntennaWaves(modifier: Modifier = Modifier, color: Color = PaperAmber) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val base = size.height * 0.92f
        val top = size.height * 0.22f
        val stroke = Stroke(width = size.height * 0.055f, cap = StrokeCap.Round)
        // 杆
        drawLine(color, Offset(cx, base), Offset(cx, top), stroke.width, StrokeCap.Round)
        // 底座
        drawLine(color, Offset(cx - size.width * 0.18f, base), Offset(cx + size.width * 0.18f, base), stroke.width, StrokeCap.Round)
        // 顶点
        drawCircle(color, radius = stroke.width * 1.15f, center = Offset(cx, top))
        // 电波弧（左右各两道）
        listOf(0.30f, 0.46f).forEachIndexed { i, f ->
            val r = size.width * f
            val a = if (i == 0) 52f else 38f
            drawArc(
                color = color, startAngle = -90f - a, sweepAngle = a * 2, useCenter = false,
                topLeft = Offset(cx - r, top - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = stroke
            )
        }
    }
}

/** 摩尔斯点划装饰条：给定 "01" 串，0=点 1=划 */
@Composable
private fun MorseStrip(
    code: String,
    modifier: Modifier = Modifier,
    color: Color = PaperAmber,
    dot: Float = 3f
) {
    Canvas(modifier = modifier) {
        val gap = dot * 1.6f
        val dash = dot * 3.2f
        val totalW = code.sumOf { if (it == '1') dash.toDouble() else dot.toDouble() } + gap * (code.length - 1)
        var x = (size.width - totalW.toFloat()).coerceAtLeast(0f) / 2f
        val y = size.height / 2f
        code.forEach { c ->
            if (c == '1') {
                drawLine(color, Offset(x, y), Offset(x + dash, y), dot * 1.8f, StrokeCap.Round)
                x += dash
            } else {
                drawCircle(color, radius = dot * 0.9f, center = Offset(x + dot * 0.9f, y))
                x += dot
            }
            x += gap
        }
    }
}

/** 信纸横线背景（铺满容器） */
@Composable
private fun RuledLines(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val step = 26.dp.toPx()
        var y = step
        while (y < size.height) {
            drawLine(PaperRule, Offset(0f, y), Offset(size.width, y), 1.2f)
            y += step
        }
    }
}

/** 纸张卡片：白纸 + 细边 + 可选横线 */
@Composable
private fun PaperCardBox(
    modifier: Modifier = Modifier,
    ruled: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(PaperShape)
            .background(PaperCard)
            .border(1.2.dp, PaperLine, PaperShape)
    ) {
        if (ruled) RuledLines(Modifier.matchParentSize())
        Column(Modifier.padding(14.dp)) { content() }
    }
}

// ============================================================
//  入口
// ============================================================

@Composable
fun ExamScreen(app: AppState, onBack: () -> Unit) {
    // 题库首次加载（assets JSON 解析放 IO 线程）
    var bankReady by remember {
        mutableStateOf(QuestionBank.questionsFor(ExamLevel.B).isNotEmpty())
    }
    LaunchedEffect(Unit) {
        if (!bankReady) {
            withContext(Dispatchers.IO) { QuestionBank.ensure(app.appContext) }
            bankReady = true
        }
    }
    if (!bankReady) {
        Column(Modifier.fillMaxSize().background(PaperBg)) {
            PaperHeader("业余无线电考试", onBack)
            EmptyHint("题库加载中…")
        }
        return
    }

    // 恢复上次所在页签（模式 + 题库级别），退出重进无缝续学
    var level by remember {
        mutableStateOf(
            app.exam.loadStr("ui:level")?.let { l ->
                runCatching { ExamLevel.valueOf(l) }.getOrNull()
            } ?: ExamLevel.B
        )
    }
    var mode by remember { mutableIntStateOf(app.exam.loadPos("ui:mode").coerceIn(0, 3)) }
    LaunchedEffect(level, mode) {
        app.exam.saveStr("ui:level", level.name)
        app.exam.savePos("ui:mode", mode)
    }

    Column(Modifier.fillMaxSize().background(PaperBg)) {
        // 标题栏（课本封面风）
        Box(
            Modifier.fillMaxWidth()
                .background(PaperCard)
                .border(0.dp, Color.Transparent)
        ) {
            PaperHeader("业余无线电考试", onBack)
            // 右上角天线装饰
            AntennaWaves(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(width = 46.dp, height = 34.dp))
            // 标题下摩尔斯装饰
            MorseStrip(
                "1010100010111000101",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                    .fillMaxWidth(0.42f).height(5.dp),
                color = PaperAmber.copy(alpha = 0.55f), dot = 1.6f
            )
        }

        // 题库级别（文件夹标签风，两等分）
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaperTab("B 类题库", level == ExamLevel.B, Modifier.weight(1f)) { level = ExamLevel.B }
            PaperTab("C 类题库", level == ExamLevel.C, Modifier.weight(1f)) { level = ExamLevel.C }
        }

        // 模式 Tab（四等分，占满整行）
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaperTab("按序背题", mode == 0, Modifier.weight(1f)) { mode = 0 }
            PaperTab("分类背题", mode == 1, Modifier.weight(1f)) { mode = 1 }
            PaperTab("错题本", mode == 2, Modifier.weight(1f)) { mode = 2 }
            PaperTab("模拟考试", mode == 3, Modifier.weight(1f)) { mode = 3 }
        }

        when (mode) {
            0 -> PracticePane(
                app = app, level = level,
                queue = remember(level, mode) { QuestionBank.questionsFor(level) },
                emptyHint = "题库加载中…",
                progressKey = "seq:${level.name}"
            )
            1 -> ClassifyPane(app, level)
            2 -> {
                val wrongQs = remember(level, app.exam.wrongB, app.exam.wrongC) {
                    app.exam.wrongQuestions(level)
                }
                if (wrongQs.isEmpty()) {
                    // 空错题本：铅笔小画 + 文案
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        AntennaWaves(Modifier.size(width = 64.dp, height = 46.dp), color = PaperLine)
                        Spacer(Modifier.height(10.dp))
                        MorseStrip("10001", Modifier.fillMaxWidth(0.2f).height(5.dp), color = PaperLine)
                        Spacer(Modifier.height(14.dp))
                        Text("错题本是空的", color = PaperDim, fontSize = 15.sp, fontFamily = ExamFont)
                        Spacer(Modifier.height(4.dp))
                        Text("答题时选错的题会自动收进来", color = PaperDim.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                } else {
                    PracticePane(
                        app = app, level = level, queue = wrongQs,
                        emptyHint = "", reveal = false, extraTop = {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("共 ${wrongQs.size} 题 · 答对自动移出错题本",
                                    color = PaperDim, fontSize = 11.sp, fontFamily = ExamFont)
                                Spacer(Modifier.weight(1f))
                                Text("清空",
                                    color = PaperRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .clickable { app.exam.clearWrong(level) }
                                        .padding(6.dp))
                            }
                        }
                    )
                }
            }
            3 -> ExamPane(app, level)
        }
    }
}

// ============================================================
//  纸质公共组件
// ============================================================

/** 课本封面式标题栏 */
@Composable
private fun PaperHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("‹ 返回", color = PaperDim, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable { onBack() }.padding(horizontal = 6.dp, vertical = 6.dp))
        Text(title, color = PaperInk, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, fontFamily = ExamFont,
            modifier = Modifier.padding(start = 6.dp))
    }
}

/** 文件夹标签式页签 */
@Composable
private fun PaperTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(if (selected) PaperBlue else PaperCard)
            .border(1.2.dp, if (selected) PaperBlue else PaperLine, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else PaperDim,
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = ExamFont, maxLines = 1)
    }
}

/** 纸质小切换标签（仅答案 / 全部高亮） */
@Composable
private fun MiniToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) PaperBlue else PaperCard)
            .border(1.dp, if (selected) PaperBlue else PaperLine, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, color = if (selected) Color.White else PaperDim,
            fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = ExamFont, maxLines = 1)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = PaperDim, fontSize = 14.sp, textAlign = TextAlign.Center,
            lineHeight = 22.sp, fontFamily = ExamFont)
    }
}

// ============================================================
//  分类背题
// ============================================================

@Composable
private fun ClassifyPane(app: AppState, level: ExamLevel) {
    // 恢复上次分类状态："subMode|secOrType"（0=题型 M/S，1=知识点小节号），退出重进可续
    val saved = remember(level) { app.exam.loadStr("cls:${level.name}") }
    var subMode by remember(level) { mutableIntStateOf(if (saved?.startsWith("1|") == true) 1 else 0) }
    var pickedCh by remember(level) {
        mutableStateOf(
            if (saved?.startsWith("1|") == true)
                saved.removePrefix("1|").substringBefore('.').toIntOrNull()
            else null
        )
    }
    var pickedMulti by remember(level) {
        mutableStateOf(
            when (saved?.removePrefix("0|")) {
                "M" -> true
                "S" -> false
                else -> null
            }
        )
    }
    LaunchedEffect(level, subMode, pickedCh, pickedMulti) {
        val tail = if (subMode == 0) {
            if (pickedMulti == true) "M" else if (pickedMulti == false) "S" else ""
        } else pickedCh?.toString() ?: ""
        app.exam.saveStr("cls:${level.name}", "$subMode|$tail")
    }

    Column(Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperTab("按题型", subMode == 0) { subMode = 0; pickedCh = null; pickedMulti = null }
            PaperTab("按知识点", subMode == 1) { subMode = 1; pickedCh = null; pickedMulti = null }
        }

        if (subMode == 0) {
            val total = QuestionBank.questionsFor(level).size
            val multiN = QuestionBank.byType(level, true).size
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperTab("单选题 ${total - multiN}", pickedMulti == false) { pickedMulti = false }
                PaperTab("多选题 $multiN", pickedMulti == true) { pickedMulti = true }
            }
            if (pickedMulti != null) {
                PracticePane(
                    app = app, level = level,
                    queue = QuestionBank.byType(level, pickedMulti!!),
                    emptyHint = "该题型暂无题目",
                    progressKey = "type:${level.name}:${if (pickedMulti == true) "M" else "S"}"
                )
            } else {
                // 待选择：作业本封面
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    MorseStrip("110100101", Modifier.fillMaxWidth(0.3f).height(6.dp), color = PaperLine)
                    Spacer(Modifier.height(12.dp))
                    Text("选择题型开始背题", color = PaperDim, fontSize = 14.sp, fontFamily = ExamFont)
                }
            }
        } else {
            if (pickedCh == null) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("按大类背题", color = PaperInk, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                        Spacer(Modifier.width(8.dp))
                        MorseStrip("10", Modifier.width(26.dp).height(4.dp), dot = 1.4f)
                    }
                    Spacer(Modifier.height(8.dp))
                    for (ch in QuestionBank.chapters(level)) {
                        val n = QuestionBank.countByChapter(level, ch)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(PaperShape).background(PaperCard)
                                .border(1.2.dp, PaperLine, PaperShape)
                                .clickable { pickedCh = ch }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 章号牌
                            Box(
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                    .background(PaperBg)
                                    .border(1.2.dp, PaperLine, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$ch", color = PaperBlue, fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ExamChapters.title(ch), color = PaperInk, fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                                Spacer(Modifier.height(3.dp))
                                Text("共 $n 题", color = PaperDim, fontSize = 11.sp, fontFamily = ExamFont)
                            }
                            Text("›", color = PaperDim, fontSize = 20.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            } else {
                val ch = pickedCh!!
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ 换大类", color = PaperDim, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { pickedCh = null }.padding(6.dp))
                    Spacer(Modifier.weight(1f))
                    Text("${ExamChapters.title(ch)} · ${QuestionBank.countByChapter(level, ch)} 题",
                        color = PaperBlue, fontSize = 13.sp, fontFamily = ExamFont)
                }
                PracticePane(
                    app = app, level = level,
                    queue = QuestionBank.byChapter(level, ch),
                    emptyHint = "该大类暂无题目",
                    progressKey = "ch:${level.name}:$ch"
                )
            }
        }
    }
}

// ============================================================
//  背题练习（按序 / 分类 / 错题本共用）
// ============================================================

@Composable
private fun PracticePane(
    app: AppState,
    level: ExamLevel,
    queue: List<Question>,
    emptyHint: String,
    progressKey: String? = null,
    reveal: Boolean = true,
    extraTop: (@Composable () -> Unit)? = null
) {
    if (queue.isEmpty()) {
        EmptyHint(emptyHint); return
    }
    // 恢复上次背到的位置（按序/分类背题退出重进可续；错题本不记忆）
    var idx by remember(queue, progressKey) {
        mutableIntStateOf(
            if (progressKey != null) app.exam.loadPos(progressKey).coerceIn(0, queue.size - 1) else 0
        )
    }
    LaunchedEffect(progressKey, idx) {
        if (progressKey != null) app.exam.savePos(progressKey, idx)
    }
    var picked by remember(queue, idx) { mutableStateOf<Set<Int>>(emptySet()) }
    var submitted by remember(queue, idx) { mutableStateOf(false) }
    // 显示方式：true=只列正确项，false=全部显示并高亮正确答案（仅背题模式）
    var onlyAnswer by remember { mutableStateOf(app.exam.loadStr("disp:only") == "1") }

    if (idx >= queue.size) idx = queue.size - 1
    val q = queue[idx]
    val letters = listOf("A", "B", "C", "D")
    // 每题单独一页：翻页后从页首读起
    val scroll = remember(queue, idx) { ScrollState(0) }

    Column(Modifier.fillMaxSize()) {
        extraTop?.invoke()

        // 进度条 + 显示方式切换
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${idx + 1} / ${queue.size} · ${q.id} · 知识点 ${q.sec}" + if (q.isMulti) " · 多选题" else "",
                    color = PaperDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (reveal) {
                    MiniToggle("仅答案", onlyAnswer) {
                        onlyAnswer = true
                        app.exam.saveStr("disp:only", "1")
                    }
                    Spacer(Modifier.width(4.dp))
                    MiniToggle("全部高亮", !onlyAnswer) {
                        onlyAnswer = false
                        app.exam.saveStr("disp:only", "0")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(PaperRule)) {
                Box(Modifier.fillMaxWidth((idx + 1f) / queue.size).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(PaperAmber.copy(alpha = 0.6f)))
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth()
            .pointerInput(queue) {
                // 像翻书一样左右滑动切页
                val flipPx = 72.dp.toPx()
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = {
                        when {
                            acc <= -flipPx && idx < queue.size - 1 -> idx++
                            acc >= flipPx && idx > 0 -> idx--
                        }
                    }
                ) { _, amount -> acc += amount }
            }
            .verticalScroll(scroll)
            .padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(6.dp))

            // 题干卡片：作业本纸
            Box(
                Modifier.fillMaxWidth().clip(PaperShape).background(PaperCard)
                    .border(1.2.dp, PaperLine, PaperShape)
            ) {
                RuledLines(Modifier.matchParentSize())
                Column(Modifier.padding(14.dp)) {
                    if (q.isMulti) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(RoundedCornerShape(4.dp))
                                .background(PaperAmber.copy(alpha = 0.15f))
                                .border(1.dp, PaperAmber, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)) {
                                Text("多选", color = PaperAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    reveal && onlyAnswer -> "仅显示正确选项"
                                    reveal -> "多项正确答案，已在选项中标出"
                                    else -> "以下选项均需判断"
                                },
                                color = PaperDim, fontSize = 10.sp, fontFamily = ExamFont)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(q.stem, color = PaperInk, fontSize = 15.5.sp, lineHeight = 25.sp,
                        fontFamily = ExamFont)
                    QuestionImage(q.img)
                    // 卡片右下摩尔斯角落装饰
                    MorseStrip("101",
                        Modifier.align(Alignment.End).padding(top = 6.dp).width(30.dp).height(4.dp),
                        color = PaperLine, dot = 1.6f)
                }
            }
            Spacer(Modifier.height(10.dp))

            // 选项：背题模式下可只列正确项；错题本保留作答
            val shownIdx = if (reveal && onlyAnswer) q.options.indices.filter { it in q.ans }
                           else q.options.indices.toList()
            shownIdx.forEach { i ->
                val opt = q.options[i]
                val isAns = i in q.ans
                val isPicked = i in picked
                val showAns = reveal || submitted
                val wrongPick = !reveal && submitted && isPicked && !isAns
                val bg = when {
                    showAns && isAns -> PaperGreen.copy(alpha = 0.14f)
                    wrongPick -> PaperRed.copy(alpha = 0.10f)
                    isPicked -> PaperBlue.copy(alpha = 0.08f)
                    else -> PaperCard
                }
                val border = when {
                    showAns && isAns -> PaperGreen
                    wrongPick -> PaperRed
                    isPicked -> PaperBlue
                    else -> PaperLine
                }
                val ink = when {
                    showAns && isAns -> PaperGreen
                    wrongPick -> PaperRed
                    isPicked -> PaperBlue
                    else -> PaperInk
                }
                val markBg = when {
                    showAns && isAns -> PaperGreen
                    wrongPick -> PaperRed
                    isPicked -> PaperBlue
                    else -> PaperBg
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clip(PaperShape).background(bg)
                        .border(if (showAns && isAns) 1.8.dp else 1.2.dp, border, PaperShape)
                        .clickable(enabled = !reveal && !submitted) {
                            picked = if (q.isMulti) {
                                if (i in picked) picked - i else picked + i
                            } else {
                                setOf(i)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 答题卡涂格
                    Box(
                        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                            .background(markBg)
                            .border(1.4.dp, if (markBg == PaperBg) PaperLine else markBg,
                                RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (wrongPick) "✗" else letters.getOrNull(i) ?: "?",
                            color = if (markBg == PaperBg) PaperDim else Color.White,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(opt, color = ink, fontSize = 14.5.sp, lineHeight = 21.sp,
                        fontFamily = ExamFont, modifier = Modifier.weight(1f))
                    if (showAns && isAns) {
                        Spacer(Modifier.width(6.dp))
                        Text("✓ 答案", color = PaperGreen, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                    }
                }
            }

            // 正确答案汇总
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("正确答案：${q.answerText}",
                    color = PaperGreen, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                Spacer(Modifier.weight(1f))
                if (!reveal && submitted) {
                    val ok = picked == q.ans.toSet()
                    Text(if (ok) "回答正确 ✓" else "回答错误 ✗",
                        color = if (ok) PaperGreen else PaperRed,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                } else {
                    MorseStrip("1", Modifier.width(24.dp).height(5.dp),
                        color = PaperGreen.copy(alpha = 0.5f), dot = 1.8f)
                }
            }
            Spacer(Modifier.height(12.dp))

            // 多选确认（仅错题本作答模式）
            if (!reveal && q.isMulti && !submitted) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(PaperShape)
                        .background(if (picked.isEmpty()) PaperCard else PaperBlue)
                        .border(1.2.dp, if (picked.isEmpty()) PaperLine else PaperBlue, PaperShape)
                        .clickable(enabled = picked.isNotEmpty()) { submitted = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("确定 · 已选 ${picked.size} 项",
                        color = if (picked.isEmpty()) PaperDim else Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 底部翻页（醒目的实心按钮）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaperButton("‹ 上一题", enabled = idx > 0, modifier = Modifier.weight(1f)) { idx-- }
            if (reveal) {
                PaperButton("⟲ 从头", enabled = idx > 0,
                    modifier = Modifier.weight(1f)) { idx = 0 }
            }
            if (!reveal && !q.isMulti && !submitted) {
                PaperButton("看答案", primary = picked.isNotEmpty(), enabled = picked.isNotEmpty(),
                    modifier = Modifier.weight(1f)) {
                    submitted = true
                }
            }
            PaperButton(if (idx < queue.size - 1) "下一题 ›" else "已到末题",
                enabled = idx < queue.size - 1,
                primary = idx < queue.size - 1,
                modifier = Modifier.weight(1f)) { idx++ }
        }
    }
}

private data class PaperQuadruple(
    val bg: Color, val border: Color, val ink: Color, val mark: String
)

@Composable
private fun PaperButton(
    text: String,
    enabled: Boolean = true,
    primary: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clip(PaperShape)
            .background(
                when {
                    primary -> PaperBlue
                    enabled -> PaperBg
                    else -> PaperCard
                }
            )
            .border(
                1.4.dp,
                when {
                    primary -> PaperBlue
                    enabled -> PaperInk.copy(alpha = 0.35f)
                    else -> PaperLine
                },
                PaperShape
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text,
            color = when {
                primary -> Color.White
                enabled -> PaperInk
                else -> PaperDim.copy(alpha = 0.55f)
            },
            fontSize = 15.sp, fontWeight = FontWeight.Bold,
            fontFamily = ExamFont, maxLines = 1)
    }
}

// ============================================================
//  模拟考试
// ============================================================

@Composable
private fun ExamPane(app: AppState, level: ExamLevel) {
    var stage by remember(level) { mutableStateOf("intro") }
    var questions by remember(level) { mutableStateOf<List<Question>>(emptyList()) }
    var answers by remember(level) { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }
    var idx by remember(level) { mutableIntStateOf(0) }
    var result by remember(level) { mutableStateOf<com.wc.cwtrainer.core.ExamResult?>(null) }
    var startedAt by remember(level) { mutableStateOf(0L) }
    var remainingSec by remember(level) { mutableIntStateOf(level.durationMin * 60) }
    var submitted by remember(level) { mutableStateOf(false) }
    var autoSubmitted by remember(level) { mutableStateOf(false) }

    // 交卷（手动或超时自动）：判分、存档、错题入库
    val finishExam: (Boolean) -> Unit = { auto ->
        if (!submitted) {
            submitted = true
            autoSubmitted = auto
            val r = app.exam.gradeExam(
                level, questions, answers,
                System.currentTimeMillis() - startedAt
            )
            result = r
            app.exam.saveBestIfBetter(r)
            app.exam.addRecord(r)
            questions.forEach { qq ->
                val ua = answers[qq.id]
                app.exam.recordAnswer(level, qq.id, ua != null && ua == qq.ans.toSet())
            }
            stage = "result"
        }
    }

    // 倒计时
    LaunchedEffect(stage, startedAt) {
        if (stage != "session") return@LaunchedEffect
        val endAt = startedAt + level.durationMin * 60_000L
        while (true) {
            val left = ((endAt - System.currentTimeMillis()) / 1000L).toInt()
            remainingSec = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(200)
        }
    }
    LaunchedEffect(remainingSec, stage) {
        if (stage == "session" && remainingSec <= 0) finishExam(true)
    }

    when (stage) {
        "intro" -> {
            val best = app.exam.bestResult(level)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(14.dp))
                // 考试通知单
                Box(Modifier.fillMaxWidth().clip(PaperShape).background(PaperCard)
                    .border(1.2.dp, PaperLine, PaperShape)) {
                    RuledLines(Modifier.matchParentSize())
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AntennaWaves(Modifier.size(width = 30.dp, height = 24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("考试通知", color = PaperInk, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                            Spacer(Modifier.weight(1f))
                            Text(level.cn, color = PaperAmber, fontSize = 14.sp,
                                fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${level.cn}考试：共 ${level.examCount} 题（单选 ${level.singleCount} 题、多选 ${level.multiCount} 题），" +
                                "限时 ${level.durationMin} 分钟，答对 ${level.passCount} 题及以上合格。\n" +
                                "多选题须与标准答案完全一致才得分，多选或少选均不得分；超出限时自动交卷。\n" +
                                "题目从官方题库全部 ${QuestionBank.questionsFor(level).size} 题中随机抽取。",
                            color = PaperInk, fontSize = 13.5.sp, lineHeight = 23.sp, fontFamily = ExamFont
                        )
                        best?.let {
                            Spacer(Modifier.height(10.dp))
                            Text("历史最佳：${(it.bestRate * 100).toInt()}%（${if (it.passed) "通过" else "未通过"}）",
                                color = PaperGreen, fontSize = 13.sp, fontFamily = ExamFont)
                        }
                        Spacer(Modifier.height(6.dp))
                        MorseStrip("1011101", Modifier.fillMaxWidth(0.34f).height(5.dp),
                            color = PaperAmber.copy(alpha = 0.5f), dot = 1.8f)
                    }
                }
                Spacer(Modifier.height(16.dp))
                PaperButton("开始考试", primary = true) {
                    questions = app.exam.drawExam(level)
                    answers = emptyMap()
                    idx = 0
                    startedAt = System.currentTimeMillis()
                    remainingSec = level.durationMin * 60
                    submitted = false
                    autoSubmitted = false
                    result = null
                    stage = "session"
                }
                Spacer(Modifier.height(16.dp))
                ExamHistory(app.exam.history(level), level)
                Spacer(Modifier.height(12.dp))
            }
        }

        "session" -> {
            if (idx >= questions.size) idx = questions.size - 1
            val q = questions[idx]
            val picked = answers[q.id] ?: emptySet()
            val letters = listOf("A", "B", "C", "D")

            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${idx + 1} / ${questions.size} 题 · 已答 ${answers.size}" +
                            if (q.isMulti) " · 多选题" else "",
                            color = PaperDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f))
                        Text("剩余 ${fmtClock(remainingSec)}",
                            color = if (remainingSec <= 300) PaperRed else PaperDim,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(PaperRule)) {
                        Box(Modifier.fillMaxWidth((answers.size.toFloat()) / questions.size)
                            .clip(RoundedCornerShape(2.dp)).background(PaperBlue))
                    }
                }

                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().clip(PaperShape).background(PaperCard)
                        .border(1.2.dp, PaperLine, PaperShape)) {
                        RuledLines(Modifier.matchParentSize())
                        Column(Modifier.padding(14.dp)) {
                            if (q.isMulti) {
                                Box(Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(PaperAmber.copy(alpha = 0.15f))
                                    .border(1.dp, PaperAmber, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)) {
                                    Text("多选", color = PaperAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(q.stem, color = PaperInk, fontSize = 15.5.sp, lineHeight = 25.sp,
                                fontFamily = ExamFont)
                            QuestionImage(q.img)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    q.options.forEachIndexed { i, opt ->
                        val isPicked = i in picked
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(PaperShape)
                                .background(if (isPicked) PaperBlue.copy(alpha = 0.08f) else PaperCard)
                                .border(1.2.dp, if (isPicked) PaperBlue else PaperLine, PaperShape)
                                .clickable {
                                    val cur = answers.toMutableMap()
                                    cur[q.id] = if (q.isMulti) {
                                        if (i in picked) picked - i else picked + i
                                    } else setOf(i)
                                    answers = cur
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (isPicked) PaperBlue else PaperBg)
                                    .border(1.4.dp, if (isPicked) PaperBlue else PaperLine, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(letters.getOrNull(i) ?: "?",
                                    color = if (isPicked) Color.White else PaperDim,
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(opt, color = if (isPicked) PaperBlue else PaperInk,
                                fontSize = 14.sp, lineHeight = 21.sp, fontFamily = ExamFont,
                                modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaperButton("上一题", enabled = idx > 0, modifier = Modifier.weight(1f)) { idx-- }
                    if (idx < questions.size - 1) {
                        PaperButton("下一题", enabled = true, modifier = Modifier.weight(1f)) { idx++ }
                    } else {
                        PaperButton("交卷", primary = true, modifier = Modifier.weight(1f)) {
                            finishExam(false)
                        }
                    }
                }
            }
        }

        "result" -> {
            val r = result
            if (r == null) { stage = "intro"; return }
            var reviewing by remember { mutableStateOf(false) }
            var ridx by remember { mutableIntStateOf(0) }

            if (reviewing) {
                if (ridx >= questions.size) ridx = questions.size - 1
                val q = questions[ridx]
                val ua = answers[q.id] ?: emptySet()
                val letters = listOf("A", "B", "C", "D")
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("逐题回顾 ${ridx + 1} / ${questions.size} · ${q.id}",
                            color = PaperDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(PaperRule)) {
                            Box(Modifier.fillMaxWidth((ridx + 1f) / questions.size)
                                .clip(RoundedCornerShape(2.dp)).background(PaperAmber.copy(alpha = 0.6f)))
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().clip(PaperShape).background(PaperCard)
                            .border(1.2.dp, PaperLine, PaperShape)) {
                            RuledLines(Modifier.matchParentSize())
                            Column(Modifier.padding(14.dp)) {
                                Text(q.stem, color = PaperInk, fontSize = 15.5.sp, lineHeight = 25.sp,
                                    fontFamily = ExamFont)
                                QuestionImage(q.img)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        q.options.forEachIndexed { i, opt ->
                            val isAns = i in q.ans
                            val isPicked = i in ua
                            val (bg, border, ink, mark) = when {
                                isAns -> PaperQuadruple(PaperGreen.copy(alpha = 0.10f), PaperGreen, PaperGreen, "✓")
                                isPicked -> PaperQuadruple(PaperRed.copy(alpha = 0.10f), PaperRed, PaperRed, "✗")
                                else -> PaperQuadruple(PaperCard, PaperLine, PaperInk, "")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clip(PaperShape).background(bg)
                                    .border(1.2.dp, border, PaperShape)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (mark.isNotEmpty()) (if (mark == "✓") PaperGreen else PaperRed) else PaperBg)
                                        .border(1.4.dp, if (mark.isEmpty()) PaperLine else Color.Transparent, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (mark.isNotEmpty()) mark else letters.getOrNull(i) ?: "?",
                                        color = if (mark.isNotEmpty()) Color.White else PaperDim,
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(opt, color = ink, fontSize = 14.sp, lineHeight = 21.sp,
                                    fontFamily = ExamFont, modifier = Modifier.weight(1f))
                            }
                        }
                        val ok = ua == q.ans.toSet()
                        Spacer(Modifier.height(8.dp))
                        Text(if (ok) "本题正确 ✓" else
                            "你的答案：${ua.sorted().joinToString("、") { c -> ("ABCD".getOrNull(c) ?: "?").toString() }}\n正确答案：${q.answerText}",
                            color = if (ok) PaperGreen else PaperRed, fontSize = 14.sp,
                            lineHeight = 22.sp, fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperButton("上一题", enabled = ridx > 0, modifier = Modifier.weight(1f)) { ridx-- }
                        PaperButton("下一题", enabled = ridx < questions.size - 1, modifier = Modifier.weight(1f)) { ridx++ }
                        PaperButton("返回成绩", modifier = Modifier.weight(1f)) { reviewing = false }
                    }
                }
            } else {
                // 成绩单：红章
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(28.dp))
                    Box(Modifier.fillMaxWidth().clip(PaperShape).background(PaperCard)
                        .border(1.2.dp, PaperLine, PaperShape)) {
                        RuledLines(Modifier.matchParentSize())
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(8.dp))
                            // 印章
                            Box(
                                modifier = Modifier.rotate(-8f)
                                    .border(3.dp, if (r.passed) PaperGreen else PaperRed, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 22.dp, vertical = 8.dp)
                            ) {
                                Text(if (r.passed) "通  过" else "未  通  过",
                                    color = if (r.passed) PaperGreen else PaperRed,
                                    fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = ExamFont, letterSpacing = 2.sp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("答对 ${r.correct} / ${r.total} 题（需 ${level.passCount} 题）",
                                color = PaperInk, fontSize = 16.sp, fontFamily = ExamFont)
                            Text("正确率 ${(r.rate * 100).toInt()}% · 用时 ${r.timeMs / 60000}分${(r.timeMs / 1000) % 60}秒",
                                color = PaperDim, fontSize = 13.sp, fontFamily = ExamFont,
                                modifier = Modifier.padding(top = 4.dp))
                            if (autoSubmitted) {
                                Text("考试时间已到，系统已自动交卷",
                                    color = PaperRed, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = ExamFont, modifier = Modifier.padding(top = 6.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            MorseStrip("10111000101",
                                Modifier.fillMaxWidth(0.4f).height(5.dp),
                                color = PaperAmber.copy(alpha = 0.5f), dot = 1.8f)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    PaperButton("逐题回顾", primary = true) { reviewing = true }
                    Spacer(Modifier.height(8.dp))
                    PaperButton("再考一次") { stage = "intro" }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

/** 剩余时间 mm:ss */
private fun fmtClock(sec: Int): String =
    "%02d:%02d".format(sec / 60, sec % 60)

/** 成绩记录：最多 10 条，最新的在最上面 */
@Composable
private fun ExamHistory(list: List<ExamRecord>, level: ExamLevel) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("成绩记录", color = PaperInk, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, fontFamily = ExamFont)
            Spacer(Modifier.weight(1f))
            Text("${list.size} / ${com.wc.cwtrainer.core.ExamManager.MAX_RECORDS} 条",
                color = PaperDim, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxWidth().clip(PaperShape).background(PaperCard)
                .border(1.dp, PaperLine, PaperShape).padding(14.dp)) {
                Text("暂无记录，完成一次${level.cn}模拟考试后在这里查看",
                    color = PaperDim, fontSize = 13.sp, fontFamily = ExamFont)
            }
        } else {
            list.forEach { rec ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        .clip(PaperShape).background(PaperCard)
                        .border(1.dp, PaperLine, PaperShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(remember(rec.at) {
                            SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.getDefault()).format(Date(rec.at))
                        }, color = PaperInk, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(3.dp))
                        Text("用时 ${rec.timeMs / 60000}分${(rec.timeMs / 1000) % 60}秒",
                            color = PaperDim, fontSize = 11.5.sp, fontFamily = ExamFont)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("得分 ${rec.correct}/${rec.total}",
                            color = PaperInk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(3.dp))
                        Text(if (rec.passed) "合格" else "不合格",
                            color = if (rec.passed) PaperGreen else PaperRed,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = ExamFont)
                    }
                }
            }
        }
    }
}

/** 题干附图（assets/qimg 下的 PNG），白底卡片内等比显示 */
@Composable
private fun QuestionImage(img: String?) {
    if (img.isNullOrBlank()) return
    val context = LocalContext.current
    val bmp = remember(img) {
        runCatching {
            context.assets.open(img).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap()
    } ?: return
    Spacer(Modifier.height(8.dp))
    Image(
        bitmap = bmp,
        contentDescription = "题目附图",
        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            .clip(RoundedCornerShape(4.dp)).background(Color.White),
        contentScale = ContentScale.Fit
    )
}
