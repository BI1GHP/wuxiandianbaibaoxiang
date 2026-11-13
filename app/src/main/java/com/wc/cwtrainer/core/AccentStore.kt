package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 各国口音关卡的最佳命中率（0~1），按语言标签存储 */
class AccentStore(context: Context) {

    private val sp = context.getSharedPreferences("wc_accent", Context.MODE_PRIVATE)

    var best by mutableStateOf(load())
        private set

    fun bestOf(localeTag: String): Float? = best[localeTag]

    fun report(localeTag: String, rate: Float) {
        val old = best[localeTag] ?: 0f
        if (rate <= old) return
        best = best + (localeTag to rate)
        sp.edit().putFloat(localeTag, rate).apply()
    }

    fun reload() {
        best = load()
    }

    private fun load(): Map<String, Float> =
        sp.all.mapNotNull { (k, v) -> (v as? Float)?.let { k to it } }.toMap()
}
