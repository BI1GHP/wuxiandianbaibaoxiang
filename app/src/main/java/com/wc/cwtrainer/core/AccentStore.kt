package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 各国口音关卡的最佳命中率（0~1）。
 * 呼号组合沿用语言标签作为键，单字母练习用 "<locale>#L" 区分，互不覆盖。
 */
class AccentStore(context: Context) {

    private val sp = context.getSharedPreferences("wc_accent", Context.MODE_PRIVATE)

    var best by mutableStateOf(load())
        private set

    fun bestOf(localeTag: String): Float? = best[localeTag]

    fun bestOf(localeTag: String, letterMode: Boolean): Float? =
        best[keyOf(localeTag, letterMode)]

    /** 兼容旧调用：默认呼号组合 */
    fun report(localeTag: String, rate: Float) = report(localeTag, rate, false)

    fun report(localeTag: String, rate: Float, letterMode: Boolean) {
        val key = keyOf(localeTag, letterMode)
        val old = best[key] ?: 0f
        if (rate <= old) return
        best = best + (key to rate)
        sp.edit().putFloat(key, rate).apply()
    }

    fun reload() {
        best = load()
    }

    private fun keyOf(localeTag: String, letterMode: Boolean): String =
        if (letterMode) "$localeTag#L" else localeTag

    private fun load(): Map<String, Float> =
        sp.all.mapNotNull { (k, v) -> (v as? Float)?.let { k to it } }.toMap()
}
