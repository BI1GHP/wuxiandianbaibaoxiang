package com.wc.cwtrainer.core

import android.content.Context
import org.json.JSONObject

/** 官方题库（2025 年 B/C 类），从 assets/question_bank_2025.json 加载 */
object QuestionBank {

    private val cache = mutableMapOf<ExamLevel, List<Question>>()
    private val sectionIndex = mutableMapOf<ExamLevel, List<String>>()

    /** 必须先调用一次（AppState 初始化时） */
    fun ensure(context: Context) {
        if (cache.isNotEmpty()) return
        val json = context.assets.open("question_bank_2025.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        for (level in ExamLevel.entries) {
            val arr = root.getJSONArray(level.name)
            val list = ArrayList<Question>(arr.length())
            val secs = linkedSetOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val optsArr = o.getJSONArray("opts")
                val ansArr = o.getJSONArray("ans")
                val sec = o.getString("sec")
                secs.add(sec)
                list += Question(
                    id = o.getString("id"),
                    level = level,
                    sec = sec,
                    isMulti = o.getString("type") == "M",
                    stem = o.getString("stem"),
                    options = List(optsArr.length()) { optsArr.getString(it) },
                    ans = List(ansArr.length()) { ansArr.getInt(it) }
                )
            }
            cache[level] = list
            sectionIndex[level] = secs.sorted()
        }
    }

    fun questionsFor(level: ExamLevel): List<Question> = cache[level] ?: emptyList()

    /** 该级别所有小节编号（升序） */
    fun sections(level: ExamLevel): List<String> = sectionIndex[level] ?: emptyList()

    fun countBySection(level: ExamLevel, sec: String): Int =
        questionsFor(level).count { it.sec == sec }

    /** 按知识点小节取题 */
    fun bySection(level: ExamLevel, sec: String): List<Question> =
        questionsFor(level).filter { it.sec == sec }

    /** 按题型取题（多选/单选） */
    fun byType(level: ExamLevel, multi: Boolean): List<Question> =
        questionsFor(level).filter { it.isMulti == multi }
}
