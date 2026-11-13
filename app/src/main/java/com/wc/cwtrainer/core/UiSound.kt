package com.wc.cwtrainer.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 全局音效入口：组件直接调用，无需持有 Context。
 * 由 AppState 在启动时 attach，设置里的开关通过 [enabled] 即时生效。
 */
object UiSounds {
    @Volatile
    private var player: UiSound? = null

    @Volatile
    var enabled: Boolean = true

    fun attach(sound: UiSound) {
        player = sound
    }

    fun click() {
        if (enabled) player?.click()
    }

    fun tick() {
        if (enabled) player?.tick()
    }
}

/**
 * 界面音效：继电器「咔嗒」与旋钮「嘀」。
 *
 * 不打包音频资源：首次使用时在 cache 目录生成两个极短的 16bit WAV，
 * 再用 SoundPool 预加载，延迟低、可叠加，体积只有几 KB。
 */
class UiSound(context: Context) {

    private val appContext = context.applicationContext
    private val random = Random(20251113)

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    @Volatile
    private var loaded = 0

    private val clickId: Int = load("ui_click.wav") { relayClick() }
    private val tickId: Int = load("ui_tick.wav") { knobTick() }

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) loaded += 1
        }
    }

    /** 继电器咔嗒：按键、主操作 */
    fun click() {
        if (loaded > 0 && clickId != 0) {
            runCatching { pool.play(clickId, 0.55f, 0.55f, 1, 0, 1f) }
        }
    }

    /** 旋钮嘀声：加减档、切换标签 */
    fun tick() {
        if (loaded > 0 && tickId != 0) {
            runCatching { pool.play(tickId, 0.35f, 0.35f, 1, 0, 1f) }
        }
    }

    fun release() {
        runCatching { pool.release() }
    }

    private fun load(name: String, generate: () -> ShortArray): Int = runCatching {
        val dir = File(appContext.cacheDir, "ui_sound")
        val file = File(dir, name)
        if (!file.exists()) {
            dir.mkdirs()
            file.writeBytes(toWav(generate()))
        }
        pool.load(file.absolutePath, 1)
    }.getOrDefault(0)

    /** 24ms 低通噪声爆发，模拟机械触点 */
    private fun relayClick(rate: Int = 22050): ShortArray {
        val count = rate * 24 / 1000
        val out = ShortArray(count)
        var low = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val env = exp(-9f * t)
            val noise = random.nextFloat() * 2f - 1f
            low += (noise - low) * 0.34f
            out[i] = (low * env * 0.75f * Short.MAX_VALUE)
                .toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** 12ms 高频「嘀」，模拟旋钮档位 */
    private fun knobTick(rate: Int = 22050): ShortArray {
        val count = rate * 12 / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val env = exp(-16f * t)
            val tone = sin(2.0 * PI * 1900.0 * i / rate).toFloat()
            val noise = (random.nextFloat() * 2f - 1f) * 0.18f
            out[i] = ((tone * 0.65f + noise) * env * 0.5f * Short.MAX_VALUE)
                .toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** 16bit 单声道 PCM → WAV 字节流 */
    private fun toWav(pcm: ShortArray, rate: Int = 22050): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF)
            out.write((v shr 24) and 0xFF)
        }
        fun i16(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }
        str("RIFF"); i32(36 + dataSize); str("WAVE")
        str("fmt "); i32(16); i16(1); i16(1)
        i32(rate); i32(rate * 2); i16(2); i16(16)
        str("data"); i32(dataSize)
        pcm.forEach { i16(it.toInt()) }
        return out.toByteArray()
    }
}
