# MES 报表（Report）— 架构设计

> 定位：班会 / 对账级**事后复盘**——可筛选的 Move（过站）与 Hold 分布；**只读、不下写**  
> 范式：**Report 不造状态**；只读 History / Hold（及必要窄查询），口径可对账  
> 产品口径：制造主管 / IE「昨天过了多少、锁在哪类原因」；不是指挥屏、不是 YMS、不是真 OEE  
> 对齐：`docs/架构/半导MES架构设计.md` §5.11；业务清单 §12；实施进度「Report 基础报表」；业界分层（MES 运营报表 ≠ YMS）  
> 前提：Track→`mes_tx_log`、Hold、History 调查台、Dashboard 一期已齐  
> 状态：**一期已完成 · 联调通过（Rep-1～4）**  
> 更新：2026-09-10

---

## 0. 入口（产品）

| 结论 | 说明 |
|------|------|
| **要有侧栏菜单** | 报表是主动查找（班会/对账），链入不能当唯一入口 |
| **不进「生产执行」** | 该组是班次主路径（看板→过站…）；报表是事后复盘，硬插会抢「看板」注意力、侧栏更挤 |
| **独立轻分组** | 侧栏新增目录 **「复盘」**（或「分析」），其下挂 **报表** → `/app/report` |
| **链入仍保留** | 看板趋势区 / 履历页文字链作下钻辅助，不替代菜单 |

---

## 1. 边界

```
Report    = 只读聚合 API + Admin 报表页；Move 过站 / Hold 分布；时间窗筛选
Dashboard = 当前态指挥屏 + TrackOut 粗趋势；不问「按站/按因明细」
History   = 单 Lot / 条件明细调查台；Report 不做行级翻页调查
Hold      = 锁批真相（mes_hold + 原因码）；Report 只聚合
Track     = 事务写入 mes_tx_log；Report 只 COUNT/GROUP
WIP/Eqp   = 可选 P1 辅助维度；一期不进顶报
YMS/SPC   = 良率学习 / 判异；禁止 Report 一期冒充 Yield 分析台
Adapter   = GEM 稼动；真 OEE / WPH 后置
```

**与 Dashboard 的分工（硬边界）**

| | Dashboard | Report |
|--|-----------|--------|
| 时间语义 | 现在（+ 近 7 日粗趋势） | 选定区间的历史 |
| 问题 | 哪里堵 / 坏 / 锁 | 过了多少、Hold 什么因、哪站多 |
| 刷新 | 轮询秒级 | 查询触发；无 Live |
| 导出 | 不做 | 一期不做 Excel；P1 再评 |

**本切片做什么**

- 包 `com.mes.report`；对外 **仅读** `ReportFacade` + 少量 GET
- Move：按日、按站 `TRACK_OUT` 计数（过站量）
- Hold：按原因分布；可选已释放时长分位（P0 最小：计数 + 可选 avg 时长）
- Admin 页 `/app/report`；权限 `report:view`
- 侧栏：**「复盘」分组 +「报表」菜单**（见 §0）；**禁止**挂进「生产执行」
- 看板 / 履历可链入（辅助）；服务端编排；前端不扇出 History/Hold 自行拼表

**本切片不做什么**

- Yield / 站良率 / 累积良率 / Scrap Pareto
- 真 OEE、WPH、SEMI E10 稼动公式
- Excel / 邮件订阅 / 低代码自助报表
- 预聚合日表、XXL-JOB、独立只读库
- 写接口、改 Hold/Lot、Report 内 Release
- 周期 X-factor、WIP 目标剖面（SLIM 类）— P1/P2
- 把「报表」塞进「生产执行」或「系统管理」

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Report 直查外域表绕过 Facade/Service 约定 | 口径漂移；同 Dashboard P1 |
| P2 | 一期顶卡 / 主表展示「良率 %」「稼动 %」 | 无 YMS/GEM 定义则假数毁信任 |
| P3 | Report 包内写业务表 | 复盘台变执行器 |
| P4 | 把 History 调查台重做成报表页 | 职责分裂；明细仍 `/app/history` |
| P5 | 前端并行打 History+Hold 自聚合且无契约 | 扇出、权限与半失败难治理 |
| P6 | 为报表双写「产出日表」与 tx_log | 真相分裂；一期 GROUP BY 履历即可 |
| P7 | 与 Dashboard overview 合并成一个巨型 API | 刷新策略与受众不同 |
| P8 | 「报表」挂在「生产执行」目录下 | 与班次主路径混心智；抢看板入口 |

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 形态 | 同进程只读 Facade；无独立微服务 | 与 Dashboard/History 一致 |
| D2 | 职责 | **聚合编排**，不拥有业务表 | SSOT 在 Track/Hold |
| D3 | 对外 API | 按主题拆 GET（move / hold）；可选 `summary` 壳 | 报表常单开一题；避免 overview 式强绑 |
| D4 | Move 口径 | `tx_type = TRACK_OUT`；按 `create_time` 日界、按 `step_id` 分组 | 与 Dashboard 趋势同源；「过站」= 出站成功 |
| D5 | Hold 口径 | 以 `mes_hold.hold_time` 落入查询窗计「发生」；按 `reason_code` 聚合 | 原因码已是主数据；不靠自由文本 |
| D6 | 时长 | 已释放：`release_time - hold_time`；仍 active：可用 `now - hold_time` 标 `open=true` | 班会关心「挂了多久」；口径写进清单锁死 |
| D7 | 时间窗 | Query：`from`/`to`（含）本地日期；默认近 7 日；上限 31 日（一期） | 防全表扫；放大窗后置 |
| D8 | 时区 | 与 Dashboard/History 同一本地时区约定 | 跨日对账一致 |
| D9 | 缓存 / 预聚合 | 一期无；压测后再评短 TTL 或日汇总表 | 现无 XXL-JOB；勿先上双写 |
| D10 | 权限 | `report:view`；聚合读取不要求调用方持有 `history:view`/`hold:list` | 制造看报表即可；明细页仍各自鉴权 |
| D11 | 入口 | 侧栏 **复盘 → 报表**；辅以看板/履历链入 | 要有稳定入口；与生产执行分家 |
| D12 | 下钻 | 链到 `/app/history`、`/app/hold`；一期可不带深链参数 | 行动在源页 |
| D13 | 与 YMS | Yield 深度分析永不进 Report 内核一期 | 业界 MES 运营报表 ≠ Yield Management |
| D14 | 失败 | 单查询失败 → 该块空 + `partial`；不整页 500 | 与 Dashboard 一致 |

**刻度**：一期只消费 **已落地** `mes_tx_log` + `mes_hold`（+ 原因名）。设备 GEM、良率、班次日历后置。

---

## 3. 依赖与数据流

```
浏览器（侧栏 复盘/报表 · 或看板/履历链入）
    │  GET /report/move?...
    │  GET /report/hold?...
    ▼
ReportFacade
    ├── Move：HistoryFacade 扩展 或 TxLog 窄聚合（按日 / 按 stepId）
    │         step 显示名：Route/Step 只读解析（可选 code 回退 id）
    └── Hold：HoldService / Mapper 聚合（按 reasonCode；时长）
    ▼
ReportMoveVO / ReportHoldVO
```

**读法约束**

- 优先经 `HistoryFacade` / `HoldService` 增加**报表专用**聚合方法；禁止 Report Mapper 随意 JOIN 全库。
- Move 按站需要 `step_id`：履历已有；缺 step 的行归「未归属」桶，不丢计数。
- Hold 按站：`mes_hold` **无** step 字段 → **一期不做按站 Hold**（避免用「当前 WIP 站」事后臆造）；P1 可从 `HOLD` 履历 `step_id` 补。

```
业界对照（公开产品叙事，非某 IDM 内部制度）
  Applied：Operational vs Historical reporting；Lot vs Eqp 视角
  CM WIP Analyzer：queued / in-process / on-hold 分布与趋势
  Applied/SemiEng：YMS 与 MES 分列 — Yield 学习不进本包
```

---

## 4. 门面契约

```
ReportFacade（只读）
  moveSummary(from, to) → ReportMoveVO
  holdSummary(from, to) → ReportHoldVO

ReportMoveVO
  generatedAt
  partial: boolean
  errors?: string[]
  from, to                    // 回显查询窗
  byDay: [{ day, trackOutCount }]
  byStep: [{ stepId?, stepCode?, stepName?, trackOutCount }]
  totalTrackOut               // 窗内合计

ReportHoldVO
  generatedAt
  partial / errors / from / to
  byReason: [{
    reasonCode, reasonName?,
    holdCount,                // hold_time ∈ [from,to]
    activeCount,              // 其中仍 active
    avgDurationMinutes?       // 有结束时刻或用 now；实现锁死一种
  }]
  totalHold
```

HTTP：

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/report/move` | `report:view` | `from`/`to`（`yyyy-MM-dd`）；默认近 7 日；跨度 ≤31 |
| GET | `/report/hold` | `report:view` | 同上 |

无 POST/PUT/DELETE。  
**不**提供 `/report/overview` 一期（避免与 Dashboard 心智混淆）；前端一页可并行打两个 GET。

---

## 5. 与现有模块对齐

| 模块 | Report 用法 | 不碰 |
|------|-------------|------|
| History / tx_log | TRACK_OUT 按日/按站 COUNT | 不改 Track 写路径；不替代调查台 |
| Hold | hold_time 窗内按原因聚合 | 不在报表 Release |
| Route/Step | 解析 step 显示名 | 不改路线版本 |
| Dashboard | 同源 TRACK_OUT 日计数应对齐；可链入报表 | 不合并 API；「看板」仍属生产执行 |
| SPC/EDC/Alarm | 不进一期报表主表 | — |
| Auth / 菜单 | 种子 `report:view` + 目录「复盘」+ 菜单「报表」 | 对齐 `MES-菜单权限方案.md` |

**菜单树（一期增）**

```
生产执行（既有，不动）
 ├─ 看板 / 批次 / … / 追溯 / …

复盘（新目录，perm_code=NULL 或 review）
 └─ 报表  report:view → /app/report

系统管理（既有，不动）
```

---

## 6. 前端

- 页：`/app/report`（新建）；Admin Light，与现有表格页气质一致
- 入口：侧栏「复盘 / 报表」为主；看板 / 履历文字链为辅
- **禁止** 把报表项挂进生产执行或系统管理
- 筛：日期范围（默认近 7 日）；「查询」按钮（非轮询）
- 区：Move（按日折线或柱 + 按站表）、Hold（原因 Pareto / 表）
- 空态 / partial 提示；链回 History、Hold
- **禁止** 页内假良率、假稼动

---

## 7. 切片（落地顺序）

| 切片 | 交付 | 状态 |
|------|------|------|
| Rep-1 | Facade + `GET /report/move`；按日 TRACK_OUT；`report:view` 权限种子 | ✅ |
| Rep-2 | Move 按站；step 显示名 | ✅ |
| Rep-3 | `GET /report/hold`；按原因；计数 + 时长口径 | ✅ |
| Rep-4 | Admin `/app/report` + **复盘/报表**菜单种子 + 可选看板/履历链入 | ✅ |

建议：1 → 2 → 3 → 4。  
**禁止** Rep-4 先于 Rep-1（再造前端假表）。  
**禁止** 任一切片引入 Yield/OEE 字段。  
**禁止** 菜单挂在「生产执行」下。

明细见 `MES-Report一期功能清单.md`。

---

## 8. 后置（非本期）

| 项 | 说明 |
|----|------|
| P1 Hold 按站 | 从 `tx_type=HOLD` 的 `step_id` 聚合 |
| P1 深链 | 点原因 → Hold 列表带 filter |
| P1 导出 | CSV/Excel；仍只读 |
| P1 班次日历 | 业务清单「班次」；按班而非自然日 |
| P1 简易 CT | 需理论工时；X-factor 后置 |
| P2 预聚合 | 日汇总表 + 定时任务 |
| P2 Yield / Scrap | 独立质量报表或 YMS 对接 |
| P2 真 OEE | Adapter + 停机原因治理 |
| P2 自助 BI | 只读视图 / 数仓；不进 MES 内核定制 |

---

## 9. 验收口径（架构）

- [x] 窗内 TrackOut 次数与 History 按类型筛选可对上（允许按站「未归属」）
- [x] 新 Hold 一条且 `hold_time` 在窗内 → 对应 `reasonCode` 计数 +1
- [x] Report 包无写库业务表；无 Yield/稼动文案
- [x] `report:view` 可查；无权限 403；无权限时侧栏不显示「报表」
- [x] 侧栏可见「复盘 → 报表」；**生产执行**分组内无「报表」
- [x] 与 Dashboard 近 7 日 TrackOut **同口径**（同 tx_type、同时区）时合计一致

---

## 10. 文档入口

| 文档 | 路径 |
|------|------|
| 本设计 | `docs/模块/Report（报表）模块/MES-Report架构设计.md` |
| 一期清单 | `docs/模块/Report（报表）模块/MES-Report一期功能清单.md` |
| 已完成查验 | `docs/模块/Report（报表）模块/MES-Report已完成功能.md` |
| Dashboard（对照） | `docs/模块/Dashboard（看板）模块/MES-Dashboard架构设计.md` |
| History | `docs/模块/History（履历）模块/` |
| 进度 | `docs/架构/MES-实施进度与下一步.md` |
| 业务 | `docs/业务清单/MES-半导体业务清单.md` §12 |
| 总册 | `docs/架构/半导MES架构设计.md` §5.11 |
