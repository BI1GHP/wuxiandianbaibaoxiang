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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.wc.cwtrainer.ui.Cyan
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.NameplateCard
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain

@Composable
fun HomeScreen(app: AppState, onOpen: (AppScreen) -> Unit) {
    val s = app.prefs.data
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        NameplateCard(
            modifier = Modifier.clickable { onOpen(AppScreen.CALLSIGN) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MODEL WC-2025",
                    color = TextDim,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Amber)
                )
                Text(
                    text = "PWR",
                    color = Amber,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "CW练考通",
                        color = TextMain,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${com.wc.cwtrainer.BuildConfig.VERSION_NAME}  ·  A/B/C  ·  ${s.callsign}",
                        color = Amber,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Pill("设置", false) { onOpen(AppScreen.SETTINGS) }
            }
        }

        val last = app.prefs.lastScreen?.let { name ->
            runCatching { AppScreen.valueOf(name) }.getOrNull()
        }
        if (last != null && last != AppScreen.HOME && last != AppScreen.SETTINGS) {
            Spacer(Modifier.height(18.dp))
            ContinueCard(subjectTitle(last)) { onOpen(last) }
        }

        Spacer(Modifier.height(14.dp))
        TodayPlanCard(app) { onOpen(it) }

        Spacer(Modifier.height(10.dp))
        CoachEntryCard(app) { onOpen(AppScreen.COACH) }

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
            desc = "CW / SSB 赛事场景，盲听接收、频谱瀑布、信号报告与序号",
            accent = Color(0xFFFF9F6B)
        ) { onOpen(AppScreen.CONTEST) }

        Spacer(Modifier.height(10.dp))
        MenuCard(
            index = "04",
            title = "CW 翻译器",
            desc = "实时解码外部电码 · 屏幕手键/自动键发码 · 文本转 CW 播放",
            accent = Color(0xFF7ED4FF)
        ) { onOpen(AppScreen.TRANSLATOR) }

        Spacer(Modifier.height(10.dp))
        MenuCard(
            index = "05",
            title = "业余无线电考试",
            desc = "按序背题 · 分类背题 · 错题本 · 模拟考试（A/B/C 类）",
            accent = Color(0xFFB388FF)
        ) { onOpen(AppScreen.EXAM) }

        Spacer(Modifier.height(10.dp))
        MenuCard(
            index = "06",
            title = "口音适应",
            desc = "15 国母语音色 · 字母解释法听辨 · 逐国命中率",
            accent = Cyan
        ) { onOpen(AppScreen.ACCENT) }

        Spacer(Modifier.height(18.dp))
        SectionCard {
            SectionTitle("练习统计")
            Spacer(Modifier.height(6.dp))
            StatLine("背码正确率", learnText(app))
            StatLine("听抄最佳", if (app.stats.copyBest <= 0f) "—" else pct(app.stats.copyBest))
            StatLine("赛事最佳", "${app.stats.contestBest}/3")
        }

        Spacer(Modifier.height(14.dp))
        TrainingStatsCard(app)

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

private fun subjectTitle(screen: AppScreen): String = when (screen) {
    AppScreen.COPY -> "听抄练习"
    AppScreen.LEARN -> "背码训练"
    AppScreen.CONTEST -> "赛事听抄"
    AppScreen.ACCENT -> "口音适应"
    AppScreen.COACH -> "AI 教练"
    AppScreen.TRANSLATOR -> "CW 翻译器"
    AppScreen.EXAM -> "业余无线电考试"
    AppScreen.SETTINGS -> "设置"
    AppScreen.THEME_PREVIEW -> "主题预览"
    AppScreen.SKIN_STORE -> "皮肤商店"
    AppScreen.CALLSIGN -> "呼号牌"
    AppScreen.SYNC -> "进度同步"
    AppScreen.ACCOUNT -> "账号云同步"
    AppScreen.MEMBER -> "会员与支付"
    AppScreen.HOME -> "首页"
}

/** 首页顶部「继续上次训练」卡片 */
@Composable
private fun ContinueCard(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Amber.copy(alpha = 0.18f), Surface1)))
            .border(1.dp, Amber.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "继续上次训练",
                color = Amber,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = title,
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Text(text = "›", color = Amber, fontSize = 22.sp)
    }
}

private fun learnText(app: AppState): String {
    val total = app.stats.learnTotal
    return if (total == 0) "—" else pct(app.stats.learnCorrect.toFloat() / total)
}

private fun pct(v: Float): String = String.format("%.0f%%", v * 100f)

/** 今日 3 件事：按 SRS 到期数量与当日训练记录生成推荐，点一下直达对应训练 */
@Composable
private fun TodayPlanCard(app: AppState, onOpen: (AppScreen) -> Unit) {
    val today = app.stats.last7Days().lastOrNull()
    val due = app.srs.dueCount()
    val copyDone = (today?.copyCount ?: 0) > 0
    val contestDone = (today?.contestCount ?: 0) > 0
    val dueDone = due == 0
    val done = (if (dueDone) 1 else 0) + (if (copyDone) 1 else 0) + (if (contestDone) 1 else 0)

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("今日 3 件事")
            Spacer(Modifier.weight(1f))
            Text(
                text = "完成 $done/3",
                color = if (done == 3) Green else TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(8.dp))
        TaskRow(
            index = "01",
            title = if (dueDone) "今日字符复习已完成" else "复习 $due 个到期字符",
            desc = "间隔重复 · 约 5 分钟",
            done = dueDone
        ) { onOpen(AppScreen.LEARN) }
        Spacer(Modifier.height(6.dp))
        TaskRow(
            index = "02",
            title = if (copyDone) "今日听抄已完成" else "听抄 5 组字符",
            desc = "随机字母 / 数字，听后判分 · 约 7 分钟",
            done = copyDone
        ) { onOpen(AppScreen.COPY) }
        Spacer(Modifier.height(6.dp))
        TaskRow(
            index = "03",
            title = if (contestDone) "今日赛事已完成" else "赛事听抄 1 轮",
            desc = "CW / SSB 盲听与判分 · 约 3 分钟",
            done = contestDone
        ) { onOpen(AppScreen.CONTEST) }
    }
}

/** AI 教练入口：展示本周重点与今日任务，点开看完整 7 天清单 */
@Composable
private fun CoachEntryCard(app: AppState, onClick: () -> Unit) {
    val plan = coachPlan(app)
    val today = plan.todayTask
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Surface1, Cyan.copy(alpha = 0.10f))))
            .border(1.dp, Cyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI 教练",
                    color = Cyan,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "本周 ${plan.days.count { it.done }}/7",
                    color = TextDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = plan.focus,
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp)
            )
            today?.let {
                Text(
                    text = "今天：${it.theme} · ${it.detail}",
                    color = TextDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Text(text = "›", color = Cyan, fontSize = 22.sp)
    }
}

@Composable
private fun TaskRow(
    index: String,
    title: String,
    desc: String,
    done: Boolean,
    onClick: () -> Unit
) {
    val accent = if (done) Green else Amber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (done) Green.copy(alpha = 0.08f) else Surface1)
            .border(1.dp, if (done) Green.copy(alpha = 0.4f) else Line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (done) "✓" else index,
            color = accent,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(24.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                color = TextDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(text = "›", color = accent, fontSize = 18.sp)
    }
}


/** 训练历史：连续天数 + 7 日训练量曲线 + 最易混字符 Top 5 */
@Composable
private fun TrainingStatsCard(app: AppState) {
    val days = app.stats.last7Days()
    val streak = app.stats.streakDays()
    val confused = app.stats.topConfused()
    val today = days.lastOrNull()

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("训练历史")
            Spacer(Modifier.weight(1f))
            Text(
                text = if (streak > 0) "连续 $streak 天" else "今天还没练",
                color = if (streak > 0) Amber else TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(10.dp))
        WeekBars(days)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "今日 ${today?.totalCount ?: 0} 次 · 7 日合计 ${days.sumOf { it.totalCount }} 次",
                color = TextDim,
                fontSize = 11.sp
            )
            Text(
                text = "背码 ${today?.learnCount ?: 0} · 听抄 ${today?.copyCount ?: 0} · 赛事 ${today?.contestCount ?: 0}",
                color = TextDim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "最易混字符 Top 5",
            color = TextDim,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        if (confused.isEmpty()) {
            Text(
                text = "积累一些听抄 / 背码记录后，这里会列出你最常听错的字符。",
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                confused.forEach { (c, miss) ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface1)
                            .border(1.dp, Line, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = c.toString(),
                            color = Amber,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = " ×$miss",
                            color = TextDim,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBars(days: List<com.wc.cwtrainer.core.DayStat>) {
    val max = days.maxOfOrNull { it.totalCount }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEach { d ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val h = if (d.totalCount > 0) (44f * d.totalCount / max).coerceAtLeast(5f) else 2f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(h.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (d.totalCount > 0) Amber else Line)
                    )
                }
                Text(
                    text = d.date.substring(5).replace("-", "/"),
                    color = TextDim,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


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
