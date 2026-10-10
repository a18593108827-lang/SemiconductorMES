---
type: 方案
module: 
status: draft
slices: [TD-2]
aligns: [MES-封测测试数据与Bin回流方案.md, INT-0001-封测颗级追溯与测试数据回流.md, BK-0001-测试与Bin分档.md]
updated: 2026-10-10
---

# MES 业界调研 — 测试不良 Bin 处置与 Hold/Rework 联动（TD-2 前置）

> 调研：AI 会话 / 状态：draft（TD-2 规格的前置输入，不出实现决策）
> 用途：TD-2（不良 Bin → Hold/Rework **建议**）动手出规格前，先看半导体大厂 / YMS 厂商 / MES 厂商在这块的标准做法，校准方案 §6 已锁的边界。
> 结论一句话：**业界主流 = 「统计限/阈值触发 → 批级 Hold → 工程调查 → 人工处置（release / retest / scrap）→ 全程留痕」四段式，且普遍坚持"系统只拦不改、处置必须人工"** —— 与本项目 A1/P7（只出建议、绝不自动改状态）完全同向。

---

## 0. 调研问题

1. 不良 Bin 超限后，大厂的 MES / YMS 是怎么判定的（阈值怎么来）？
2. 触发后是自动 Hold 还是只提示？
3. 处置动作有哪几种、由谁拍板、怎么留痕？
4. 有哪些「我们不该抄」的越界能力（YMS / 颗级范畴）？

---

## 1. 业界通用模型：四段式

各家实现形态不同（YMS 外挂 / MES 内建 / QMS 联动），但结构高度一致：

| 段 | 业界叫法 | 做什么 | 本项目对应 |
|----|----------|--------|-----------|
| ① 检测 | Bin Limits / Statistical Bin Limits (SBL) / Statistical Yield Limits (SYL) | 按 bin 维度判定批级不良率是否超限 | TD-2 阈值规则 |
| ② 拦截 | Lot on Hold / Exception / NCR | 批停住，进入待调查状态 | 既有 Hold（TD-2 只出建议，人工确认后走 `HoldService.create`） |
| ③ 调查 | Engineering Review / Lot Commonality | 找根因；查同类批是否共案 | 建议单 + 既有履历/客诉包 |
| ④ 处置 | Lot Disposition / MRB | release / retest(rework) / use-as-is / scrap，人工拍板+签字留痕 | 既有事务（rework 边 + reason_codes）+ `mes_test_advice` 留痕 |

---

## 2. 关键实践（按来源）

### 2.1 车规零缺陷：SBL / SYL 统计限（AEC-Q100 惯例，ISSI 公开口径）

对功率器件 / SiC 车规场景**参考价值最高**：

- **限值怎么来**：不是拍脑袋静态值，而是收集 ≥6 批生产数据，按「每批 bin-out 占比」算 Mean±3σ（一级）/ Mean±4σ（二级）；早期数据不足时用 characterization 批替代。
- **限值保鲜**：前 6 个月**每 30 天**复核更新（至少用最近 8 批）；6 个月后每季度更新。过期数据不得使用。
- **两级响应**：
  - 超 **一级限**（3σ）→ 批 **Hold for engineering review**，须留存根因与纠正措施记录；
  - 超 **二级限**（4σ）→ 批可 **impound（扣留）且须客户通知后才能放行**。
- 对应 EE Times 车规最佳实践综述：SYL/SBL 是 outlier control 的批级手段，与颗级的 PAT / DPO / lonely-die 属两个层级。

### 2.2 三星专利 US6055463：bin 限值自动生成 + 批判定序列

- 主机**自动周期性**按历史数据生成各 bin category 限值（按失效比例分档定限，如 0.3%–1.8% 分五档参考值）。
- FT 完成后跑**批判定序列**：yield 达标？>100%（数据异常）？任一 bin 超限？→ 结果分流 go / no-go 库；no-go 批转 incoming inspection 或 retest（必要时双倍抽样）。
- 要点：**判定基于每一个 bin，不只看总 yield**——early detection 靠 bin 结构性异常，与本项目「按 bin 配阈值」同构。

### 2.3 YMS 厂商（yieldHUB / yieldWerx）——这条赛道就是「检测+建议」的外挂形态

- **yieldHUB**：
  - SBL 交互式生成、配置可保存复用（从生产数据算，不是手填）；
  - **Lots-on-Hold dashboard**：因 yield/异常 bin 触发 hold 的批集中挂板，跨工程团队追踪调查状态，加速 disposition——「建议单列表」的业界原型；
  - 实时告警：**批还在 handler 上**就出 yield/bin/漂移告警（不是测完第二天）。
  - 博客《Are lots on hold holding you back?》：Subcon 现实痛点——hold 批平均滞留数天、见过一周+，瓶颈不在判定而在**工程师异地沟通调查**。→ 提示：建议单要自带上下文（批、bin、占比、规则快照），减少来回问。
- **yieldWerx**（Real-Time Lot Control & Disposition 模块）：
  - 规则触发 alert → **hold 批 或 重定向到指定 test 程序**（重测建议也是处置建议的一种）；
  - disposition 四选项：**scrap / retest / downgrade / release**，每个决定**强制附文档**；
  - **Lot Commonality 报告**：查共同制造历史的兄弟批，辅助处置扩散判断。

### 2.4 MES 厂商内建（Critical Manufacturing MES）

- **Exception Management**：SPC 违例/采数超限可**自动开异常工作流**；异常单可人工随时开；支持**逐批处置（scrap / rework）**、任务项跟踪至闭环。
- **NCR & Dispositions**：不合格记录 → 处置决定（含按批逐一拍板）→ 留痕。
- **Rework Limits（11.2 内建）**：返工次数限额，三维度可组合——按产品全局限 / 按工序限 / 按返工原因限（Smart Table 配置）；超限则不许再返工，转 Hold 或开 NCR。计数器可复位。
- 要点：**「建议/异常单」在 MES 内是独立实体，有自己的工作流与状态**，处置执行后回写留痕。

### 2.5 QMS/质量口径：MRB（Material Review Board）

- ISO 9001 / IATF 16949 要求的正式跨职能评审：NCR → 隔离(containment) → 影响分析 → **处置（use-as-is / rework / RTV / scrap）** → 必要时客户通知 → CAPA。
- MRB 决定**绑定生效**，只有 MRB 能放行超差物料；决定含条件（如放行须过 168h burn-in）须在 MES 里跟踪完成。
- 实操纪律（EMS Handbook）：**MRB 滞留 30 天未决 → 系统默认按 scrap 处理**；考核指标 Average Days in MRB < 7。→ 提示：建议单应有**超期升级/老化**机制，不能无限期挂起。

### 2.6 国内专利 CN117558651A（MES 分 Bin 控制与良率管理）

- 加工前 IT 配置「量测项超上下限 → 不良原因 → 对应料仓」映射链；
- 不良率超限由 **SPC 判定 → 整批 Hold** 防流转；混档批自动分批+Hold；
- 不良品推 **QMS 评审**，评审结果分「报废 / 部分返工 / 整体返工」；MES 记录复测结果，旧档位**备份进历史表**供溯源。
- 要点：处置联动走 **QMS/评审单**而非 MES 直接改状态，与本项目「建议单 + 人工确认」同构。

---

## 3. 对本项目 TD-2 的启示（映射，非决策）

| # | 业界做法 | 对 TD-2 的启示 | 强度 |
|---|----------|----------------|------|
| J1 | 阈值 = 静态配置 + 统计限（SBL）双轨；SBL 需 ≥6 批历史 | TD-2 首版做**静态阈值**（产品/程序版本/bin 维度，可空回退对齐 D14）；SBL 自动计算明确后置——TD-1 刚落地数据不足，现在上 SBL 是空中楼阁 | 强 |
| J2 | 两级限（3σ 审查 / 4σ 扣留+客户通知） | 可借鉴**限值分级**：一级=建议 Hold/Rework；二级=建议 + 界面强提示（车规产品线将来可挂客户通知，本期不做） | 中 |
| J3 | 判定逐 bin，不只看总 yield | 方案 §6 口径已含（按 bin 占比）；确认实现时**每个超限 bin 一条建议明细**，不只一条汇总 | 强 |
| J4 | 建议单自带完整上下文（批/bin/占比/规则快照） | `mes_test_advice` 落库时**快照触发时的 bin 数与规则**，避免字典后续改动污染证据链（与 D7 双轨精神一致） | 强 |
| J5 | 处置选项业界口径：release / retest / downgrade / scrap | TD-2 建议类型对齐为：**建议 Hold / 建议 Rework / 建议重测(retest)**；scrap 涉及资产处置，建议**不进 TD-2**（留人工既有流程） | 强 |
| J6 | 处置强制附文档/原因；忽略也留痕 | 方案 §6 第 5 条（忽略记原因）与业界一致，规格化时按**必填原因 + 枚举**设计 | 强 |
| J7 | 异常单有独立状态机与超期升级（MRB 30 天默认 scrap / <7 天考核） | 建议单需要**状态机（待确认/已确认/已忽略/已过期）+ 超期提醒**；「默认 scrap」这类激进行为不做，超期只升级/提醒 | 中 |
| J8 | Rework Limits（产品/工序/原因三维度返工限额） | 业界 out-of-box 能力；本项目 route_edge rework 已就绪，**返工次数限额后置**（可记入 TD-2 非目标） | 中 |
| J9 | Lots-on-Hold dashboard 集中追踪 | 建议单列表页即轻量版（管理端已有 Test 页可加 Tab），不必单开现场台入口（A5 零改动不变） | 强 |
| J10 | Lot Commonality 兄弟批共案排查 | 同产品/同程序近期同 bin 超限的**提示**可做可不做；建议 TD-2 记为非目标，先留数据（advice 落库即可支持将来分析） | 弱 |
| J11 | 触发时机：批还在 handler 上就告警 | 本项目测试记录走管理端手工/API 提交（TD-3 才有 STDF 解析），TD-2 触发点=**测试记录落库成功后同步判定**，天然接近实时 | 信息 |

## 4. 不该抄的（越界清单，与方案 P 表对齐）

| 业界能力 | 为何不进 TD-2 |
|----------|---------------|
| SBL/SYL 统计限自动计算与季度滚动 | 数据不足（TD-1 刚落地）；属 YMS 分析范畴，且 J1 已定后置 |
| 颗级 outlier（PAT / DPO / lonely-die / Wafer Map） | 颗级 Die 是 TD-3；图形化是 YMS 范畴（P5） |
| 自动 impound / 自动 scrap（超期默认报废） | 违反 P7（绝不自动改状态）；资产处置有既有线下流程 |
| 自动开 CAPA / QMS 深度工作流 | 本项目无 QMS 模块；Alarm 复用（方案 §6 第 4 条）已覆盖可见性 |
| 重测重定向到指定 test 程序 | 涉及 Dispatch/Recipe 联动，超出「建议+人工确认」最小集 |

---

## 5. 信息来源

| 来源 | 内容 | 链接 |
|------|------|------|
| ISSI《Zero Defect》程序文件 | SBL/SYL 定义、Mean±3σ/4σ、限值更新节奏、两级响应 | issi.com/WW/pdf/zerodefect.pdf |
| EE Times 车规可靠性最佳实践 | SBL/SYL/PAT/DPO outlier 控制层级、hold→scrap 处置例 | eetimes.com |
| 三星专利 US6055463 | bin category 限值自动生成 + 批判定序列（go/no-go 库） | freepatentsonline.net/6223098.html |
| yieldHUB 产品页 + 博客 | SBL 交互生成、Lots-on-Hold dashboard、Subcon hold 滞留痛点 | yieldhub.com |
| yieldWerx 产品页 + 指南 | Real-Time Lot Control & Disposition：alert→hold/重测、四处置选项、Lot Commonality | yieldwerx.com |
| Critical Manufacturing MES 文档/博客 | Exception Management、NCR & Dispositions、Rework Limits（11.2） | criticalmanufacturing.com / devblog / help |
| ChipFoundry / EMS Handbook / Connect981 MRB 词条 | MRB 流程、处置矩阵、30 天超期默认 scrap、 Average Days in MRB | chipfoundryservices.com 等 |
| 中国专利 CN117558651A | MES 分 Bin 控制：SPC 超率整批 Hold → QMS 评审 → 返工/报废、档位历史备份 | patents.google.com/patent/CN117558651A/zh |
| SG Systems Global 异常处理工作流词条 | auto-hold 触发逻辑、处置模式 A–D（纠正/替代/返工/报废） | sgsystemsglobal.com |

---

## 关联

- 方案：`MES-封测测试数据与Bin回流方案.md`（§6 已锁 TD-2 边界；本调研不推翻任何 A/P/D 条目）
- 意图：`docs/intent/INT-0001-封测颗级追溯与测试数据回流.md`（验收 3）
- 业务知识：`docs/业务知识/BK-0001-测试与Bin分档.md` · 待产：MRB / Lot Disposition 概念可考虑沉淀为 BK
