package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** 错题本条目 */
data class WrongItem(
    val questionId: String,
    val wrongCount: Int,
    val lastWrongAt: Long
)

/** 模拟考试：随机抽题（B 50 题/对 40 过；C 80 题/对 60 过），判分，错题入库 */
class ExamManager(context: Context) {

    private val sp = context.getSharedPreferences("wc_exam", Context.MODE_PRIVATE)

    /** 错题本（响应式，UI 直接观察） */
    var wrongB by mutableStateOf(loadWrong(ExamLevel.B))
        private set
    var wrongC by mutableStateOf(loadWrong(ExamLevel.C))
        private set

    private fun loadWrong(level: ExamLevel): Map<String, WrongItem> {
        val p = level.name
        val map = linkedMapOf<String, WrongItem>()
        val n = sp.getInt("$p:wrongCount", 0)
        for (i in 0 until n) {
            val id = sp.getString("$p:wId$i", null) ?: continue
            map[id] = WrongItem(
                id,
                sp.getInt("$p:wCnt$i", 1),
                sp.getLong("$p:wTs$i", 0L)
            )
        }
        return map
    }

    private fun saveWrong(level: ExamLevel, map: Map<String, WrongItem>) {
        val p = level.name
        sp.edit().apply {
            putInt("$p:wrongCount", map.size)
            map.values.forEachIndexed { i, it ->
                putString("$p:wId$i", it.questionId)
                putInt("$p:wCnt$i", it.wrongCount)
                putLong("$p:wTs$i", it.lastWrongAt)
            }
        }.apply()
    }

    private fun wrongMap(level: ExamLevel) = if (level == ExamLevel.B) wrongB else wrongC

    private fun updateWrongMap(level: ExamLevel, map: Map<String, WrongItem>) {
        if (level == ExamLevel.B) wrongB = map else wrongC = map
        saveWrong(level, map)
    }

    /** 答题记录：答错入错题本，答对从错题本移除 */
    fun recordAnswer(level: ExamLevel, qid: String, correct: Boolean) {
        val cur = wrongMap(level).toMutableMap()
        if (correct) {
            if (cur.remove(qid) != null) updateWrongMap(level, cur)
        } else {
            val old = cur[qid]
            cur[qid] = WrongItem(qid, (old?.wrongCount ?: 0) + 1, System.currentTimeMillis())
            updateWrongMap(level, cur)
        }
    }

    fun clearWrong(level: ExamLevel) = updateWrongMap(level, emptyMap())

    fun wrongCount(level: ExamLevel) = wrongMap(level).size

    /** 错题列表（按错误次数、最近时间排序） */
    fun wrongQuestions(level: ExamLevel): List<Question> {
        val ids = wrongMap(level).keys
        return QuestionBank.questionsFor(level).filter { it.id in ids }
    }

    // ========== 模拟考试 ==========

    /** 随机抽题 */
    fun drawExam(level: ExamLevel, seed: Long? = null): List<Question> {
        val pool = QuestionBank.questionsFor(level).toMutableList()
        pool.shuffle(if (seed != null) Random(seed) else Random)
        return pool.take(level.examCount)
    }

    /** 判分：所选集合须与正确答案完全一致 */
    fun gradeExam(
        level: ExamLevel,
        questions: List<Question>,
        userAnswers: Map<String, Set<Int>>,
        timeMs: Long
    ): ExamResult {
        var correct = 0
        questions.forEach { q ->
            if (userAnswers[q.id] == q.ans.toSet()) correct++
        }
        return ExamResult(level, questions.size, correct, timeMs, correct >= level.passCount)
    }

    /** 历史最佳（正确率 + 是否通过） */
    data class BestResult(val level: ExamLevel, val bestRate: Float, val passed: Boolean)

    fun bestResult(level: ExamLevel): BestResult? {
        val rate = sp.getFloat("${level.name}:bestRate", -1f)
        if (rate < 0f) return null
        return BestResult(level, rate, sp.getBoolean("${level.name}:bestPassed", false))
    }

    fun saveBestIfBetter(result: ExamResult) {
        val cur = bestResult(result.level)
        if (cur == null || result.rate > cur.bestRate || (result.passed && !cur.passed)) {
            sp.edit()
                .putFloat("${result.level.name}:bestRate", result.rate)
                .putBoolean("${result.level.name}:bestPassed", result.passed)
                .apply()
        }
    }

    // ========== 背题进度记忆（按序/分类背题，退出重进可续） ==========

    /** 记忆背题位置 */
    fun savePos(key: String, idx: Int) {
        sp.edit().putInt("pos:$key", idx).apply()
    }

    fun loadPos(key: String): Int = sp.getInt("pos:$key", 0)

    /** 记忆字符串状态（分类页选择的小节/题型、末次所在页签等） */
    fun saveStr(key: String, value: String) {
        sp.edit().putString("st:$key", value).apply()
    }

    fun loadStr(key: String): String? = sp.getString("st:$key", null)
}
