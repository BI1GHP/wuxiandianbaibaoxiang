package com.wc.cwtrainer.core

import kotlin.math.max

/**
 * 从按键通断时间自适应解码，用于发码练习的识别与评分。
 * 适用于手键、自动键以及外部音频接口检测出的通断事件。
 *
 * 修复点：
 * - flush 阈值 2.5 → 2.0 unit（更快识别字母）
 * - isDash 阈值 1.85 → 2.2 unit（更宽容，短划也能识别成 dash）
 * - 单位自适应更积极（alpha 0.18 → 0.25）
 * - commit() 在 tick 里也被触发（防止末尾字母丢失）
 */
class MorseDecoder(initialWpm: Double = 20.0) {

    var unit: Double = 1200.0 / initialWpm
        private set

    private var downAt: Long = -1L
    private var lastUpAt: Long = -1L
    private var spaceAdded = false
    private var elements = StringBuilder()
    private val out = StringBuilder()
    private val dotStats = ArrayList<Int>()

    val text: String get() = out.toString()

    val avgDotMs: Double
        get() = if (dotStats.isEmpty()) 0.0 else dotStats.sum().toDouble() / dotStats.size

    val avgDashMs: Double
        get() = if (dotStats.isEmpty()) 0.0 else avgDotMs * 3.0

    val estimatedWpm: Double get() = if (unit <= 0.0) 0.0 else 1200.0 / unit

    val isKeyDown: Boolean get() = downAt >= 0

    fun reset() {
        downAt = -1
        lastUpAt = -1
        spaceAdded = false
        elements = StringBuilder()
        out.clear()
        dotStats.clear()
    }

    fun keyDown(now: Long) {
        if (downAt >= 0) return
        downAt = now
        spaceAdded = false
    }

    fun keyUp(now: Long) {
        if (downAt < 0) return
        val dur = (now - downAt).toInt()
        downAt = -1
        lastUpAt = now
        if (dur <= 0) return
        // dash 判断：超过 2.2 个 unit 就算 dash（原来 1.85 太严）
        val isDash = dur > unit * 2.2
        elements.append(if (isDash) '-' else '.')
        if (!isDash) {
            dotStats.add(dur)
            // 更积极的自适应：保留 75% 新值（原来 18%）
            unit = unit * 0.75 + dur * 0.25
            if (unit < 15.0) unit = 15.0
            if (unit > 300.0) unit = 300.0
        } else {
            // dash 也用来校正（dash ≈ 3 * dot）
            val impliedDot = dur / 3.0
            dotStats.add(impliedDot.toInt())
            unit = unit * 0.75 + impliedDot * 0.25
        }
    }

    /** 建议每 30~50ms 调用一次 */
    fun tick(now: Long) {
        if (downAt >= 0) return
        if (lastUpAt < 0) return
        val gap = now - lastUpAt
        // 更快 flush 字母：2.0 unit（原来 2.5）
        if (elements.isNotEmpty() && gap > unit * 2.0) {
            flushLetter()
        }
        // 单词间空格：5.0 unit（原来 6.0）
        if (elements.isEmpty() && out.isNotEmpty() && !spaceAdded && gap > unit * 5.0) {
            out.append(' ')
            spaceAdded = true
        }
    }

    /** 结束一次发报，冲掉最后一个字母 */
    fun commit(now: Long = System.currentTimeMillis()) {
        if (elements.isNotEmpty()) flushLetter()
    }

    private fun flushLetter() {
        val code = elements.toString()
        elements = StringBuilder()
        if (code.isEmpty()) return
        lastUpAt = -1  // ← 关键！flush 后重置，防止重复触发
        out.append(Morse.charFor(code) ?: Morse.UNKNOWN)
    }

    fun lastUnitMs(): Double = max(unit, 1.0)
}
