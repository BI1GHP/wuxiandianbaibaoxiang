package com.wc.cwtrainer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AccountSession(
    val username: String,
    val token: String,
    val updatedAt: Long,
    val hasPayload: Boolean
)

data class AccountSnapshot(
    val username: String,
    val payload: String,
    val updatedAt: Long
)

/**
 * 账号云同步客户端：用户名 + 密码登录换取 token，再按账号上传/下载备份 JSON。
 * 服务端见 server/sync-server.mjs（密码只存加盐哈希）。
 */
object AccountClient {

    private const val TIMEOUT_MS = 8000

    suspend fun register(
        endpoint: String,
        username: String,
        password: String
    ): Result<AccountSession> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val response = postJson(endpoint, "account/register", body, "")
            AccountSession(
                username = response.optString("username", username),
                token = response.optString("token"),
                updatedAt = response.optLong("updatedAt"),
                hasPayload = response.optBoolean("hasPayload")
            )
        }
    }

    suspend fun login(
        endpoint: String,
        username: String,
        password: String
    ): Result<AccountSession> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val response = postJson(endpoint, "account/login", body, "")
            AccountSession(
                username = response.optString("username", username),
                token = response.optString("token"),
                updatedAt = response.optLong("updatedAt"),
                hasPayload = response.optBoolean("hasPayload")
            )
        }
    }

    suspend fun push(
        endpoint: String,
        token: String,
        payloadJson: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply { put("payload", JSONObject(payloadJson)) }
            val response = postJson(endpoint, "account/push", body, token)
            response.optLong("updatedAt")
        }
    }

    suspend fun pull(
        endpoint: String,
        token: String
    ): Result<AccountSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(endpoint, "account/pull", "GET", token)
            val text = readResponse(conn)
            val obj = JSONObject(text)
            if (conn.responseCode !in 200..299 || !obj.optBoolean("ok")) {
                throw IllegalStateException(obj.optString("error", "下载失败"))
            }
            val payload = obj.optJSONObject("payload")
                ?: throw IllegalStateException("服务器返回的数据不完整")
            AccountSnapshot(
                username = obj.optString("username"),
                payload = payload.toString(),
                updatedAt = obj.optLong("updatedAt")
            )
        }
    }

    private fun postJson(
        endpoint: String,
        path: String,
        body: JSONObject,
        token: String
    ): JSONObject {
        val conn = open(endpoint, path, "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json; charset=utf-8")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val text = readResponse(conn)
        val obj = JSONObject(text)
        if (conn.responseCode !in 200..299 || !obj.optBoolean("ok")) {
            throw IllegalStateException(obj.optString("error", "请求失败"))
        }
        return obj
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
            if (token.isNotBlank()) setRequestProperty("x-account-token", token.trim())
        }
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        return text
    }
}
