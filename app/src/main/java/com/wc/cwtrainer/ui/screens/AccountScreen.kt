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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.AccountClient
import com.wc.cwtrainer.core.AccountSnapshot
import com.wc.cwtrainer.core.AccountStore
import com.wc.cwtrainer.core.ApiException
import com.wc.cwtrainer.core.Backup
import com.wc.cwtrainer.core.DEFAULT_SYNC_SERVER
import com.wc.cwtrainer.core.skinOf
import com.wc.cwtrainer.core.trainingPoints
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

private fun formatAccountTime(ms: Long): String =
    if (ms <= 0) "尚未同步"
    else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

/**
 * 账号云同步：用户名 + 密码登录，按账号上传/下载整体备份。
 * 与「进度同步」的同步码模式并存，离线训练不受影响。
 */
@Composable
fun AccountScreen(app: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences(ACCOUNT_PREFS, Context.MODE_PRIVATE) }
    // 服务器地址默认沿用进度同步里填过的同一台服务
    val syncSp = remember { context.getSharedPreferences("wc_sync", Context.MODE_PRIVATE) }
    var server by remember {
        mutableStateOf(
            sp.getString("server", null)?.takeIf { it.isNotBlank() }
                ?: syncSp.getString("server", null)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SYNC_SERVER
        )
    }
    var username by remember { mutableStateOf(sp.getString("username", "") ?: "") }
    var email by remember { mutableStateOf(sp.getString("email", "") ?: "") }
    var password by remember { mutableStateOf("") }
    val savedTokens = remember { AccountStore.load(context) }
    var token by remember { mutableStateOf(savedTokens.accessToken) }
    var refreshToken by remember { mutableStateOf(savedTokens.refreshToken) }
    var lastSync by remember { mutableStateOf(sp.getLong("lastSync", 0L)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var pendingPayload by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(server) { sp.edit().putString("server", server).apply() }
    LaunchedEffect(username) { sp.edit().putString("username", username).apply() }
    LaunchedEffect(email) { sp.edit().putString("email", email).apply() }
    LaunchedEffect(lastSync) { sp.edit().putLong("lastSync", lastSync).apply() }

    val loggedIn = token.isNotBlank() && username.isNotBlank()
    val s = app.prefs.data
    val points = (trainingPoints(app.stats) - s.spentPoints).coerceAtLeast(0)

    fun begin(register: Boolean): Boolean {
        if (server.isBlank()) {
            status = "请先填写服务器地址"; statusOk = false; return false
        }
        if (username.length < 3) {
            status = "用户名至少 3 位"; statusOk = false; return false
        }
        if (register && (!email.contains("@") || !email.contains("."))) {
            status = "注册需要填写有效邮箱"; statusOk = false; return false
        }
        if (password.length < 8) {
            status = "密码至少 8 位"; statusOk = false; return false
        }
        return true
    }

    /** 取可用 access token；临近过期时自动刷新 */
    suspend fun freshToken(): String {
        val tokens = AccountStore.ensureFresh(context, server).getOrThrow()
        token = tokens.accessToken
        refreshToken = tokens.refreshToken
        return tokens.accessToken
    }

    /** 请求遇到 401 时刷新一次并重试 */
    suspend fun <T> authed(block: suspend (String) -> T): T {
        val first = freshToken()
        return try {
            block(first)
        } catch (error: ApiException) {
            if (error.status != 401) throw error
            val refreshed = AccountClient.refresh(server, refreshToken).getOrThrow()
            val next = AccountStore.Tokens(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken,
                expiresAt = System.currentTimeMillis() / 1000 + refreshed.expiresIn,
                username = refreshed.user.username.ifBlank { username }
            )
            AccountStore.save(context, next)
            token = next.accessToken
            refreshToken = next.refreshToken
            block(next.accessToken)
        }
    }

    fun submit(register: Boolean) {
        if (!begin(register)) return
        scope.launch {
            busy = true
            status = if (register) "注册中…" else "登录中…"
            statusOk = false
            val result = if (register) {
                AccountClient.register(server, username, email.trim(), password)
            } else {
                AccountClient.login(server, username, password)
            }
            result
                .onSuccess { auth ->
                    val displayName = auth.user.username.ifBlank { username }
                    AccountStore.save(
                        context,
                        AccountStore.Tokens(
                            accessToken = auth.accessToken,
                            refreshToken = auth.refreshToken,
                            expiresAt = System.currentTimeMillis() / 1000 + auth.expiresIn,
                            username = displayName
                        )
                    )
                    token = auth.accessToken
                    refreshToken = auth.refreshToken
                    username = displayName
                    password = ""
                    status = if (register) "注册成功，已登录" else "已登录"
                    statusOk = true
                    AccountClient.profile(server, auth.accessToken).onSuccess { profile ->
                        lastSync = profile.updatedAt
                        status = when {
                            register -> "注册成功，已登录"
                            profile.hasPayload -> "已登录，可下载云端进度"
                            else -> "已登录，云端还没有进度"
                        }
                    }
                }
                .onFailure {
                    status = "${if (register) "注册" else "登录"}失败：${it.message}"
                    statusOk = false
                }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        ScreenHeader("账号云同步", onBack, subtitle = "ACCOUNT · CLOUD")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "使用 HMA 研习社账号登录后，可在多台设备与网页端（/account.html）之间同步进度；" +
                    "CW 不保存密码，只验证 HMA 签发的令牌。不上传时 App 完全离线可用。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(14.dp))
            AccountField(
                label = "服务器地址 ENDPOINT",
                value = server,
                placeholder = DEFAULT_SYNC_SERVER
            ) { server = it.trim() }

            Spacer(Modifier.height(12.dp))
            AccountField(
                label = "用户名 USERNAME",
                value = username,
                placeholder = "3–20 位字母、数字或下划线"
            ) { username = it.trim().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(20) }

            Spacer(Modifier.height(12.dp))
            AccountField(
                label = "邮箱 EMAIL（注册必填）",
                value = email,
                placeholder = "用于找回账号，登录时可留空",
                keyboardType = KeyboardType.Email
            ) { email = it.trim().take(120) }

            Spacer(Modifier.height(12.dp))
            AccountField(
                label = "密码 PASSWORD",
                value = password,
                placeholder = "至少 8 位",
                password = true,
                keyboardType = KeyboardType.Password
            ) { password = it.take(64) }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountAction("登录", true, busy, Modifier.weight(1f)) { submit(false) }
                AccountAction("注册并登录", false, busy, Modifier.weight(1f)) { submit(true) }
            }

            status?.let {
                Text(
                    text = it,
                    color = if (statusOk) Green else Red,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            if (loggedIn) {
                Spacer(Modifier.height(14.dp))
                SectionCard {
                    SectionTitle("当前账号", username)
                    Text(
                        text = "最近同步 ${formatAccountTime(lastSync)}",
                        color = TextDim,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccountAction("上传本机进度", true, busy, Modifier.weight(1f)) {
                            scope.launch {
                                busy = true
                                status = "上传中…"; statusOk = false
                                runCatching {
                                    authed<Long> { fresh ->
                                        AccountClient.push(server, fresh, Backup.export(context)).getOrThrow()
                                    }
                                }
                                    .onSuccess {
                                        lastSync = it
                                        status = "已上传 · ${formatAccountTime(it)}"; statusOk = true
                                    }
                                    .onFailure {
                                        status = "上传失败：${it.message}"; statusOk = false
                                    }
                                busy = false
                            }
                        }
                        AccountAction("下载并覆盖", false, busy, Modifier.weight(1f)) {
                            scope.launch {
                                busy = true
                                status = "读取中…"; statusOk = false
                                runCatching {
                                    authed<AccountSnapshot> { fresh ->
                                        AccountClient.pull(server, fresh).getOrThrow()
                                    }
                                }
                                    .onSuccess {
                                        pendingPayload = it.payload
                                        lastSync = it.updatedAt
                                        status = "已读取 ${formatAccountTime(it.updatedAt)} 的进度，确认后覆盖"
                                        statusOk = true
                                    }
                                    .onFailure {
                                        status = "下载失败：${it.message}"; statusOk = false
                                    }
                                busy = false
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Pill("退出登录", false) {
                            AccountStore.clear(context)
                            token = ""
                            refreshToken = ""
                            password = ""
                            status = "已退出登录"
                            statusOk = true
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionCard {
                SectionTitle("本机进度")
                SnapshotLine("呼号 / 等级", "${s.callsign} · ${if (s.opClass == "NONE") "未考证" else s.opClass + " 类"}")
                SnapshotLine("背码题数", "${app.stats.learnTotal}")
                SnapshotLine("听抄最佳", "${(app.stats.copyBest * 100).toInt()}%")
                SnapshotLine(
                    "赛事听抄正确率",
                    if (app.stats.contestTotal == 0) "—"
                    else "${(app.stats.contestRate * 100).toInt()}%"
                )
                SnapshotLine("当前皮肤", skinOf(s.theme).name)
                SnapshotLine("训练积分", "$points")
            }

            Text(
                text = "上传会把本机设置、练习统计、错题本与考试记录整体写入账号；" +
                    "下载会用云端数据覆盖本机。账号 token 仅保存在本机，退出登录即清除。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
            )
        }
    }

    pendingPayload?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingPayload = null },
            title = { Text("覆盖本机数据？") },
            text = {
                Text("本机设置、练习统计、错题与考试记录将被账号云端数据替换，此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { Backup.import(context, json) }
                        .onSuccess { count ->
                            app.prefs.reload()
                            app.stats.reload()
                            app.exam.reload()
                            status = "已恢复 $count 项数据"
                            statusOk = true
                        }
                        .onFailure {
                            status = "恢复失败：${it.message}"
                            statusOk = false
                        }
                    pendingPayload = null
                }) { Text("覆盖", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPayload = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AccountField(
    label: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    Column {
        Text(label, color = TextDim, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(5.dp))
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
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(
                    color = TextMain,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(Amber),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(placeholder, color = TextDim.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AccountAction(
    text: String,
    primary: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) Amber else Surface1)
            .border(
                1.dp,
                if (primary) Amber else Line,
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !busy) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) Color(0xFF1A1200) else TextMain,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SnapshotLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(label, color = TextDim, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = TextMain,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
