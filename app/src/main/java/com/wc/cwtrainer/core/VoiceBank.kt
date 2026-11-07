package com.wc.cwtrainer.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 内置真人语音库：assets/ssb/<locale>/ 下是按单词切好的神经网络真人语音
 * （Edge-TTS 预生成，每种语言一个音色），运行时不依赖网络与设备 TTS。
 *
 * 用某国母语音色读英文单词，天然带上该国口音；德语/意大利语/西班牙语
 * 台站则直接使用本国字母解释法单词，由本国音色朗读。
 */
class VoiceBank(context: Context) {

    private val assets = context.applicationContext.assets
    private val banks = HashMap<String, Map<String, String>>()
    private val cache = HashMap<String, ShortArray>()

    private var cachedKey: String? = null
    private var cachedPcm: ShortArray? = null

    private var track: AudioTrack? = null
    private var token = 0

    var isPlaying by mutableStateOf(false)
        private set

    val available: Boolean get() = index(FALLBACK).isNotEmpty()

    private fun index(lang: String): Map<String, String> = banks.getOrPut(lang) { loadIndex(lang) }

    private fun loadIndex(lang: String): Map<String, String> = runCatching {
        val text = assets.open("ssb/$lang/index.json").bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k, json.getString(k))
            }
        }
    }.getOrDefault(emptyMap())

    /**
     * 按单词顺序拼成一句话并播放。
     * @param voiceKey 音色键（en-US / en-US-f / de-DE-s ...），缺失的词逐级回退
     * @param rate 语速倍率（0.9~1.1），通过重采样实现
     */
    suspend fun play(words: List<String>, voiceKey: String, rate: Float, volume: Float): Result<Unit> =
        withContext(Dispatchers.Default) {
            val key = "$voiceKey#${words.joinToString("|")}#${"%.2f".format(rate)}"
            val pcm = if (key == cachedKey) cachedPcm else build(words, voiceKey, rate)
                ?.also { cachedKey = key; cachedPcm = it }
            if (pcm == null) return@withContext Result.failure(IllegalStateException("voice bank miss"))
            playPcm(pcm, volume)
            Result.success(Unit)
        }

    private fun build(words: List<String>, voiceKey: String, rate: Float): ShortArray? {
        if (words.isEmpty()) return null
        val n = words.size
        val reportIdx = words.indexOfFirst { it == "five nine" || it == "five nine nine" }
        // words = "This is" + 呼号 + 呼号 + 报告 + 序号
        // reportIdx = 1 + 2 * callLen，所以呼号词数必须除以 2
        val callLen = if (reportIdx > 1) (reportIdx - 1) / 2 else 0
        val secondCall = if (callLen > 0) 1 + callLen else -1
        val serialStart = if (reportIdx >= 0) reportIdx + 1 else n

        val parts = ArrayList<ShortArray>(n)
        val gaps = IntArray(n)
        for ((i, w) in words.withIndex()) {
            val raw = wordPcm(w, voiceKey) ?: return null

            // 语调轮廓：句首略高、句尾略低（自然人声的降调），重报呼号稍提一点再落下来；
            // 用重采样实现，幅度只有 ±3%，听感是语气变化而不是变调。
            val p = i.toFloat() / (n - 1).coerceAtLeast(1)
            var r = rate * (1.03f - 0.07f * p)
            if (secondCall in 1..reportIdx && i >= secondCall && i < reportIdx) r *= 1.02f
            if (i >= serialStart) r *= 0.98f
            r = r.coerceIn(0.82f, 1.22f)
            val targetSr = (SR * r).roundToInt()
            val seg = if (targetSr == SR) raw.copyOf() else RadioDsp.resample(raw, SR, targetSr)

            // 逐词音量随句尾轻微收敛，避免每个词都同样响的“机关枪”感
            val gain = (1.00f - 0.10f * p).coerceIn(0.85f, 1f)
            val fade = (0.008f * SR).toInt().coerceAtMost(seg.size / 2)
            for (k in seg.indices) {
                var v = seg[k] * gain
                if (k < fade) v *= k.toFloat() / fade
                val tailIdx = seg.size - 1 - k
                if (tailIdx < fade) v *= tailIdx.toFloat() / fade
                seg[k] = v.toInt().coerceIn(-32768, 32767).toShort()
            }
            parts.add(seg)

            gaps[i] = when {
                i == 0 -> 0
                i == 1 -> 200                    // “This is” 后略停再报呼号
                i == secondCall -> 300           // 呼号重报前喘口气
                i == reportIdx -> 240            // 报信号报告前
                i == serialStart -> 220          // 报序号前
                i >= serialStart -> 115          // 序号逐位，读清楚
                else -> 55 + ((i * 7) % 4) * 7   // 呼号字母 55~76ms，带轻微节奏变化
            }
        }

        val lead = ShortArray((0.04f * SR).toInt())
        val tail = ShortArray((0.08f * SR).toInt())
        var total = lead.size + tail.size
        for (i in parts.indices) total += parts[i].size + (if (i == 0) 0 else (gaps[i] * SR / 1000))
        val out = ShortArray(total)
        var pos = 0
        lead.copyInto(out, pos); pos += lead.size
        for (i in parts.indices) {
            if (i > 0) pos += gaps[i] * SR / 1000
            parts[i].copyInto(out, pos); pos += parts[i].size
        }
        tail.copyInto(out, pos)

        val shaped = RadioDsp.shape(out, SR)
        val fade = (0.01f * SR).toInt().coerceAtMost(shaped.size / 2)
        for (i in 0 until fade) {
            val g = i.toFloat() / fade
            shaped[i] = (shaped[i] * g).toInt().toShort()
            val j = shaped.size - 1 - i
            shaped[j] = (shaped[j] * g).toInt().toShort()
        }
        return shaped
    }

    private fun wordPcm(word: String, voiceKey: String): ShortArray? {
        val key = "$voiceKey|$word"
        cache[key]?.let { return it }
        for (folder in folders(voiceKey)) {
            val file = index(folder)[word] ?: continue
            val decoded = decodeMp3("ssb/$folder/$file") ?: continue
            val (rate, pcm) = decoded
            val fixed = if (rate == SR) pcm else RadioDsp.resample(pcm, rate, SR)
            cache[key] = fixed
            return fixed
        }
        return null
    }

    /**
     * 用系统 MP3 解码器把 24kHz 词音频解成 PCM。
     * 词库保持 MP3（体积只有 8kHz WAV 的 1/3），但音质是完整 24kHz，
     * 听感与试听小样一致，不再有 8kHz 的“闷”。
     */
    private fun decodeMp3(path: String): Pair<Int, ShortArray>? = runCatching {
        assets.openFd(path).use { fd ->
            val extractor = MediaExtractor()
            var dec: MediaCodec? = null
            var sampleRate = SRC_SR
            try {
                extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                var track = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        track = i; format = f; break
                    }
                }
                if (track < 0 || format == null) return@use null
                extractor.selectTrack(track)
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val codec = MediaCodec.createDecoderByType(mime)
                dec = codec
                codec.configure(format, null, null, 0)
                codec.start()

                val chunks = ArrayList<ShortArray>(8)
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var guard = 0
                while (!outputDone && guard++ < 4000) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(5000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outIdx = codec.dequeueOutputBuffer(info, 5000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                            sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)

                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                        else -> if (outIdx >= 0) {
                            val ob = codec.getOutputBuffer(outIdx)!!
                            ob.position(info.offset)
                            ob.limit(info.offset + info.size)
                            val sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            if (sb.remaining() > 0) {
                                val a = ShortArray(sb.remaining())
                                sb.get(a)
                                chunks.add(a)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
                var total = 0
                for (c in chunks) total += c.size
                if (total == 0) return@use null
                val out = ShortArray(total)
                var p = 0
                for (c in chunks) {
                    c.copyInto(out, p)
                    p += c.size
                }
                sampleRate to out
            } finally {
                try { dec?.stop() } catch (_: Exception) {}
                try { dec?.release() } catch (_: Exception) {}
                extractor.release()
            }
        }
    }.getOrNull()

    /** 音色键 → 词库目录，逐级回退：de-DE-f → de-DE → en-US */
    private fun folders(voiceKey: String): List<String> {
        val base = if (voiceKey.endsWith("-f") || voiceKey.endsWith("-s")) voiceKey.dropLast(2) else voiceKey
        return listOf(voiceKey, base, FALLBACK).distinct()
    }

    private suspend fun playPcm(pcm: ShortArray, volume: Float) {
        stopTrack()
        val myToken = ++token
        val t = try {
            val minBuf = AudioTrack.getMinBufferSize(
                SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SR)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBuf.coerceAtLeast(8192))
                .build()
        } catch (e: Exception) {
            Log.e("VoiceBank", "init failed", e)
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
            if (token == myToken) delay(pcm.size.toLong() * 1000L / SR + 100L)
        } catch (e: Exception) {
            Log.e("VoiceBank", "play failed", e)
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

    private companion object {
        // 词库为 24kHz MP3（解码后使用），保持与试听小样一致的清晰度
        const val SR = 24000
        const val SRC_SR = 24000
        const val FALLBACK = "en-US"
    }
}
