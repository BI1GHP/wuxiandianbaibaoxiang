package com.wc.cwtrainer.core

/** 皮肤商店条目：主题、名称、卖点、兑换所需训练积分 */
data class SkinInfo(
    val mode: ThemeMode,
    val name: String,
    val tagline: String,
    val cost: Int
)

val Skins = listOf(
    SkinInfo(ThemeMode.FIELD, "野战电台", "军规金属面板 · 默认皮肤", 0),
    SkinInfo(ThemeMode.CREAM, "奶油手帐", "纸感珊瑚 · 柔美主题", 0),
    SkinInfo(ThemeMode.OLIVE, "丛林军绿", "哑光军绿 · 野战电台", 300),
    SkinInfo(ThemeMode.SAND, "沙漠黄沙", "暖沙面板 · 强光可读", 600),
    SkinInfo(ThemeMode.OCEAN, "深海蓝调", "冷蓝背光 · 夜间守听", 900)
)

fun skinOf(mode: ThemeMode): SkinInfo = Skins.first { it.mode == mode }

/**
 * 训练积分：完全由本地练习数据换算，不需要账号与支付。
 * 背码题数 + 听抄最佳正确率 ×2 + 赛事最佳分。
 */
fun trainingPoints(stats: StatsStore): Int =
    stats.learnTotal + (stats.copyBest * 100).toInt() * 2 + stats.contestBest
