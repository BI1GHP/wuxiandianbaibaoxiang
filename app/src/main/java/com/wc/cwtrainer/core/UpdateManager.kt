package com.wc.cwtrainer.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Release 自更新模块
 *
 * 工作流程：
 * 1. 查询 GitHub API: https://api.github.com/repos/BI1GHP/wuxiandianbaibaoxiang/releases/latest
 * 2. 解析 tag_name (如 "V1.2") 和 assets 中的 .apk 文件
 * 3. 对比 versionCode，有新版本就下载
 * 4. Android 8+ 需要先请求 INSTALL_UNKNOWN_APPS 权限
 * 5. 通过 FileProvider 触发系统安装器
 *
 * 依赖：implementation "androidx.core:core-ktx:1.13.1" (已有)
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val OWNER = "BI1GHP"
    private const val REPO = "wuxiandianbaibaoxiang"
    private const val GITHUB_LATEST = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class ReleaseInfo(
        val versionName: String,       // e.g. "V1.2"
        val versionCode: Int,          // 从 tag_name 解析或取最大 asset 版本
        val htmlUrl: String,           // Release 页面
        val apkUrl: String?,           // APK 直接下载链接
        val apkSizeBytes: Long,        // APK 大小
        val body: String               // 更新说明
    )

    data class UpdateResult(
        val hasUpdate: Boolean,
        val info: ReleaseInfo? = null,
        val error: String? = null
    )

    /**
     * 查询 GitHub 最新 Release
     * 在 IO 线程调用
     */
    suspend fun checkUpdate(currentCode: Int): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(GITHUB_LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "wc-trainer")
            }
            if (conn.responseCode != 200) {
                return@withContext UpdateResult(false, error = "HTTP ${conn.responseCode}")
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)

            val tag = root.optString("tag_name", "")
            val htmlUrl = root.optString("html_url", "")
            val body = root.optString("body", "")

            // 找 assets 里第一个 .apk
            var apkUrl: String? = null
            var apkSize: Long = 0L
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", null)
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            // 解析版本号：tag_name 可能是 "V1.2" 或 "v1.2.0" 或 "v1.2(3)"
            val versionCode = parseVersionCode(tag)
            val info = ReleaseInfo(
                versionName = tag,
                versionCode = versionCode,
                htmlUrl = htmlUrl,
                apkUrl = apkUrl,
                apkSizeBytes = apkSize,
                body = body
            )

            UpdateResult(info.versionCode > currentCode, info = info)
        } catch (e: Exception) {
            Log.e(TAG, "checkUpdate failed", e)
            UpdateResult(false, error = e.message)
        }
    }

    /** 从 tag_name 解析版本号：V1.2 → 120, V1.2.3 → 123 */
    private fun parseVersionCode(tag: String): Int {
        val digits = tag.filter { it.isDigit() }
        // V1.2 -> 12, V1.2.3 -> 123, 如果只有 2 位补零到 3 位
        val padded = digits.padEnd(3, '0')
        return padded.toIntOrNull() ?: 0
    }

    /**
     * 下载 APK 到系统 Download 目录
     * 返回下载的 File
     * 在 IO 线程调用
     */
    suspend fun downloadApk(context: Context, apkUrl: String): File = withContext(Dispatchers.IO) {
        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "wc-trainer")
        }

        val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "wc-updates")
        }
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val apkFile = File(downloadsDir, "wc-update.apk")
        if (apkFile.exists()) apkFile.delete()

        conn.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        apkFile
    }

    /**
     * 安装 APK
     * Android 8+ 需要先调 requestInstallPermission()
     */
    fun installApk(context: Context, apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * 请求"允许安装未知应用"权限（Android 8+）
     * 在设置里打开的 Intent
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (context.packageManager.canRequestPackageInstalls()) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 当前是否可以安装未知应用 */
    fun canInstallUnknownApp(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }
}
