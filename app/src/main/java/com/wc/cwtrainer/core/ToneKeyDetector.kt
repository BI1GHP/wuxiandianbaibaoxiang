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
import kotlin.math.sqrt

/**
 * 通过麦克风/音频接口检测外部手键或自动键的通断。
 *
 * 使用方式：外部键控音源（电台侧音、带侧音输出的键控器、USB 声卡）接入手机
 * 3.5mm 麦克风输入或 USB 音频输入，APP 检测设定频率上的音调能量，转换为
 * key down / key up 事件。
 *
 * 识别优化（v2）：
 * - AudioSource.UNPROCESSED/VOICE_RECOGNITION 获取干净音频（避免 AGC/降噪）
 * - 极灵敏的音调检测：toneRatio 0.03 就触发
 * - 单帧确认（最快 ~11ms，FRAME=512）
 * - 自适应阈值：噪声底快追踪
 */
class ToneKeyDetector(
    private val detectorHz: Double,
    private val sensitivity: Float,
    private val onDown: (Long) -> Unit,
    private val onUp: (Long) -> Unit,
    private val onLevel: (Float) -> Unit = {}
) {

    private companion object {
        const val SR = 44100
        const val FRAME = 512   // 每帧 ~11.6ms
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SR,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = max(minBuf * 2, SR / 4 * 2)

        // 优先用 UNPROCESSED（最干净），fallback 到 VOICE_RECOGNITION，再 fallback MIC
        val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        } else {
            intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        }

        var rec: AudioRecord? = null
        for (src in sources) {
            try {
                rec = AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SR)
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

        // 启动时快速建立噪声底（前 20 帧）
        var noiseFloor = 0.004f
        var warmup = 20

        var isOn = false
        var onCount = 0
        var offCount = 0
        var emittedDown = false
        var levelTick = 0

        try {
            while (running) {
                val read = rec.read(buf, 0, buf.size)
                if (read <= 0) continue
                framesRead += read
                val now = startedAt + framesRead * 1000L / SR

                // —— RMS 能量（整帧）——
                var sumSq = 0.0
                for (i in 0 until read) {
                    val x = buf[i].toDouble()
                    sumSq += x * x
                }
                val meanSq = sumSq / read
                val level = sqrt(meanSq) / 32768.0

                // —— 音调能量检测（IIR 窄带滤波器，Q ≈ 20）——
                var s1 = 0.0
                var s2 = 0.0
                val w = 2.0 * PI * detectorHz / SR
                val coeff = 2.0 * cos(w)
                for (i in 0 until read) {
                    val s0 = buf[i].toDouble() + coeff * s1 - s2
                    s2 = s1
                    s1 = s0
                }
                val tonePower = (s1 * s1 + s2 * s2 - coeff * s1 * s2) / (read.toDouble() * read.toDouble())
                val toneRatio = tonePower / (meanSq + 1e-9)

                // —— 自适应阈值（非常灵敏）——
                // sensitivity 0→1 线性映射阈值
                val sensFactor = 2.0f - sensitivity * 1.5f   // sens=0 → 2.0; sens=1 → 0.5
                val rmsThreshold = max(noiseFloor * sensFactor, 0.0025f)
                val toneThreshold = 0.03   // toneRatio > 0.03（3% 能量在目标频率）

                // 双条件触发：RMS + 音调占比
                val lit = level > rmsThreshold && toneRatio > toneThreshold

                if (lit) {
                    onCount++
                    offCount = 0
                    if (!isOn) noiseFloor = noiseFloor * 0.9f + level.toFloat() * 0.1f
                } else {
                    offCount++
                    onCount = 0
                    if (!isOn) {
                        // 快速噪声底追踪（只在静音时）
                        noiseFloor = noiseFloor * 0.94f + level.toFloat() * 0.06f
                    }
                }
                if (warmup > 0) {
                    warmup--
                    if (!lit) noiseFloor = noiseFloor * 0.92f + level.toFloat() * 0.08f
                    continue
                }

                // —— 单帧确认就触发（最快 ~11.6ms）——
                if (!isOn && onCount >= 1) {
                    isOn = true
                    emittedDown = true
                    onDown(now)
                } else if (isOn && offCount >= 2) {
                    isOn = false
                    if (emittedDown) onUp(now)
                    emittedDown = false
                }

                // —— 电平回调（每 2 帧 ~23ms）——
                levelTick++
                if (levelTick >= 2) {
                    levelTick = 0
                    onLevel(level.toFloat().coerceIn(0f, 1f))
                }
            }
        } catch (e: Exception) {
            if (running) Log.e("ToneKeyDetector", "loop failed", e)
        } finally {
            if (isOn && emittedDown) onUp(SystemClock.elapsedRealtime())
        }
    }
}
