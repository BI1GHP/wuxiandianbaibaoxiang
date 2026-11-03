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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 双源自动更新模块：GitHub + Gitee
 *
 * 查询顺序：GitHub Releases API → Gitee Releases API → Gitee Tags API（无 Release 时的 fallback）
 * 国内网络 GitHub 不通时自动切 Gitee，延迟 < 3s（GitHub connectTimeout=2s）。
 *
 * 发版约定：
 * - GitHub 发 Release + 上传 .apk asset
 * - Gitee 发 Release + 上传 .apk asset 或打 Tag 并挂载附件
 * - tag_name 格式统一 "V主.次.补丁"（如 V1.01, V1.02）
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val OWNER = "BI1GHP"
    private const val REPO = "wuxiandianbaibaoxiang"

    // 双源 API
    private const val GITHUB_RELEASES = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val GITEE_RELEASES = "https://gitee.com/api/v5/repos/$OWNER/$REPO/releases/latest"
    private const val GITEE_TAGS = "https://gitee.com/api/v5/repos/$OWNER/$REPO/tags"

    data class ReleaseInfo(
        val versionName: String,
        val versionCode: Int,
        val htmlUrl: String,
        val apkUrl: String?,
        val apkSizeBytes: Long,
        val body: String,
        val source: String   // "GitHub" / "Gitee" — 让用户知道从哪更的
    )

    data class UpdateResult(
        val hasUpdate: Boolean,
        val info: ReleaseInfo? = null,
        val error: String? = null
    )

    private data class Parsed(
        val tag: String, val htmlUrl: String, val body: String,
        val apkUrl: String?, val apkSize: Long
    )

    suspend fun checkUpdate(currentCode: Int): UpdateResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // —— 1. 先试 GitHub ——
        try {
            val parsed = fetchGitHub()
            if (parsed != null) {
                val code = parseVersionCode(parsed.tag)
                if (code > 0) {
                    val info = ReleaseInfo(
                        versionName = parsed.tag, versionCode = code,
                        htmlUrl = parsed.htmlUrl, apkUrl = parsed.apkUrl,
                        apkSizeBytes = parsed.apkSize, body = parsed.body, source = "GitHub"
                    )
                    if (info.apkUrl != null) {
                        Log.d(TAG, "GitHub hit: ${parsed.tag}, code=$code, cur=$currentCode")
                        return@withContext UpdateResult(code > currentCode, info = info)
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("GitHub: ${e.message}")
            Log.w(TAG, "GitHub failed, falling back to Gitee", e)
        }

        // —— 2. 再试 Gitee Releases ——
        try {
            val parsed = fetchGiteeReleases()
            if (parsed != null) {
                val code = parseVersionCode(parsed.tag)
                if (code > 0) {
                    val info = ReleaseInfo(
                        versionName = parsed.tag, versionCode = code,
                        htmlUrl = parsed.htmlUrl, apkUrl = parsed.apkUrl,
                        apkSizeBytes = parsed.apkSize, body = parsed.body, source = "Gitee"
                    )
                    if (info.apkUrl != null) {
                        Log.d(TAG, "Gitee Releases hit: ${parsed.tag}")
                        return@withContext UpdateResult(code > currentCode, info = info)
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Gitee Releases: ${e.message}")
        }

        // —— 3. 最后试 Gitee Tags ——
        try {
            val parsed = fetchGiteeTags()
            if (parsed != null) {
                val code = parseVersionCode(parsed.tag)
                if (code > 0 && parsed.apkUrl != null) {
                    val info = ReleaseInfo(
                        versionName = parsed.tag, versionCode = code,
                        htmlUrl = parsed.htmlUrl, apkUrl = parsed.apkUrl,
                        apkSizeBytes = parsed.apkSize, body = parsed.body, source = "Gitee Tags"
                    )
                    Log.d(TAG, "Gitee Tags hit: ${parsed.tag}")
                    return@withContext UpdateResult(code > currentCode, info = info)
                }
            }
        } catch (e: Exception) {
            errors.add("Gitee Tags: ${e.message}")
        }

        UpdateResult(false, error = errors.joinToString(" | ").ifBlank { "无 Release 信息" })
    }

    // ================== 三个 source 的 fetch 函数 ==================

    private fun httpGet(urlStr: String, accept: String = "*/*", timeout: Int = 2500): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "wc-trainer")
            // Gitee API 支持 token，但我们用公开仓库不需要
            if (urlStr.contains("gitee.com/api/v5")) {
                setRequestProperty("Accept", "application/json")
            }
        }
        val code = conn.responseCode
        if (code != 200) throw Exception("HTTP $code")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    /** GitHub 官方 Releases API */
    private fun fetchGitHub(): Parsed? {
        val json = httpGet(GITHUB_RELEASES, accept = "application/vnd.github+json")
        val root = JSONObject(json)
        val tag = root.optString("tag_name", "")
        if (tag.isBlank()) return null
        return Parsed(
            tag = tag,
            htmlUrl = root.optString("html_url", ""),
            body = root.optString("body", ""),
            apkUrl = findApkGitHub(root.optJSONArray("assets")),
            apkSize = findApkSizeGitHub(root.optJSONArray("assets"))
        )
    }

    private fun findApkGitHub(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name", "").endsWith(".apk", true)) {
                return a.optString("browser_download_url", null)
            }
        }
        return null
    }

    private fun findApkSizeGitHub(assets: JSONArray?): Long {
        if (assets == null) return 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name", "").endsWith(".apk", true)) {
                return a.optLong("size", 0L)
            }
        }
        return 0L
    }

    /** Gitee Releases API —— 字段和 GitHub 略有不同 */
    private fun fetchGiteeReleases(): Parsed? {
        val json = httpGet(GITEE_RELEASES)
        val root = JSONObject(json)
        // Gitee 的字段可能带星号（tag_name vs. tagName），兼容两种
        val tag = root.optString("tag_name", "").ifBlank { root.optString("tagName", "") }
        if (tag.isBlank()) return null
        return Parsed(
            tag = tag,
            htmlUrl = root.optString("html_url", "").ifBlank { root.optString("htmlUrl", "") },
            body = root.optString("body", ""),
            apkUrl = findApkGitee(root.optJSONArray("assets")),
            apkSize = findApkSizeGitee(root.optJSONArray("assets"))
        )
    }

    private fun findApkGitee(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name", "")
            if (name.endsWith(".apk", true)) {
                // Gitee 用 browser_download_url 也兼容
                a.optString("browser_download_url", null)?.let { return it }
                // fallback：用 download_url
                a.optString("download_url", null)?.let { return it }
            }
        }
        return null
    }

    private fun findApkSizeGitee(assets: JSONArray?): Long {
        if (assets == null) return 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name", "").endsWith(".apk", true)) {
                return a.optLong("size", 0L)
            }
        }
        return 0L
    }

    /** Gitee Tags API —— 没有 Release 时的最后兜底 */
    private fun fetchGiteeTags(): Parsed? {
        val json = httpGet(GITEE_TAGS)
        val arr = JSONArray(json)
        if (arr.length() == 0) return null
        val tagObj = arr.getJSONObject(0)
        val tag = tagObj.optString("name", "")
        if (tag.isBlank()) return null
        val htmlUrl = "https://gitee.com/$OWNER/$REPO/releases/tag/$tag"
        // 直接拼下载链接：Gitee 下载附件格式
        val apkUrl = "https://gitee.com/$OWNER/$REPO/releases/download/$tag/CW_Baibaoxiang.apk"
        return Parsed(tag = tag, htmlUrl = htmlUrl, body = "", apkUrl = apkUrl, apkSize = 0L)
    }

    // ================== 版本号解析 ==================

    /** tag_name → 数字版本码：V1.01 → 101, V1.2 → 120, V2.0.1 → 201 */
    private fun parseVersionCode(tag: String): Int {
        val digits = tag.filter { it.isDigit() }
        val padded = digits.padEnd(3, '0')
        return padded.toIntOrNull() ?: 0
    }

    // ================== 下载 + 安装 ==================

    suspend fun downloadApk(context: Context, apkUrl: String): File = withContext(Dispatchers.IO) {
        // 用更短的 timeout + 支持 302 重定向（Gitee/GitHub 下载都需要）
        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "wc-trainer")
        }

        val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "wc-updates")
        }
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val apkFile = File(downloadsDir, "cw-update.apk")
        if (apkFile.exists()) apkFile.delete()

        conn.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(16384)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        apkFile
    }

    fun installApk(context: Context, apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

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

    fun canInstallUnknownApp(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
}
