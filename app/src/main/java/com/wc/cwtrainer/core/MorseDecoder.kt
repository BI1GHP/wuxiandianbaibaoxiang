package com.wc.cwtrainer.core

import kotlin.math.max

/**
 * 从按键通断时间自适应解码，用于发码练习的识别与评分。
 * 适用于手键、自动键以及外部音频接口检测出的通断事件。
 */
class MorseDecoder(initialWpm: Double = 20.0) {

    var unit: Double = 1200.0 / initialWpm
        private set

    private var downAt: Long = -1L
    private var lastUpAt: Long = -1L
    private var letterEndAt: Long = -1L
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
        letterEndAt = -1
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
        val isDash = dur > unit * 1.85
        elements.append(if (isDash) '-' else '.')
        if (!isDash) {
            dotStats.add(dur)
            // 自适应点长（保留初值权重，避免过快收敛）
            unit = unit * 0.82 + dur * 0.18
            if (unit < 18.0) unit = 18.0
        }
    }

    /** 建议每 30~50ms 调用一次 */
    fun tick(now: Long) {
        if (downAt >= 0) return
        if (lastUpAt < 0) return
        val gap = now - lastUpAt
        if (elements.isNotEmpty() && gap > unit * 2.5) {
            flushLetter()
        }
        if (elements.isEmpty() && out.isNotEmpty() && !spaceAdded && gap > unit * 6.0) {
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
        letterEndAt = lastUpAt
        out.append(Morse.charFor(code) ?: Morse.UNKNOWN)
    }

    fun lastUnitMs(): Double = max(unit, 1.0)
}
