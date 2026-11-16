package com.wc.cwtrainer.core

/**
 * 通联手册内容：日常通联规范（UV / 短波）与常用 Q 短语。
 * 内容按条目拆分，便于页面内检索。
 */
data class HandbookEntry(
    val category: String,
    val title: String,
    val body: String,
    val keywords: String = ""
)

object Handbook {

    const val UV = "UV 通联"
    const val HF = "短波通联"
    const val Q = "Q 短语"

    val categories = listOf(UV, HF, Q)

    val entries: List<HandbookEntry> = listOf(
        // ——— UV（VHF / UHF）———
        HandbookEntry(
            UV, "频段与常用频率",
            "2m：144.000–148.000 MHz；70cm：430.000–440.000 MHz。\n" +
                "常用直频：2m 145.000 FM、144.200 SSB、144.100 CW；70cm 438.500 FM、432.100 CW/SSB。\n" +
                "中继频差常见 ±5 MHz，亚音 88.5 Hz；具体以当地频率协调和执照规定为准。",
            "144 430 145 438 频率 中继 频差 亚音"
        ),
        HandbookEntry(
            UV, "通联前先守听",
            "上频率先守听 10–30 秒；问一句「这个频率有人使用吗？」；确认无人后再发起 CQ。\n" +
                "不要压住正在进行的 QSO；听到 QRL 立即改频。",
            "守听 占用 QRL 礼仪"
        ),
        HandbookEntry(
            UV, "CQ 呼叫格式（语音）",
            "CQ CQ CQ，这里是 BG5ABC，BRAVO GOLF FIVE ALPHA BRAVO CHARLIE，CQ 并等待回答。\n" +
                "呼号用字母解释法读两遍，语速平稳、句子简短；呼叫后留出应答时间。",
            "CQ 呼叫 呼号 解释法 语音"
        ),
        HandbookEntry(
            UV, "应答与信号报告",
            "应答：BG5ABC，这里是 BG5XYZ，收到你 59，我的名字是……，QTH……，请讲。\n" +
                "SSB 常用 RS 报告（如 59），CW 用 RST（如 599）；听不清时直接说「请重复」。",
            "59 55 RST 信号报告 应答"
        ),
        HandbookEntry(
            UV, "中继通联要点",
            "先确认上行/下行频率与亚音；报呼号后简短说明来意；不长时间占用中继；遇紧急通信立即让频。\n" +
                "跨中继通联结束说 73，并说明 QSY 去向，避免双方失联。",
            "中继 亚音 上行 下行 差频 让频"
        ),
        HandbookEntry(
            UV, "字母解释法（NATO）",
            "A Alpha  B Bravo  C Charlie  D Delta  E Echo  F Foxtrot  G Golf  H Hotel  I India  J Juliett\n" +
                "K Kilo  L Lima  M Mike  N November  O Oscar  P Papa  Q Quebec  R Romeo  S Sierra  T Tango\n" +
                "U Uniform  V Victor  W Whiskey  X X-ray  Y Yankee  Z Zulu；数字逐位读。",
            "NATO 解释法 Alpha Bravo 拼音 字母"
        ),
        HandbookEntry(
            UV, "结束语与改频",
            "结束：73（美好祝愿），BG5ABC 73。\n" +
                "改频：说明「QSY 到 145.500」，确认后双方一起改频；离开频率前简短告别。 ",
            "73 88 QSY 结束 改频"
        ),
        HandbookEntry(
            UV, "UV 使用禁区",
            "不占用应急、卫星、信标与公共业务频率；不播放音乐、广告或广播内容；不匿名、不冒用呼号；\n" +
                "发射功率以完成通联所需最小为原则，不擅自改装设备。",
            "应急 卫星 信标 功率 规范 禁区"
        ),

        // ——— 短波（HF）———
        HandbookEntry(
            HF, "常用频段与模式",
            "80/40/20/15/10 m。10 MHz 以下用 LSB，以上用 USB；CW 多集中在频段低端。\n" +
                "常用：40m 7.023 CW / 7.050 LSB；20m 14.023 CW / 14.270 USB；15m 21.023 / 21.300；10m 28.023 / 28.500。\n" +
                "实际使用以国内频率划分和本人执照权限为准。",
            "40m 20m 15m 10m 80m 7.023 14.270 LSB USB"
        ),
        HandbookEntry(
            HF, "CQ 呼叫（CW）",
            "CQ CQ CQ DE BG5ABC BG5ABC K\n" +
                "CQ 重复三次、DE、呼号两次、K 结束；无回应时隔几秒再呼叫，不要连续长时间发射。",
            "CQ DE K CW 呼叫"
        ),
        HandbookEntry(
            HF, "CQ 呼叫（SSB）",
            "CQ CQ CQ，这里是 BG5ABC……呼叫并等待回答。\n" +
                "每次 10–15 秒，呼叫后守听；必要时报「QRZ?」确认是否有人回应。",
            "CQ SSB 语音 呼叫 QRZ"
        ),
        HandbookEntry(
            HF, "标准 QSO 流程",
            "应答 → 信号报告 → 名字 → QTH → 设备/天线 → 天气（可选）→ 73 与 QSL 约定。\n" +
                "跟随对方节奏，信息短而清楚；不确定时请对方重复，不要猜。",
            "QSO 流程 通联 步骤 QSL 73"
        ),
        HandbookEntry(
            HF, "信号报告 RST",
            "R 可读度 1–5；S 信号强度 1–9；T 音调 1–9（仅 CW）。\n" +
                "SSB 常用 59，CW 常用 599；信号弱可报 55、579 等。",
            "RST 59 599 579 信号报告"
        ),
        HandbookEntry(
            HF, "DX 与竞赛通联",
            "只发必要信息：呼号、信号报告、序号；跟随 DX 的 split（如 UP 5）操作；不打断、不长时间 CQ。\n" +
                "听清对方呼号再应答，避免反复 QRZ?；竞赛中按规则交换序号。",
            "DX 竞赛 split UP 序号 远征"
        ),
        HandbookEntry(
            HF, "频率使用礼仪",
            "先守听；不抢占他人 QSO；不无理由长时间占用频率；遇紧急通信立即让频；功率最小够用。\n" +
                "需要加入时等对方通联结束，用简短呼号示意。",
            "礼仪 占用 紧急 功率 加入"
        ),
        HandbookEntry(
            HF, "传播与时段",
            "白天优先 20/15/10m，夜间 80/40m；日出日落前后灰线传播增强；太阳活动影响可用频率（MUF）。\n" +
                "根据实际传播调整频段，避免在无传播时长时间无效呼叫。",
            "传播 灰线 太阳 MUF 时段 80m 40m"
        ),
        HandbookEntry(
            HF, "短波法规要点",
            "持证操作，按执照等级使用频率与功率；不得与非持证者通信（紧急情况除外）；\n" +
                "不得用于商业、广播或违法用途；建议保留通联日志，QSL 往来遵守邮政与海关规定。",
            "法规 执照 功率 日志 合法"
        ),

        // ——— Q 短语 ———
        HandbookEntry(Q, "QRL", "频率忙 / 有人正在使用。发「QRL?」询问；听到 QRL 应立即改频（QSY）。", "占用 频率忙 询问"),
        HandbookEntry(Q, "QRM", "人为干扰（其他电台、设备噪声等）。可报「QRM 5」表示干扰很重。", "干扰 人为"),
        HandbookEntry(Q, "QRN", "天电噪声 / 大气噪声，通常由雷电、静电等引起。", "噪声 天电 静电"),
        HandbookEntry(Q, "QRO", "提高发射功率；也可指大功率操作。", "功率 增大 大功率"),
        HandbookEntry(Q, "QRP", "降低发射功率；也指小功率通联爱好者（通常 ≤5W）。", "小功率 降低 5W"),
        HandbookEntry(Q, "QRQ", "请发快一点（Send faster）。", "加快 速度"),
        HandbookEntry(Q, "QRS", "请发慢一点（Send slower）。", "减慢 速度 新手"),
        HandbookEntry(Q, "QRT", "停止发射 / 关机结束通联。", "停止 关机 结束"),
        HandbookEntry(Q, "QRV", "已准备就绪，可以开始通联。", "准备 就绪"),
        HandbookEntry(Q, "QRX", "请稍等，稍后再联络（常带时间，如 QRX 5）。", "稍等 等待"),
        HandbookEntry(Q, "QRZ", "谁在呼叫我？常用于没听清对方呼号时（QRZ?）。", "谁 呼叫 没听清"),
        HandbookEntry(Q, "QSB", "信号衰落（忽强忽弱），常由传播变化引起。", "衰落 忽强忽弱 传播"),
        HandbookEntry(Q, "QSK", "可以插入 / 全双工操作，CW 中可在发报间隙收听。", "插入 全双工 CW"),
        HandbookEntry(Q, "QSL", "确认收妥；也指 QSL 卡片（通联证明卡）。", "确认 卡片 QSL卡"),
        HandbookEntry(Q, "QSO", "一次完整的双向通联。", "通联 联络"),
        HandbookEntry(Q, "QSP", "请代为转达（转报）。", "转达 转发"),
        HandbookEntry(Q, "QST", "面向全体电台的通告（一般性广播）。", "通告 广播 全体"),
        HandbookEntry(Q, "QSY", "改变频率；通联中说「QSY 到 …」并确认。", "改频 换频"),
        HandbookEntry(Q, "QTH", "电台位置 / 所在地。", "位置 地址 QTH"),
        HandbookEntry(Q, "QTR", "正确时间（UTC 或当地时间）。", "时间 对时 UTC"),
        HandbookEntry(Q, "73", "致敬、美好祝愿，通联结束时使用。", "结束 祝福 敬礼"),
        HandbookEntry(Q, "88", "爱与祝福，通常用于异性爱好者之间。", "祝福 异性"),
        HandbookEntry(Q, "TU", "Thank you，谢谢。", "谢谢 感谢"),
        HandbookEntry(Q, "GL", "Good luck，祝好运。", "好运 祝福"),
        HandbookEntry(Q, "OM / YL / XYL", "OM：老友 / 男爱好者；YL：女爱好者；XYL：妻子（原意「前妻」，现多作谦称）。", "称谓 老友 女 妻子"),
        HandbookEntry(Q, "DE", "「来自」，CW 中用于分隔：对方呼号 DE 本台呼号。", "来自 分隔 CW"),
        HandbookEntry(Q, "K / KN", "K：请任何电台回答；KN：仅请指定电台回答。", "回答 指定 仅"),
        HandbookEntry(Q, "AR / SK", "AR：报文结束；SK：本次联络结束（Silent Key 另指已故爱好者）。", "结束 报文 静默键"),
        HandbookEntry(Q, "BK", "插入 / 打断，用于 CW 中请求插入或快速切换。", "插入 打断"),
        HandbookEntry(Q, "RST", "CW 信号报告体系：R 可读度、S 强度、T 音调，各 1–9（R 为 1–5）。", "信号报告 RST 599"),
        HandbookEntry(Q, "CQ", "广泛呼叫，希望任何电台应答；可加 CQ DX、CQ Contest 等限定。", "呼叫 广泛 DX 竞赛"),
        HandbookEntry(Q, "DX", "远距离 / 稀有电台或地区，常需要耐心呼叫与 split 操作。", "远距离 稀有 远征")
    )
}
