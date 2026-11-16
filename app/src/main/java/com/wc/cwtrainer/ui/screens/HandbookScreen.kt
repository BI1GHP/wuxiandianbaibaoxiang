package com.wc.cwtrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wc.cwtrainer.core.Handbook
import com.wc.cwtrainer.core.HandbookEntry
import com.wc.cwtrainer.ui.Amber
import com.wc.cwtrainer.ui.Bg
import com.wc.cwtrainer.ui.Line
import com.wc.cwtrainer.ui.ScreenHeader
import com.wc.cwtrainer.ui.Surface1
import com.wc.cwtrainer.ui.Surface2
import com.wc.cwtrainer.ui.TextDim
import com.wc.cwtrainer.ui.TextMain

/** 通联手册：UV / 短波通联规范与 Q 短语，支持关键字检索 */
@Composable
fun HandbookScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }

    val filtered = remember(query, category) {
        val q = query.trim().lowercase()
        Handbook.entries.filter { e ->
            (category == "全部" || e.category == category) &&
                (q.isEmpty() || (e.category + e.title + e.body + e.keywords).lowercase().contains(q))
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        ScreenHeader("通联手册", onBack, subtitle = "HANDBOOK · OPERATING")
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface1)
                .border(1.dp, Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = TextMain, fontSize = 14.sp),
                cursorBrush = SolidColor(Amber),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("搜索：频率 / CQ / 中继 / QSL …", color = TextDim, fontSize = 13.sp)
                    }
                    inner()
                }
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (listOf("全部") + Handbook.categories).forEach { c ->
                val selected = c == category
                Text(
                    text = c,
                    color = if (selected) Bg else TextDim,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Amber else Surface2)
                        .border(1.dp, if (selected) Amber else Line, RoundedCornerShape(20.dp))
                        .clickable { category = c }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "共 ${filtered.size} 条",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(6.dp))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)
        ) {
            if (filtered.isEmpty()) {
                Text("没有匹配的条目，换个关键词试试。", color = TextDim, fontSize = 12.sp)
            }
            filtered.forEach { item ->
                EntryCard(item)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EntryCard(item: HandbookEntry) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                color = Amber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(item.category, color = TextDim, fontSize = 10.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.body,
            color = TextMain.copy(alpha = 0.88f),
            fontSize = 12.sp,
            lineHeight = 19.sp
        )
    }
}
