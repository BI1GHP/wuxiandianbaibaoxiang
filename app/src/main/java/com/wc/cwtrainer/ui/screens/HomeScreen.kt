package com.wc.cwtrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppScreen
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain

@Composable
fun HomeScreen(app: AppState, onOpen: (AppScreen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "无线电百宝箱",
                    color = TextMain,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "V1.0  ·  BI1GHP",
                    color = Amber,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Pill("设置", false) { onOpen(AppScreen.SETTINGS) }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = "训练科目",
            color = TextDim,
            fontSize = 12.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(10.dp))

        MenuCard(
            index = "01",
            title = "听抄练习",
            desc = "随机字母 / 数字 / 呼号，听音抄收并自动判分",
            accent = Amber
        ) { onOpen(AppScreen.COPY) }

        Spacer(Modifier.height(10.dp))
        MenuCard(
            index = "02",
            title = "背码训练",
            desc = "码表速查、听音辨码、看码辨字，逐步形成条件反射",
            accent = Green
        ) { onOpen(AppScreen.LEARN) }

        Spacer(Modifier.height(10.dp))
        MenuCard(
            index = "03",
            title = "赛事听抄",
            desc = "CW 赛事场景，盲听接收、频谱瀑布、信号报告与序号",
            accent = Color(0xFFFF9F6B)
        ) { onOpen(AppScreen.CONTEST) }

        Spacer(Modifier.height(10.dp))
        MenuCard(
            index = "04",
            title = "CW 翻译器",
            desc = "实时解码外部电码 · 屏幕手键/自动键发码 · 文本转 CW 播放",
            accent = Color(0xFF7ED4FF)
        ) { onOpen(AppScreen.TRANSLATOR) }

        Spacer(Modifier.height(18.dp))
        SectionCard {
            SectionTitle("练习统计")
            Spacer(Modifier.height(6.dp))
            StatLine("背码正确率", learnText(app))
            StatLine("听抄最佳", if (app.stats.copyBest <= 0f) "—" else pct(app.stats.copyBest))
            StatLine("赛事最佳", "${app.stats.contestBest}/3")
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "提示：听抄与发码请使用有线耳机或外放，避免扬声器回授到麦克风。",
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun learnText(app: AppState): String {
    val total = app.stats.learnTotal
    return if (total == 0) "—" else pct(app.stats.learnCorrect.toFloat() / total)
}

private fun pct(v: Float): String = String.format("%.0f%%", v * 100f)

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextDim, fontSize = 13.sp)
        Text(
            text = value,
            color = TextMain,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MenuCard(
    index: String,
    title: String,
    desc: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Surface1, accent.copy(alpha = 0.07f))))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index,
            color = accent,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Text(text = "›", color = accent, fontSize = 22.sp)
    }
}
