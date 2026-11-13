package com.wc.cwtrainer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.AppState
import com.wc.cwtrainer.BuildConfig
import com.wc.cwtrainer.core.Backup
import com.wc.cwtrainer.core.InputMode
import com.wc.cwtrainer.core.KeyMode
import com.wc.cwtrainer.core.ThemeMode
import com.wc.cwtrainer.core.UpdateManager
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.CreamPalette
import com.wc.cwtrainer.ui.FieldPalette
import com.wc.cwtrainer.ui.Green
import com.wc.cwtrainer.ui.WcColors
import com.wc.cwtrainer.ui.LabeledSlider
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.Pill
import com.wc.cwtrainer.ui.Red
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.SectionCard
import com.wc.cwtrainer.ui.SectionTitle
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    app: AppState,
    onBack: () -> Unit,
    onPreviewTheme: () -> Unit,
    onOpenSkins: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenMember: () -> Unit
) {
    val s = app.prefs.data
    var showLicense by remember { mutableStateOf(false) }
    var presetMsg by remember { mutableStateOf<String?>(null) }
    var termOpen by remember { mutableStateOf<String?>(null) }

    // —— 数据备份 / 恢复 ——
    val context = LocalContext.current
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var backupOk by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val r = runCatching {
                val text = Backup.export(context)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入所选位置")
            }
            backupOk = r.isSuccess
            backupMsg = r.fold(
                { "备份已导出，请妥善保存该文件" },
                { "导出失败：${it.message ?: "未知错误"}" }
            )
        }
    }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("无法读取所选文件")
            }.getOrNull()
            if (text == null) {
                backupOk = false
                backupMsg = "导入失败：无法读取所选文件"
            } else {
                pendingImport = text
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("设置", onBack, subtitle = "SYSTEM · WC-2025")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionCard {
                SectionTitle("训练预设")
                Text(
                    text = "一键把码速、侧音、底噪调到对应阶段；应用后仍可单独微调。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("新手", false) {
                        app.prefs.update {
                            it.copy(
                                wpm = 12, farnsworth = 20, toneHz = 700, volume = 0.8f,
                                copyWpm = 12, learnWpm = 12, contestWpm = 15,
                                translatorWpm = 12, noiseLevel = 0.05f, contestNoise = 0.08f,
                                ttsRate = 0.9f
                            )
                        }
                        presetMsg = "已应用「新手」预设：12 WPM，Farnsworth 20"
                    }
                    Pill("进阶", false) {
                        app.prefs.update {
                            it.copy(
                                wpm = 20, farnsworth = 0, toneHz = 650, volume = 0.85f,
                                copyWpm = 20, learnWpm = 20, contestWpm = 25,
                                translatorWpm = 20, noiseLevel = 0.08f, contestNoise = 0.12f,
                                ttsRate = 1.0f
                            )
                        }
                        presetMsg = "已应用「进阶」预设：20 WPM，常规侧音"
                    }
                    Pill("竞赛", false) {
                        app.prefs.update {
                            it.copy(
                                wpm = 28, farnsworth = 0, toneHz = 600, volume = 0.9f,
                                copyWpm = 28, learnWpm = 24, contestWpm = 30,
                                translatorWpm = 24, noiseLevel = 0.18f, contestNoise = 0.3f,
                                ttsRate = 1.1f
                            )
                        }
                        presetMsg = "已应用「竞赛」预设：28 WPM，高底噪"
                    }
                }
                presetMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, color = Green, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("外观主题")
                Text(
                    text = "两套主题共用同一套操作逻辑，点选立即应用到整个 App。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePreviewCard(
                        title = "野战电台",
                        subtitle = "金属面板 · 琥珀 LED",
                        palette = FieldPalette,
                        selected = s.theme == ThemeMode.FIELD,
                        modifier = Modifier.weight(1f)
                    ) { app.prefs.update { it.copy(theme = ThemeMode.FIELD) } }
                    ThemePreviewCard(
                        title = "奶油手帐",
                        subtitle = "纸感圆角 · 珊瑚点缀",
                        palette = CreamPalette,
                        selected = s.theme == ThemeMode.CREAM,
                        modifier = Modifier.weight(1f)
                    ) { app.prefs.update { it.copy(theme = ThemeMode.CREAM) } }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("全屏预览", false) { onPreviewTheme() }
                    Spacer(Modifier.width(8.dp))
                    Pill("皮肤商店", false) { onOpenSkins() }
                }
                Text(
                    text = "全屏对比两套主题；更多皮肤可用训练积分在商店解锁",
                    color = TextDim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("字号", color = TextMain, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${(s.fontScale * 100).roundToInt()}%",
                        color = Amber,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("标准", s.fontScale == 1f) {
                        app.prefs.update { it.copy(fontScale = 1f) }
                    }
                    Pill("大", s.fontScale == 1.15f) {
                        app.prefs.update { it.copy(fontScale = 1.15f) }
                    }
                    Pill("特大", s.fontScale == 1.3f) {
                        app.prefs.update { it.copy(fontScale = 1.3f) }
                    }
                }
                Text(
                    text = "放大字号不影响按钮与间距，适合年长用户或强光环境阅读。",
                    color = TextDim,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("界面音效", color = TextMain, fontSize = 13.sp)
                        Text(
                            text = "继电器咔嗒与旋钮嘀声，可随时关闭",
                            color = TextDim,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Switch(
                        checked = s.uiSound,
                        onCheckedChange = { on ->
                            app.prefs.update { it.copy(uiSound = on) }
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("电码与音频")
                LabeledSlider(
                    label = "发送速度",
                    value = s.wpm.toFloat(),
                    range = 5f..40f,
                    valueText = "${s.wpm} WPM",
                    steps = 34
                ) { v ->
                    val wpm = v.toInt()
                    app.prefs.update {
                        it.copy(wpm = wpm, farnsworth = if (it.farnsworth > wpm) 0 else it.farnsworth)
                    }
                }
                LabeledSlider(
                    label = "Farnsworth 有效速度",
                    value = s.farnsworth.toFloat(),
                    range = 0f..s.wpm.toFloat(),
                    valueText = if (s.farnsworth == 0) "关闭" else "${s.farnsworth} WPM",
                    steps = 0
                ) { v ->
                    app.prefs.update { it.copy(farnsworth = v.roundToInt()) }
                }
                LabeledSlider(
                    label = "侧音频率",
                    value = s.toneHz.toFloat(),
                    range = 400f..1000f,
                    valueText = "${s.toneHz} Hz",
                    steps = 29
                ) { v ->
                    app.prefs.update { it.copy(toneHz = v.roundToInt()) }
                }
                LabeledSlider(
                    label = "音量",
                    value = s.volume,
                    range = 0f..1f,
                    valueText = "${(s.volume * 100).roundToInt()}%",
                    steps = 9
                ) { v ->
                    app.prefs.update { it.copy(volume = v) }
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("发码")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("手键模式", s.keyMode == KeyMode.STRAIGHT) {
                        app.prefs.update { it.copy(keyMode = KeyMode.STRAIGHT) }
                    }
                    Pill("自动键模式", s.keyMode == KeyMode.PADDLE) {
                        app.prefs.update { it.copy(keyMode = KeyMode.PADDLE) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "IAMBIC 记忆（B 模式）",
                            color = TextMain,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "自动键双桨同时按下时交替发码，并记忆反向桨",
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = s.iambicB,
                        onCheckedChange = { v -> app.prefs.update { it.copy(iambicB = v) } }
                    )
                }
                LabeledSlider(
                    label = "外部输入灵敏度",
                    value = s.micSensitivity,
                    range = 0f..1f,
                    valueText = "${(s.micSensitivity * 100).roundToInt()}%",
                    steps = 9
                ) { v ->
                    app.prefs.update { it.copy(micSensitivity = v) }
                }
                LabeledSlider(
                    label = "外部检测音调频率",
                    value = s.extToneHz.toFloat(),
                    range = 300f..900f,
                    valueText = "${s.extToneHz} Hz",
                    steps = 59
                ) { v ->
                    app.prefs.update { it.copy(extToneHz = v.roundToInt()) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("默认屏幕按键", s.inputSource == InputMode.SCREEN) {
                        app.prefs.update { it.copy(inputSource = InputMode.SCREEN) }
                    }
                    Pill("默认音频接口", s.inputSource == InputMode.AUDIO) {
                        app.prefs.update { it.copy(inputSource = InputMode.AUDIO) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("赛事模拟")
                LabeledSlider(
                    label = "背景噪声",
                    value = s.noiseLevel,
                    range = 0f..0.3f,
                    valueText = "${(s.noiseLevel * 100).roundToInt()}%",
                    steps = 29
                ) { v ->
                    app.prefs.update { it.copy(noiseLevel = v) }
                }
                LabeledSlider(
                    label = "TTS 语速系数",
                    value = s.ttsRate,
                    range = 0.7f..1.3f,
                    valueText = "${"%.2f".format(s.ttsRate)}x",
                    steps = 5
                ) { v ->
                    app.prefs.update { it.copy(ttsRate = v) }
                }
                Text(
                    text = "SSB 口音依赖系统 TTS 语言包。若某国语音包缺失，会自动回退到英语。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("外部按键接入说明")
                Text(
                    text = "1. 手机 3.5mm 耳机孔（四段 TRRS）或 USB 声卡作为音频输入。\n\n" +
                        "2. 外部需提供 [键控音源]：电台 CW 侧音、外置键控器/练习器的音频输出，或电脑播放的电报音。\n\n" +
                        "3. 纯机械手键/双桨是干接点开关，手机音频口无法直接读取通断，需要外接一台有源键控器（带侧音输出），再接入手机。\n\n" +
                        "4. APP 检测设定频率上的音调能量，判断 key down / key up，再自适应解码为点、划、字符。\n\n" +
                        "5. 自动键模式：外部自动键需自带键控器，APP 只负责识别与评分；屏幕双桨则由 APP 内置 IAMBIC 键控器。\n\n" +
                        "6. 建议使用有线耳机，避免外放声音回授到麦克风造成误判。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("术语说明")
                Text(
                    text = "点开查看常用缩写，训练时不用再猜参数含义。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Terms.forEach { (term, desc) ->
                    TermRow(
                        term = term,
                        desc = desc,
                        expanded = termOpen == term
                    ) { termOpen = if (termOpen == term) null else term }
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("训练统计")
                val days = app.stats.last7Days()
                val confused = app.stats.topConfused()
                Text(
                    text = "连续训练 ${app.stats.streakDays()} 天 · 7 日合计 ${days.sumOf { it.totalCount }} 次",
                    color = TextMain,
                    fontSize = 13.sp
                )
                Text(
                    text = "今日：背码 ${days.lastOrNull()?.learnCount ?: 0} · 听抄 ${days.lastOrNull()?.copyCount ?: 0} · 赛事 ${days.lastOrNull()?.contestCount ?: 0}",
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = if (confused.isEmpty()) "最易混字符：暂无记录"
                    else "最易混字符：" + confused.joinToString("  ") { (c, n) -> "$c ×$n" },
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "每日训练曲线见首页「训练历史」卡片。",
                    color = TextDim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("数据备份")
                Text(
                    text = "导出全部设置、练习统计、错题本与考试记录；换机或重装后导入同一文件即可恢复。" +
                        "「账号云同步」可把同一份备份在多台设备与网页端之间搬运。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("导出数据", false) { exporter.launch(backupFileName()) }
                    Pill("导入数据", false) { importer.launch(arrayOf("*/*")) }
                    Pill("进度同步", false) { onOpenSync() }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("账号云同步", false) { onOpenAccount() }
                }
                backupMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = if (backupOk) Green else Red,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("会员与支付")
                Text(
                    text = "会员功能默认暂未开放，由管理后台统一开启或关闭；当前全部训练功能免费使用。",
                    color = TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("会员中心", false) { onOpenMember() }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (app.member.enabled) "已开放" else "暂未开放",
                        color = if (app.member.enabled) Green else TextDim,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            SectionCard {
                SectionTitle("关于")
                Text(
                    text = "CW练考通  ${BuildConfig.VERSION_NAME}",
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "听抄 · 背码 · 发码 · 赛事听抄\n制作者 BI1GHP",
                    color = Amber,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                CheckForUpdateButton()
            }

            // 版权链接（纯文字，点击弹出 GNU AGPLv3 许可头）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "版权 © BI1GHP <2026> · <CW练考通>",
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showLicense = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("确认恢复数据") },
            text = {
                Text(
                    text = "导入会覆盖当前的设置、练习统计、错题本与考试记录，且无法撤销。确定继续？",
                    fontSize = 13.sp,
                    color = TextMain
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val r = runCatching {
                            val n = Backup.import(context, text)
                            app.prefs.reload()
                            app.stats.reload()
                            app.srs.reload()
                            app.contestLog.reload()
                            app.accent.reload()
                            app.exam.reload()
                            n
                        }
                        backupOk = r.isSuccess
                        backupMsg = r.fold(
                            { "已恢复 $it 项设置与练习记录" },
                            { "导入失败：${it.message ?: "文件格式不正确"}" }
                        )
                        pendingImport = null
                    }
                ) { Text("覆盖并恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            }
        )
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("GNU Affero 通用公共许可证 v3") },
            text = {
                Text(
                    text = """
版权 © 2026 BI1GHP

本程序为自由软件：您可以依据由自由软件基金会发布的 GNU Affero 通用公共许可证（版本 3）条款，对其进行再发布及/或修改。

发布本程序是希望它有用，但不提供任何担保；甚至没有适销性或特定用途适用性的默示保证。有关更多细节，请参阅 GNU Affero 通用公共许可证。

您应该已随本程序一并收到 GNU Affero 通用公共许可证的副本。如果没有，请参见 https://www.gnu.org/licenses/ 。

[2026] [BI1GHP]""".trimIndent(),
                    fontSize = 12.sp,
                    color = TextMain
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text("知道了") }
            }
        )
    }
}

/**
 * 主题实时预览卡：用目标主题的色板画一个迷你电台面板，
 * 因此预览本身不受当前主题影响，点选后整个 App 立即切换。
 */
@Composable
private fun ThemePreviewCard(
    title: String,
    subtitle: String,
    palette: WcColors,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.accent else palette.line,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surface1)
                .border(1.dp, palette.line, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "CW",
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "20 WPM",
                    color = palette.textDim,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(palette.surface2)
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth(0.72f).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(palette.line)
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniChip("发报", palette.accent)
                MiniChip("听抄", palette.accentAlt)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            color = palette.textMain,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = palette.textDim,
            fontSize = 9.sp,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 8.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 常用术语：训练与考试里反复出现的缩写 */
private val Terms = listOf(
    "WPM" to "每分钟单词数（词/分）。CW 按 Paris 标准计：1 个单词 = 50 个码元时长，20 WPM 约等于每秒 1 个字母的节奏。",
    "Farnsworth" to "法恩斯沃斯变速法：字符本身保持高速，只把字母之间、单词之间的间隔拉长。初学先用高速认字符，再逐步缩短间隔提总速。",
    "侧音" to "Sidetone：发报时自己耳机里听到的监听音，应与发射音调一致；练习用 600–700 Hz 最耐听。",
    "Iambic" to "双桨键模式：左右桨交替按压时自动补点补划（B 模式多补一个），适合高速发报；单桨键请关闭。",
    "SSB" to "单边带（J3E），话音通信方式。本 App 的「SSB 解释法」指呼号字母的口语读法，默认 NATO（Alpha、Bravo…），可切换中国读法。"
)

@Composable
private fun TermRow(
    term: String,
    desc: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = term,
                color = if (expanded) Amber else TextMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (expanded) "收起" else "说明",
                color = TextDim,
                fontSize = 9.sp
            )
        }
        if (expanded) {
            Text(
                text = desc,
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

private fun backupFileName(): String =
    "cwtrainer_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"

@Composable
private fun CheckForUpdateButton() {
    val ctx = LocalContext.current
    // 状态放在 UpdateManager 里，离开设置页/重组都不会丢，也不会取消下载协程
    val flow by UpdateManager.flow.collectAsState()
    val versionCode = remember { BuildConfig.VERSION_NAME.filter { it.isDigit() }.toIntOrNull() ?: 0 }
    val status = flow.status
    val message = flow.message
    val isClickable = status != "checking" && status != "downloading" && status != "installing"

    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when (status) {
                    "new" -> Amber.copy(alpha = 0.2f)
                    "error" -> Red.copy(alpha = 0.15f)
                    "up_to_date" -> Surface2
                    else -> Surface2
                }
            )
            .border(
                1.dp,
                when (status) {
                    "new" -> Amber
                    "error" -> Red
                    else -> Line
                },
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = isClickable) {
                when (status) {
                    "new", "need_perm" -> UpdateManager.downloadAndInstallAsync(ctx)
                    "checking", "downloading", "installing" -> Unit
                    else -> UpdateManager.checkUpdateAsync(versionCode)
                }
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            // 始终显示 message —— 各分支已把对应文案（含错误详情）写进 message
            // 之前 error 状态显示笼统的 onClickLabel，把详细错误吃掉了
            text = message,
            color = when (status) {
                "new", "downloading", "installing" -> Amber
                "error" -> Red
                "up_to_date" -> TextDim
                else -> TextMain
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
