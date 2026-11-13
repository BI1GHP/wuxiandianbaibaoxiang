package com.wc.cwtrainer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SyncSnapshot(val payload: String, val updatedAt: Long)

/**
 * 网页端进度同步客户端：只搬运备份 JSON，不涉及账号。
 * 服务器地址与同步码由用户自行填写（局域网或自建服务）。
 */
object SyncClient {

    private const val TIMEOUT_MS = 8000

    suspend fun push(
        endpoint: String,
        code: String,
        payloadJson: String,
        token: String = ""
    ): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("code", code)
                    put("payload", JSONObject(payloadJson))
                }
                val conn = open(endpoint, "sync", "POST", token)
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val text = readResponse(conn)
                val obj = JSONObject(text)
                if (conn.responseCode !in 200..299 || !obj.optBoolean("ok")) {
                    throw IllegalStateException(obj.optString("error", "上传失败"))
                }
                obj.optLong("updatedAt")
            }
        }

    suspend fun pull(
        endpoint: String,
        code: String,
        token: String = ""
    ): Result<SyncSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = open(endpoint, "sync?code=$code", "GET", token)
                val text = readResponse(conn)
                val obj = JSONObject(text)
                if (conn.responseCode !in 200..299 || !obj.optBoolean("ok")) {
                    throw IllegalStateException(obj.optString("error", "下载失败"))
                }
                val payload = obj.optJSONObject("payload")
                    ?: throw IllegalStateException("服务器返回的数据不完整")
                SyncSnapshot(payload.toString(), obj.optLong("updatedAt"))
            }
        }

    private fun open(
        endpoint: String,
        path: String,
        method: String,
        token: String
    ): HttpURLConnection {
        val base = endpoint.trim().trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "服务器地址需以 http:// 或 https:// 开头"
        }
        val url = URL("$base/api/$path")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("accept", "application/json")
            if (token.isNotBlank()) setRequestProperty("x-sync-token", token.trim())
        }
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return text
    }
}
