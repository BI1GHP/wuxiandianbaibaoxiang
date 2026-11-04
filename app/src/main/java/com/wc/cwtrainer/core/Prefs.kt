package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class KeyMode { STRAIGHT, PADDLE }
enum class InputMode { SCREEN, AUDIO }

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
    val translatorWpm: Int = 20
)

/** 轻量设置存储，Compose 状态可直接读取 */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("wc_trainer", Context.MODE_PRIVATE)

    var data by mutableStateOf(load())
        private set

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
        translatorWpm = sp.getInt("translatorWpm", 20)
    )

    fun update(block: (SettingsData) -> SettingsData) {
        val next = block(data)
        data = next
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
            .putInt("translatorWpm", next.translatorWpm)
            .apply()
    }
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

    fun addLearn(ok: Boolean) {
        learnTotal += 1
        if (ok) learnCorrect += 1
        sp.edit().putInt("learnTotal", learnTotal).putInt("learnCorrect", learnCorrect).apply()
    }

    fun reportCopy(accuracy: Float) {
        if (accuracy > copyBest) {
            copyBest = accuracy
            sp.edit().putFloat("copyBest", copyBest).apply()
        }
    }

    fun reportContest(score: Int) {
        if (score > contestBest) {
            contestBest = score
            sp.edit().putInt("contestBest", contestBest).apply()
        }
    }
}
