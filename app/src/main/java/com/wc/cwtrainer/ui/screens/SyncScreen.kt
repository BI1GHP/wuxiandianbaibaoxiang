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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.core.Backup
import com.wc.cwtrainer.core.SyncClient
import com.wc.cwtrainer.core.skinOf
import com.wc.cwtrainer.core.trainingPoints
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SYNC_PREFS = "wc_sync"
private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

private fun newCode(): String = (1..6).map { CODE_CHARS.random() }.joinToString("")

private fun formatTime(ms: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ms))

/**
 * 进度同步：用同步码在 App 与网页之间搬运备份 JSON。
 * 不建账号；同步码即密钥，服务器可自建（server/sync-server.mjs）。
 */
@Composable
fun SyncScreen(app: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE) }
    var code by remember { mutableStateOf(sp.getString("code", null) ?: newCode()) }
    var server by remember { mutableStateOf(sp.getString("server", null) ?: "") }
    var token by remember { mutableStateOf(sp.getString("token", null) ?: "") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var pendingPayload by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(code) { sp.edit().putString("code", code).apply() }
    LaunchedEffect(server) { sp.edit().putString("server", server).apply() }
    LaunchedEffect(token) { sp.edit().putString("token", token).apply() }

    val s = app.prefs.data
    val points = (trainingPoints(app.stats) - s.spentPoints).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        ScreenHeader("进度同步", onBack, subtitle = "SYNC · LOCAL FIRST")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "同步码就是取回数据的钥匙，只发给自己的教练。服务器可自建：node server/sync-server.mjs；" +
                    "教练在网页端 /coach.html 输入学员同步码即可汇总进度。",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(14.dp))
            LabeledField(
                label = "服务器地址 ENDPOINT",
                value = server,
                placeholder = "http://192.168.1.20:8787"
            ) { server = it.trim() }

            Spacer(Modifier.height(12.dp))
            LabeledField(
                label = "访问口令 TOKEN（服务器未启用可留空）",
                value = token,
                placeholder = "可选"
            ) { token = it.trim() }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    LabeledField(
                        label = "同步码 SYNC CODE",
                        value = code,
                        placeholder = "6 位字母数字"
                    ) { raw ->
                        code = raw.uppercase()
                            .filter { it.isLetterOrDigit() }
                            .take(12)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Pill("换一个", false) { code = newCode() }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("上传本机进度", true, busy, Modifier.weight(1f)) {
                    if (server.isBlank()) {
                        status = "请先填写服务器地址"; statusOk = false; return@ActionButton
                    }
                    if (code.length < 4) {
                        status = "同步码至少 4 位"; statusOk = false; return@ActionButton
                    }
                    scope.launch {
                        busy = true
                        status = "上传中…"; statusOk = false
                        SyncClient.push(server, code, Backup.export(context), token)
                            .onSuccess {
                                status = "已上传 · ${formatTime(it)}"; statusOk = true
                            }
                            .onFailure {
                                status = "上传失败：${it.message}"; statusOk = false
                            }
                        busy = false
                    }
                }
                ActionButton("下载并覆盖", false, busy, Modifier.weight(1f)) {
                    if (server.isBlank()) {
                        status = "请先填写服务器地址"; statusOk = false; return@ActionButton
                    }
                    if (code.length < 4) {
                        status = "同步码至少 4 位"; statusOk = false; return@ActionButton
                    }
                    scope.launch {
                        busy = true
                        status = "读取中…"; statusOk = false
                        SyncClient.pull(server, code, token)
                            .onSuccess {
                                pendingPayload = it.payload
                                status = "已读取 ${formatTime(it.updatedAt)} 的进度，确认后覆盖"
                                statusOk = true
                            }
                            .onFailure {
                                status = "下载失败：${it.message}"; statusOk = false
                            }
                        busy = false
                    }
                }
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

            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1)
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "本机进度",
                    color = TextDim,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(8.dp))
                SnapshotRow("呼号 / 等级", "${s.callsign} · ${if (s.opClass == "NONE") "未考证" else s.opClass + " 类"}")
                SnapshotRow("背码题数", "${app.stats.learnTotal}")
                SnapshotRow("听抄最佳", "${(app.stats.copyBest * 100).toInt()}%")
                SnapshotRow("赛事最佳", "${app.stats.contestBest}")
                SnapshotRow("当前皮肤", skinOf(s.theme).name)
                SnapshotRow("训练积分", "$points")
            }

            Text(
                text = "上传会把本机设置、练习统计、错题本与考试记录整体写入该同步码；" +
                    "下载会用云端数据覆盖本机。网页端可访问服务器首页查看与下载同一份备份。",
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
                Text("本机设置、练习统计、错题与考试记录将被云端数据替换，此操作不可撤销。")
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
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
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
private fun ActionButton(
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
private fun SnapshotRow(label: String, value: String) {
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
