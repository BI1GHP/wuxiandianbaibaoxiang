package com.wc.cwtrainer.core

import android.content.Context
import com.wc.cwtrainer.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地数据备份 / 恢复：把设置、练习统计、错题本与考试记录整体导出为 JSON，
 * 换机或重装后用同一文件导入即可恢复。不带账号，全部读写都在本机完成。
 */
object Backup {

    /** 参与备份的 SharedPreferences 文件 */
    private val PREFS = listOf("wc_trainer", "wc_stats", "wc_exam", "wc_srs", "wc_contest", "wc_accent")

    fun export(context: Context): String {
        val root = JSONObject()
        root.put("app", "CW练考通")
        root.put("version", BuildConfig.VERSION_NAME)
        root.put("exportedAt", System.currentTimeMillis())
        val all = JSONObject()
        PREFS.forEach { name ->
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val one = JSONObject()
            sp.all.forEach { (key, value) -> one.put(key, encode(value)) }
            all.put(name, one)
        }
        root.put("prefs", all)
        return root.toString(2)
    }

    /**
     * 用备份文件覆盖本地数据，返回导入的条目数。
     * 文件不是本 App 导出的格式时抛 [IllegalArgumentException]。
     */
    fun import(context: Context, json: String): Int {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("文件内容不是有效的 JSON") }
        val prefs = root.optJSONObject("prefs")
            ?: throw IllegalArgumentException("不是 CW练考通 的备份文件")

        var count = 0
        PREFS.forEach { name ->
            val one = prefs.optJSONObject(name) ?: return@forEach
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
            val keys = one.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = one.optJSONObject(key) ?: continue
                when (item.optString("t")) {
                    "b" -> editor.putBoolean(key, item.optBoolean("v"))
                    "i" -> editor.putInt(key, item.optInt("v"))
                    "l" -> editor.putLong(key, item.optLong("v"))
                    "f" -> editor.putFloat(key, item.optDouble("v").toFloat())
                    "ss" -> {
                        val arr = item.optJSONArray("v") ?: JSONArray()
                        val set = LinkedHashSet<String>()
                        for (i in 0 until arr.length()) set.add(arr.optString(i))
                        editor.putStringSet(key, set)
                    }
                    else -> editor.putString(key, item.optString("v"))
                }
                count++
            }
            // 同步落盘，保证导入后立刻读取到新值
            editor.commit()
        }
        if (count == 0) throw IllegalArgumentException("备份文件里没有可恢复的数据")
        return count
    }

    /** 带类型的 JSON，避免导入后 Int/Long/Float 互相错位 */
    private fun encode(value: Any?): JSONObject = JSONObject().apply {
        when (value) {
            is Boolean -> { put("t", "b"); put("v", value) }
            is Int -> { put("t", "i"); put("v", value) }
            is Long -> { put("t", "l"); put("v", value) }
            is Float -> { put("t", "f"); put("v", value.toDouble()) }
            is Set<*> -> {
                put("t", "ss")
                put("v", JSONArray(value.map { it?.toString() ?: "" }))
            }
            else -> { put("t", "s"); put("v", value?.toString() ?: "") }
        }
    }
}
