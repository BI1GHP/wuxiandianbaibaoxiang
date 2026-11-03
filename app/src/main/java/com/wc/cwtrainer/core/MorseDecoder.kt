package com.wc.cwtrainer.core

import kotlin.math.abs
import kotlin.math.max

/**
 * 从按键通断时间自适应解码（v4）。
 *
 * 旧版问题：每收到一个元素就立刻按“初始设置 WPM”定型，且用一个容易被噪声带偏的
 * 单位长度去判定点/划，结果外部电码只要码速和设置不一致，就全部解成 E / T。
 *
 * v4 的做法：
 * 1. 元素只记录原始时长，等到字母结束（间隔超过字母间隔）时才统一判定点/划。
 *    这样即使最初的判断不准，学到真实码速后也能回溯纠正整组元素。
 * 2. 1 unit 直接由本字母内部结构反推：
 *    - 字母内部元素间隔 = 1 unit；
 *    - 点长 = 1 unit，划长 = 3 unit；
 *    取 min(最短元素, 内部间隔中位数)，即使整组全是“划”（M、O、0）也能算对。
 * 3. 单元素字母（E、T）用“字母间隔 ≈ 3 unit”反推，仅在合理范围内采用。
 * 4. 全局码速取最近若干字母估计的中位数；与旧值偏差大时直接跳变，码速切换能立刻跟上。
 * 5. 只过滤绝对噪声（<14ms）和异常长音（>1.8s），不再用初始 WPM 过滤，
 *    因此 5～66 WPM 的电码都能匹配。
 * 6. 字母间隔 2.5 unit（元素间隙 1 unit，不会把同一个字母拆开），词间隔 6 unit。
 */
class MorseDecoder(
    initialWpm: Double = 20.0,
    /** 手键/自动键这类由 App 自己发码的场景，初始码速可信；外部收听时不可信 */
    private val trustInitialWpm: Boolean = false
) {

    private companion object {
        const val MIN_ELEMENT_MS = 14
        const val MAX_ELEMENT_MS = 1800
        const val MIN_UNIT_MS = 18.0     // 约 66 WPM
        const val MAX_UNIT_MS = 320.0    // 约 4 WPM
        const val LETTER_GAP_UNITS = 2.5
        const val WORD_GAP_UNITS = 6.0
        const val DASH_UNITS = 2.0
        const val MAX_LETTER_UNITS = 9
        const val MAX_DOT_SAMPLES = 9
    }

    private val initialUnit: Double =
        (1200.0 / initialWpm.coerceIn(4.0, 66.0)).coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)

    /** 当前估计的 1 unit 长度（ms） */
    var unit: Double = initialUnit
        private set

    private var downAt: Long = -1
    private var lastUpAt: Long = -1
    private var spaceAdded = false
    // 是否已经有可信码速（外部码速未知时，先别急着插词间空格）
    private var hasReliableUnit = trustInitialWpm

    // 当前字母的原始数据：元素时长 + 元素之间的间隔
    private val elems = ArrayList<Int>()
    private val gaps = ArrayList<Long>()
    // 最近观察到的元素间隔（含字母间隔）。最小值≈1 unit，可在码速未知时给出可靠基准
    private val recentGaps = ArrayList<Long>()

    // 最近几个字母反推出的 unit，用于平滑跟踪码速
    private val letterUnits = ArrayList<Double>()
    // 最近的点长，仅用于界面显示
    private val dotSamples = ArrayList<Int>()

    private val out = StringBuilder()

    val text: String get() = out.toString()
    val isKeyDown: Boolean get() = downAt >= 0
    val estimatedWpm: Double get() = if (unit <= 0.0) 0.0 else 1200.0 / unit
    val avgDotMs: Double
        get() = if (dotSamples.isEmpty()) 0.0 else dotSamples.sum().toDouble() / dotSamples.size
    val avgDashMs: Double get() = if (dotSamples.isEmpty()) 0.0 else avgDotMs * 3.0

    fun reset() {
        downAt = -1
        lastUpAt = -1
        spaceAdded = false
        hasReliableUnit = trustInitialWpm
        elems.clear()
        gaps.clear()
        recentGaps.clear()
        letterUnits.clear()
        dotSamples.clear()
        out.clear()
        unit = initialUnit
    }

    fun keyDown(now: Long) {
        if (downAt >= 0) return
        downAt = now

        // 用音频事件自身的时钟结算间隔（不受缓冲延迟影响）：
        // 间隔超过字母间隔 → 上一封字母结束；否则记为字母内部间隔。
        if (lastUpAt >= 0) {
            val gap = now - lastUpAt
            if (gap > 0) {
                if (elems.isNotEmpty()) {
                    if (gap > gapLimit()) {
                        flushLetter(gap)
                    } else {
                        gaps.add(gap)
                        rememberGap(gap)
                    }
                } else if (hasReliableUnit && out.isNotEmpty() && !spaceAdded &&
                    gap > unit * WORD_GAP_UNITS
                ) {
                    out.append(' ')
                    spaceAdded = true
                }
            }
        }
    }

    fun keyUp(now: Long) {
        if (downAt < 0) return
        val dur = (now - downAt).toInt()
        downAt = -1

        // 只过滤绝对噪声和异常长音；不做相对初始 WPM 的过滤，否则码速一变就全丢
        if (dur < MIN_ELEMENT_MS) return
        if (dur > MAX_ELEMENT_MS) return

        lastUpAt = now
        elems.add(dur)
        spaceAdded = false
    }

    /** 建议每 30~50ms 调用一次 */
    fun tick(now: Long) {
        if (downAt >= 0) return
        if (lastUpAt < 0) return

        val gap = now - lastUpAt
        if (elems.isNotEmpty() && gap > gapLimit()) {
            flushLetter(gap)
            return
        }
        // 词间隔：字母已结算且长时间没有新元素（码速未可信前不插空格，避免误加）
        if (elems.isEmpty() && hasReliableUnit && out.isNotEmpty() && !spaceAdded &&
            gap > unit * WORD_GAP_UNITS
        ) {
            out.append(' ')
            spaceAdded = true
        }
    }

    /** 结束一次发报/停止监听，冲掉最后一个字母 */
    fun commit(now: Long = System.currentTimeMillis()) {
        if (elems.isNotEmpty()) flushLetter(0L)
        // 丢掉“仍在按住”的那个元素，避免停止时音频线程补发的 keyUp 又塞进一个新元素
        downAt = -1
        // 避免下次启动时把这段空白当成词间隔补一个空格
        lastUpAt = -1
        spaceAdded = false
    }

    // —— 内部实现 ——

    private fun flushLetter(letterGap: Long) {
        if (elems.isEmpty()) return

        // 本字母内部可靠的单位估计（有元素间隔时）
        val durEst = durationUnitEstimate()
        // 单元素字母只能靠字母间隔粗略反推
        val gapEst = if (letterGap > 0L) letterGap / 3.0 else 0.0

        // 只有“内部结构”估计才是可信的；首字母无内部结构时才用字母间隔兜底
        val updateEst = when {
            durEst > 0.0 -> durEst
            letterUnits.isEmpty() && gapEst > 0.0 &&
                gapEst > initialUnit * 0.25 && gapEst < initialUnit * 4.0 -> gapEst
            else -> 0.0
        }
        if (updateEst > 0.0) {
            // 本字母内部结构算出的 unit 可信，之后就可以正常插词间空格
            if (durEst > 0.0) hasReliableUnit = true
            letterUnits.add(updateEst)
            if (letterUnits.size > MAX_LETTER_UNITS) letterUnits.removeAt(0)
            val med = median(letterUnits)
            unit = when {
                // 码速与当前估计差异明显：直接跳到新码速，避免被旧值锁死
                abs(med - unit) > unit * 0.30 -> med
                else -> unit * 0.65 + med * 0.35
            }.coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)
        }

        // 判定点/划时优先用本字母自己的单位，其次用全局
        val classifyUnit = when {
            durEst > 0.0 -> durEst
            letterUnits.size >= 3 -> unit
            gapEst > 0.0 -> gapEst
            else -> unit
        }.coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)

        val threshold = classifyUnit * DASH_UNITS
        val code = StringBuilder(elems.size)
        for (d in elems) {
            code.append(if (d > threshold) Morse.DASH else Morse.DOT)
            if (d <= threshold) {
                dotSamples.add(d)
                if (dotSamples.size > MAX_DOT_SAMPLES) dotSamples.removeAt(0)
            }
        }
        out.append(Morse.charFor(code.toString()) ?: Morse.UNKNOWN)

        elems.clear()
        gaps.clear()
    }

    /** 由“最短元素”和“元素内部间隔中位数”反推 1 unit */
    private fun durationUnitEstimate(): Double {
        if (elems.isEmpty()) return 0.0
        if (gaps.isEmpty()) return 0.0
        val minDur = (elems.minOrNull() ?: return 0.0).toDouble()
        val gapMed = median(gaps.map { it.toDouble() })
        return minOf(minDur, gapMed)
    }

    /**
     * 字母间隔阈值。
     * 优先级：本字母内部已测得的 unit（非常准）→ 最近元素间隔的最小值（≈1 unit）
     * → 设置里的初始码速。这样即使初始设置与实际码速差很多，第一封字母也不会被拆开。
     */
    private fun gapLimit(): Double {
        val local = durationUnitEstimate()
        val minGap = recentGaps.minOrNull()?.toDouble() ?: 0.0
        val only = if (elems.size == 1) elems[0].toDouble() else 0.0
        val base = when {
            local > 0.0 -> local
            minGap > 0.0 -> minGap
            // 只有一个元素且明显比当前码速长：更像“划”，用 d/3 估计 1 unit，
            // 避免第一封字母因设置码速偏慢而被误判成字母结束
            only > unit * 1.5 -> only / 3.0
            else -> unit
        }.coerceIn(MIN_UNIT_MS, MAX_UNIT_MS)
        return base * LETTER_GAP_UNITS
    }

    private fun rememberGap(gap: Long) {
        if (gap <= 0) return
        recentGaps.add(gap)
        if (recentGaps.size > 10) recentGaps.removeAt(0)
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        return s[s.size / 2]
    }

    fun lastUnitMs(): Double = max(unit, 1.0)
}
