package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 一条抄收记录（模拟赛 QSO） */
data class ContestQso(
    val timeMs: Long,
    val call: String,
    val country: String,
    val mode: String,
    val serial: String,
    val report: String,
    val callOk: Boolean,
    val reportOk: Boolean,
    val serialOk: Boolean
) {
    val score: Int get() = (if (callOk) 1 else 0) + (if (reportOk) 1 else 0) + (if (serialOk) 1 else 0)
}

/** 一次复盘统计 */
data class ContestReview(
    val total: Int,
    val callRate: Float,
    val serialRate: Float,
    val avgScore: Float
)

/**
 * 赛事抄收日志：保存每条 QSO，支持复盘统计与 ADIF 导出。
 * 数据存在独立 SharedPreferences 中，并纳入本地备份。
 */
class ContestLogStore(context: Context) {

    private val sp = context.getSharedPreferences("wc_contest", Context.MODE_PRIVATE)

    var items by mutableStateOf(load())
        private set

    fun add(qso: ContestQso) {
        items = (items + qso).takeLast(500)
        save()
    }

    fun clear() {
        items = emptyList()
        sp.edit().clear().apply()
    }

    fun review(sinceMs: Long = 0L): ContestReview {
        val list = items.filter { it.timeMs >= sinceMs }
        if (list.isEmpty()) return ContestReview(0, 0f, 0f, 0f)
        return ContestReview(
            total = list.size,
            callRate = list.count { it.callOk }.toFloat() / list.size,
            serialRate = list.count { it.serialOk }.toFloat() / list.size,
            avgScore = list.sumOf { it.score }.toFloat() / list.size
        )
    }

    fun reload() {
        items = load()
    }

    /** 生成 ADIF 3.1.4 文本；字段长度按 UTF-8 字节数计算 */
    fun toAdif(operatorCall: String, sinceMs: Long = 0L): String {
        val sb = StringBuilder()
        sb.append("ADIF export from CW练考通\n")
        sb.append(field("ADIF_VER", "3.1.4")).append('\n')
        sb.append(field("PROGRAMID", "CW TRAINER")).append('\n')
        sb.append("<EOH>\n\n")
        val df = DateTimeFormatter.ofPattern("yyyyMMdd")
        val tf = DateTimeFormatter.ofPattern("HHmmss")
        items.filter { it.timeMs >= sinceMs }.forEachIndexed { index, q ->
            val dt = Instant.ofEpochMilli(q.timeMs).atZone(ZoneId.systemDefault())
            sb.append(field("QSO_DATE", dt.format(df))).append(' ')
            sb.append(field("TIME_ON", dt.format(tf))).append(' ')
            sb.append(field("CALL", q.call)).append(' ')
            sb.append(field("BAND", "40M")).append(' ')
            sb.append(field("MODE", q.mode)).append(' ')
            sb.append(field("RST_SENT", "599")).append(' ')
            sb.append(field("RST_RCVD", q.report)).append(' ')
            sb.append(field("STX", "%03d".format(index + 1))).append(' ')
            sb.append(field("SRX", q.serial)).append(' ')
            if (operatorCall.isNotBlank()) sb.append(field("OPERATOR", operatorCall)).append(' ')
            sb.append("<EOR>\n")
        }
        return sb.toString()
    }

    private fun field(tag: String, value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8).size
        return "<$tag:$bytes>$value"
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { q ->
            arr.put(
                JSONObject()
                    .put("timeMs", q.timeMs)
                    .put("call", q.call)
                    .put("country", q.country)
                    .put("mode", q.mode)
                    .put("serial", q.serial)
                    .put("report", q.report)
                    .put("callOk", q.callOk)
                    .put("reportOk", q.reportOk)
                    .put("serialOk", q.serialOk)
            )
        }
        sp.edit().putString("items", arr.toString()).apply()
    }

    private fun load(): List<ContestQso> {
        val raw = sp.getString("items", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ContestQso(
                    timeMs = o.getLong("timeMs"),
                    call = o.getString("call"),
                    country = o.optString("country"),
                    mode = o.optString("mode", "CW"),
                    serial = o.optString("serial"),
                    report = o.optString("report", "599"),
                    callOk = o.getBoolean("callOk"),
                    reportOk = o.getBoolean("reportOk"),
                    serialOk = o.getBoolean("serialOk")
                )
            }
        }.getOrDefault(emptyList())
    }
}
