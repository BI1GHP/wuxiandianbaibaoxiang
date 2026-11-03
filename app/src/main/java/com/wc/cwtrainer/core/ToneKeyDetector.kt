package com.wc.cwtrainer.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
        const val FRAME = 512
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
        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SR)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf * 2, SR / 4 * 2))
                .build()
        } catch (e: Exception) {
            Log.e("ToneKeyDetector", "build failed", e)
            return false
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
        var noiseFloor = 0.004f
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

                var sumSq = 0.0
                for (i in 0 until read) {
                    val x = buf[i].toDouble()
                    sumSq += x * x
                }
                val meanSq = sumSq / read
                val level = sqrt(meanSq) / 32768.0

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

                val threshold = max(noiseFloor * 3.2f, 0.006f) * (1.6f - 1.0f * sensitivity)
                val lit = level > threshold && toneRatio > 0.08

                if (lit) {
                    onCount++
                    offCount = 0
                } else {
                    offCount++
                    onCount = 0
                    if (!isOn) {
                        noiseFloor = noiseFloor * 0.97f + level.toFloat() * 0.03f
                    }
                }

                if (!isOn && onCount >= 2) {
                    isOn = true
                    emittedDown = true
                    onDown(now)
                } else if (isOn && offCount >= 2) {
                    isOn = false
                    if (emittedDown) onUp(now)
                    emittedDown = false
                }

                levelTick++
                if (levelTick >= 5) {
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
