package com.wc.cwtrainer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MemberConfig(val enabled: Boolean, val price: Int, val updatedAt: Long)

data class MemberStatus(
    val username: String,
    val enabled: Boolean,
    val price: Int,
    val memberUntil: Long,
    val active: Boolean
)

/**
 * 会员接口客户端：读取开关、查询账号会员状态、走下单入口。
 * 支付渠道尚未接入，checkout 在功能开放时返回服务端的提示信息。
 */
object MemberClient {

    private const val TIMEOUT_MS = 8000

    suspend fun config(endpoint: String): Result<MemberConfig> =
        withContext(Dispatchers.IO) {
            runCatching {
                val obj = request(endpoint, "member/config", "GET", "")
                MemberConfig(
                    enabled = obj.optBoolean("enabled"),
                    price = obj.optInt("price", 18),
                    updatedAt = obj.optLong("updatedAt")
                )
            }
        }

    suspend fun status(endpoint: String, token: String): Result<MemberStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val obj = request(endpoint, "member/status", "GET", token)
                MemberStatus(
                    username = obj.optString("username"),
                    enabled = obj.optBoolean("enabled"),
                    price = obj.optInt("price", 18),
                    memberUntil = obj.optLong("memberUntil"),
                    active = obj.optBoolean("active")
                )
            }
        }

    /** 下单入口；服务端未接入支付时抛错并带上提示文案 */
    suspend fun checkout(endpoint: String, token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                request(endpoint, "member/checkout", "POST", token)
                Unit
            }
        }

    /** 兑换码开通 / 续期，返回新的会员到期时间 */
    suspend fun redeem(endpoint: String, token: String, code: String): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply { put("code", code) }
                request(endpoint, "member/redeem", "POST", token, body).optLong("memberUntil")
            }
        }

    private fun request(
        endpoint: String,
        path: String,
        method: String,
        token: String,
        body: JSONObject? = null
    ): JSONObject {
        val base = endpoint.trim().trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "服务器地址需以 http:// 或 https:// 开头"
        }
        val conn = (URL("$base/api/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("accept", "application/json")
            if (token.isNotBlank()) setRequestProperty("x-account-token", token.trim())
            if (method == "POST") {
                doOutput = true
                setRequestProperty("content-type", "application/json; charset=utf-8")
            }
        }
        if (method == "POST") {
            conn.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)) }
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val code = conn.responseCode
        conn.disconnect()
        val obj = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (code !in 200..299 || !obj.optBoolean("ok")) {
            throw IllegalStateException(obj.optString("error", "请求失败"))
        }
        return obj
    }
}
