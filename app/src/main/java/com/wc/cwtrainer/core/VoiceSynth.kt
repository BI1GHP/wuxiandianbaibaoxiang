package com.wc.cwtrainer.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.random.Random

/**
 * 内置轻量人声合成（共振峰合成，纯 Kotlin，无需系统语音引擎或任何模型文件）。
 *
 * 用途：SSB 模式下若设备没有对应语言的 TTS 语音包，用它在本地合成"人声感"报文，
 * 用户无需下载任何组件。音质偏机械，仅作兜底，正常情况仍优先系统 TTS。
 */
class VoiceSynth(context: Context) {

    private val appContext = context.applicationContext
    private var track: AudioTrack? = null

    var isPlaying by mutableStateOf(false)
        private set

    /** pitch: 1.0 为常速男声；rate: 1.0 常速，>1 更快 */
    suspend fun speak(text: String, pitch: Float = 1f, rate: Float = 1f): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val pcm = Synth.render(text, (118f * pitch).coerceIn(80f, 260f), rate.coerceIn(0.5f, 1.8f))
                require(pcm.isNotEmpty()) { "nothing to speak" }
                play(pcm)
            }
        }

    private fun play(pcm: ShortArray) {
        stop()
        AudioGate.acquire(appContext, TAG) { stop() }
        val minBuf = AudioTrack.getMinBufferSize(
            Synth.SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Synth.SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(maxOf(minBuf, pcm.size * 2))
            .build()

        track = t
        t.write(pcm, 0, pcm.size)
        t.play()
        isPlaying = true
        try {
            while (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                if (t.playbackHeadPosition >= pcm.size) break
                Thread.sleep(30)
            }
        } finally {
            isPlaying = false
            release(t)
            if (track === t) {
                track = null
                AudioGate.release(TAG)
            }
        }
    }

    fun stop() {
        val t = track
        track = null
        isPlaying = false
        if (t != null) release(t)
        AudioGate.release(TAG)
    }

    private fun release(t: AudioTrack) {
        try { t.pause() } catch (_: Exception) {}
        try { t.flush() } catch (_: Exception) {}
        try { t.stop() } catch (_: Exception) {}
        try { t.release() } catch (_: Exception) {}
    }

    private companion object {
        const val TAG = "voice_synth"
    }
}

/** 共振峰合成核心：文本 → 音素序列 → PCM */
private object Synth {

    const val SR = 44100
    private const val TWO_PI = 2.0 * PI

    private const val VOWEL = 0
    private const val NASAL = 1
    private const val LIQUID = 2
    private const val FRIC = 3
    private const val STOP = 4
    private const val SIL = 5

    private class Ph(
        val kind: Int,
        val f1: Float,
        val f2: Float,
        val f3: Float,
        val durMs: Float,
        val voiced: Boolean,
        val noiseF: Float = 0f,
        val amp: Float = 1f
    )

    private fun v(f1: Float, f2: Float, f3: Float, ms: Float) = Ph(VOWEL, f1, f2, f3, ms, true)
    private fun n(f1: Float, f2: Float, f3: Float, ms: Float) = Ph(NASAL, f1, f2, f3, ms, true, amp = 0.55f)
    private fun l(f1: Float, f2: Float, f3: Float, ms: Float) = Ph(LIQUID, f1, f2, f3, ms, true, amp = 0.75f)
    private fun f(hz: Float, ms: Float, voiced: Boolean = false, amp: Float = 0.55f) =
        Ph(FRIC, 500f, 1500f, 2500f, ms, voiced, hz, amp)

    private fun s(hz: Float, ms: Float, voiced: Boolean = false) =
        Ph(STOP, 400f, 1200f, 2400f, ms, voiced, hz, 0.8f)

    private val PH: Map<String, Ph> = mapOf(
        "AA" to v(730f, 1090f, 2440f, 130f),
        "AE" to v(660f, 1720f, 2410f, 130f),
        "AH" to v(620f, 1220f, 2550f, 90f),
        "AO" to v(570f, 840f, 2410f, 130f),
        "EH" to v(530f, 1840f, 2480f, 110f),
        "ER" to v(490f, 1350f, 1690f, 110f),
        "IH" to v(390f, 1990f, 2550f, 90f),
        "IY" to v(270f, 2290f, 3010f, 120f),
        "OW" to v(570f, 840f, 2410f, 130f),
        "UH" to v(440f, 1020f, 2240f, 90f),
        "UW" to v(300f, 870f, 2240f, 120f),
        "AY" to v(680f, 1400f, 2500f, 150f),
        "EY" to v(530f, 1840f, 2480f, 140f),
        "AW" to v(650f, 1000f, 2400f, 150f),
        "OY" to v(560f, 1000f, 2400f, 150f),
        "M" to n(250f, 1100f, 2200f, 70f),
        "N" to n(250f, 1700f, 2600f, 65f),
        "NG" to n(250f, 2300f, 2750f, 70f),
        "L" to l(380f, 880f, 2600f, 55f),
        "R" to l(460f, 1270f, 1600f, 55f),
        "W" to l(300f, 610f, 2200f, 45f),
        "Y" to l(260f, 2070f, 3020f, 45f),
        "S" to f(5600f, 90f),
        "Z" to f(5600f, 80f, voiced = true),
        "SH" to f(2900f, 90f),
        "ZH" to f(2900f, 80f, voiced = true),
        "F" to f(2400f, 85f),
        "V" to f(2400f, 75f, voiced = true),
        "TH" to f(3200f, 85f),
        "DH" to f(3200f, 70f, voiced = true),
        "HH" to f(1500f, 60f, amp = 0.45f),
        "CH" to f(2700f, 85f),
        "JH" to f(2700f, 75f, voiced = true),
        "T" to s(4200f, 70f),
        "D" to s(4200f, 60f, voiced = true),
        "K" to s(2200f, 75f),
        "G" to s(2200f, 65f, voiced = true),
        "P" to s(900f, 70f),
        "B" to s(900f, 60f, voiced = true)
    )

    /** 单词 → 音素串 */
    private val WORDS: Map<String, String> = buildMap {
        put("a", "EY"); put("b", "B IY"); put("c", "S IY"); put("d", "D IY")
        put("e", "IY"); put("f", "EH F"); put("g", "JH IY"); put("h", "EY CH")
        put("i", "AY"); put("j", "JH EY"); put("k", "K EY"); put("l", "EH L")
        put("m", "EH M"); put("n", "EH N"); put("o", "OW"); put("p", "P IY")
        put("q", "K Y UW"); put("r", "AA R"); put("s", "EH S"); put("t", "T IY")
        put("u", "Y UW"); put("v", "V IY"); put("w", "D AH B AH L Y UW"); put("x", "EH K S")
        put("y", "W AY"); put("z", "Z IY")

        put("zero", "Z IH R OW"); put("one", "W AH N"); put("two", "T UW")
        put("three", "TH R IY"); put("four", "F AO R"); put("five", "F AY V")
        put("six", "S IH K S"); put("seven", "S EH V AH N"); put("eight", "EY T")
        put("nine", "N AY N"); put("ten", "T EH N")

        put("cq", "S IY K Y UW"); put("de", "D IY IY"); put("test", "T EH S T")
        put("contest", "K AA N T EH S T"); put("qrz", "K Y UW AA R Z IY")
        put("qsl", "K Y UW EH S EH L"); put("qso", "K Y UW EH S OW")
        put("qrt", "K Y UW AA R T IY"); put("qrm", "K Y UW AA R EH M")
        put("qth", "K Y UW T IY EY CH"); put("qsb", "K Y UW EH S B IY")
        put("this", "DH IH S"); put("is", "IH Z"); put("it", "IH T")
        put("you", "Y UW"); put("are", "AA R"); put("your", "Y AO R")
        put("am", "AE M"); put("my", "M AY")
        put("number", "N AH M B ER"); put("nr", "N AH M B ER")
        put("report", "R IY P AO R T"); put("signal", "S IH G N AH L")
        put("thanks", "TH AE NG K S"); put("thank", "TH AE NG K")
        put("for", "F AO R"); put("the", "DH AH"); put("call", "K AO L")
        put("good", "G UH D"); put("luck", "L AH K"); put("again", "AH G EH N")
        put("please", "P L IY Z"); put("radio", "R EY D IY OW"); put("and", "AE N D")
        put("hope", "HH OW P"); put("to", "T UW"); put("hear", "HH IH R")
        put("copy", "K AA P IY"); put("best", "B EH S T"); put("regards", "R IH G AA R D Z")
        put("fifty", "F IH F T IY"); put("cons", "K AA N Z"); put("info", "IH N F OW")

        put("alfa", "AE L F AH"); put("alpha", "AE L F AH")
        put("bravo", "B R AA V OW"); put("charlie", "CH AA R L IY")
        put("delta", "D EH L T AH"); put("echo", "EH K OW")
        put("foxtrot", "F AA K S T R AA T"); put("golf", "G AA L F")
        put("hotel", "HH OW T EH L"); put("india", "IH N D IY AH")
        put("juliett", "JH UW L IY EH T"); put("kilo", "K IY L OW")
        put("lima", "L IY M AH"); put("mike", "M AY K")
        put("november", "N OW V EH M B ER"); put("oscar", "AA S K AH R")
        put("papa", "P AA P AA"); put("quebec", "K EH B EH K")
        put("romeo", "R OW M IY OW"); put("sierra", "S IY EH R AH")
        put("tango", "T AE NG G OW"); put("uniform", "Y UW N IH F AO R M")
        put("victor", "V IH K T AH R"); put("whiskey", "W IH S K IY")
        put("xray", "EH K S R EY"); put("yankee", "Y AE NG K IY")
        put("zulu", "Z UW L UW")
    }

    private val parsedUnits = HashMap<String, List<Ph>>()
    private val parsedWords = HashMap<String, List<Ph>>()

    private fun unitsOf(code: String): List<Ph> = parsedUnits.getOrPut(code) {
        code.split(' ').mapNotNull { PH[it.trim()] }
    }

    private fun wordUnits(w: String): List<Ph> = parsedWords.getOrPut(w) {
        WORDS[w]?.let { unitsOf(it) }
            ?: w.mapNotNull { ch ->
                when {
                    ch.isLetter() -> unitsOf(ch.toString())
                    ch.isDigit() -> unitsOf(digitWord(ch))
                    else -> null
                }
            }.flatten()
    }

    private fun digitWord(c: Char) = when (c) {
        '0' -> "zero"; '1' -> "one"; '2' -> "two"; '3' -> "three"; '4' -> "four"
        '5' -> "five"; '6' -> "six"; '7' -> "seven"; '8' -> "eight"; else -> "nine"
    }

    private fun plan(text: String, rate: Float): List<Ph> {
        val out = ArrayList<Ph>()
        for (tok in text.lowercase().split(Regex("[^a-z0-9]+"))) {
            if (tok.isEmpty()) continue
            val seq = wordUnits(tok)
            if (seq.isEmpty()) continue
            out.addAll(seq)
            out.add(Ph(SIL, 500f, 1500f, 2500f, (55f / rate).coerceIn(25f, 90f), false, amp = 0f))
        }
        out.add(Ph(SIL, 500f, 1500f, 2500f, 120f, false, amp = 0f))
        return out
    }

    fun render(text: String, f0: Float, rate: Float): ShortArray {
        val units = plan(text, rate)
        if (units.isEmpty()) return ShortArray(0)

        val rnd = Random(System.nanoTime())
        val out = ArrayList<Float>(SR * 4)

        var f1 = 520f; var f2 = 1500f; var f3 = 2500f
        var nz1 = 500f
        var y1a = 0f; var y1b = 0f
        var y2a = 0f; var y2b = 0f
        var y3a = 0f; var y3b = 0f
        var nza = 0f; var nzb = 0f
        var gPhase = 0.0
        var ampEnv = 0f

        val alpha = (1.0 - exp(-1.0 / (0.018 * SR))).toFloat()
        val total = units.size.toFloat()

        units.forEachIndexed { ui, u ->
            val n = ((u.durMs / rate) * SR / 1000f).toInt().coerceAtLeast(1)
            val decl = 1f - 0.10f * (ui / total)
            val f0u = (f0 * decl * (1f + (rnd.nextFloat() - 0.5f) * 0.04f)).coerceIn(70f, 320f)

            for (i in 0 until n) {
                if (u.kind == SIL) { out.add(0f); continue }
                val t = i.toFloat() / n

                val tf1 = if (u.kind == FRIC || u.kind == STOP) 480f else u.f1
                f1 += (tf1 - f1) * alpha
                f2 += (u.f2 - f2) * alpha
                f3 += (u.f3 - f3) * alpha
                nz1 += (u.noiseF - nz1) * alpha

                val atk = (i / (0.006f * SR)).coerceAtMost(1f)
                val rel = ((n - i) / (0.010f * SR)).coerceAtMost(1f)
                ampEnv += (u.amp * atk * rel - ampEnv) * 0.02f

                var src = 0f
                if (u.kind == STOP && t < 0.55f) {
                    src = 0f
                } else if (u.voiced) {
                    gPhase += f0u / SR
                    if (gPhase >= 1.0) { gPhase -= 1.0; src = 1f }
                }

                var noise = 0f
                if (u.kind == FRIC || (u.kind == STOP && t >= 0.55f)) {
                    val w = rnd.nextFloat() * 2f - 1f
                    val r = 0.985f
                    val c1 = (2f * r * cos(TWO_PI * nz1 / SR)).toFloat()
                    nza = w + c1 * nza - r * r * nzb
                    nzb = nza
                    noise = nza * (1f - r) * 3.2f
                }

                val y1 = formant(src, f1, 90f, y1a, y1b); y1b = y1a; y1a = y1
                val y2 = formant(src, f2, 110f, y2a, y2b); y2b = y2a; y2a = y2
                val y3 = formant(src, f3, 160f, y3a, y3b); y3b = y3a; y3a = y3
                val voiced = y1 + 0.55f * y2 + 0.28f * y3

                var sample = when (u.kind) {
                    FRIC -> noise * 0.9f + (if (u.voiced) voiced * 0.25f else 0f)
                    STOP -> if (t < 0.55f) 0f else noise
                    else -> voiced * 0.6f + noise * 0.15f
                }
                sample *= ampEnv
                out.add(sample)
            }
        }

        var peak = 1e-6f
        for (s in out) peak = maxOf(peak, abs(s))
        val gain = 0.85f / peak
        val pcm = ShortArray(out.size)
        for (i in out.indices) {
            pcm[i] = ((out[i] * gain).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return pcm
    }

    private fun formant(x: Float, f: Float, bw: Float, y1: Float, y2: Float): Float {
        val r = exp(-PI * bw / SR).toFloat()
        val b = (2f * r * cos(TWO_PI * f / SR)).toFloat()
        val c = -r * r
        val a = 1f - b - c
        return a * x + b * y1 + c * y2
    }
}
