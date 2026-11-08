package com.wc.cwtrainer.core

import kotlin.random.Random

enum class ContestMode { SSB, CW }

enum class PhoneticStyle { NATO, GERMAN, ITALIAN, SPANISH, JAPANESE }

data class ContestStation(
    val country: String,
    val accent: String,
    val call: String,
    val localeTag: String,
    val voiceKey: String,
    val style: PhoneticStyle,
    val rate: Float,
    val pitch: Float,
    val habit: String,
    val filler: String,
    val report: String,
    val serial: String,
    // 标准解释法模式下的语气：0=平稳 1=急促 2=拖长（仅节奏/语速差异）
    val tone: Int,
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
        val accent: String,
        val suffix: IntRange = 2..3
    )

    // 呼号按 ITU 前缀分配规则生成：国家前缀 + 分区数字 + 1~3 位字母后缀。
    // 每个国家只列出真实在用、比赛中常见的呼号段，避免出现 "意大利 IA1XYZ" 这类越界呼号。
    private val countries = listOf(
        Country("美国", listOf("K", "W", "N", "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AI", "AJ", "AK",
            "KA", "KB", "KC", "KD", "KE", "KF", "KG", "KI", "KJ", "KK", "KL", "KM", "KN", "KO",
            "KP", "KQ", "KR", "KS", "KT", "KU", "KV", "KW", "KX", "KY", "KZ"),
            "en-US", PhoneticStyle.NATO, "美式英语", 1..3),
        Country("英国", listOf("G", "M", "2E", "2M", "GM", "GW", "GD", "GI", "GJ", "GK"),
            "en-GB", PhoneticStyle.NATO, "英式英语"),
        Country("德国", listOf("DA", "DB", "DC", "DD", "DF", "DG", "DH", "DJ", "DK", "DL", "DM", "DN", "DO", "DP", "DQ", "DR"),
            "de-DE", PhoneticStyle.GERMAN, "德语口音 · 德式字母解释法"),
        Country("日本", listOf("JA", "JE", "JF", "JG", "JH", "JI", "JJ", "JK", "JL", "JM", "JN", "JO", "JP", "JQ", "JR", "JS"),
            "ja-JP", PhoneticStyle.JAPANESE, "日式英语 · 和制读音"),
        Country("意大利", listOf("I", "IK", "IZ", "IU", "II", "IW", "IS", "IN", "IR"),
            "it-IT", PhoneticStyle.ITALIAN, "意大利语腔 · 意式字母解释法"),
        Country("西班牙", listOf("EA", "EB", "EC", "ED", "EE", "EF", "EG", "EH"),
            "es-ES", PhoneticStyle.SPANISH, "西班牙语腔 · 西式字母解释法"),
        Country("法国", listOf("F", "TM"),
            "fr-FR", PhoneticStyle.NATO, "法语腔"),
        Country("俄罗斯", listOf("R", "RA", "RV", "RW", "RX", "RZ", "UA", "UB", "UC", "UD", "UF", "UG"),
            "ru-RU", PhoneticStyle.NATO, "俄语腔"),
        Country("巴西", listOf("PY", "PT", "PP", "PU", "PR", "PS", "PV", "PW", "PX", "ZV", "ZW", "ZZ"),
            "pt-BR", PhoneticStyle.NATO, "葡萄牙语腔"),
        Country("中国", listOf("BG", "BD", "BA", "BY", "BT", "BZ", "BH", "BI", "BJ", "BL", "BM", "BN"),
            "zh-CN", PhoneticStyle.NATO, "中式英语腔"),
        Country("韩国", listOf("HL", "DS", "DT", "6K", "6L", "6M", "6N"),
            "ko-KR", PhoneticStyle.NATO, "韩式英语腔"),
        Country("澳大利亚", listOf("VK", "AX"),
            "en-AU", PhoneticStyle.NATO, "澳洲英语"),
        Country("瑞典", listOf("SA", "SB", "SC", "SD", "SE", "SF", "SG", "SH", "SI", "SJ", "SK", "SL", "SM", "8S"),
            "sv-SE", PhoneticStyle.NATO, "北欧腔"),
        Country("波兰", listOf("SN", "SO", "SP", "SQ", "SR", "HF", "3Z"),
            "pl-PL", PhoneticStyle.NATO, "波兰腔"),
        Country("荷兰", listOf("PA", "PB", "PC", "PD", "PE", "PF", "PG", "PH", "PI"),
            "nl-NL", PhoneticStyle.NATO, "荷兰腔")
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

    fun station(rng: Random = Random): ContestStation {
        val c = countries[rng.nextInt(countries.size)]
        val prefix = c.prefixes[rng.nextInt(c.prefixes.size)]
        val digit = rng.nextInt(10)
        val suffixLen = c.suffix.first + rng.nextInt(c.suffix.last - c.suffix.first + 1)
        val suffix = (1..suffixLen).map { ('A' + rng.nextInt(26)) }.joinToString("")
        val call = prefix + digit + suffix
        // 同一国家也换人：男声 42%、女声 36%、老年 22%
        val pick = rng.nextFloat()
        val variant = when {
            pick < 0.22f -> "-s"
            pick < 0.58f -> "-f"
            else -> ""
        }
        return ContestStation(
            country = c.name,
            accent = c.accent,
            call = call,
            localeTag = c.localeTag,
            voiceKey = c.localeTag + variant,
            style = c.style,
            rate = 0.85f + rng.nextFloat() * 0.35f,
            pitch = 0.85f + rng.nextFloat() * 0.35f,
            habit = habits[rng.nextInt(habits.size)],
            filler = fillers[rng.nextInt(fillers.size)],
            // RST 固定 599：不再出 5NN 变种，抄收时无需输入 RST
            report = "599",
            serial = Generators.serial(rng),
            tone = rng.nextInt(3),
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

    // —— SSB 标准解释法模式的语气：词不变（规范 NATO + 国家口音），只有节奏/语速差异 ——
    fun toneName(st: ContestStation): String = when (st.tone) { 1 -> "急促"; 2 -> "拖长"; else -> "平稳" }
    fun toneRateMul(st: ContestStation): Float = when (st.tone) { 1 -> 1.08f; 2 -> 0.92f; else -> 1f }
    fun tonePauseMul(st: ContestStation): Float = when (st.tone) { 1 -> 0.6f; 2 -> 1.35f; else -> 1f }

    fun script(st: ContestStation, mode: ContestMode, standard: Boolean = false, rng: Random = Random): String {
        return if (mode == ContestMode.SSB) {
            // SSB 只报三要素：呼号（字母解释法）、信号报告、序号，不加 CQ / 客套话
            // standard=true 时不论台站口语习惯，一律用规范 NATO 解释法（口音由音色保留）
            val style = if (standard) PhoneticStyle.NATO else st.style
            val callWords = spellCall(st.call, style, rng)
            val reportWords = if (st.report == "5NN") "five nine nine" else "five nine"
            "This is $callWords, $callWords. $reportWords, ${serialSpoken(st.serial)}."
        } else {
            val call = st.call
            val report = if (st.report == "5NN") "5NN" else "599"
            "$call TEST $call $report $report ${st.serial} K"
        }
    }

    /**
     * SSB 报文的明文版（非字母解释法）：呼号原样 + 数字报告与序号，
     * 与 script()/ssbWords() 实际播报的内容一一对应，用于报文面板第二行显示。
     */
    fun ssbPlain(st: ContestStation): String {
        val report = if (st.report == "5NN") "599" else "59"
        return "${st.call}, ${st.call}. $report, ${st.serial}."
    }

    /**
     * SSB 报文的单词序列（供内置真人语音库逐词播放）：
     * This is + 呼号（两遍，字母解释法）+ 信号报告 + 序号，只有这三要素。
     */
    fun ssbWords(st: ContestStation, standard: Boolean = false, rng: Random = Random): List<String> {
        val style = if (standard) PhoneticStyle.NATO else st.style
        val callWords = spellCall(st.call, style, rng).split(" ").filter { it.isNotBlank() }
        val report = if (st.report == "5NN") listOf("five nine nine") else listOf("five nine")
        val serial = st.serial.mapNotNull { c ->
            if (c.isDigit()) digitWords[c - '0'] else null
        }
        return buildList {
            add("This is")
            addAll(callWords)
            addAll(callWords)
            addAll(report)
            addAll(serial)
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
