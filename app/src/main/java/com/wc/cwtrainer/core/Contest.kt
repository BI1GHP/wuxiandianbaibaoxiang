package com.wc.cwtrainer.core

import kotlin.random.Random

enum class ContestMode { SSB, CW }

enum class PhoneticStyle { NATO, GERMAN, ITALIAN, SPANISH, JAPANESE }

data class ContestStation(
    val country: String,
    val accent: String,
    val call: String,
    val localeTag: String,
    val style: PhoneticStyle,
    val rate: Float,
    val pitch: Float,
    val habit: String,
    val filler: String,
    val report: String,
    val serial: String,
    val cwFist: Fist
)

data class ContestCheck(
    val callOk: Boolean,
    val reportOk: Boolean,
    val serialOk: Boolean
) {
    val score: Int get() = (if (callOk) 1 else 0) + (if (reportOk) 1 else 0) + (if (serialOk) 1 else 0)
}

object ContestGen {

    private data class Country(
        val name: String,
        val prefixes: List<String>,
        val localeTag: String,
        val style: PhoneticStyle,
        val accent: String
    )

    private val countries = listOf(
        Country("美国", listOf("W", "K", "N", "AA", "AB", "AC"), "en-US", PhoneticStyle.NATO, "美式英语"),
        Country("英国", listOf("G", "M", "2E", "2M"), "en-GB", PhoneticStyle.NATO, "英式英语"),
        Country("德国", listOf("DL", "DJ", "DK", "DA", "DB"), "de-DE", PhoneticStyle.GERMAN, "德语口音 · 德式字母解释法"),
        Country("日本", listOf("JA", "JH", "JE", "JR", "JG"), "ja-JP", PhoneticStyle.JAPANESE, "日式英语 · 和制读音"),
        Country("意大利", listOf("I", "IK", "IZ", "IU"), "it-IT", PhoneticStyle.ITALIAN, "意大利语腔 · 意式字母解释法"),
        Country("西班牙", listOf("EA", "EB", "EC", "ED"), "es-ES", PhoneticStyle.SPANISH, "西班牙语腔 · 西式字母解释法"),
        Country("法国", listOf("F", "TM", "F5"), "fr-FR", PhoneticStyle.NATO, "法语腔"),
        Country("俄罗斯", listOf("R", "UA", "RA", "RV", "RW"), "ru-RU", PhoneticStyle.NATO, "俄语腔"),
        Country("巴西", listOf("PY", "PP", "PT", "PU"), "pt-BR", PhoneticStyle.NATO, "葡萄牙语腔"),
        Country("中国", listOf("BG", "BY", "BD", "BA"), "zh-CN", PhoneticStyle.NATO, "中式英语腔"),
        Country("韩国", listOf("HL", "DS", "DT", "6K"), "ko-KR", PhoneticStyle.NATO, "韩式英语腔"),
        Country("澳大利亚", listOf("VK", "VK2", "VK4"), "en-AU", PhoneticStyle.NATO, "澳洲英语"),
        Country("瑞典", listOf("SM", "SA", "SB", "SC"), "sv-SE", PhoneticStyle.NATO, "北欧腔"),
        Country("波兰", listOf("SP", "SN", "SO", "SQ"), "pl-PL", PhoneticStyle.NATO, "波兰腔"),
        Country("荷兰", listOf("PA", "PB", "PD", "PE"), "nl-NL", PhoneticStyle.NATO, "荷兰腔")
    )

    private val habits = listOf(
        "报呼号时习惯连读两遍",
        "序号前常加一声 \"ah\"",
        "句尾习惯带 roger",
        "语速偏快，尾音上扬",
        "先报信号报告，再报序号",
        "习惯把 five nine 说成 five by nine",
        "口音较重，报呼号时尾音拖长",
        "习惯重复 QRZ contest",
        "报序号时会稍作停顿",
        "喜欢在开头加一声 \"hi\""
    )

    private val fillers = listOf("", "", "", "ah", "hi", "okay")

    private val closings = listOf(
        "QSL, good luck.",
        "Roger, QRZ contest.",
        "Thanks, seventy three.",
        "Please copy.",
        "Back to you.",
        "Good luck in the contest."
    )

    fun station(rng: Random = Random): ContestStation {
        val c = countries[rng.nextInt(countries.size)]
        val prefix = c.prefixes[rng.nextInt(c.prefixes.size)]
        val digit = rng.nextInt(10)
        val suffixLen = if (rng.nextBoolean()) 2 else 3
        val suffix = (1..suffixLen).map { ('A' + rng.nextInt(26)) }.joinToString("")
        val call = prefix + digit + suffix
        return ContestStation(
            country = c.name,
            accent = c.accent,
            call = call,
            localeTag = c.localeTag,
            style = c.style,
            rate = 0.85f + rng.nextFloat() * 0.35f,
            pitch = 0.85f + rng.nextFloat() * 0.35f,
            habit = habits[rng.nextInt(habits.size)],
            filler = fillers[rng.nextInt(fillers.size)],
            report = if (rng.nextInt(3) == 0) "5NN" else "599",
            serial = Generators.serial(rng),
            cwFist = Fist(
                wpm = 18.0 + rng.nextInt(15),
                dashRatio = 2.7 + rng.nextDouble() * 0.8,
                weight = 0.9 + rng.nextDouble() * 0.25,
                gapRatio = 0.9 + rng.nextDouble() * 0.3,
                jitter = 0.02 + rng.nextDouble() * 0.08
            )
        )
    }

    private val nato = mapOf(
        'A' to "Alfa", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta", 'E' to "Echo",
        'F' to "Foxtrot", 'G' to "Golf", 'H' to "Hotel", 'I' to "India", 'J' to "Juliett",
        'K' to "Kilo", 'L' to "Lima", 'M' to "Mike", 'N' to "November", 'O' to "Oscar",
        'P' to "Papa", 'Q' to "Quebec", 'R' to "Romeo", 'S' to "Sierra", 'T' to "Tango",
        'U' to "Uniform", 'V' to "Victor", 'W' to "Whiskey", 'X' to "X-ray", 'Y' to "Yankee",
        'Z' to "Zulu"
    )

    private val german = mapOf(
        'A' to "Anton", 'B' to "Berta", 'C' to "Cäsar", 'D' to "Dora", 'E' to "Emil",
        'F' to "Friedrich", 'G' to "Gustav", 'H' to "Heinrich", 'I' to "Ida", 'J' to "Julius",
        'K' to "Kaufmann", 'L' to "Ludwig", 'M' to "Martha", 'N' to "Nordpol", 'O' to "Otto",
        'P' to "Paula", 'Q' to "Quelle", 'R' to "Richard", 'S' to "Samuel", 'T' to "Theodor",
        'U' to "Ulrich", 'V' to "Viktor", 'W' to "Wilhelm", 'X' to "Xanthippe", 'Y' to "Ypsilon",
        'Z' to "Zacharias"
    )

    private val italian = mapOf(
        'A' to "Ancona", 'B' to "Bologna", 'C' to "Como", 'D' to "Domodossola", 'E' to "Empoli",
        'F' to "Firenze", 'G' to "Genova", 'H' to "Hotel", 'I' to "Imola", 'J' to "Jesolo",
        'K' to "Kappa", 'L' to "Livorno", 'M' to "Milano", 'N' to "Napoli", 'O' to "Otranto",
        'P' to "Padova", 'Q' to "Quarto", 'R' to "Roma", 'S' to "Savona", 'T' to "Torino",
        'U' to "Udine", 'V' to "Venezia", 'W' to "Washington", 'X' to "Xanto", 'Y' to "York",
        'Z' to "Zurigo"
    )

    private val spanish = mapOf(
        'A' to "Antonio", 'B' to "Barcelona", 'C' to "Carmen", 'D' to "Dolores", 'E' to "Enrique",
        'F' to "Francia", 'G' to "Granada", 'H' to "Hotel", 'I' to "India", 'J' to "José",
        'K' to "Kilo", 'L' to "Lorenzo", 'M' to "Madrid", 'N' to "Navarra", 'O' to "Oviedo",
        'P' to "Paris", 'Q' to "Querido", 'R' to "Roma", 'S' to "Sevilla", 'T' to "Toledo",
        'U' to "Ulises", 'V' to "Valencia", 'W' to "Washington", 'X' to "Xilófono", 'Y' to "Yugoslavia",
        'Z' to "Zaragoza"
    )

    private fun phonetic(c: Char, style: PhoneticStyle): String {
        val up = c.uppercaseChar()
        return when (style) {
            PhoneticStyle.NATO, PhoneticStyle.JAPANESE -> nato[up] ?: up.toString()
            PhoneticStyle.GERMAN -> german[up] ?: up.toString()
            PhoneticStyle.ITALIAN -> italian[up] ?: up.toString()
            PhoneticStyle.SPANISH -> spanish[up] ?: up.toString()
        }
    }

    private val digitWords = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    fun spellCall(call: String, style: PhoneticStyle, rng: Random = Random): String =
        call.map { c ->
            when {
                c.isDigit() -> digitWords[c - '0']
                c.isLetter() -> phonetic(c, style)
                else -> ""
            }
        }.filter { it.isNotEmpty() }.joinToString(" ")

    fun serialSpoken(serial: String): String =
        serial.map { c -> if (c.isDigit()) digitWords[c - '0'] else c.toString() }.joinToString(" ")

    fun script(st: ContestStation, mode: ContestMode, rng: Random = Random): String {
        return if (mode == ContestMode.SSB) {
            val callWords = spellCall(st.call, st.style, rng)
            val opening = listOf(
                "CQ contest, CQ contest.",
                "CQ test, CQ test.",
                "QRZ contest, QRZ contest."
            )[rng.nextInt(3)]
            val fill = if (st.filler.isEmpty()) "" else ", ${st.filler},"
            val reportWords = if (st.report == "5NN") "five nine nine" else "five nine"
            val closing = closings[rng.nextInt(closings.size)]
            "$fill $opening This is $callWords, $callWords. You are $reportWords, number ${serialSpoken(st.serial)}. $closing"
        } else {
            val call = st.call
            val report = if (st.report == "5NN") "5NN" else "599"
            "$call TEST $call $report $report ${st.serial} K"
        }
    }

    /**
     * SSB 语音模式的 CW 回退脚本：把每个字符转换为其本国字母解释法单词，
     * 整串单词按 CW 编码发送。用户听的是 "Anton Dora eins Berta Gustav" 的电报音，
     * 模拟真人按本国口音逐个念出呼号的节奏。
     */
    fun ssbPhoneticCwScript(st: ContestStation, rng: Random = Random): String {
        val callWords = spellCall(st.call, st.style, rng)
        val reportWords = if (st.report == "5NN") "FIVE NINE NINE" else "FIVE NINE"
        val serialWords = serialSpoken(st.serial).uppercase()
        return "CQ CQ $callWords $callWords DE ${st.call} $reportWords $serialWords K"
    }

    fun check(st: ContestStation, callInput: String, rstInput: String, serialInput: String): ContestCheck {
        val callNorm = callInput.uppercase().filter { it.isLetterOrDigit() }
        val rstNorm = rstInput.uppercase().filter { it.isLetterOrDigit() }
        val serialNorm = serialInput.filter { it.isDigit() }.trimStart('0').ifEmpty { "0" }
        return ContestCheck(
            callOk = callNorm == st.call,
            reportOk = rstNorm == "59" || rstNorm == "599" || rstNorm == "5NN",
            serialOk = serialNorm == st.serial.trimStart('0').ifEmpty { "0" }
        )
    }
}
