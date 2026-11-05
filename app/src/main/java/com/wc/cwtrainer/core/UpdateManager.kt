package com.wc.cwtrainer.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
 * - APK 附件名统一 "CW百宝箱_{tag}.apk"（如 CW百宝箱_V1.03.apk）。
 *   Release 资产按 .apk 后缀匹配，改名也能识别；Tags 兜底没有资产列表可查，
 *   只能按这个约定拼下载地址，附件名不一致会 404。
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
        val candidates = mutableListOf<ReleaseInfo>()

        // 查询顺序：Gitee Releases → Gitee Tags → GitHub
        // 国内 Gitee 稳定 (<0.3s)，GitHub 常超时 2.5s 白等；
        // 但三个源结果统一收集后取 versionCode 最大者，不会因顺序丢新

        // —— 1. Gitee Releases（首选）——
        try {
            fetchGiteeReleases()?.let { parsed ->
                val code = parseVersionCode(parsed.tag)
                if (code > 0 && parsed.apkUrl != null) {
                    candidates += ReleaseInfo(
                        versionName = parsed.tag, versionCode = code,
                        htmlUrl = parsed.htmlUrl, apkUrl = parsed.apkUrl,
                        apkSizeBytes = parsed.apkSize, body = parsed.body, source = "Gitee"
                    )
                    Log.d(TAG, "Gitee Releases hit: ${parsed.tag}, code=$code")
                }
            }
        } catch (e: Exception) {
            errors.add("Gitee Releases: ${e.message}")
        }

        // —— 2. Gitee Tags（兜底，无 Release 时按约定拼下载链接）——
        try {
            fetchGiteeTags()?.let { parsed ->
                val code = parseVersionCode(parsed.tag)
                if (code > 0 && parsed.apkUrl != null) {
                    candidates += ReleaseInfo(
                        versionName = parsed.tag, versionCode = code,
                        htmlUrl = parsed.htmlUrl, apkUrl = parsed.apkUrl,
                        apkSizeBytes = parsed.apkSize, body = parsed.body, source = "Gitee Tags"
                    )
                    Log.d(TAG, "Gitee Tags hit: ${parsed.tag}, code=$code")
                }
            }
        } catch (e: Exception) {
            errors.add("Gitee Tags: ${e.message}")
        }

        // —— 3. GitHub（仅海外或 Gitee 完全不通时才会命中）——
        try {
            fetchGitHub()?.let { parsed ->
                val code = parseVersionCode(parsed.tag)
                if (code > 0 && parsed.apkUrl != null) {
                    candidates += ReleaseInfo(
                        versionName = parsed.tag, versionCode = code,
                        htmlUrl = parsed.htmlUrl, apkUrl = parsed.apkUrl,
                        apkSizeBytes = parsed.apkSize, body = parsed.body, source = "GitHub"
                    )
                    Log.d(TAG, "GitHub hit: ${parsed.tag}, code=$code")
                }
            }
        } catch (e: Exception) {
            errors.add("GitHub: ${e.message}")
            Log.w(TAG, "GitHub failed", e)
        }

        if (candidates.isEmpty()) {
            return@withContext UpdateResult(false,
                error = errors.joinToString(" | ").ifBlank { "无 Release 信息" })
        }

        val best = candidates.maxByOrNull { it.versionCode }!!
        Log.d(TAG, "best source=${best.source} tag=${best.versionName} " +
            "code=${best.versionCode} cur=$currentCode")
        UpdateResult(best.versionCode > currentCode, info = best)
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
            if (a.optString("name").endsWith(".apk", true)) {
                val url = a.optString("browser_download_url")
                if (url.isNotBlank()) return url
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
            val name = a.optString("name")
            if (name.endsWith(".apk", true)) {
                // Gitee 用 browser_download_url，缺失时回退到 download_url
                val url1 = a.optString("browser_download_url")
                if (url1.isNotBlank()) return url1
                val url2 = a.optString("download_url")
                if (url2.isNotBlank()) return url2
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
        // 按发版约定拼下载链接：CW百宝箱_V1.03.apk（中文名需百分号编码）
        val apkUrl = "https://gitee.com/$OWNER/$REPO/releases/download/$tag/" +
            Uri.encode("CW百宝箱_$tag.apk")
        return Parsed(tag = tag, htmlUrl = htmlUrl, body = "", apkUrl = apkUrl, apkSize = 0L)
    }

    // ================== 版本号解析 ==================

    /** tag_name → 数字版本码：V1.01 → 101, V1.2 → 120, V2.0.1 → 201 */
    private fun parseVersionCode(tag: String): Int {
        val digits = tag.filter { it.isDigit() }
        val padded = digits.padEnd(3, '0')
        return padded.toIntOrNull() ?: 0
    }

    // ================== 应用级更新流程状态 ==================
    // 检查/下载跑在应用级作用域：离开设置页、重组或返回都不会被取消。
    // 之前下载写在 LaunchedEffect 里，Composable 退出时协程被取消，界面上报
    // TimeoutCancellationException: The coroutine scope left the composition。

    data class Flow(
        val status: String = "idle",   // idle/checking/new/downloading/installing/need_perm/up_to_date/error
        val message: String = "🔄 点击检查更新",
        val newVersion: String? = null,
        val apkUrl: String? = null,
        val source: String? = null,
        val apkSizeBytes: Long = 0L
    )

    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _flow = MutableStateFlow(Flow())
    val flow: StateFlow<Flow> = _flow

    fun checkUpdateAsync(currentCode: Int) {
        if (_flow.value.status == "checking") return
        _flow.value = _flow.value.copy(status = "checking", message = "⏳ 检查中…")
        workScope.launch {
            val result = checkUpdate(currentCode)
            val info = result.info
            _flow.value = when {
                result.hasUpdate && info != null -> Flow(
                    status = "new",
                    message = "⬇ 发现 ${info.versionName}（%.1f MB · ${info.source}），点击下载"
                        .format(info.apkSizeBytes / 1024.0 / 1024.0),
                    newVersion = info.versionName,
                    apkUrl = info.apkUrl,
                    source = info.source,
                    apkSizeBytes = info.apkSizeBytes
                )
                result.error != null -> _flow.value.copy(status = "error", message = "❌ ${result.error}")
                else -> _flow.value.copy(status = "up_to_date", message = "✅ 已是最新版本（源: ${info?.source ?: "-"}）")
            }
        }
    }

    fun downloadAndInstallAsync(context: Context) {
        val url = _flow.value.apkUrl ?: return
        val status = _flow.value.status
        if (status == "downloading" || status == "installing") return
        _flow.value = _flow.value.copy(status = "downloading", message = "⏳ 下载中…")
        workScope.launch {
            try {
                val file = downloadApk(context, url)
                _flow.value = _flow.value.copy(status = "installing", message = "📦 正在安装…")
                if (canInstallUnknownApp(context)) {
                    installApk(context, file)
                    // 回到可点击态，避免安装器返回后按钮一直停在 installing
                    _flow.value = _flow.value.copy(status = "idle", message = "📦 已启动安装器")
                } else {
                    requestInstallPermission(context)
                    _flow.value = _flow.value.copy(status = "need_perm", message = "⚠ 请允许安装未知应用，然后重新点击")
                }
            } catch (e: Exception) {
                // 带异常类型便于定位：ConnectException / SocketTimeoutException / SSLException 等
                _flow.value = _flow.value.copy(status = "error", message = "❌ 下载失败：${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    // ================== 下载 + 安装 ==================

    suspend fun downloadApk(context: Context, apkUrl: String): File = withContext(Dispatchers.IO) {
        // 手动跟随重定向（Gitee 会 302 两次到 foruda CDN，部分机型自动跟随不可靠）
        var url = apkUrl
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false   // 手动处理，避免跨域自动跟随的坑
                setRequestProperty("User-Agent", "wc-trainer")
            }
            when (conn.responseCode) {
                in 200..299 -> break
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307, 308 -> {
                    val loc = conn.getHeaderField("Location")
                        ?: throw Exception("HTTP ${conn.responseCode} 无跳转地址")
                    conn.disconnect()
                    url = URL(URL(url), loc).toString()   // 支持相对路径跳转
                    if (++redirects > 5) throw Exception("重定向次数过多")
                }
                else -> throw Exception("HTTP ${conn.responseCode}")
            }
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
        conn.disconnect()

        // 校验文件完整性：APK 是 ZIP，必须以 PK 头开头
        val magic = apkFile.inputStream().use { it.read().toByte() to it.read().toByte() }
        if (apkFile.length() < 1024 * 1024 || magic != (0x50.toByte() to 0x4B.toByte())) {
            apkFile.delete()
            throw Exception("下载文件无效（${apkFile.length()} 字节）")
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
