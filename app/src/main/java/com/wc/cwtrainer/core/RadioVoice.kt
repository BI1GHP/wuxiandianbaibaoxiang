package com.wc.cwtrainer.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/**
 * SSB 真人语音（系统 TTS 路线）：先用系统 TTS 合成到 WAV，再做 300~2800Hz 单边带滤波，
 * 最后流式播放。只在设备 TTS 支持目标语言时使用，适合还原各国口音。
 */
class RadioVoice(context: Context, private val speaker: Speaker) {

    private val appContext = context.applicationContext
    private var track: AudioTrack? = null
    private var token = 0
    private var cacheKey: String? = null
    private var cached: ShortArray? = null

    var isPlaying by mutableStateOf(false)
        private set

    suspend fun play(
        text: String, tag: String, rate: Float, pitch: Float, volume: Float
    ): Result<Unit> = withContext(Dispatchers.Default) {
        val key = "$tag|$rate|$pitch|$text"
        val pcm = if (key == cacheKey) cached else {
            val file = File(appContext.cacheDir, "ssb_tts.wav")
            val synth = speaker.synthesizeToFile(text, tag, rate, pitch, file)
            if (synth.isFailure) {
                return@withContext Result.failure(
                    synth.exceptionOrNull() ?: IllegalStateException("tts failed")
                )
            }
            val decoded = runCatching { RadioDsp.load(file) }.getOrNull()
            if (decoded == null) {
                return@withContext Result.failure(IllegalStateException("wav decode failed"))
            }
            cacheKey = key
            cached = decoded
            decoded
        } ?: return@withContext Result.failure(IllegalStateException("empty pcm"))

        playPcm(pcm, volume)
        Result.success(Unit)
    }

    /** 流式写入，避免静态模式下整段 buffer 导致的爆音/卡顿 */
    private suspend fun playPcm(pcm: ShortArray, volume: Float) {
        stopTrack()
        val myToken = ++token
        val t = try {
            val minBuf = AudioTrack.getMinBufferSize(
                RadioDsp.SR,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        // 与底噪轨/CW 轨同类型，避免部分 ROM 对 SPEECH 轨启动时闪避其它音轨
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RadioDsp.SR)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBuf.coerceAtLeast(8192))
                .build()
        } catch (e: Exception) {
            Log.e("RadioVoice", "init failed", e)
            return
        }
        track = t
        isPlaying = true
        try {
            t.setVolume(volume.coerceIn(0f, 1f))
            t.play()
            var offset = 0
            while (offset < pcm.size && token == myToken) {
                val n = min(2048, pcm.size - offset)
                t.write(pcm, offset, n, AudioTrack.WRITE_BLOCKING)
                offset += n
            }
            if (token == myToken) delay(pcm.size.toLong() * 1000L / RadioDsp.SR + 100L)
        } catch (e: Exception) {
            Log.e("RadioVoice", "play failed", e)
        } finally {
            if (token == myToken) {
                isPlaying = false
                stopTrack()
            }
        }
    }

    private fun stopTrack() {
        try { track?.pause(); track?.flush() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
    }

    fun stop() {
        token++
        stopTrack()
        isPlaying = false
    }
}

/**
 * SSB 音频后期：WAV 解码 / 重采样 / 300~2800Hz 带通 / 峰值归一 + 软限幅。
 * RadioVoice 与内置词库 VoiceBank 共用。
 */
internal object RadioDsp {
    const val SR = 44100

    fun load(file: File): ShortArray? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val (rate, pcm) = decode(bytes) ?: return null
        val res = if (rate == SR) pcm else resample(pcm, rate, SR)
        return shape(res, SR)
    }

    /** 解析 16bit WAV，返回 (采样率, 单声道 PCM)；24/32bit 与多声道一并兼容 */
    fun decode(b: ByteArray): Pair<Int, ShortArray>? {
        if (b.size < 44 || String(b, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        var pos = 12
        var channels = 1
        var bits = 16
        var sampleRate = 22050
        var dataOffset = -1
        var dataLen = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val size = le32(b, pos + 4)
            when (id) {
                "fmt " -> {
                    channels = le16(b, pos + 10).coerceAtLeast(1)
                    sampleRate = le32(b, pos + 12)
                    bits = le16(b, pos + 22)
                }
                "data" -> {
                    dataOffset = pos + 8
                    dataLen = minOf(size, b.size - dataOffset)
                    break
                }
            }
            pos += 8 + size + (size and 1)
        }
        if (dataOffset < 0 || dataLen <= 0 || sampleRate <= 0) return null

        val bytesPer = (bits / 8).coerceAtLeast(1)
        val frameBytes = bytesPer * channels
        val frames = dataLen / frameBytes
        if (frames <= 0) return null

        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                val p = dataOffset + i * frameBytes + c * bytesPer
                sum += when (bits) {
                    8 -> ((b[p].toInt() and 0xFF) - 128) / 128f
                    24 -> {
                        var v = (b[p].toInt() and 0xFF) or
                            ((b[p + 1].toInt() and 0xFF) shl 8) or
                            ((b[p + 2].toInt() and 0xFF) shl 16)
                        if (v and 0x800000 != 0) v = v or 0xFF000000.toInt()
                        v / 8388608f
                    }
                    32 -> le32(b, p) / 2147483648f
                    else -> le16(b, p) / 32768f
                }
            }
            mono[i] = sum / channels
        }
        val out = ShortArray(frames) { (mono[it] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort() }
        return sampleRate to out
    }

    fun resample(pcm: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || pcm.isEmpty()) return pcm
        val outLen = (pcm.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val x = i.toDouble() * from / to
            val i0 = x.toInt().coerceIn(0, pcm.size - 1)
            val i1 = (i0 + 1).coerceAtMost(pcm.size - 1)
            val f = (x - i0).toFloat()
            out[i] = (pcm[i0] * (1f - f) + pcm[i1] * f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /**
     * WSOLA 时间伸缩：只改语速、不改音高（变速不变调）。
     * rate > 1 变快（输出更短），rate < 1 变慢（输出更长）。
     * 30ms 帧、50% 重叠、±6ms 相似度搜索，对单词级语音足够自然。
     */
    fun timeStretch(pcm: ShortArray, sampleRate: Int, rate: Float): ShortArray {
        if (pcm.isEmpty() || rate <= 0.05f) return pcm
        if (abs(rate - 1f) < 0.005f) return pcm.copyOf()
        val frame = (0.030 * sampleRate).toInt().coerceAtLeast(64)
        val hop = frame / 2
        val search = (0.006 * sampleRate).toInt().coerceAtLeast(8)
        val outLen = (pcm.size / rate).toInt().coerceAtLeast(1)
        val acc = FloatArray(outLen + frame)
        val win = FloatArray(frame) { i ->
            (0.5 - 0.5 * cos(2.0 * PI * i / frame)).toFloat()
        }
        val cur = FloatArray(frame)
        var pos = 0.0
        var outPos = 0
        var first = true
        while (outPos < outLen) {
            var shift = 0
            if (!first) {
                // 在上一次的相邻位置附近找波形最相似的起点，保证拼接连续、不出现咔哒
                var best = -Float.MAX_VALUE
                val base = pos.toInt()
                for (d in -search..search) {
                    val s = base + d
                    if (s < 0 || s + hop > pcm.size) continue
                    var sum = 0f
                    var i = 0
                    while (i < hop) {
                        sum += pcm[s + i] * cur[hop + i]
                        i += 2
                    }
                    if (sum > best) { best = sum; shift = d }
                }
            }
            val start = pos.toInt() + shift
            for (i in 0 until frame) {
                val k = start + i
                cur[i] = if (k in pcm.indices) pcm[k] / 32768f else 0f
            }
            for (i in 0 until frame) acc[outPos + i] += cur[i] * win[i]
            outPos += hop
            pos += hop * rate
            first = false
        }
        return ShortArray(outLen) {
            (acc[it] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** 单边带话音频段 + 温和增益：只做轻度带限和峰值控制，不刻意做失真/过载 */
    fun shape(pcm: ShortArray, sampleRate: Int): ShortArray {
        if (pcm.isEmpty()) return pcm
        // 女声基频约 180~220Hz，高通放在 120Hz 以下，避免把基频削掉造成“电话音/沙哑”；
        // 低通放到 4.2kHz，保留齿音与辅音的清晰度（原有 3.4k 偏闷）
        val hp = Biquad.highPass(120.0, sampleRate, 0.6)
        val lp = Biquad.lowPass(min(4200.0, sampleRate / 2.0 - 400.0), sampleRate, 0.6)
        val filtered = FloatArray(pcm.size)
        var peak = 0f
        for (i in pcm.indices) {
            val v = lp.process(hp.process(pcm[i] / 32768f))
            filtered[i] = v
            if (abs(v) > peak) peak = abs(v)
        }
        // 只做一次整体增益，不做逐段 AGC，避免把底噪一起抬起来
        val gain = if (peak < 1e-5f) 0f else (0.86f / peak).coerceIn(0.4f, 2.2f)
        val out = ShortArray(pcm.size)
        for (i in filtered.indices) {
            out[i] = (softLimit(filtered[i] * gain) * 32767f)
                .roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** 0.8 以下直通，只有峰值逼近满刻度时才平滑压一点，不引入谐波失真 */
    private fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= 0.8f) return x
        val y = 0.8f + 0.15f * tanh((a - 0.8f) / 0.15f)
        return if (x < 0) -y else y
    }

    private fun le16(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or
            ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or
            ((b[p + 3].toInt() and 0xFF) shl 24)
}

/** RBJ 双二阶滤波器 */
internal class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Float): Float {
        val xi = x.toDouble()
        val y = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = xi
        y2 = y1; y1 = y
        return y.toFloat()
    }

    companion object {
        fun lowPass(f0: Double, sr: Int, q: Double = 0.7071): Biquad {
            val w = 2 * PI * f0 / sr
            val cw = cos(w)
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            return Biquad(
                (1 - cw) / 2 / a0, (1 - cw) / a0, (1 - cw) / 2 / a0,
                -2 * cw / a0, (1 - alpha) / a0
            )
        }

        fun highPass(f0: Double, sr: Int, q: Double = 0.7071): Biquad {
            val w = 2 * PI * f0 / sr
            val cw = cos(w)
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            return Biquad(
                (1 + cw) / 2 / a0, -(1 + cw) / a0, (1 + cw) / 2 / a0,
                -2 * cw / a0, (1 - alpha) / a0
            )
        }
    }
}
