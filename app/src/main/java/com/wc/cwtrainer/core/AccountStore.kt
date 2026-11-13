package com.wc.cwtrainer.core

import android.content.Context

/**
 * 账号令牌本地存储：access token 15 分钟，refresh token 30 天。
 * 只保存在本机，退出登录即清除。
 */
object AccountStore {

    const val PREFS = "wc_account"

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val username: String
    )

    fun load(context: Context): Tokens {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Tokens(
            accessToken = sp.getString("token", "") ?: "",
            refreshToken = sp.getString("refreshToken", "") ?: "",
            expiresAt = sp.getLong("expiresAt", 0L),
            username = sp.getString("username", "") ?: ""
        )
    }

    fun save(context: Context, tokens: Tokens) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", tokens.accessToken)
            .putString("refreshToken", tokens.refreshToken)
            .putLong("expiresAt", tokens.expiresAt)
            .putString("username", tokens.username)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("token")
            .remove("refreshToken")
            .remove("expiresAt")
            .apply()
    }

    /** 返回可用的 access token；临近过期或已过期时自动用 refresh token 换新 */
    suspend fun ensureFresh(context: Context, endpoint: String): Result<Tokens> {
        val current = load(context)
        val nowSec = System.currentTimeMillis() / 1000
        if (current.accessToken.isNotBlank() && current.expiresAt > nowSec + 60) {
            return Result.success(current)
        }
        if (current.refreshToken.isBlank()) {
            return Result.failure(ApiException(401, "登录已过期，请重新登录"))
        }
        return AccountClient.refresh(endpoint, current.refreshToken).map { result ->
            val next = Tokens(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresAt = nowSec + result.expiresIn,
                username = result.user.username.ifBlank { current.username }
            )
            save(context, next)
            next
        }
    }
}
