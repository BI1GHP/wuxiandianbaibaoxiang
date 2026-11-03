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
 * 识别优化：
 * - AudioSource.UNPROCESSED/VOICE_RECOGNITION 获取干净音频（避免 AGC/降噪）
 * - 更灵敏的音调检测阈值（toneRatio 从 0.08 降到 0.03）
 * - 帧确认从 2 降到 1（降低延迟）
 * - 更快的噪声追踪
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
        const val FRAME = 256      // 256 样本 = ~5.8ms（比 512 延迟减半）
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
        // 缓冲区稍大一点避免 underrun，但不要太大增加延迟
        val bufSize = max(minBuf * 2, SR / 2 * 2)

        // 优先用 UNPROCESSED（最干净），fallback 到 VOICE_RECOGNITION
        val src = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> MediaRecorder.AudioSource.UNPROCESSED
            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }

        val rec = try {
            AudioRecord.Builder()
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
        } catch (e: Exception) {
            Log.w("ToneKeyDetector", "UNPROCESSED failed, try VOICE_RECOGNITION", e)
            tryBuildFallback(bufSize) ?: return false
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
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

    @SuppressLint("MissingPermission")
    private fun tryBuildFallback(bufSize: Int): AudioRecord? {
        return try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SR)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .build()
        } catch (_: Exception) { null }
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        thread?.interrupt()
        thread = null
        try {
            record?.release()
        } catch (_: Exception) {
        }
        record = null
    }

    private fun loop(rec: AudioRecord) {
        val buf = ShortArray(FRAME)
        var framesRead = 0L
        val startedAt = SystemClock.elapsedRealtime()

        // 启动时快速建立噪声底（前 15 帧，~90ms）
        var noiseFloor = 0.003f
        var warmup = 15

        var isOn = false
        var onCount = 0
        var offCount = 0
        var emittedDown = false
        var levelTick = 0
        var toneLevelTick = 0

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

                // —— 音调能量检测（IIR 窄带滤波器）——
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
                // toneRatio 范围：0~1，越接近 1 说明音调越纯

                // —— 自适应阈值 ——
                // sensitivity 0~1：越大越灵敏
                // 低灵敏度 → 阈值高；高灵敏度 → 阈值低
                val sensFactor = 2.4f - sensitivity * 1.8f  // sens=0 → 2.4；sens=1 → 0.6
                val rmsThreshold = max(noiseFloor * sensFactor, 0.003f)
                val toneThreshold = 0.035  // 音调能量占比（从 0.08 降到 0.035）

                // 双条件：RMS 能量够 AND 音调占比够
                val lit = level > rmsThreshold && toneRatio > toneThreshold

                if (lit) {
                    onCount++
                    offCount = 0
                } else {
                    offCount++
                    onCount = 0
                    if (!isOn) {
                        // 快速追踪噪声底（指数平均，alpha=0.15）
                        noiseFloor = noiseFloor * 0.85f + level.toFloat() * 0.15f
                    }
                }
                if (warmup > 0) {
                    warmup--
                    // 预热期间只更新噪声，不触发事件
                    if (!lit) noiseFloor = noiseFloor * 0.9f + level.toFloat() * 0.1f
                    continue
                }

                // —— 触发：1 帧确认（比原来的 2 帧延迟减半）——
                if (!isOn && onCount >= 1) {
                    isOn = true
                    emittedDown = true
                    onDown(now)
                } else if (isOn && offCount >= 1) {
                    isOn = false
                    if (emittedDown) onUp(now)
                    emittedDown = false
                }

                // —— 电平回调（每 3 帧 ~17ms）——
                levelTick++
                if (levelTick >= 3) {
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
