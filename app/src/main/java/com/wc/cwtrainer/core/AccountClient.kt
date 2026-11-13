package com.wc.cwtrainer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 服务端返回非 2xx 时抛出；status 用于判断是否需要刷新令牌 */
class ApiException(val status: Int, message: String) : IllegalStateException(message)

data class AuthUser(
    val id: String,
    val username: String,
    val email: String,
    val memberUntil: Long
)

data class AuthResult(
    val user: AuthUser,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

data class AccountProfile(
    val username: String,
    val memberUntil: Long,
    val updatedAt: Long,
    val hasPayload: Boolean
)

data class AccountSnapshot(
    val username: String,
    val payload: String,
    val updatedAt: Long
)

/**
 * 账号云同步客户端：登录走 HMA 研习社账号（CW 服务端代理认证接口），
 * 备份上传/下载使用 Bearer access token，旧版 x-account-token 仍可兼容。
 */
object AccountClient {

    private const val TIMEOUT_MS = 8000

    suspend fun register(
        endpoint: String,
        username: String,
        email: String,
        password: String
    ): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("username", username)
                put("email", email)
                put("password", password)
            }
            parseAuth(authRequest(endpoint, "auth/register", body, ""))
        }
    }

    suspend fun login(
        endpoint: String,
        account: String,
        password: String
    ): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("account", account)
                put("password", password)
            }
            parseAuth(authRequest(endpoint, "auth/login", body, ""))
        }
    }

    suspend fun refresh(endpoint: String, refreshToken: String): Result<AuthResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply { put("refreshToken", refreshToken) }
                parseAuth(authRequest(endpoint, "auth/refresh", body, ""))
            }
        }

    suspend fun profile(endpoint: String, token: String): Result<AccountProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val obj = dataRequest(endpoint, "account/me", "GET", token, null)
                AccountProfile(
                    username = obj.optString("username"),
                    memberUntil = obj.optLong("memberUntil"),
                    updatedAt = obj.optLong("updatedAt"),
                    hasPayload = obj.optBoolean("hasPayload")
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
            dataRequest(endpoint, "account/push", "POST", token, body).optLong("updatedAt")
        }
    }

    suspend fun pull(
        endpoint: String,
        token: String
    ): Result<AccountSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = dataRequest(endpoint, "account/pull", "GET", token, null)
            val payload = obj.optJSONObject("payload")
                ?: throw IllegalStateException("服务器返回的数据不完整")
            AccountSnapshot(
                username = obj.optString("username"),
                payload = payload.toString(),
                updatedAt = obj.optLong("updatedAt")
            )
        }
    }

    private fun parseAuth(obj: JSONObject): AuthResult {
        val data = obj.optJSONObject("data")
            ?: throw ApiException(500, "账号服务返回的数据不完整")
        val user = data.optJSONObject("user") ?: JSONObject()
        return AuthResult(
            user = AuthUser(
                id = user.optString("id"),
                username = user.optString("username"),
                email = user.optString("email"),
                memberUntil = data.optLong("memberUntil")
            ),
            accessToken = data.optString("accessToken"),
            refreshToken = data.optString("refreshToken"),
            expiresIn = data.optInt("expiresIn", 900)
        )
    }

    /** HMA 风格响应：{ code, message, data } */
    private fun authRequest(
        endpoint: String,
        path: String,
        body: JSONObject,
        token: String
    ): JSONObject {
        val (status, obj) = request(endpoint, path, "POST", token, body)
        if (status !in 200..299 || obj.optInt("code", -1) != 0) {
            throw ApiException(status, messageOf(obj))
        }
        return obj
    }

    /** CW 风格响应：{ ok, error } */
    private fun dataRequest(
        endpoint: String,
        path: String,
        method: String,
        token: String,
        body: JSONObject?
    ): JSONObject {
        val (status, obj) = request(endpoint, path, method, token, body)
        if (status !in 200..299 || !obj.optBoolean("ok")) {
            throw ApiException(status, messageOf(obj))
        }
        return obj
    }

    private fun request(
        endpoint: String,
        path: String,
        method: String,
        token: String,
        body: JSONObject?
    ): Pair<Int, JSONObject> {
        val base = endpoint.trim().trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "服务器地址需以 http:// 或 https:// 开头"
        }
        val conn = (URL("$base/api/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("accept", "application/json")
            if (token.isNotBlank()) {
                setRequestProperty("authorization", "Bearer ${token.trim()}")
                setRequestProperty("x-account-token", token.trim())
            }
            if (method == "POST") {
                doOutput = true
                setRequestProperty("content-type", "application/json; charset=utf-8")
            }
        }
        if (method == "POST") {
            conn.outputStream.use {
                it.write((body ?: JSONObject()).toString().toByteArray(Charsets.UTF_8))
            }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        val obj = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        return status to obj
    }

    private fun messageOf(obj: JSONObject): String =
        obj.optString("message").ifBlank { obj.optString("error") }.ifBlank { "请求失败" }
}
