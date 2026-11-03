package com.wc.cwtrainer.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class VoiceSpec(
    val text: String,
    val fist: Fist,
    val toneHz: Double,
    val startMs: Int = 0,
    val gain: Float = 1f
)

/**
 * 将多路电报声混合渲染为 PCM，可模拟：
 * - 同频多台（pileup）
 * - 静态低通噪声
 * - 传播衰落（QSB 秒级慢衰落 + Flutter 毫秒级快衰落）
 *
 * 真实 HF 传播模型：
 * - QSB（fading）：5~15s 周期，深度 6~10dB，Rayleigh 分布
 * - Flutter（快衰落）：几十毫秒级，小幅度叠加
 * - 背景噪声：白噪声 + 低通滤波（模拟大气噪声）
 */
object CwRenderer {
    const val SR = 44100
    private const val MAX_MS = 60_000

    private data class Item(val startMs: Int, val ms: Int, val on: Boolean)

    /**
     * 生成一个随时间变化的衰落因子（0~1）。
     * @param phase 随机相位偏移，让每次不同
     * @param qsbDepth QSB 深度（0~1，越大衰落越深）
     */
    private fun fadingFactor(phase: Double, qsbDepth: Float = 0.6f): DoubleArray {
        // 生成 10 秒的衰落曲线，每 10ms 一个采样点
        val numPoints = 1000
        val out = DoubleArray(numPoints)
        for (i in 0 until numPoints) {
            val t = i * 0.01  // 秒
            // QSB 慢衰落：3 个不同频率的正弦叠加（模拟多径干涉）
            val qsb = (
                sin(2.0 * PI * 0.15 * t + phase) * 0.5 +    // 15s 周期
                sin(2.0 * PI * 0.22 * t + phase * 1.7) * 0.3 +  // ~9s
                sin(2.0 * PI * 0.35 * t + phase * 2.3) * 0.2   // ~6s
                ) * qsbDepth
            // Flutter 快衰落：高频小幅抖动
            val flutter = sin(2.0 * PI * 2.5 * t + phase * 5.0) * 0.12 +
                          sin(2.0 * PI * 4.0 * t + phase * 7.0) * 0.08
            // 归一化到 0~1
            out[i] = ((1.0 + qsb + flutter).coerceAtLeast(0.15)).coerceAtMost(1.2)
        }
        return out
    }

    fun render(voices: List<VoiceSpec>, noiseLevel: Float): ShortArray {
        var totalMs = 0
        val timelines = voices.map { v ->
            var t = v.startMs
            val items = buildSegments(v.text, v.fist).map { s ->
                val item = Item(t, s.ms, s.on)
                t += s.ms
                item
            }
            if (t > totalMs) totalMs = t
            v to items
        }
        val cappedMs = min(totalMs, MAX_MS)
        val total = (((cappedMs.toLong() + 120L) * SR) / 1000L).toInt()
        val buf = FloatArray(total)

        // 生成每个 voice 独立的衰落曲线
        val fadingCurves = voices.map { fadingFactor(Math.random() * 1000.0, qsbDepth = 0.55f) }

        for ((vi, voice) in voices.withIndex()) {
            val w = 2.0 * PI * voice.toneHz / SR
            val fade = fadingCurves[vi]
            val fadeStep = fade.size.toDouble() / cappedMs   // 每 ms 对应几个 fade 采样

            for ((idx, item) in timelines[vi].second.withIndex()) {
                if (!item.on) continue
                val startSample = item.startMs.toLong() * SR / 1000L
                if (startSample >= total) continue
                val n = min(item.ms.toLong() * SR / 1000L, total.toLong() - startSample).toInt()
                if (n <= 0) continue
                val start = startSample.toInt()
                val ramp = min(n / 2, (5L * SR / 1000L).toInt())

                // 预计算每个 sample 的衰落值（每 10ms 采一个点，中间线性插值）
                val startMsOfSample = item.startMs
                for (i in 0 until n) {
                    val env = when {
                        ramp > 1 && i < ramp -> i.toFloat() / ramp
                        ramp > 1 && i >= n - ramp -> (n - i).toFloat() / ramp
                        else -> 1f
                    }
                    // 衰落插值：从 fade 曲线取
                    val tMs = startMsOfSample + i * 1000.0 / SR
                    val fadeIdx = (tMs * fadeStep).toInt().coerceIn(0, fade.size - 1)
                    val fadeVal = fade[fadeIdx]

                    buf[start + i] += (sin(w * (start + i)) * env * voice.gain * fadeVal).toFloat()
                }
            }
        }

        // —— 背景噪声（静态低通）——
        if (noiseLevel > 0f) {
            val rnd = Random(20261103)
            var lp = 0f
            for (i in buf.indices) {
                val white = rnd.nextFloat() * 2f - 1f
                lp = lp * 0.92f + white * 0.08f
                buf[i] += lp * noiseLevel * 4f
            }
        }

        val out = ShortArray(total)
        for (i in buf.indices) {
            val x = (buf[i] * 0.42f).coerceIn(-0.95f, 0.95f)
            out[i] = (x * 32767f).toInt().toShort()
        }
        return out
    }
}

/** 报文播放器 */
class CwAudio(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var track: AudioTrack? = null
    private var token = 0

    var isPlaying by mutableStateOf(false)
        private set

    fun play(
        voices: List<VoiceSpec>,
        volume: Float = 0.85f,
        noiseLevel: Float = 0f,
        onDone: () -> Unit = {}
    ) {
        if (voices.isEmpty()) return
        stop()
        val pcm = CwRenderer.render(voices, noiseLevel)
        if (pcm.isEmpty()) return
        val myToken = ++token
        isPlaying = true

        job = scope.launch(Dispatchers.Default) {
            var t: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    CwRenderer.SR,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(CwRenderer.SR)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(max(minBuf, 8192))
                    .build()
                track = t
                t.setVolume(volume.coerceIn(0f, 1f))
                t.play()

                var offset = 0
                while (offset < pcm.size) {
                    val count = min(4096, pcm.size - offset)
                    t.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                    offset += count
                }
                delay(pcm.size.toLong() * 1000L / CwRenderer.SR + 120L)
                if (token == myToken) {
                    isPlaying = false
                    onDone()
                }
            } catch (e: Exception) {
                Log.e("CwAudio", "play failed", e)
            } finally {
                try { t?.stop() } catch (_: Exception) {}
                try { t?.release() } catch (_: Exception) {}
                if (track === t) track = null
                if (token == myToken) isPlaying = false
            }
        }
    }

    fun stop() {
        token++
        job?.cancel()
        job = null
        try { track?.pause(); track?.flush() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
        isPlaying = false
    }
}
