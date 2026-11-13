package com.wc.cwtrainer.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 会员状态本地缓存：服务端开通后由 /api/member/status 回写。
 * 会员数据不进本地备份，避免通过导入备份伪造权益。
 */
class MemberStore(context: Context) {

    private val sp = context.getSharedPreferences("wc_member", Context.MODE_PRIVATE)

    var memberUntil by mutableStateOf(sp.getLong("memberUntil", 0L))
        private set
    var enabled by mutableStateOf(sp.getBoolean("enabled", false))
        private set
    var price by mutableStateOf(sp.getInt("price", 18))
        private set

    val active: Boolean get() = memberUntil > System.currentTimeMillis()

    fun save(enabled: Boolean, price: Int, memberUntil: Long) {
        this.enabled = enabled
        this.price = price
        this.memberUntil = memberUntil
        sp.edit()
            .putBoolean("enabled", enabled)
            .putInt("price", price)
            .putLong("memberUntil", memberUntil)
            .apply()
    }

    fun reload() {
        enabled = sp.getBoolean("enabled", false)
        price = sp.getInt("price", 18)
        memberUntil = sp.getLong("memberUntil", 0L)
    }
}
