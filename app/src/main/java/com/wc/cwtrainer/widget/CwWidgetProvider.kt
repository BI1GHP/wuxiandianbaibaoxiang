package com.wc.cwtrainer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.wc.cwtrainer.AppScreen
import com.wc.cwtrainer.MainActivity
import com.wc.cwtrainer.R

/**
 * 桌面小组件：S 表风格的码速面板。
 *
 * - 显示当前听抄码速与最高正确率
 * - 底部按钮一键进入最近一次训练科目
 * 数据与 App 共用 SharedPreferences，无需额外存储；在 MainActivity.onResume 刷新。
 */
class CwWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = buildViews(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {

        /** 设置变更 / 回到前台时刷新所有实例 */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CwWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences("wc_trainer", Context.MODE_PRIVATE)
            val stats = context.getSharedPreferences("wc_stats", Context.MODE_PRIVATE)

            val wpm = prefs.getInt("copyWpm", 20)
            val best = stats.getFloat("copyBest", 0f)
            val screen = runCatching {
                AppScreen.valueOf(prefs.getString("lastScreen", "COPY") ?: "COPY")
            }.getOrDefault(AppScreen.COPY)
            val target = if (screen in setOf(
                    AppScreen.COPY, AppScreen.LEARN, AppScreen.CONTEST,
                    AppScreen.TRANSLATOR, AppScreen.EXAM
                )
            ) screen else AppScreen.COPY

            val views = RemoteViews(context.packageName, R.layout.widget_cw)
            views.setTextViewText(R.id.tvWpm, wpm.toString())
            views.setTextViewText(
                R.id.tvBest,
                if (best <= 0f) "最高 —" else "最高 ${(best * 100).toInt()}%"
            )
            views.setTextViewText(R.id.tvMode, modeLabel(target))
            views.setTextViewText(R.id.tvContinue, "继续：${subjectLabel(target)}")

            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("screen", target.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tvContinue, pending)
            views.setOnClickPendingIntent(R.id.widgetRoot, pending)
            return views
        }

        private fun subjectLabel(screen: AppScreen): String = when (screen) {
            AppScreen.COPY -> "听抄练习"
            AppScreen.LEARN -> "背码训练"
            AppScreen.CONTEST -> "赛事听抄"
            AppScreen.TRANSLATOR -> "CW 翻译器"
            AppScreen.EXAM -> "考证刷题"
            else -> "听抄练习"
        }

        private fun modeLabel(screen: AppScreen): String = when (screen) {
            AppScreen.COPY -> "COPY · RX"
            AppScreen.LEARN -> "LEARN · KEY"
            AppScreen.CONTEST -> "CONTEST · QSO"
            AppScreen.TRANSLATOR -> "TRANSLATOR"
            AppScreen.EXAM -> "EXAM · A/B/C"
            else -> "COPY · RX"
        }
    }
}
