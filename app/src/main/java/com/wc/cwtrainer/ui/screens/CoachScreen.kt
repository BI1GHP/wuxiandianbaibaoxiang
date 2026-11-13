package com.wc.cwtrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppScreen
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.CoachDay
import com.wc.cwtrainer.core.CoachEngine
import com.wc.cwtrainer.core.CoachPlan
import com.wc.cwtrainer.core.CoachTarget
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Cyan
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import java.time.DayOfWeek
import java.time.LocalDate

/** 按当前统计数据生成教练清单：首页卡片与教练页共用同一份结果 */
fun coachPlan(app: AppState): CoachPlan = CoachEngine.weekly(
    copyWpm = app.prefs.data.copyWpm,
    learnTotal = app.stats.learnTotal,
    copyBest = app.stats.copyBest,
    contestBest = app.stats.contestBest,
    confused = app.stats.topConfused(),
    dueCount = app.srs.dueCount(),
    streak = app.stats.streakDays(),
    accentBest = app.accent.best,
    history = app.stats.history
)

@Composable
fun CoachScreen(app: AppState, onBack: () -> Unit, onOpen: (AppScreen) -> Unit) {
    val plan = coachPlan(app)
    val today = LocalDate.now()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "AI 教练",
            onBack = onBack,
            subtitle = "本周训练清单 · 规则版",
            status = "LOCAL"
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本周重点", color = TextDim, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = plan.weekLabel,
                        color = TextDim, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = plan.focus,
                    color = Amber, fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                if (plan.insight.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    plan.insight.forEach { line ->
                        InsightLine(line)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "本周清单",
                    color = TextDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${plan.days.count { it.done }}/7 完成",
                    color = Green, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(10.dp))
            plan.days.forEachIndexed { index, day ->
                CoachDayRow(day, today) { onOpen(screenOf(day.target)) }
                if (index < plan.days.lastIndex) Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "教练按本地统计规则生成，不联网、不上传。训练数据越完整，清单越贴合你的短板。",
                color = TextDim, fontSize = 11.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InsightLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .width(4.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Cyan)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextDim, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun CoachDayRow(day: CoachDay, today: LocalDate, onClick: () -> Unit) {
    val isToday = day.date == today
    val isPast = day.date.isBefore(today)
    val accent = when {
        day.done -> Green
        isToday -> Amber
        isPast -> TextDim
        else -> Cyan
    }
    val status = when {
        day.done -> "✓ 完成"
        isToday -> "今天 ›"
        isPast -> "补练 ›"
        else -> "计划"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday) Amber.copy(alpha = 0.08f) else Surface1)
            .border(1.dp, if (isToday) Amber.copy(alpha = 0.5f) else Line, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(42.dp)) {
            Text(
                text = weekdayCn(day.date.dayOfWeek),
                color = accent, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "${day.date.monthValue}/${day.date.dayOfMonth}",
                color = TextDim, fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 6.dp, end = 8.dp)
        ) {
            Text(
                text = day.theme,
                color = if (day.done) TextDim else TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (day.done) TextDecoration.LineThrough else null
            )
            Text(
                text = day.detail,
                color = TextDim, fontSize = 10.sp, lineHeight = 14.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = status,
            color = accent, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun weekdayCn(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private fun screenOf(target: CoachTarget): AppScreen = when (target) {
    CoachTarget.LEARN -> AppScreen.LEARN
    CoachTarget.COPY -> AppScreen.COPY
    CoachTarget.CONTEST -> AppScreen.CONTEST
    CoachTarget.ACCENT -> AppScreen.ACCENT
}
