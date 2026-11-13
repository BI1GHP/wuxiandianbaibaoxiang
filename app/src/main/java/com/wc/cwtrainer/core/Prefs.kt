package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class KeyMode { STRAIGHT, PADDLE }
enum class InputMode { SCREEN, AUDIO }

/** 外观主题 / 皮肤 */
enum class ThemeMode { FIELD, CREAM, OLIVE, SAND, OCEAN }

data class SettingsData(
    val wpm: Int = 20,
    val farnsworth: Int = 0,
    val toneHz: Int = 700,
    val volume: Float = 0.85f,
    val keyMode: KeyMode = KeyMode.STRAIGHT,
    val iambicB: Boolean = true,
    val inputSource: InputMode = InputMode.SCREEN,
    val micSensitivity: Float = 0.6f,
    val extToneHz: Int = 700,
    val noiseLevel: Float = 0.08f,
    val ttsRate: Float = 1.0f,
    // 各板块独立码速
    val copyWpm: Int = 20,
    val learnWpm: Int = 20,
    val contestWpm: Int = 25,
    val contestMode: ContestMode = ContestMode.CW,
    val translatorWpm: Int = 20,
    // 赛事听抄：独立底噪通道电平、SSB 标准解释法（NATO）开关
    val contestNoise: Float = 0.12f,
    val ssbStandard: Boolean = false,
    // 外观主题
    val theme: ThemeMode = ThemeMode.FIELD,
    // 字号倍率：1.0 标准 / 1.15 大 / 1.3 特大
    val fontScale: Float = 1f,
    // 界面音效（继电器咔嗒 / 旋钮嘀声），可关闭
    val uiSound: Boolean = true,
    // 已解锁皮肤（ThemeMode.name）
    val unlockedSkins: Set<String> = setOf("FIELD", "CREAM"),
    // 皮肤商店已花费的训练积分（总积分由练习数据实时计算）
    val spentPoints: Int = 0,
    // 呼号牌：呼号、操作证等级（A/B/C/NONE）、个人格言
    val callsign: String = "BI1GHP",
    val opClass: String = "A",
    val motto: String = "73",
    // 首启引导是否已完成
    val onboarded: Boolean = false
)

/** 轻量设置存储，Compose 状态可直接读取 */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("wc_trainer", Context.MODE_PRIVATE)

    var data by mutableStateOf(load())
        private set

    /** 最近一次进入的训练科目（AppScreen.name），用于首页「继续上次训练」 */
    var lastScreen by mutableStateOf(sp.getString("lastScreen", null))
        private set

    fun recordScreen(name: String) {
        if (name == lastScreen) return
        lastScreen = name
        sp.edit().putString("lastScreen", name).apply()
    }

    /** 导入备份后从 SharedPreferences 重新读入内存状态 */
    fun reload() {
        data = load()
        lastScreen = sp.getString("lastScreen", null)
    }

    private fun load(): SettingsData = SettingsData(
        wpm = sp.getInt("wpm", 20),
        farnsworth = sp.getInt("farnsworth", 0),
        toneHz = sp.getInt("toneHz", 700),
        volume = sp.getFloat("volume", 0.85f),
        keyMode = runCatching { KeyMode.valueOf(sp.getString("keyMode", "STRAIGHT")!!) }
            .getOrDefault(KeyMode.STRAIGHT),
        iambicB = sp.getBoolean("iambicB", true),
        inputSource = runCatching { InputMode.valueOf(sp.getString("inputSource", "SCREEN")!!) }
            .getOrDefault(InputMode.SCREEN),
        micSensitivity = sp.getFloat("micSensitivity", 0.6f),
        extToneHz = sp.getInt("extToneHz", 700),
        noiseLevel = sp.getFloat("noiseLevel", 0.08f),
        ttsRate = sp.getFloat("ttsRate", 1.0f),
        copyWpm = sp.getInt("copyWpm", 20),
        learnWpm = sp.getInt("learnWpm", 20),
        contestWpm = sp.getInt("contestWpm", 25),
        contestMode = runCatching { ContestMode.valueOf(sp.getString("contestMode", "CW")!!) }
            .getOrDefault(ContestMode.CW),
        translatorWpm = sp.getInt("translatorWpm", 20),
        contestNoise = sp.getFloat("contestNoise", 0.12f),
        ssbStandard = sp.getBoolean("ssbStandard", false),
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", "FIELD")!!) }
            .getOrDefault(ThemeMode.FIELD),
        fontScale = sp.getFloat("fontScale", 1f).coerceIn(1f, 1.4f),
        uiSound = sp.getBoolean("uiSound", true),
        unlockedSkins = sp.getStringSet("unlockedSkins", setOf("FIELD", "CREAM"))
            ?.toSet() ?: setOf("FIELD", "CREAM"),
        spentPoints = sp.getInt("spentPoints", 0),
        callsign = sp.getString("callsign", "BI1GHP") ?: "BI1GHP",
        opClass = sp.getString("opClass", "A") ?: "A",
        motto = sp.getString("motto", "73") ?: "73",
        onboarded = sp.getBoolean("onboarded", false)
    )

    fun update(block: (SettingsData) -> SettingsData) {
        val next = block(data)
        data = next
        UiSounds.enabled = next.uiSound
        sp.edit()
            .putInt("wpm", next.wpm)
            .putInt("farnsworth", next.farnsworth)
            .putInt("toneHz", next.toneHz)
            .putFloat("volume", next.volume)
            .putString("keyMode", next.keyMode.name)
            .putBoolean("iambicB", next.iambicB)
            .putString("inputSource", next.inputSource.name)
            .putFloat("micSensitivity", next.micSensitivity)
            .putInt("extToneHz", next.extToneHz)
            .putFloat("noiseLevel", next.noiseLevel)
            .putFloat("ttsRate", next.ttsRate)
            .putInt("copyWpm", next.copyWpm)
            .putInt("learnWpm", next.learnWpm)
            .putInt("contestWpm", next.contestWpm)
            .putString("contestMode", next.contestMode.name)
            .putInt("translatorWpm", next.translatorWpm)
            .putFloat("contestNoise", next.contestNoise)
            .putBoolean("ssbStandard", next.ssbStandard)
            .putString("theme", next.theme.name)
            .putFloat("fontScale", next.fontScale)
            .putBoolean("uiSound", next.uiSound)
            .putStringSet("unlockedSkins", next.unlockedSkins)
            .putInt("spentPoints", next.spentPoints)
            .putString("callsign", next.callsign)
            .putString("opClass", next.opClass)
            .putString("motto", next.motto)
            .putBoolean("onboarded", next.onboarded)
            .apply()
    }
}

/** 单日训练记录（训练历史 / 7 日曲线） */
data class DayStat(
    val date: String,
    val learnCount: Int = 0,
    val learnCorrect: Int = 0,
    val copyCount: Int = 0,
    val copyBest: Float = 0f,
    val contestCount: Int = 0,
    val contestBest: Int = 0,
    val accentCount: Int = 0
) {
    val hasActivity: Boolean get() = learnCount + copyCount + contestCount + accentCount > 0
    val totalCount: Int get() = learnCount + copyCount + contestCount + accentCount
}

/** 练习统计 */
class StatsStore(context: Context) {

    private val sp = context.getSharedPreferences("wc_stats", Context.MODE_PRIVATE)

    var learnCorrect by mutableStateOf(sp.getInt("learnCorrect", 0))
        private set
    var learnTotal by mutableStateOf(sp.getInt("learnTotal", 0))
        private set
    var copyBest by mutableStateOf(sp.getFloat("copyBest", 0f))
        private set
    var contestBest by mutableStateOf(sp.getInt("contestBest", 0))
        private set

    /** 最近 30 天训练历史，按日期升序 */
    var history by mutableStateOf(loadHistory())
        private set

    /** 单字符掌握度：字符 -> (答对次数, 出现次数) */
    var charStats by mutableStateOf(loadCharStats())
        private set

    fun addLearn(ok: Boolean, ch: Char? = null) {
        learnTotal += 1
        if (ok) learnCorrect += 1
        sp.edit().putInt("learnTotal", learnTotal).putInt("learnCorrect", learnCorrect).apply()
        updateToday { it.copy(learnCount = it.learnCount + 1, learnCorrect = it.learnCorrect + if (ok) 1 else 0) }
        ch?.let { recordChar(it, ok) }
    }

    fun reportCopy(accuracy: Float, target: String? = null, answer: String? = null) {
        if (accuracy > copyBest) {
            copyBest = accuracy
            sp.edit().putFloat("copyBest", copyBest).apply()
        }
        updateToday {
            it.copy(copyCount = it.copyCount + 1, copyBest = maxOf(it.copyBest, accuracy))
        }
        if (target != null) {
            val t = target.uppercase().replace(" ", "")
            val a = (answer ?: "").uppercase().replace(" ", "")
            for (i in t.indices) {
                recordChar(t[i], a.getOrNull(i) == t[i])
            }
        }
    }

    fun reportContest(score: Int) {
        if (score > contestBest) {
            contestBest = score
            sp.edit().putInt("contestBest", contestBest).apply()
        }
        updateToday { it.copy(contestCount = it.contestCount + 1, contestBest = maxOf(it.contestBest, score)) }
    }

    /** 口音适应完成一轮 */
    fun reportAccent() {
        updateToday { it.copy(accentCount = it.accentCount + 1) }
    }

    /** 连续训练天数：今天还没练时从昨天往前算 */
    fun streakDays(): Int {
        val byDate = history.associateBy { it.date }
        var day = LocalDate.now()
        if (byDate[day.toString()]?.hasActivity != true) day = day.minusDays(1)
        var n = 0
        while (true) {
            val d = byDate[day.toString()] ?: break
            if (!d.hasActivity) break
            n++
            day = day.minusDays(1)
        }
        return n
    }

    /** 最近 7 天（含无训练的空白日），按日期升序 */
    fun last7Days(): List<DayStat> {
        val byDate = history.associateBy { it.date }
        return (6 downTo 0).map { offset ->
            val d = LocalDate.now().minusDays(offset.toLong()).toString()
            byDate[d] ?: DayStat(d)
        }
    }

    /** 最容易混淆的字符：按听错次数降序 */
    fun topConfused(limit: Int = 5): List<Pair<Char, Int>> =
        charStats
            .mapValues { (_, v) -> v.second - v.first }
            .filter { it.value > 0 }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }

    private fun recordChar(c: Char, ok: Boolean) {
        val key = c.uppercaseChar()
        val prev = charStats[key] ?: (0 to 0)
        val next = (prev.first + if (ok) 1 else 0) to (prev.second + 1)
        charStats = charStats + (key to next)
        saveCharStats()
    }

    private fun updateToday(block: (DayStat) -> DayStat) {
        val today = LocalDate.now().toString()
        val list = history.toMutableList()
        val idx = list.indexOfFirst { it.date == today }
        if (idx >= 0) {
            list[idx] = block(list[idx])
        } else {
            list.add(block(DayStat(today)))
        }
        history = list.sortedBy { it.date }.takeLast(30)
        saveHistory()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.forEach { d ->
            arr.put(
                JSONObject()
                    .put("date", d.date)
                    .put("learnCount", d.learnCount)
                    .put("learnCorrect", d.learnCorrect)
                    .put("copyCount", d.copyCount)
                    .put("copyBest", d.copyBest.toDouble())
                    .put("contestCount", d.contestCount)
                    .put("contestBest", d.contestBest)
                    .put("accentCount", d.accentCount)
            )
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<DayStat> {
        val raw = sp.getString("history", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DayStat(
                    date = o.getString("date"),
                    learnCount = o.optInt("learnCount"),
                    learnCorrect = o.optInt("learnCorrect"),
                    copyCount = o.optInt("copyCount"),
                    copyBest = o.optDouble("copyBest", 0.0).toFloat(),
                    contestCount = o.optInt("contestCount"),
                    contestBest = o.optInt("contestBest"),
                    accentCount = o.optInt("accentCount")
                )
            }.sortedBy { it.date }.takeLast(30)
        }.getOrDefault(emptyList())
    }

    private fun saveCharStats() {
        val obj = JSONObject()
        charStats.forEach { (c, v) ->
            obj.put(c.toString(), JSONArray().put(v.first).put(v.second))
        }
        sp.edit().putString("charStats", obj.toString()).apply()
    }

    private fun loadCharStats(): Map<Char, Pair<Int, Int>> {
        val raw = sp.getString("charStats", null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associate { key ->
                val arr = obj.getJSONArray(key)
                key.first() to (arr.getInt(0) to arr.getInt(1))
            }
        }.getOrDefault(emptyMap())
    }

    /** 导入备份后从 SharedPreferences 重新读入内存状态 */
    fun reload() {
        learnCorrect = sp.getInt("learnCorrect", 0)
        learnTotal = sp.getInt("learnTotal", 0)
        copyBest = sp.getFloat("copyBest", 0f)
        contestBest = sp.getInt("contestBest", 0)
        history = loadHistory()
        charStats = loadCharStats()
    }
}
