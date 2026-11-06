package com.wc.cwtrainer.core

/** 业余无线电操作证考试级别（2025 年官方题库）
 *
 * B 类考试：随机抽 50 题，答对 40 题通过
 * C 类考试：随机抽 80 题，答对 60 题通过
 */
enum class ExamLevel(val cn: String, val examCount: Int, val passCount: Int) {
    B("B类", 50, 40),
    C("C类", 80, 60)
}

/** 一道题（来自官方题库 JSON） */
data class Question(
    val id: String,           // 官方内部编号，如 "MC1-0005"
    val level: ExamLevel,
    val sec: String,          // 知识点小节，如 "1.1"
    val isMulti: Boolean,     // true=多选题
    val stem: String,         // 题干
    val options: List<String>,
    val ans: List<Int>        // 正确选项下标（0=A ...）
) {
    val chapter: Int get() = sec.substringBefore('.').toIntOrNull() ?: 0
    val answerText: String
        get() = ans.joinToString("、") { "ABCD".getOrNull(it)?.toString() ?: "?" }
}

/** 章节标题（按 2025 题库内容归纳） */
object ExamChapters {
    val titles = mapOf(
        1 to "法规与设台管理",
        2 to "操作规程与频率划分",
        3 to "设备、天线与传输线",
        4 to "电工与电路基础",
        5 to "发射设备技术指标"
    )
    fun title(ch: Int) = titles[ch] ?: "第 $ch 章"
}

/** 考试结果 */
data class ExamResult(
    val level: ExamLevel,
    val total: Int,
    val correct: Int,
    val timeMs: Long,
    val passed: Boolean
) {
    val rate get() = if (total == 0) 0f else correct.toFloat() / total
}
