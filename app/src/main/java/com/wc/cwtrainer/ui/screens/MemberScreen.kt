package com.wc.cwtrainer.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.MemberClient
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ACCOUNT_PREFS = "wc_account"

private fun formatDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ms))

/**
 * 会员与支付：展示权益、功能开关与账号会员状态。
 * 默认暂未开放；开放后开通入口调用服务端下单接口（支付渠道接入前只返回提示）。
 */
@Composable
fun MemberScreen(app: AppState, onBack: () -> Unit, onOpenAccount: () -> Unit) {
    val context = LocalContext.current
    val accountSp = remember { context.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE) }
    val server = accountSp.getString("server", "") ?: ""
    val token = accountSp.getString("token", "") ?: ""
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var redeemCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val member = app.member

    fun refresh() {
        if (server.isBlank()) {
            status = "尚未配置服务器：请先在「账号云同步」填写服务器地址"
            statusOk = false
            loaded = true
            return
        }
        scope.launch {
            busy = true
            MemberClient.config(server)
                .onSuccess { cfg ->
                    var until = member.memberUntil
                    if (token.isNotBlank()) {
                        MemberClient.status(server, token)
                            .onSuccess { st ->
                                until = st.memberUntil
                                status = if (st.active) {
                                    "会员有效期至 ${formatDate(st.memberUntil)}"
                                } else {
                                    "当前为免费版"
                                }
                                statusOk = st.active
                            }
                            .onFailure {
                                status = "会员状态读取失败：${it.message}"
                                statusOk = false
                            }
                    } else {
                        status = "未登录账号，仅显示功能开关状态"
                        statusOk = false
                    }
                    member.save(cfg.enabled, cfg.price, until)
                }
                .onFailure {
                    status = "无法连接服务器：${it.message}"
                    statusOk = false
                }
            busy = false
            loaded = true
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        ScreenHeader("会员与支付", onBack, subtitle = "MEMBER · GATE")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "会员功能默认暂不开放，由管理后台统一开启或关闭；当前全部训练功能免费使用。" +
                    "开通与支付渠道仍在接入中。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(12.dp))
            SectionCard {
                SectionTitle("功能状态")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (member.enabled) Green else TextDim)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (member.enabled) "已开放" else "暂未开放",
                        color = if (member.enabled) Green else TextDim,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "¥${member.price} / 月",
                        color = Amber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(8.dp))
                InfoRow("我的会员", if (member.active) "PLUS · 至 ${formatDate(member.memberUntil)}" else "免费版")
                InfoRow("账号", accountSp.getString("username", "")?.takeIf { it.isNotBlank() } ?: "未登录")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("刷新状态", false) { if (!busy) refresh() }
                    Pill("账号云同步", false) { onOpenAccount() }
                }
            }

            status?.let {
                Text(
                    text = it,
                    color = if (statusOk) Green else TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                )
            }

            if (member.enabled) {
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    SectionTitle("兑换码开通")
                    Text(
                        text = "支付渠道接入前，可使用管理后台生成的兑换码开通或续期会员，" +
                            "每个兑换码只能使用一次。",
                        color = TextDim,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    RedeemField(redeemCode) { raw ->
                        redeemCode = raw.uppercase()
                            .filter { it.isLetterOrDigit() }
                            .take(12)
                    }
                    Spacer(Modifier.height(10.dp))
                    MemberButton(
                        text = when {
                            busy -> "兑换中…"
                            token.isBlank() -> "请先登录账号"
                            else -> "兑换并开通"
                        },
                        primary = true,
                        enabled = redeemCode.length >= 8 && token.isNotBlank() && !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                MemberClient.redeem(server, token, redeemCode)
                                    .onSuccess { until ->
                                        member.save(member.enabled, member.price, until)
                                        redeemCode = ""
                                        status = "兑换成功，会员有效期至 ${formatDate(until)}"
                                        statusOk = true
                                    }
                                    .onFailure {
                                        status = "兑换失败：${it.message}"
                                        statusOk = false
                                    }
                                busy = false
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                SectionTitle("权益对比")
                BenefitRow("基础训练", "听抄 / 背码 / 发码 / 考试", true)
                BenefitRow("本地备份", "导出与导入全部记录", true)
                BenefitRow("云端多设备同步", "账号云同步不限次数", false)
                BenefitRow("赛事复盘导出", "ADIF 批量导出与失误分析", false)
                BenefitRow("全部皮肤", "训练积分商店全部解锁", false)
                BenefitRow("教练周报", "AI 教练每周计划导出", false)
                Text(
                    text = "· 会员权益在支付渠道接入后正式生效，当前不会限制任何已有功能。",
                    color = TextDim,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            MemberButton(
                text = when {
                    !loaded -> "读取中…"
                    !member.enabled -> "暂未开放"
                    member.active -> "续费 PLUS"
                    else -> "开通 PLUS"
                },
                primary = member.enabled,
                enabled = loaded && member.enabled && !busy,
                onClick = {
                    if (token.isBlank()) {
                        status = "请先在「账号云同步」登录后再开通"
                        statusOk = false
                        return@MemberButton
                    }
                    scope.launch {
                        busy = true
                        MemberClient.checkout(server, token)
                            .onSuccess {
                                status = "下单成功，请按提示完成支付"
                                statusOk = true
                            }
                            .onFailure {
                                status = "开通失败：${it.message}"
                                statusOk = false
                            }
                        busy = false
                    }
                }
            )

            Text(
                text = "支付渠道尚未接入；开放后可用管理后台生成的兑换码开通，" +
                    "也可由管理员按账号手动开通。开通后回到本页点「刷新状态」即可看到有效期。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun RedeemField(value: String, onChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = TextMain,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            cursorBrush = SolidColor(Amber),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) {
            Text("兑换码 REDEEM CODE", color = TextDim.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .width(8.dp)
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(label, color = TextDim, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = TextMain, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BenefitRow(title: String, desc: String, free: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (free) "免费" else "PLUS",
            color = if (free) Green else Amber,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(38.dp)
        )
        Column {
            Text(title, color = TextMain, fontSize = 12.sp)
            Text(desc, color = TextDim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MemberButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary && enabled) Amber else Surface1)
            .border(
                1.dp,
                if (primary && enabled) Amber else Line,
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary && enabled) Color(0xFF1A1200) else if (primary) TextDim else TextDim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
