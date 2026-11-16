package com.wc.cwtrainer.core

/**
 * 背题口诀生成。
 *
 * 题库有 2400+ 题，无法逐题人工撰写，因此采用「重点精编 + 规则生成」：
 * 1. 命中内置高频考点（法规、频率、功率、天线、电路等）时返回精编口诀；
 * 2. 未命中时用正确选项提炼关键词，生成「关键词 → 选项字母」的记忆钩子；
 * 3. 含数字的答案额外给出数字绑定提示。
 *
 * 口诀只在背题模式展示，模拟考试与错题本作答不受影响。
 */
object Mnemonics {

    /** 重点精编规则：正则命中题干或任一选项时使用 */
    private val curated: List<Pair<Regex, String>> = listOf(
        Regex("业余无线电台管理办法|设台申请|设置业余无线电台") to
            "记「条例管频率，办法管设台」：设台、执照、呼号找《业余无线电台管理办法》",
        Regex("施行|生效日期|自.*起施行") to
            "时间锚点：2024 年 3 月 1 日新《办法》施行，记「24·3·1，办法上路」",
        Regex("划分|分配|指配") to
            "三段口诀：划分定频段 → 分配定业务 → 指配定台站，从粗到细",
        Regex("呼号前缀|呼号组成|呼号结构") to
            "呼号三段：前缀定国家、数字定分区、后缀定台站",
        Regex("Q简语|QSL|QRZ|QSY|QRT|QRM|QRN|QRP|QRO") to
            "Q 简语记高频：QRZ 谁呼我、QSY 换频率、QSL 已确认、QRT 停止发射、QRM 人为干扰",
        Regex("字母解释法|NATO|Alfa|Bravo|Charlie") to
            "字母解释法先背前五：Alfa Bravo Charlie Delta Echo，再按 NATO 全表扩展",
        Regex("驻波比|SWR") to
            "驻波比越接近 1 越好：日常 1.5 以内，2 以上必须查天线与接头",
        Regex("dBm|dBW|分贝|增益") to
            "功率换算：翻倍 +3dB，十倍 +10dB；30dBm=1W，40dBm=10W",
        Regex("欧姆定律|电阻|电压|电流|电功率") to
            "电路三式：U=IR、P=UI、P=I²R；先画等效图，再代数",
        Regex("波长|天线长度|半波|四分之一波长|偶极") to
            "天线长度记 λ：半波偶极 0.5λ，四分之一垂直 0.25λ，波长 λ=300/f(MHz)",
        Regex("干扰|QRM|邻频|杂散") to
            "干扰排查顺序：先查自身设备 → 再查馈线接头 → 最后查邻频与外部干扰",
        Regex("安全|触电|接地|防雷|雷击") to
            "安全口诀：先断电、后操作；接地防雷、单手操作、保持高压安全距离",
        Regex("频率换算|MHz|kHz|Hz|频段") to
            "频率换算记千进位：1 MHz=1000 kHz=1,000,000 Hz",
        Regex("调制|调幅|调频|单边带|SSB|USB|LSB") to
            "调制速记：AM 调幅、FM 调频、SSB 单边带；USB 上边带、LSB 下边带",
        Regex("中继|差频|亚音|CTCSS|上下行") to
            "中继三要素：上行差频、下行频率、亚音码，缺一不通",
        Regex("操作证|A类|B类|C类|等级") to
            "操作证记等级：A 类入门、B 类升级、C 类最高；等级越高可用频段越多",
        Regex("执照|电台执照|有效期|延续") to
            "执照记「五年一换」：到期前及时申请延续，过期不得发射",
        Regex("发射功率|功率限制|最大功率") to
            "功率题先找「不得超过」后的数字，再对照操作证等级与频段",
        Regex("电磁兼容|EMC|电磁辐射|辐射限值") to
            "电磁兼容记两头：设备少干扰、也抗干扰；人体防护看辐射限值",
        Regex("天馈|馈线|同轴|接头|驻波") to
            "天馈口诀：馈线越短损耗越小，接头防水比盲目加增益更重要",
        Regex("检波|混频|振荡|放大|滤波|收发链路") to
            "收发链路顺序：振荡 → 混频 → 放大 → 滤波 → 天线",
        Regex("摩尔斯|Morse|电码|点划") to
            "摩尔斯记长短：点 1 划 3；码内间隔 1，字母间隔 3，单词间隔 7",
        Regex("应急|救灾|求救|SOS|紧急通信") to
            "应急通信先报位置与需求；SOS 三短三长三短，保持频率安静",
        Regex("法规|条例|工信部|无线电管理机构|监督") to
            "法规题抓主体：国务院/中央军委定条例，工信部与地方无线电管理机构管执行",
        Regex("国际电信联盟|ITU|频率划分表") to
            "ITU 三步：划分（全球）→ 分配（国家）→ 指配（台站）",
        Regex("公众对讲|免执照|微功率|短距离") to
            "免执照设备记「微功率、短距离、天线不可改装」",
        Regex("业余卫星|卫星|多普勒|信标") to
            "业余卫星：先听信标对频率，再算多普勒，最后按上行/下行发射",
        Regex("数字模式|FT8|DMR|C4FM|数字通信") to
            "数字模式先对频率与带宽，再对时隙/时隙号，最后确认呼号与报告",
        Regex("天线增益|方向性|波束|前后比") to
            "增益题记：增益靠方向性换来，方向越窄增益越高，前后比看抑制",
        Regex("阻抗|50欧|匹配|巴伦") to
            "阻抗匹配记 50Ω：馈线、天线、设备一致，失配会反射形成驻波"
    )

    /** 按章节给一句通用记忆提示 */
    private fun chapterHint(chapter: Int): String = when (chapter) {
        1 -> "法规题记：谁主管、办什么、多久有效"
        2 -> "操作题记：先听后发、报全呼号、守好频段"
        3 -> "设备题记：阻抗匹配、馈线损耗、天线谐振"
        4 -> "电路题记：先画等效图，再套 U=IR / P=UI"
        5 -> "指标题记：功率看 dB、杂散看限值、稳定看温漂"
        else -> "先抓题干主体，再用排除法去掉绝对化选项"
    }

    /** 去掉常见虚词与标点，提炼选项里的记忆关键词 */
    private fun keyword(option: String, limit: Int): String {
        val cleaned = option
            .replace(Regex("^(应当|应该|可以|必须|需要|属于|是指|包括|采用|使用|按照|根据|由)"), "")
            .trim()
            .trimEnd('。', '，', ',', '；', ';', '：', ':')
        return if (cleaned.length <= limit) cleaned else cleaned.take(limit) + "…"
    }

    /** 生成一道题的背诵口诀，保证非空 */
    fun of(q: Question): String {
        val haystack = q.stem + " " + q.options.joinToString(" ")
        val answerLetters = q.ans.mapNotNull { "ABCD".getOrNull(it)?.toString() }.joinToString("、")
        val answerText = q.ans.mapNotNull { q.options.getOrNull(it) }.joinToString("；")

        // 1. 重点精编：命中后仍附上本题答案，避免口诀与题目脱节
        curated.firstOrNull { it.first.containsMatchIn(haystack) }?.let { (_, tip) ->
            return "$tip。本题答案：$answerText → $answerLetters"
        }

        // 2. 规则生成：从正确选项提炼关键词
        val keys = q.ans.mapNotNull { q.options.getOrNull(it) }
            .map { keyword(it, if (q.isMulti) 12 else 22) }
            .filter { it.isNotBlank() }

        val base = if (q.isMulti) {
            "口诀：$answerLetters 一组 · ${keys.joinToString(" · ")}（多选先找齐，再排除漏项）"
        } else {
            "口诀：${keys.firstOrNull() ?: keyword(q.stem, 22)} → 选 $answerLetters"
        }

        // 3. 数字题额外绑定
        val digits = answerText.filter { it.isDigit() }
        val digitTip = if (digits.length in 1..6 && !answerText.contains("无")) {
            "；数字题：把「$digits」与题干关键词绑在一起记"
        } else {
            ""
        }

        return "$base。${chapterHint(q.chapter)}$digitTip"
    }
}
