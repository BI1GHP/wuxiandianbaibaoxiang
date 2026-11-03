package com.wc.cwtrainer.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * 系统 TTS 封装：返回 Result 告知调用方实际用了哪个语言播放，
 * 以便上层 UI 准确显示"实际播报国家"。
 *
 * 选语言策略：
 *  1. 目标 locale 在设备可用列表中 → 直接用
 *  2. 目标不可用 → 回退到同语系（de-DE → de-AT）或英语 en-US
 *  3. TTS 引擎完全不可用 → Result.failure
 */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private val ready = CompletableDeferred<Boolean>()
    private var engineOk = false
    private var pending: CompletableDeferred<Boolean>? = null
    private var pendingId: String? = null
    private var counter = 0

    var available by mutableStateOf(false)
        private set

    /** 当前设备 TTS 引擎可用的语言标签集合（设备启动后填充） */
    var availableLocales by mutableStateOf(setOf<String>())
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            engineOk = status == TextToSpeech.SUCCESS
            available = engineOk
            if (engineOk) {
                runCatching { tts?.setOnUtteranceProgressListener(listener) }
                // 收集设备实际可用的语言
                runCatching {
                    val all = tts?.availableLanguages ?: return@runCatching
                    val set = mutableSetOf<String>()
                    for (loc in all) {
                        if (loc != Locale.ROOT) set.add(loc.toLanguageTag())
                    }
                    availableLocales = set
                }
            }
            if (!ready.isCompleted) ready.complete(engineOk)
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId == pendingId) pending?.complete(true)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId == pendingId) pending?.complete(false)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId == pendingId) pending?.complete(false)
        }
    }

    /**
     * 播报文本。返回 [Result.success] 时附带实际使用的语言标签。
     * Result.failure 表示 TTS 引擎或语言不可用。
     */
    suspend fun speak(text: String, wantedTag: String, rate: Float, pitch: Float): Result<String> {
        withTimeoutOrNull(3000) { ready.await() }
        val engine = tts ?: return Result.failure(IllegalStateException("TTS not initialized"))
        if (!engineOk) return Result.failure(IllegalStateException("No TTS engine available"))

        // 策略：目标可用 → 目标；同语系可用 → 同语系；否则 en-US；再失败 → 失败
        val wanted = Locale.forLanguageTag(wantedTag)
        val engineSupports = { loc: Locale ->
            runCatching { engine.isLanguageAvailable(loc) }.getOrDefault(-1) >= 0
        }

        val actualLocale: Locale = when {
            engineSupports(wanted) -> wanted
            // 同语系兜底：比如 de-DE 不可用就找任意 de_*
            engineSupports(Locale(wanted.language)) -> Locale(wanted.language)
            engineSupports(Locale.US) -> Locale.US
            else -> return Result.failure(IllegalStateException("No supported TTS locale"))
        }
        val actualTag = actualLocale.toLanguageTag()

        runCatching { engine.setLanguage(actualLocale) }
        engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

        val deferred = CompletableDeferred<Boolean>()
        pending?.complete(false)
        pending = deferred
        val id = "utt-${++counter}"
        pendingId = id
        val res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (res != TextToSpeech.SUCCESS) {
            pending = null
            pendingId = null
            return Result.failure(IllegalStateException("speak() rejected by engine"))
        }
        val ok = withTimeoutOrNull(45_000) { deferred.await() }
        return if (ok != false) Result.success(actualTag)
        else Result.failure(IllegalStateException("utterance failed"))
    }

    fun stop() {
        runCatching { tts?.stop() }
        pending?.complete(false)
        pending = null
        pendingId = null
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
