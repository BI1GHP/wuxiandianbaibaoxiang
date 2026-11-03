package com.wc.cwtrainer.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 通过麦克风/音频接口检测外部手键或自动键的通断。
 *
 * 识别优化（v4）：
 * - 持续状态的二阶带通滤波器（RBJ，Q=4）提取目标音调能量，滤波状态跨帧保留，
 *   不需要每帧重新收敛。
 * - 用“带通能量”本身的自适应噪声底做主门限，而不是用总能量门限：
 *   当 CW 音被环境噪声部分淹没时，总能量几乎不变，但目标频率上仍有明显能量，
 *   这样才能把弱信号也识别出来（灵敏度提升的关键）。
 * - 同时要求“带通/总能量比值”：纯 CW 音≈1，宽带噪声≈0.11。
 *   比值门限还会跟随实测噪声比值自适应，因此麦克风底噪、点击声不会误触发。
 * - 双帧去抖：连续 2 帧（约 12ms）有音调才算按下，连续 2 帧静音才算抬起，
 *   并回填真实边沿时间，短噪声尖峰不会被解成 E/T。
 * - 抬起门限保持足够滞回（约 1.4 倍噪底），避免元素尾部被噪声拖长导致点/划变形。
 * - 帧长 256（约 5.8ms），时间分辨率更高，40 WPM 以上的快速电码也能完整分辨。
 */
class ToneKeyDetector(
    private val detectorHz: Double,
    private val sensitivity: Float,
    private val onDown: (Long) -> Unit,
    private val onUp: (Long) -> Unit,
    private val onLevel: (Float) -> Unit = {}
) {

    private companion object {
        const val SR = 44100.0
        const val FRAME = 256          // 每帧约 5.8ms
        const val ON_FRAMES = 2        // 连续 2 帧确认按下
        const val OFF_FRAMES = 2       // 连续 2 帧确认抬起
        const val WARMUP_FRAMES = 20
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    /**
     * 采样时钟下的当前时间，与 onDown / onUp 的时间戳同一时钟。
     * 解码端的 tick 必须用它结算，否则主线程时钟比采样时钟超前一个缓冲区时间，
     * 会把字母内部间隔算大，导致同一个字母被拆成 E / T。
     */
    @Volatile
    var nowMs: Long = 0L
        private set

    // —— 带通滤波器系数（RBJ band-pass，Q=4，滤波器状态跨帧保留）——
    private val w0 = 2.0 * PI * detectorHz / SR
    private val alpha = sin(w0) / (2.0 * 4.0)
    private val a0 = 1.0 + alpha
    private val b0 = alpha / a0
    private val b2 = -alpha / a0
    private val a1 = -2.0 * cos(w0) / a0
    private val a2 = (1.0 - alpha) / a0

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SR.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = max(minBuf * 2, (SR / 4 * 2).toInt())

        // 优先 UNPROCESSED（最干净），其次 MIC；VOICE_RECOGNITION 常带语音降噪，
        // 会把持续的 CW 音当噪声抑制，放在最后兜底。
        val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        } else {
            intArrayOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }

        var rec: AudioRecord? = null
        for (src in sources) {
            try {
                rec = AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SR.toInt())
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .build()
                if (rec.state == AudioRecord.STATE_INITIALIZED) break
                rec.release()
                rec = null
            } catch (_: Exception) { /* try next */ }
        }

        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            Log.e("ToneKeyDetector", "all audio sources failed")
            return false
        }

        record = rec
        running = true
        try {
            rec.startRecording()
        } catch (e: Exception) {
            Log.e("ToneKeyDetector", "startRecording failed", e)
            running = false
            rec.release()
            record = null
            return false
        }
        thread = Thread { loop(rec) }.also {
            it.isDaemon = true
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
        return true
    }

    fun stop() {
        running = false
        try { record?.stop() } catch (_: Exception) {}
        thread?.interrupt()
        thread = null
        try { record?.release() } catch (_: Exception) {}
        record = null
    }

    private fun loop(rec: AudioRecord) {
        val buf = ShortArray(FRAME)
        var framesRead = 0L
        val startedAt = SystemClock.elapsedRealtime()

        // 目标音调上的自适应噪声底（带通后的 RMS）与实测噪声比值
        var bandFloor = 0.0002f
        var noiseRatio = 0.10f
        var warmup = WARMUP_FRAMES

        var isOn = false
        var onRun = 0
        var offRun = 0
        var emittedDown = false
        var levelTick = 0

        // 滤波器状态（跨帧保留）
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        val sens = sensitivity.coerceIn(0f, 1f)
        // 灵敏度越高 → 门限倍数越小、比值要求越松
        val onFactor = 2.2f - 0.9f * sens
        val holdFactor = 1.6f - 0.4f * sens
        val ratioGate = 0.20f - 0.05f * sens

        try {
            while (running) {
                val read = rec.read(buf, 0, buf.size)
                if (read <= 0) continue
                framesRead += read
                val now = startedAt + framesRead * 1000L / SR.toLong()
                nowMs = now
                val frameMs = read * 1000.0 / SR

                // 去直流后的总能量
                var sum = 0.0
                var sumSq = 0.0
                // 带通后的能量
                var bpSq = 0.0

                for (i in 0 until read) {
                    val x0 = buf[i] / 32768.0
                    sum += x0
                    sumSq += x0 * x0

                    val y0 = b0 * x0 + b2 * x2 - a1 * y1 - a2 * y2
                    bpSq += y0 * y0
                    x2 = x1
                    x1 = x0
                    y2 = y1
                    y1 = y0
                }

                val dc = sum / read
                val meanSq = (sumSq / read - dc * dc).coerceAtLeast(0.0)
                val level = sqrt(meanSq)
                val bpLevel = sqrt(bpSq / read)
                val ratio = bpLevel / (level + 1e-9)

                val bandGateOn = max(bandFloor * onFactor, 1.5e-4f)
                val bandGateHold = max(bandFloor * holdFactor, 1.0e-4f)
                // 比值门限跟随实测噪声比值浮动：底噪大时自动收紧，安静时放宽
                val onRatio = max(ratioGate, noiseRatio * 1.6f)
                val holdRatio = max(ratioGate * 0.85f, noiseRatio * 1.3f)
                val lit = if (isOn) {
                    bpLevel > bandGateHold && ratio > holdRatio
                } else {
                    bpLevel > bandGateOn && ratio > onRatio
                }

                if (lit) {
                    onRun++
                    offRun = 0
                } else {
                    offRun++
                    onRun = 0
                    // 只在确认静音时学习噪声底，信号期间不会把门限抬高
                    if (!isOn) {
                        bandFloor = max(bandFloor * 0.9f + bpLevel.toFloat() * 0.1f, 5e-5f)
                        noiseRatio = noiseRatio * 0.9f + ratio.toFloat() * 0.1f
                    }
                }

                if (warmup > 0) {
                    warmup--
                    continue
                }

                // —— 双帧去抖，回填真实边沿 ——
                if (!isOn && onRun >= ON_FRAMES) {
                    isOn = true
                    emittedDown = true
                    onDown(now - ((onRun - 1) * frameMs).toLong())
                } else if (isOn && offRun >= OFF_FRAMES) {
                    isOn = false
                    if (emittedDown) onUp(now - ((offRun - 1) * frameMs).toLong())
                    emittedDown = false
                }

                // —— 电平回调（每 4 帧约 23ms，够界面用，又不至于频繁重组）——
                levelTick++
                if (levelTick >= 4) {
                    levelTick = 0
                    onLevel(level.toFloat().coerceIn(0f, 1f))
                }
            }
        } catch (e: Exception) {
            if (running) Log.e("ToneKeyDetector", "loop failed", e)
        } finally {
            if (isOn && emittedDown) onUp(if (nowMs > 0L) nowMs else SystemClock.elapsedRealtime())
        }
    }
}
