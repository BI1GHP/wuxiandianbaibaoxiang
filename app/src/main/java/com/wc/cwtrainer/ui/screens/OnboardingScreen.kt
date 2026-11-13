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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.wc.cwtrainer.AppScreen
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.Fist
import com.wc.cwtrainer.core.VoiceSpec
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.NameplateCard
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain

private enum class Level(
    val title: String,
    val desc: String,
    val wpm: Int
) {
    BEGINNER("零基础", "刚开始认点划，先慢速建立条件反射", 12),
    BASIC("能抄 15 WPM", "字母数字基本认识，想提高稳定性", 18),
    ADVANCED("备赛提速", "准备参赛，需要高速听抄与口音适应", 28)
}

/**
 * 首启引导：3 步内进入第一次训练。
 * 1. 认识能力 → 2. 水平自测（可试听）→ 3. 今日计划并直达训练。
 */
@Composable
fun OnboardingScreen(app: AppState, onFinish: (AppScreen) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var level by remember { mutableStateOf(Level.BEGINNER) }

    DisposableEffect(Unit) {
        onDispose { app.audio.stop() }
    }

    fun finish(target: AppScreen) {
        app.prefs.update {
            it.copy(
                onboarded = true,
                copyWpm = level.wpm,
                learnWpm = level.wpm,
                contestWpm = maxOf(level.wpm, 20),
                farnsworth = if (level == Level.BEGINNER) 8 else 0
            )
        }
        app.audio.stop()
        onFinish(target)
    }

    BackHandler(enabled = step > 0) { step -= 1 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(Bg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            NameplateCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "CW练考通",
                            color = TextMain,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "首次设置 · 第 ${step + 1} / 3 步",
                            color = Amber,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        text = "跳过",
                        color = TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                app.prefs.update { it.copy(onboarded = true) }
                                onFinish(AppScreen.HOME)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= step) Amber else Line)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            when (step) {
                0 -> WelcomeStep()
                1 -> LevelStep(level) { picked ->
                    level = picked
                    app.audio.play(
                        voices = listOf(
                            VoiceSpec(
                                text = "E I S H 5",
                                fist = Fist(
                                    wpm = picked.wpm.toDouble(),
                                    farnsworth = if (picked == Level.BEGINNER) 8.0 else 0.0
                                ),
                                toneHz = app.prefs.data.toneHz.toDouble()
                            )
                        ),
                        volume = app.prefs.data.volume,
                        title = "水平自测",
                        subtitle = "${picked.wpm} WPM 试听"
                    )
                }
                else -> PlanStep(level)
            }
            Spacer(Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (step > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface2)
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .clickable { step -= 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text("上一步", color = TextMain, fontSize = 14.sp)
                }
            }
            Box(
                modifier = Modifier
                    .weight(2f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Amber)
                    .clickable {
                        when (step) {
                            0 -> step = 1
                            1 -> step = 2
                            else -> finish(AppScreen.LEARN)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (step) {
                        0 -> "开始水平自测"
                        1 -> "下一步"
                        else -> "开始第一次背码训练"
                    },
                    color = androidx.compose.ui.graphics.Color(0xFF1A1200),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    SectionCard {
        SectionTitle("三步开始")
        Spacer(Modifier.height(8.dp))
        FeatureLine("01", "听抄练习", "随机字母 / 数字 / 呼号，听音抄收并自动判分")
        FeatureLine("02", "背码训练", "码表、听音辨码、看码辨字与间隔复习")
        FeatureLine("03", "赛事听抄", "CW / SSB 模拟赛场，练完可复盘并导出 ADIF")
        FeatureLine("04", "业余无线电考试", "A / B / C 类题库、错题本与模拟考")
    }
    Spacer(Modifier.height(14.dp))
    SectionCard {
        SectionTitle("接下来")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "先花 30 秒做一次水平自测，我们会据此设置默认码速，然后直接带你进入第一次训练。",
            color = TextDim,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun FeatureLine(index: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Amber.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(index, color = Amber, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                desc,
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun LevelStep(selected: Level, onPick: (Level) -> Unit) {
    Text(
        text = "你现在能听清多快的电报？",
        color = TextMain,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "选一档后会自动播放 5 个字符试听，选听起来「刚好能跟上」的那一档。",
        color = TextDim,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
    )
    Level.entries.forEach { lv ->
        val on = selected == lv
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (on) Amber.copy(alpha = 0.12f) else Surface1)
                .border(1.dp, if (on) Amber else Line, RoundedCornerShape(14.dp))
                .clickable { onPick(lv) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = lv.title,
                    color = if (on) Amber else TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = lv.desc,
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${lv.wpm} WPM",
                    color = if (on) Amber else TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (on) "点击重听" else "点击试听",
                    color = TextDim,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PlanStep(level: Level) {
    SectionCard {
        SectionTitle("今日 15 分钟计划")
        Spacer(Modifier.height(8.dp))
        PlanLine("5 分钟", "背码热身", "从 ${level.wpm} WPM 开始，先听声音不数点划")
        PlanLine("7 分钟", "听抄练习", "5 组随机字符，抄收后自动判分")
        PlanLine("3 分钟", "复盘", "看最易混字符与连续天数，明天继续")
    }
    Spacer(Modifier.height(14.dp))
    SectionCard {
        SectionTitle("你的默认码速")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${level.wpm} WPM" + if (level == Level.BEGINNER) "（Farnsworth 8 WPM）" else "",
            color = Green,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "之后可在设置或各板块顶部随时调整。",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    Spacer(Modifier.height(14.dp))
    Text(
        text = "提示：练习时建议使用有线耳机，避免扬声器回授。",
        color = TextDim,
        fontSize = 11.sp
    )
}

@Composable
private fun PlanLine(time: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = time,
            color = Amber,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 1.dp)
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = TextDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
