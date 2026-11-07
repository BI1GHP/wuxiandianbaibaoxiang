package com.wc.cwtrainer.core

/** 业余无线电操作证考试级别（2025 年官方题库）
 *
 * B 类考试：60 题（单选 45 + 多选 15），限时 60 分钟，答对 45 题合格
 * C 类考试：90 题（单选 70 + 多选 20），限时 90 分钟，答对 70 题合格
 */
enum class ExamLevel(
    val cn: String,
    val singleCount: Int,
    val multiCount: Int,
    val durationMin: Int,
    val passCount: Int
) {
    B("B类", 45, 15, 60, 45),
    C("C类", 70, 20, 90, 70);

    /** 总题量 */
    val examCount: Int get() = singleCount + multiCount
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

/** 一次模拟考试的存档记录（最多保留 10 条） */
data class ExamRecord(
    val at: Long,          // 交卷时间戳
    val correct: Int,      // 答对题数
    val total: Int,        // 总题数
    val passed: Boolean,
    val timeMs: Long       // 用时
)
