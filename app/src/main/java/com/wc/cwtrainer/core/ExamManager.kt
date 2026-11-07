package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** 错题本条目 */
data class WrongItem(
    val questionId: String,
    val wrongCount: Int,
    val lastWrongAt: Long
)

/** 模拟考试：随机抽题（B 60 题/对 45 过；C 90 题/对 70 过），限时判分，错题入库，成绩存档 */
class ExamManager(context: Context) {

    private val sp = context.getSharedPreferences("wc_exam", Context.MODE_PRIVATE)

    /** 错题本（响应式，UI 直接观察） */
    var wrongB by mutableStateOf(loadWrong(ExamLevel.B))
        private set
    var wrongC by mutableStateOf(loadWrong(ExamLevel.C))
        private set

    /** 成绩记录（最新在前，最多 10 条） */
    var historyB by mutableStateOf(loadHistory(ExamLevel.B))
        private set
    var historyC by mutableStateOf(loadHistory(ExamLevel.C))
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

    /** 随机抽题：按级别规定的单选/多选数量分别抽取后混排 */
    fun drawExam(level: ExamLevel, seed: Long? = null): List<Question> {
        val rnd = if (seed != null) Random(seed) else Random
        val all = QuestionBank.questionsFor(level)
        val singles = all.filter { !it.isMulti }.shuffled(rnd).take(level.singleCount)
        val multis = all.filter { it.isMulti }.shuffled(rnd).take(level.multiCount)
        return (singles + multis).shuffled(rnd)
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

    // ========== 成绩记录（最多 10 条，超出覆盖最早一条） ==========

    fun history(level: ExamLevel): List<ExamRecord> =
        if (level == ExamLevel.B) historyB else historyC

    /** 交卷后追加一条成绩记录 */
    fun addRecord(result: ExamResult) {
        val rec = ExamRecord(
            at = System.currentTimeMillis(),
            correct = result.correct,
            total = result.total,
            passed = result.passed,
            timeMs = result.timeMs
        )
        val list = (listOf(rec) + history(result.level)).take(MAX_RECORDS)
        if (result.level == ExamLevel.B) historyB = list else historyC = list
        saveHistory(result.level, list)
    }

    fun clearHistory(level: ExamLevel) {
        if (level == ExamLevel.B) historyB = emptyList() else historyC = emptyList()
        sp.edit().remove("hist:${level.name}").apply()
    }

    private fun loadHistory(level: ExamLevel): List<ExamRecord> {
        val raw = sp.getString("hist:${level.name}", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ExamRecord(
                    at = o.optLong("at"),
                    correct = o.optInt("correct"),
                    total = o.optInt("total"),
                    passed = o.optBoolean("passed"),
                    timeMs = o.optLong("timeMs")
                )
            }.take(MAX_RECORDS)
        }.getOrDefault(emptyList())
    }

    private fun saveHistory(level: ExamLevel, list: List<ExamRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("at", r.at)
                put("correct", r.correct)
                put("total", r.total)
                put("passed", r.passed)
                put("timeMs", r.timeMs)
            })
        }
        sp.edit().putString("hist:${level.name}", arr.toString()).apply()
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

    companion object {
        /** 每个级别保留的成绩记录条数 */
        const val MAX_RECORDS = 10
    }
}
