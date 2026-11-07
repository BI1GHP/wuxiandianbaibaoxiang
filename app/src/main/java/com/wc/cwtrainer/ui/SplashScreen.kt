package com.wc.cwtrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// —— 启动图配色（深海军蓝渐变 + 琥珀描边）——
private val NavyTop = Color(0xFF031238)
private val NavyMid = Color(0xFF0A3272)
private val NavyLow = Color(0xFF0A2757)
private val NavyBottom = Color(0xFF04102C)
private val SplashAmber = Color(0xFFFFC857)
private val SplashInk = Color(0xFFEAF2FF)

/**
 * 冷启动展示 2 秒的品牌页：深蓝渐变 + CW 圆形徽标 + 名称 + 科目标签。
 * 全部用 Canvas 绘制，不依赖位图，任何分辨率都清晰。
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to NavyTop,
                    0.45f to NavyMid,
                    0.78f to NavyLow,
                    1f to NavyBottom
                )
            )
    ) {
        // 徽标后方的柔光
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.33f)
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0x3D5C9BFF), Color(0x00000000)),
                    center = center,
                    radius = size.width * 0.9f
                ),
                radius = size.width * 0.9f,
                center = center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.9f))
            LogoMark()
            Spacer(Modifier.weight(1f))
            Title()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "CW听抄训练 · 无线电考证刷题",
                color = Color(0xE6FFFFFF),
                fontSize = 13.sp,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(34.dp))
            Badges()
            Spacer(Modifier.weight(0.16f))
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun LogoMark() {
    Box(Modifier.size(214.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 1.dp.toPx()

            // 圆环 + 内部淡淡的高光
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0x14FFFFFF), Color(0x00FFFFFF)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0x4DFFFFFF),
                radius = radius,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 两侧对称的码流（负值表示圆点）
            val pattern = listOf(
                0.34f, 0.66f, 1f, 0.66f, 0.34f,
                -1f, -1f, -1f,
                0.34f, 0.66f, 1f, 0.66f, 0.34f
            )
            val waveY = size.height * 0.635f
            val maxH = size.height * 0.085f
            val startX = size.width * 0.135f
            val endX = size.width * 0.865f
            val step = (endX - startX) / (pattern.size - 1)
            pattern.forEachIndexed { i, p ->
                val x = startX + step * i
                if (p < 0f) {
                    drawCircle(SplashInk, radius = 1.9.dp.toPx(), center = Offset(x, waveY))
                } else {
                    val h = maxH * p
                    drawLine(
                        color = SplashInk,
                        start = Offset(x, waveY - h),
                        end = Offset(x, waveY + h),
                        strokeWidth = 2.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 电键笔：斜杆 + 笔尖圆点
            val penTop = Offset(size.width * 0.635f, size.height * 0.617f)
            val penBottom = Offset(size.width * 0.468f, size.height * 0.752f)
            drawLine(
                color = SplashAmber,
                start = penBottom,
                end = penTop,
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(SplashAmber, radius = 4.2.dp.toPx(), center = penTop)

            // 电键底座两条横线
            val baseHalf = size.width * 0.20f
            listOf(0.795f, 0.838f).forEach { fy ->
                drawLine(
                    color = SplashInk,
                    start = Offset(size.width / 2f - baseHalf, size.height * fy),
                    end = Offset(size.width / 2f + baseHalf, size.height * fy),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            text = "CW",
            modifier = Modifier.offset(y = (-22).dp),
            color = Color.White,
            fontSize = 70.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun Title() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "CW练考",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "通",
            color = SplashAmber,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun Badges() {
    val items = listOf("听", "抄", "背", "赛事模拟", "C/B类题库", "模拟考试")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { label ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x1AFFFFFF))
                    .border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = label,
                    color = Color(0xFFE8F1FF),
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
