package com.wc.cwtrainer.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import com.wc.cwtrainer.core.ExamLevel
import com.wc.cwtrainer.core.ExamResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// —— QSL 卡片配色（纸质卡片，独立于 App 主题）——
private val QslInk = Color(0xFF2B2620)
private val QslPaper = Color(0xFFFAF4E6)
private val QslCard = Color(0xFFFFFDF6)
private val QslLine = Color(0xFFD8CBB4)
private val QslAmber = Color(0xFFB07C1F)
private val QslGreen = Color(0xFF2F7D5B)
private val QslRed = Color(0xFFB34A3A)
private val QslDim = Color(0xFF8A7E6B)

/** 一张成绩单需要的全部展示数据 */
data class QslData(
    val subject: String,
    val mode: String,
    val rows: List<Pair<String, String>>,
    val stamp: String,
    val stampOk: Boolean,
    val rating: String,
    val serial: String,
    val dateText: String
)

private fun now(pattern: String): String =
    SimpleDateFormat(pattern, Locale.CHINA).format(Date())

private fun ratingOf(rate: Float): String = when {
    rate >= 0.95f -> "S9+20"
    rate >= 0.90f -> "S9"
    rate >= 0.80f -> "S8"
    rate >= 0.70f -> "S7"
    rate >= 0.60f -> "S5"
    else -> "S3"
}

/** 模拟考试的成绩单 */
fun examQsl(r: ExamResult, level: ExamLevel): QslData {
    val percent = (r.rate * 100).toInt()
    val time = "${r.timeMs / 60000}m ${(r.timeMs / 1000) % 60}s"
    return QslData(
        subject = "${level.cn}模拟考试",
        mode = "EXAM",
        rows = listOf(
            "日期 DATE" to now("yyyy.MM.dd HH:mm"),
            "成绩 SCORE" to "答对 ${r.correct} / ${r.total} 题 · $percent%",
            "用时 TIME" to time,
            "判定 RESULT" to if (r.passed) "合格（需 ${level.passCount} 题）" else "未通过（需 ${level.passCount} 题）"
        ),
        stamp = if (r.passed) "通  过" else "未 通 过",
        stampOk = r.passed,
        rating = ratingOf(r.rate),
        serial = "QSL-${now("yyyyMMdd-HHmm")}",
        dateText = now("yyyy.MM.dd")
    )
}

/** 听抄判分后的成绩单 */
fun copyQsl(target: String, answer: String, accuracy: Float, passCount: Int): QslData {
    val percent = (accuracy * 100).toInt()
    val length = target.filter { !it.isWhitespace() }.length
    return QslData(
        subject = "听抄练习",
        mode = "COPY",
        rows = listOf(
            "日期 DATE" to now("yyyy.MM.dd HH:mm"),
            "成绩 SCORE" to "正确率 $percent% · 达标线 $passCount%",
            "字数 LENGTH" to "$length 字符",
            "判定 RESULT" to if (accuracy >= passCount / 100f) "达标 PASS" else "未达标"
        ),
        stamp = if (accuracy >= passCount / 100f) "抄 收 准 确" else "继 续 练 习",
        stampOk = accuracy >= passCount / 100f,
        rating = ratingOf(accuracy),
        serial = "QSL-${now("yyyyMMdd-HHmm")}",
        dateText = now("yyyy.MM.dd")
    )
}

/**
 * QSL 成绩单弹窗：纸质卡片 + 分享按钮。
 * 分享时把卡片区域裁切为 PNG，经 FileProvider 交给系统分享面板。
 */
@Composable
fun QslCardDialog(data: QslData, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE0000000))
                .systemBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { cardBounds = it.boundsInWindow() }
            ) {
                QslCard(data)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QslAction("分享成绩单", true, Modifier.weight(1f)) {
                    shareCardImage(context, view, cardBounds, "分享 QSL 成绩单")
                        .onFailure { message = "分享失败：${it.message}" }
                }
                QslAction("关闭", false, Modifier.weight(1f)) { onDismiss() }
            }
            message?.let {
                Text(
                    text = it,
                    color = Color(0xCCFFFFFF),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** 把视图区域裁切成 PNG 并拉起系统分享（QSL 成绩单 / 呼号牌共用） */
fun shareCardImage(
    context: Context,
    view: android.view.View,
    bounds: Rect?,
    subject: String
): Result<Unit> = runCatching {
    val bitmap = view.drawToBitmap()
    val cropped = cropToBounds(bitmap, bounds)
    val dir = File(context.cacheDir, "qsl").apply { mkdirs() }
    val file = File(dir, "card_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}

/** 把整窗截图裁到卡片区域，失败时退回整图 */
private fun cropToBounds(bitmap: Bitmap, bounds: Rect?): Bitmap {
    if (bounds == null) return bitmap
    val left = bounds.left.toInt().coerceIn(0, bitmap.width - 1)
    val top = bounds.top.toInt().coerceIn(0, bitmap.height - 1)
    val right = bounds.right.toInt().coerceIn(left + 1, bitmap.width)
    val bottom = bounds.bottom.toInt().coerceIn(top + 1, bitmap.height)
    return runCatching {
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }.getOrDefault(bitmap)
}

@Composable
private fun QslCard(data: QslData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(QslPaper)
            .border(1.6.dp, QslInk, RoundedCornerShape(6.dp))
            .padding(5.dp)
            .border(0.8.dp, QslLine, RoundedCornerShape(3.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "QSL",
                    color = QslInk,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "成绩单",
                    color = QslInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CONFIRMATION",
                        color = QslDim,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = "OF PERFORMANCE",
                        color = QslDim,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.4.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(QslInk.copy(alpha = 0.55f)))
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "BI1GHP",
                        color = QslAmber,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${data.subject} · ${data.mode}",
                        color = QslDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = data.rating,
                    color = QslGreen,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(10.dp))
            data.rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = label,
                        color = QslDim,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.width(72.dp)
                    )
                    Text(
                        text = value,
                        color = QslInk,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .rotate(-7f)
                        .border(2.dp, if (data.stampOk) QslGreen else QslRed, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = data.stamp,
                        color = if (data.stampOk) QslGreen else QslRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = data.serial,
                        color = QslInk,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = data.dateText,
                        color = QslDim,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "·−·−·  ·−··  ···−  ·",
                    color = QslAmber.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "73 DE BI1GHP",
                    color = QslDim,
                    fontSize = 7.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
            }
            Text(
                text = "CW练考通 · 业余无线电训练与考证",
                color = QslDim.copy(alpha = 0.85f),
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun QslAction(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) QslAmber else Color(0x33FFFFFF))
            .border(
                1.dp,
                if (primary) Color.Transparent else Color(0x66FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) Color(0xFF1A1200) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
