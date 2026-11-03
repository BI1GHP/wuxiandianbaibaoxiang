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

/** 将多路电报声混合渲染为 PCM，可模拟同频多台（pileup）与噪声 */
object CwRenderer {
    const val SR = 44100
    private const val MAX_MS = 60_000

    private data class Item(val startMs: Int, val ms: Int, val on: Boolean)

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

        for ((voice, items) in timelines) {
            val w = 2.0 * PI * voice.toneHz / SR
            for (item in items) {
                if (!item.on) continue
                val startSample = item.startMs.toLong() * SR / 1000L
                if (startSample >= total) continue
                val n = min(item.ms.toLong() * SR / 1000L, total.toLong() - startSample).toInt()
                if (n <= 0) continue
                val start = startSample.toInt()
                val ramp = min(n / 2, (5L * SR / 1000L).toInt())
                for (i in 0 until n) {
                    val env = when {
                        ramp > 1 && i < ramp -> i.toFloat() / ramp
                        ramp > 1 && i >= n - ramp -> (n - i).toFloat() / ramp
                        else -> 1f
                    }
                    buf[start + i] += (sin(w * (start + i)) * env * voice.gain).toFloat()
                }
            }
        }

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
                try {
                    t?.stop()
                } catch (_: Exception) {
                }
                try {
                    t?.release()
                } catch (_: Exception) {
                }
                if (track === t) track = null
                if (token == myToken) isPlaying = false
            }
        }
    }

    fun stop() {
        token++
        job?.cancel()
        job = null
        try {
            track?.pause()
            track?.flush()
        } catch (_: Exception) {
        }
        try {
            track?.release()
        } catch (_: Exception) {
        }
        track = null
        isPlaying = false
    }
}
