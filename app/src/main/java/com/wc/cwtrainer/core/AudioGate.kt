package com.wc.cwtrainer.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 统一音频焦点管理。
 *
 * 各发声源在开播时 [acquire]，停止时 [release]。来电、其它应用播放音乐等
 * 抢占焦点时回调各自的 onLoss，让它们立刻停声并复位 UI 状态，
 * 避免「焦点丢了但 AudioTrack 还在写」导致通话中继续出声。
 */
object AudioGate {

    /** 最近一次被打断的次数，UI 可观察它来复位播放状态 */
    var interruption by mutableStateOf(0)
        private set

    private val listeners = LinkedHashMap<String, () -> Unit>()
    private var focusRequest: AudioFocusRequest? = null
    private var manager: AudioManager? = null
    private var deniedLogged = false

    private val onFocusChange = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val pending = synchronized(listeners) {
                    // 先清空再回调：onLoss 里各自的 stop() 还会调用 release，避免重复处理
                    val snapshot = listeners.values.toList()
                    listeners.clear()
                    val am = manager
                    val request = focusRequest
                    focusRequest = null
                    manager = null
                    if (am != null && request != null) {
                        runCatching { am.abandonAudioFocusRequest(request) }
                    }
                    snapshot
                }
                interruption += 1
                pending.forEach { runCatching { it() } }
            }
        }
    }

    /** 申请焦点并登记打断回调；重复调用只更新回调内容 */
    fun acquire(context: Context, tag: String, onLoss: () -> Unit) {
        val am = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        synchronized(listeners) {
            listeners[tag] = onLoss
            if (focusRequest != null) return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                // 电报音/语音训练不适合被闪避播放，宁可被暂停
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(onFocusChange)
                .build()
            if (am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = request
                manager = am
            } else if (!deniedLogged) {
                // 焦点被拒（如通话中）不阻断训练，仅记录一次
                deniedLogged = true
                Log.w("AudioGate", "audio focus denied, playback continues without focus")
            }
        }
    }

    /** 释放登记；没有其它发声源时才真正放弃焦点 */
    fun release(tag: String) {
        synchronized(listeners) {
            listeners.remove(tag)
            if (listeners.isNotEmpty()) return
            val am = manager
            val request = focusRequest
            focusRequest = null
            manager = null
            if (am != null && request != null) {
                runCatching { am.abandonAudioFocusRequest(request) }
            }
        }
    }
}
