package com.wc.cwtrainer.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.wc.cwtrainer.MainActivity
import com.wc.cwtrainer.R

/**
 * 播放控制总线：CwAudio 在开播时把自己注册为 [Controller]，
 * 通知栏 / 蓝牙耳机 / 锁屏的媒体按钮通过这里回调到正在播放的实例。
 */
object PlaybackBus {

    interface Controller {
        /** 暂停成功返回 true */
        fun pause(): Boolean
        /** 恢复成功返回 true */
        fun resume(): Boolean
        /** 彻底停止并释放 */
        fun stop()
    }

    @Volatile
    var controller: Controller? = null
}

/**
 * 前台播放服务：让听抄播报在锁屏 / 后台继续，并提供通知栏与媒体按键控制。
 *
 * 只负责「保持前台 + 展示控制」，音频渲染仍由 [CwAudio] 完成；
 * 播放结束或停止后服务自行退出并移除通知。
 */
class PlaybackService : Service() {

    private var session: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var title = "CW 练习"
    private var subtitle = "电报音频播放中"
    private var paused = false

    override fun onCreate() {
        super.onCreate()
        running = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        createChannel()
        session = MediaSession(this, "CwTrainerPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = togglePause(requested = false)
                override fun onPause() = togglePause(requested = true)
                override fun onStop() {
                    PlaybackBus.controller?.stop()
                    finish()
                }
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_STOP
                    )
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build()
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> togglePause(requested = null)
            ACTION_STOP -> {
                PlaybackBus.controller?.stop()
                finish()
            }
            ACTION_FINISH -> finish()
            else -> {
                intent?.getStringExtra(EXTRA_TITLE)?.let { title = it }
                intent?.getStringExtra(EXTRA_SUBTITLE)?.let { subtitle = it }
                paused = false
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        session?.isActive = false
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** [requested] 为 null 表示切换；否则强制暂停(true)或恢复(false) */
    private fun togglePause(requested: Boolean?) {
        val controller = PlaybackBus.controller ?: return
        val wantPause = requested ?: !paused
        val ok = if (wantPause) controller.pause() else controller.resume()
        if (!ok) return
        paused = wantPause
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (paused) 0f else 1f
                )
                .build()
        )
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAction = Notification.Action.Builder(
            Icon.createWithResource(
                this,
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            ),
            if (paused) "继续" else "暂停",
            toggleIntent
        ).build()
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "停止",
            stopIntent
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(if (paused) "已暂停 · $subtitle" else subtitle)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "播放控制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "听抄播放时的通知栏控制"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_TOGGLE = "com.wc.cwtrainer.action.TOGGLE_PLAYBACK"
        const val ACTION_STOP = "com.wc.cwtrainer.action.STOP_PLAYBACK"
        const val ACTION_FINISH = "com.wc.cwtrainer.action.FINISH_PLAYBACK"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val CHANNEL_ID = "cw_playback"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        private var running = false

        /** 开播：拉起前台服务并显示通知（必须在应用可见时调用） */
        fun start(context: Context, title: String, subtitle: String) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = "com.wc.cwtrainer.action.START_PLAYBACK"
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            runCatching { context.startForegroundService(intent) }
        }

        /** 播放自然结束或主动停止：移除通知并退出前台 */
        fun dismiss(context: Context) {
            if (!running) return
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_FINISH)
            runCatching { context.startService(intent) }
        }
    }
}
