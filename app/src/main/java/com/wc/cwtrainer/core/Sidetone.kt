package com.wc.cwtrainer.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** 发码时的侧音（实时通断，低延迟） */
class Sidetone {

    @Volatile
    var frequency: Double = 700.0

    @Volatile
    var volume: Float = 0.9f

    @Volatile
    var gated: Boolean = false

    private val lock = Any()
    private var running = false
    private var thread: Thread? = null

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            thread = Thread { loop() }.also {
                it.isDaemon = true
                it.priority = Thread.MAX_PRIORITY
                it.start()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            thread?.interrupt()
            thread = null
        }
        gated = false
    }

    private fun loop() {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                CwRenderer.SR,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
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
            track.play()

            val chunk = ShortArray(512)
            var phase = 0.0
            while (running) {
                val w = 2.0 * PI * frequency / CwRenderer.SR
                val amp = if (gated) volume.coerceIn(0f, 1f) * 0.5f else 0f
                for (i in chunk.indices) {
                    chunk[i] = (sin(phase) * amp * 32767f).toInt().toShort()
                    phase += w
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
                track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: Exception) {
            Log.e("Sidetone", "loop failed", e)
        } finally {
            try {
                track?.stop()
            } catch (_: Exception) {
            }
            try {
                track?.release()
            } catch (_: Exception) {
            }
        }
    }
}
