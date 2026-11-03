package com.wc.cwtrainer.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** 系统 TTS 封装：用于模仿各国口音的 SSB 播报 */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private val ready = CompletableDeferred<Boolean>()
    private var engineOk = false
    private var pending: CompletableDeferred<Boolean>? = null
    private var pendingId: String? = null
    private var counter = 0

    var available by mutableStateOf(false)
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            engineOk = status == TextToSpeech.SUCCESS
            available = engineOk
            if (engineOk) {
                runCatching { tts?.setOnUtteranceProgressListener(listener) }
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

    suspend fun speak(text: String, localeTag: String, rate: Float, pitch: Float): Boolean {
        withTimeoutOrNull(3000) { ready.await() }
        val engine = tts ?: return false
        if (!engineOk) return false

        val wanted = Locale.forLanguageTag(localeTag)
        val lang = if (runCatching { engine.isLanguageAvailable(wanted) }.getOrDefault(-1) >= 0) {
            wanted
        } else {
            Locale.US
        }
        runCatching { engine.setLanguage(lang) }
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
            return false
        }
        val ok = withTimeoutOrNull(45_000) { deferred.await() }
        return ok ?: true
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

    fun engineAvailable(): Boolean = engineOk
}
