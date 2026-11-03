package com.wc.cwtrainer.core

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** 国际电码表与基础工具 */
object Morse {

    const val UNKNOWN = '¿'
    const val DOT = '.'
    const val DASH = '-'

    private val table: Map<Char, String> = buildMap {
        put('A', ".-"); put('B', "-..."); put('C', "-.-."); put('D', "-.."); put('E', ".")
        put('F', "..-."); put('G', "--."); put('H', "...."); put('I', ".."); put('J', ".---")
        put('K', "-.-"); put('L', ".-.."); put('M', "--"); put('N', "-."); put('O', "---")
        put('P', ".--."); put('Q', "--.-"); put('R', ".-."); put('S', "..."); put('T', "-")
        put('U', "..-"); put('V', "...-"); put('W', ".--"); put('X', "-..-"); put('Y', "-.--")
        put('Z', "--..")

        put('0', "-----"); put('1', ".----"); put('2', "..---"); put('3', "...--")
        put('4', "....-"); put('5', "....."); put('6', "-...."); put('7', "--...")
        put('8', "---.."); put('9', "----.")

        put('/', "-..-."); put('?', "..--.."); put('.', ".-.-.-"); put(',', "--..--")
        put('=', "-...-"); put('+', ".-.-."); put('-', "-....-"); put(':', "---...")
        put('\'', ".----."); put('!', "-.-.--"); put('@', ".--.-."); put('(', "-.--.")
        put(')', "-.--.-"); put('&', ".-..."); put('_', "..--.-"); put('"', ".-..-.")
        put('$', "...-..-")
    }

    private val reverse: Map<String, Char> = table.entries.associate { it.value to it.key }

    val letters: List<Char> = ('A'..'Z').toList()
    val digits: List<Char> = (0..9).map { ('0'.code + it).toChar() }
    val punctuation: List<Char> = listOf('/', '?', '.', ',', '=', '+', '-', ':', '\'', '!', '@', '(', ')', '&', '_')

    fun code(c: Char): String? = table[c.uppercaseChar()]

    fun charFor(code: String): Char? = reverse[code]

    /** 点划的可视化：· · · — — */
    fun visual(code: String, sep: String = " "): String =
        code.map { if (it == DOT) '·' else '—' }.joinToString(sep)

    fun visualFor(c: Char): String = code(c)?.let { visual(it) } ?: ""

    /** 文本转逐字母码组，用于展示 */
    fun grouped(text: String): List<Pair<Char, String>> =
        text.filter { it != ' ' }.map { it to (code(it) ?: "") }
}

/** 发报风格（模拟不同操作员的“手法”） */
data class Fist(
    val wpm: Double = 20.0,
    /** 有效速度（Farnsworth），0 表示关闭 */
    val farnsworth: Double = 0.0,
    /** 划长（以点长为基准的倍数），标准 3.0 */
    val dashRatio: Double = 3.0,
    /** 点长权重，1.0 为标准 */
    val weight: Double = 1.0,
    /** 点划内部间隔倍数，1.0 为标准 */
    val gapRatio: Double = 1.0,
    /** 时间抖动比例 0..0.2 */
    val jitter: Double = 0.0
)

data class Seg(val on: Boolean, val ms: Int)

/** 把文本转换为时长序列（不含起始/结束静音） */
fun buildSegments(text: String, fist: Fist, random: Random = Random(0)): List<Seg> {
    val unit = 1200.0 / fist.wpm
    val effUnit = if (fist.farnsworth > 0.0 && fist.farnsworth < fist.wpm) {
        1200.0 / fist.farnsworth
    } else {
        unit
    }

    fun jittered(x: Double): Double {
        if (fist.jitter <= 0.0) return x
        val factor = 1.0 + (random.nextDouble() - 0.5) * 2.0 * fist.jitter
        return x * factor
    }

    val out = ArrayList<Seg>(text.length * 8)
    fun add(on: Boolean, ms: Double) {
        out.add(Seg(on, max(1, jittered(ms).roundToInt())))
    }

    val words = text.uppercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    words.forEachIndexed { wi, word ->
        if (wi > 0) add(false, 7.0 * effUnit)
        word.forEachIndexed { ci, raw ->
            val code = Morse.code(raw) ?: return@forEachIndexed
            code.forEachIndexed { ei, e ->
                if (ei > 0) add(false, unit * fist.gapRatio)
                add(true, if (e == Morse.DOT) unit * fist.weight else unit * fist.weight * fist.dashRatio)
            }
            if (ci < word.lastIndex) add(false, 3.0 * effUnit)
        }
    }
    return out
}

/** 文本生成器（练习内容） */
object Generators {

    const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"
    const val MIXED = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private val prefixes = listOf(
        "W", "K", "N", "AA", "JA", "JH", "DL", "DJ", "G", "M", "I", "IK",
        "EA", "EB", "UA", "R", "PY", "PP", "F", "BG", "BY", "HL", "DS",
        "VK", "VE", "SM", "SP", "PA", "ON", "OH", "LU", "CT", "9A", "YO",
        "LZ", "OK", "S5", "HA", "4X", "A6", "ZS", "VU", "9M", "HS", "DU", "BV"
    )

    fun groups(chars: String, groupCount: Int, groupSize: Int, rng: Random = Random): String =
        (1..groupCount).joinToString(" ") {
            (1..groupSize).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        }

    fun callsign(rng: Random = Random): String {
        val prefix = prefixes[rng.nextInt(prefixes.size)]
        val digit = rng.nextInt(10)
        val suffixLen = if (rng.nextBoolean()) 2 else 3
        val suffix = (1..suffixLen).map { ('A' + rng.nextInt(26)) }.joinToString("")
        return prefix + digit + suffix
    }

    fun callsigns(count: Int, rng: Random = Random): String =
        (1..count).joinToString(" ") { callsign(rng) }

    fun serial(rng: Random = Random): String = "%03d".format(rng.nextInt(1, 400))

    fun report(rng: Random = Random): String =
        listOf("599", "5NN", "589", "579", "569", "559").let { it[rng.nextInt(it.size)] }
}

/** 文本比对 */
object TextScore {

    fun normalize(text: String): String =
        text.uppercase().filterNot { it.isWhitespace() }

    fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            for (j in 0..b.length) prev[j] = cur[j]
        }
        return prev[b.length]
    }

    /** 0..1 的抄收准确率 */
    fun accuracy(target: String, answer: String): Float {
        val t = normalize(target)
        val a = normalize(answer)
        if (t.isEmpty()) return 0f
        if (a.isEmpty()) return 0f
        val dist = levenshtein(t, a)
        return (1f - dist.toFloat() / max(t.length, a.length)).coerceIn(0f, 1f)
    }

    fun correctCount(target: String, answer: String): Int {
        val t = normalize(target)
        val a = normalize(answer)
        var n = 0
        for (i in 0 until minOf(t.length, a.length)) if (t[i] == a[i]) n++
        return n
    }
}
