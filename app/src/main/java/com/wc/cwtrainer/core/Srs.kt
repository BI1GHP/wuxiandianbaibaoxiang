package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 单个字符的复习状态：等级越高间隔越长 */
data class SrsItem(
    val stage: Int = 0,
    val dueDay: Long = 0L,
    val seen: Int = 0,
    val correct: Int = 0
)

/**
 * 背码间隔重复（Leitner）。
 *
 * 答对升一级，下次复习间隔按 [INTERVALS] 递增；答错回到第 0 级并留在今日队列，
 * 直到答对为止。仅覆盖字母与数字，与背码训练的出题范围一致。
 */
class SrsStore(context: Context) {

    private val sp = context.getSharedPreferences("wc_srs", Context.MODE_PRIVATE)

    var items by mutableStateOf(load())
        private set

    private val pool: List<Char> get() = Morse.letters + Morse.digits

    fun itemOf(c: Char): SrsItem = items[c] ?: SrsItem()

    /** 今日待复习：到期日不晚于今天的字符，先低等级后高等级 */
    fun dueChars(): List<Char> {
        val today = LocalDate.now().toEpochDay()
        return pool
            .filter { itemOf(it).dueDay <= today }
            .sortedWith(compareBy({ itemOf(it).stage }, { itemOf(it).dueDay }))
    }

    fun dueCount(): Int = dueChars().size

    fun record(c: Char, ok: Boolean) {
        val cur = itemOf(c)
        val today = LocalDate.now().toEpochDay()
        val next = if (ok) {
            val stage = (cur.stage + 1).coerceAtMost(INTERVALS.size - 1)
            SrsItem(
                stage = stage,
                dueDay = today + INTERVALS[stage],
                seen = cur.seen + 1,
                correct = cur.correct + 1
            )
        } else {
            SrsItem(stage = 0, dueDay = today, seen = cur.seen + 1, correct = cur.correct)
        }
        items = items + (c to next)
        save()
    }

    fun reload() {
        items = load()
    }

    private fun save() {
        val obj = JSONObject()
        items.forEach { (c, v) ->
            obj.put(
                c.toString(),
                JSONArray().put(v.stage).put(v.dueDay).put(v.seen).put(v.correct)
            )
        }
        sp.edit().putString("items", obj.toString()).apply()
    }

    private fun load(): Map<Char, SrsItem> {
        val raw = sp.getString("items", null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associate { key ->
                val arr = obj.getJSONArray(key)
                key.first() to SrsItem(
                    stage = arr.getInt(0),
                    dueDay = arr.getLong(1),
                    seen = arr.getInt(2),
                    correct = arr.getInt(3)
                )
            }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        /** 第 n 级之后的下次复习间隔（天） */
        val INTERVALS = longArrayOf(0, 1, 2, 4, 7, 15, 30)
    }
}
