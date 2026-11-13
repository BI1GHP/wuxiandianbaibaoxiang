package com.wc.cwtrainer.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** 教练任务指向的训练板块 */
enum class CoachTarget { LEARN, COPY, CONTEST, ACCENT }

/** 一天的训练安排 */
data class CoachDay(
    val date: LocalDate,
    val theme: String,
    val detail: String,
    val target: CoachTarget,
    val done: Boolean
)

/** 一周训练清单：每周一自动按最新数据重新生成 */
data class CoachPlan(
    val weekLabel: String,
    val focus: String,
    val insight: List<String>,
    val days: List<CoachDay>
) {
    val todayTask: CoachDay? get() = days.firstOrNull { it.date == LocalDate.now() }
}

/**
 * 规则版 AI 教练：不联网，按本地统计（SRS 到期、易混字符、听抄/赛事成绩、口音命中率、
 * 连续天数）推断本周重点，并排出周一到周日 7 天清单。主题按星期固定，
 * 只有具体建议随数据变化，保证同一天内完成状态稳定。
 */
object CoachEngine {

    private val ACCENT_NAMES = mapOf(
        "en-US" to "美国", "en-GB" to "英国", "en-AU" to "澳大利亚",
        "de-DE" to "德国", "fr-FR" to "法国", "it-IT" to "意大利",
        "es-ES" to "西班牙", "ru-RU" to "俄罗斯", "ja-JP" to "日本",
        "ko-KR" to "韩国", "zh-CN" to "中国", "pt-BR" to "巴西",
        "sv-SE" to "瑞典", "pl-PL" to "波兰", "nl-NL" to "荷兰"
    )

    fun accentName(locale: String): String = ACCENT_NAMES[locale] ?: locale

    fun weekly(
        copyWpm: Int,
        learnTotal: Int,
        copyBest: Float,
        contestBest: Int,
        confused: List<Pair<Char, Int>>,
        dueCount: Int,
        streak: Int,
        accentBest: Map<String, Float>,
        history: List<DayStat>,
        today: LocalDate = LocalDate.now()
    ): CoachPlan {
        val weakAccent = accentBest.entries.filter { it.value < 0.7f }.minByOrNull { it.value }

        // —— 本周重点：按优先级取第一个命中的短板 ——
        val focus = when {
            confused.isNotEmpty() && confused.first().second >= 2 ->
                "攻克易混字符 " + confused.take(3).joinToString(" / ") { it.first.toString() }
            copyBest > 0f && copyBest < 0.75f -> "听抄准确率冲刺 85%"
            contestBest in 1..2 -> "赛事抄收稳定性"
            weakAccent != null -> "适应${accentName(weakAccent.key)}口音"
            learnTotal == 0 -> "建立字母与数字的条件反射"
            else -> "稳步提速，冲击 ${copyWpm + 5} WPM"
        }

        // —— 诊断依据：最多 3 条，都能在应用内看到对应数据 ——
        val insight = mutableListOf<String>()
        if (dueCount > 0) insight += "今日有 $dueCount 个字符到期复习，先清空队列"
        if (confused.isNotEmpty()) {
            insight += "最易混：" + confused.take(3).joinToString("、") { "${it.first}×${it.second}" }
        }
        if (copyBest > 0f) insight += "听抄最佳 ${(copyBest * 100).roundToInt()}% · 建议码速 $copyWpm WPM"
        if (contestBest > 0) {
            insight += "赛事最佳 $contestBest/3" + if (contestBest < 3) "，可尝试同频多台" else "，可开启传播波动"
        }
        if (weakAccent != null) {
            insight += "${accentName(weakAccent.key)}口音命中率 ${(weakAccent.value * 100).roundToInt()}%，列为补练"
        }
        if (streak > 0) insight += "已连续训练 $streak 天"
        if (insight.isEmpty()) {
            insight += "还没有训练数据，先完成一次背码或听抄，教练会据此调整计划"
        }

        // —— 7 天清单：主题按星期固定，完成状态按当天历史判定 ——
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val byDate = history.associateBy { it.date }

        fun doneOn(date: LocalDate, target: CoachTarget): Boolean {
            if (date.isAfter(today)) return false
            val d = byDate[date.toString()] ?: return false
            return when (target) {
                CoachTarget.LEARN -> d.learnCount > 0
                CoachTarget.COPY -> d.copyCount > 0
                CoachTarget.CONTEST -> d.contestCount > 0
                CoachTarget.ACCENT -> d.accentCount > 0
            }
        }

        val confusedText = if (confused.isEmpty()) "E / I / S / H"
            else confused.take(3).joinToString(" / ") { it.first.toString() }
        val accentPick = weakAccent?.key ?: "de-DE"
        val contestAdvice = if (contestBest >= 3) "开启「同频多台 + 传播波动」挑战一轮"
            else "先关闭干扰，稳定抄收呼号与序号"
        val reviewTarget = when {
            confused.isNotEmpty() -> CoachTarget.LEARN
            copyBest < 0.8f -> CoachTarget.COPY
            else -> CoachTarget.CONTEST
        }

        val template = listOf(
            Triple("字符复习", "清空 SRS 到期队列 · 约 5 分钟", CoachTarget.LEARN),
            Triple("听抄基础", "$copyWpm WPM · 5 组字符，目标 ≥85%", CoachTarget.COPY),
            Triple("口音适应", "${accentName(accentPick)} · 5 题一轮，记录命中率", CoachTarget.ACCENT),
            Triple("易混字符专项", "反复听辨 $confusedText，直到连续 3 次全对", CoachTarget.LEARN),
            Triple("听抄提速", "${copyWpm + 3} WPM · 3 组，错一个降回基础速", CoachTarget.COPY),
            Triple("赛事听抄", contestAdvice, CoachTarget.CONTEST),
            Triple("周复盘", "回看失误分布，弱项再练一轮", reviewTarget)
        )
        val days = template.mapIndexed { i, (theme, detail, target) ->
            val date = monday.plusDays(i.toLong())
            CoachDay(date, theme, detail, target, doneOn(date, target))
        }

        val sunday = monday.plusDays(6)
        return CoachPlan(
            weekLabel = "${monday.monthValue}/${monday.dayOfMonth} – ${sunday.monthValue}/${sunday.dayOfMonth}",
            focus = focus,
            insight = insight.take(3),
            days = days
        )
    }
}
