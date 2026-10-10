---
type: 方案
module: Test
status: draft
slices: [TD-2]
aligns: [MES-封测测试数据与Bin回流方案.md, MES-业界调研-不良Bin处置与HoldRework联动.md, MES-产品分析-不良Bin处置建议联动.md, INT-0001-封测颗级追溯与测试数据回流.md]
updated: 2026-10-10
---

# MES TD-2 规格 — 不良 Bin 处置建议联动

> 撰写：AI 会话 / 状态：draft（**批准前不动码**；批准后切片 `docs/模块/测试数据（Test）模块/TD-2-plan.md`）
> 定位：把方案 §6 锁定的 TD-2 边界（6 条口径）落成可实施的规格；业界依据见调研文档（J1–J11），产品依据见产品分析（R1–R6 / P0 清单）。
> 一句话：**测试记录落库即判定 → 超 bin 阈值生成建议单（快照留证）→ 事找人（回执/角标/默认待办）→ 质量侧拍板（执行/放行/忽略/转报废）→ 全程留痕可稽核。绝不自动改状态（P7）。**
> 产品红线（第 1 轮审查定版）：**功能上线 ≠ 防线上线。**「谁知道（回执+角标）、谁拍板（角色绑定）、拍的是什么（终态四拆）、能不能拿去审厂（规则审计+快照）」四件事属 P0，不得下放 P1（C15）。

---

## 0. 范围与分期

| 分期 | 交付 | 出处 |
|------|------|------|
| **P0（本规格）** | 阈值规则 CRUD（静态值，四级回退，**默认原因码**，**变更审计**）· 记录落库同步判定 · 建议单主表+明细（快照，**详情带全 bin 对照**）· **终态四拆**（执行 HOLD/REWORK/RETEST/转报废 · 放行 · 忽略）· **提交回执 + 建议角标 + 默认 PENDING 列表** · **动作前置禁用矩阵** · **并发 CAS** · 作废级联（PENDING）与已执行单打标 · 管理端「处置建议」Tab · 权限 4 码 + **角色绑定** | 产品分析 P0 + 审查第 1 轮 C1–C14 |
| P1 | 超期升级提醒 · Alarm 复用 raise · 处置统计视图（放行/忽略率） · 二级限值（level） · 默认阈值模板 | J2/J7/R1 |
| 后置 | SBL 统计限 · 返工次数限额扩展 · Lot Commonality · 重测指定程序重定向 · SOFT bin 规则 | J1/J8/J10 / 调研 §4 |

非目标（重申）：现场台零改动（A5，**处置动作只发生在管理端——这是设计而非漏做**，C14）；scrap 不由系统执行（转报废仅留痕，走既有报废流程，J5）；绝不自动 impound / 自动 scrap（P7）。
**上线条件（C10）**：质量侧已配置**至少一条试点规则**。未配规则 = 系统不会举牌，界面须明示「当前无生效规则」，不得让「没配规则」被当成系统坏了；默认模板后置 P1。

---

## 1. 核心流程

```
测试工程师提交测试记录（POST /test/records，TD-1 既有）
  └─ 同一事务：记录+汇总落库成功后 → 判定（§3）
       ├─ 无命中 → 响应 hit=false，流程结束
       └─ 命中 → 生成建议单（主表 1 张 + 每命中规则 1 条明细，快照）
            └─ 响应 hit=true + adviceId → 前端立即提示「本批命中 N 条阈值规则」可跳详情
                 └─ 质量工程师在「处置建议」Tab（角标 = PENDING 数）拍板：
                      ├─ 执行：HOLD（自动）/ REWORK（既有事务）/ RETEST（留痕）/ 转报废（留痕，走既有流程）
                      ├─ 放行：终态 RELEASED，放行原因必填（业界 use-as-is）
                      ├─ 忽略：仅误判/重复单（终态 IGNORED）
                      └─ 源记录作废 → PENDING 单级联 IGNORED/VOIDED_RECORD（系统动作）
                           已执行单不回滚，保持 CONFIRMED + 打「源记录已作废」标（C6）
```

---

## 2. 库表设计（新增 3 表，脚本 `migrate_test_advice.sql`）

硬约束沿用：id 雪花 19 位；`deleted` TINYINT 软删且 **UK 一律不含 deleted**；不适用维度写**空串**不写 NULL（对齐 `mes_bin_def` 惯例）。

### 2.1 `mes_test_advice_rule`（阈值规则）

| 字段 | 类型 | 说明 |
|------|------|------|
| product_code / program_name / program_version | varchar | 维度组合，四级：`(P,PN,PV)`→`(P,PN,'')`→`(P,'','')`→`('','','')`；不适用填空串 |
| bin_type | varchar(8) | **P0 仅 HARD**（分母 ΣHARD==total_qty 恒定）；SOFT 后置 |
| bin_code | varchar(32) | 规则盯的 bin |
| max_ratio | decimal(7,6) | 占比上限（0~1）。**判定口径 `ratio > max_ratio` 严格大于**——恰好等于上限不举牌，此口径须在规则编辑页文案写明（C9） |
| suggested_action | varchar(16) | SUGGEST_HOLD / SUGGEST_REWORK / SUGGEST_RETEST |
| **default_reason_code** | varchar(32) 可空 | 确认 HOLD 时预填的原因码（取自 Hold 既有原因码字典，P3 不混用；不让用户面对整本字典空选，C11） |
| enabled | tinyint | 启停；停用不删行 |
| remark | varchar(255) | — |
| version | 乐观锁 | @Version + updateById（全库惯例） |
| deleted | tinyint | 软删 |

UK：`(product_code, program_name, program_version, bin_type, bin_code)` —— 同 scope 每 bin 一条，镜像 `mes_bin_def` UK 形状。索引：`(enabled, bin_type)`。

**规则变更审计（C8）**：新增 `mes_test_rule_log`（无软删、只追加）：`rule_id / action(CREATE/UPDATE/DELETE) / before_json / after_json / op_by / op_at`。规则是质量资产——**改宽/改窄必须可回溯**（「2% 是谁改成 20% 的」要能答）；建议单快照只证明「触发当时的限」，证明不了「限后来怎么被改的」，两者缺一不可。

### 2.2 `mes_test_advice`（建议单主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| record_id | bigint **UK** | 1:1 —— 判定与记录创建同事务，一记录至多一张单 |
| lot_id / lot_no | 快照 | — |
| product_code / program_name / program_version | 快照 | 取自记录 |
| total_qty | int | 触发时快照（J4） |
| **version** | 乐观锁 | **并发拍板防线（C13）**：确认/放行/忽略走 `PENDING→终态` 的 CAS 更新，影响行数=0 报「该单已被处理」 |
| status | varchar(16) | PENDING / CONFIRMED / **RELEASED** / IGNORED（终态四拆，C2） |
| action_taken | varchar(16) 可空 | CONFIRMED 时：HOLD / REWORK / RETEST / **TO_SCRAP**（转报废留痕，系统不执行报废，C2） |
| hold_id | bigint 可空 | 确认 HOLD 回写（`HoldService.create` 返回的 MesHoldVO id） |
| exec_note | varchar(255) 可空 | REWORK 执行快照（toSortNo、reworkCount/maxReworkCount）；TO_SCRAP/RETEST 存说明 |
| confirm_remark | varchar(255) 可空 | 确认备注 |
| **release_reason** | varchar(32) 可空 | RELEASED 时必填：ENG_REVIEW_PASS（工程评审通过）/ CUSTOMER_APPROVED（客户同意）/ OTHER（+release_remark 必填）——业界 use-as-is 的落点，**不与忽略混装**（C2） |
| release_remark | varchar(255) 可空 | — |
| ignore_reason | varchar(32) 可空 | **人工可选仅 MISJUDGE（误判）/ DUPLICATE（重复建议）**；VOIDED_RECORD 仅系统级联使用、不出现在人工下拉（C2） |
| ignore_remark | varchar(255) | MISJUDGE 建议必填说明 |
| **record_voided** | tinyint | 源记录作废标记：任何终态均可置 1（已执行事务**不回滚**，详情打标，C6） |
| confirmed_by/at · released_by/at · ignored_by/at | 审计 | — |

索引：`(status)` · `(lot_id)`。

### 2.3 `mes_test_advice_item`（明细，无软删，随主表生命周期）

| 字段 | 说明 |
|------|------|
| advice_id / bin_id | — |
| bin_type / bin_code / **bin_name** | bin_name 为**触发时快照**（防字典改名污染证据链，J4） |
| qty / ratio | 该 bin 数量与 `qty/total_qty`，decimal(7,6) |
| rule_id / rule_scope / **rule_max_ratio** | 规则快照：命中时按哪一级 scope 的哪条规则、上限多少 |
| **is_hit** | 该行是否命中（详情返回**全部 HARD bin 行**，未超限行作对照，拍板不盲选，C3） |

UK：`(advice_id, bin_id)`。

### 2.4 权限与角色绑定（4 码 + 绑定是规格正文，C7）

| perm_code | 授予角色约束（**正文定版，非 plan 探针**） |
|-----------|------------------------------------------|
| `test:advice-view` | 质量、生产/计划、工艺（生产只读——看得到进度，拍不了板） |
| `test:advice-confirm` | **仅质量侧角色**（确认执行/转报废） |
| `test:advice-release` | **仅质量侧角色**（放行，与确认同级敏感） |
| `test:advice-ignore` | **仅质量侧角色**（忽略——比确认更敏感，单列） |
| `test:edit-rule` | **工艺工程师或质量主管**（规则=质量资产） |

注：为守 4 码规模，`advice-release`/`advice-ignore` 从 confirm 中拆出后实际为 **6 码**（view / confirm / release / ignore / edit-rule 共 5 个业务码 + 菜单复用 340）。id 自 **344** 起——plan 阶段探针实测 `sys_permission MAX(id)` 续接；**种子 SQL 按 C7 绑定授角色，缺角色映射即不合入**。

---

## 3. 判定规则

1. **时机**：测试记录创建事务内、汇总行插入完成后同步判定；**判定异常 → 记录创建整体回滚**（拦截完整率 100%；宁可提交失败可重试，不可漏举牌——防流出优先，D4）。判定为纯计算 + 规则表/汇总表只读查询，无外部调用。
2. **匹配**：对记录的每个 HARD 汇总行，按 `product_code, program_name, program_version` 四级回退找 `enabled=1` 的规则（最具体 scope 唯一命中）；无规则 → 无建议（响应 hit=false，**不报错**）。
3. **口径**：`ratio = qty / total_qty`；`ratio > max_ratio` **严格大于**（恰好等于不举牌，C9）。
4. **默认动作 = 明细中严重度最高者**：**HOLD > REWORK > RETEST**（C3——「首条明细」顺序无定义，废除该口径）；确认时可改选，改选留痕。
5. **幂等**：无独立判定 API；判定只挂在记录创建事务内，重入被守卫表 `mes_test_submit_guard` 挡（TD-1 V6）。

---

## 4. 状态机与拍板口径

```
PENDING ──确认执行──> CONFIRMED(action_taken = HOLD/REWORK/RETEST/TO_SCRAP)
PENDING ──放行──────> RELEASED(release_reason 必填)          ← use-as-is 的落点
PENDING ──忽略──────> IGNORED(仅 MISJUDGE/DUPLICATE)
PENDING ──源记录作废─> IGNORED(VOIDED_RECORD，系统动作)
任何终态 + 源记录作废 → record_voided=1 打标，事务不回滚、终态不变（C6）
终态不可逆（改判 = 新事实新记录，不翻旧单）
```

### 4.1 动作前置禁用矩阵（C5：不可执行的动作在确认前禁用并写原因，而非提交后报错）

| 批现状 \ 动作 | HOLD | REWORK | RETEST / 放行 / 转报废 |
|---------------|------|--------|------------------------|
| 正常在制 | 可 | 可（须有 rework 边，无则禁用+提示） | 可 |
| 已有活跃 Hold | **禁用**（提示跳原 Hold 单，可改 RETEST/放行） | 以 Track 侧断言为准（plan 实测 Hold×Rework 互斥） | 可 |
| 已完工 / 已出货 / 已报废 | **禁用** | **禁用** | 可（放行/转报废仍有意义；RETEST 禁用） |

批现状读取来源：lot 主数据当前状态/当前站（TD-1 读 lot 既有先例）；前端按矩阵禁用按钮 + 原因文案。

### 4.2 各动作口径

- **HOLD**：`reason_code` 必填，**默认预填规则的 `default_reason_code`**（C11），从 Hold 既有字典选（P3 不混用）；`hasActive` 检查保留为服务端最后防线（EDC 先例 `MesEdcCollectionServiceImpl.java:351`）；成功回写 `hold_id`。
- **REWORK**：`to_sort_no` + `reason_code` 必填；调 `TrackService.rework(lotId, toSortNo, reasonCode, remark)`（`TrackServiceImpl.java:1152`，既有事务 + rework 边 + maxReworkCount 全复用，D9）；回写 `exec_note`。
- **RETEST（C4，口径写死）**：本刀只证明「**质量决定重测**」，不证明「已经重测」——CONFIRMED(RETEST) **不是**闭环态的伪装。详情页展示同批**后续测试记录**（有则链上可跳，无则明示「重测记录未提交」）；后续记录落库会自动重判、可能产生新建议单（新事实新单）。
- **TO_SCRAP**：`remark` 必填；系统不执行报废，仅留「质量决定转报废」痕，报废走既有流程。
- **放行 RELEASED**：`release_reason` 必填；即业界 use-as-is——调查后判定可接受的正式处置记录，稽核可答。
- **忽略 IGNORED**：仅 MISJUDGE / DUPLICATE；MISJUDGE 须说明（喂给 P1 统计视图调阈值）。
- **并发（C13）**：全部拍板动作走 version CAS；两人同时拍同一单，恰好一人成功，另一人收「该单已被处理」。

---

## 5. 接口设计（前缀 `/test`，响应 R 包装；无 `/api` 前缀）

| # | 方法与路径 | 权限 | 说明 |
|---|-----------|------|------|
| 1 | GET `/test/rules` | `test:advice-view` | 规则分页（product/bin/enabled 过滤） |
| 2 | POST `/test/rules` | `test:edit-rule` | 新建（UK 冲突报业务错）+ 写 rule_log |
| 3 | PUT `/test/rules/{id}` | `test:edit-rule` | 修改（乐观锁）+ 写 rule_log before/after |
| 4 | DELETE `/test/rules/{id}` | `test:edit-rule` | 软删 + 写 rule_log |
| 5 | GET `/test/advice` | `test:advice-view` | 分页；**默认 status=PENDING、按生成时间倒序**（C1）；过滤 status/lot_no/时间段 |
| 6 | GET `/test/advice/{id}` | `test:advice-view` | 详情：明细**含全部 HARD bin 对照**（is_hit）+ lot 当前站/是否已 Hold + PENDING 时长 + RETEST 的后续记录链（C3/C4/C12） |
| 7 | POST `/test/advice/{id}/confirm` | `test:advice-confirm` | `{action: HOLD/REWORK/RETEST/TO_SCRAP, reasonCode?, toSortNo?, remark?}`（按 §4 校验，version CAS） |
| 8 | POST `/test/advice/{id}/release` | `test:advice-release` | `{releaseReason, releaseRemark?}`（C2 拆出） |
| 9 | POST `/test/advice/{id}/ignore` | `test:advice-ignore` | `{ignoreReason: MISJUDGE/DUPLICATE, ignoreRemark?}`（C2 收窄） |
| 10 | GET `/test/advice/pending-count` | `test:advice-view` | 角标数据源（或并入既有 summary 接口，plan 定） |

**提交回执（C1）**：`POST /test/records` 成功响应**扩展** `adviceHit: boolean` + `adviceId: Long?`（向后兼容，TD-1 接口文档同步更新）；前端提交成功后即时提示并可跳建议详情——「提交后立刻知道这批有没有问题」的落点。

无「手动重判」接口（D5：判定只挂记录事务，杜绝第二判定入口）。

---

## 6. 管理端 UI 最小要求（P0，处置时延/悬单率指标的载体）

| # | 要求 |
|---|------|
| U1 | `/app/test` 加「处置建议」Tab：**角标 = PENDING 数**；列表默认 PENDING + 生成时间倒序（C1） |
| U2 | 列表最小列：生成时间、PENDING 时长、批号、产品、命中 bin 摘要、建议动作、批当前站、是否已 Hold、状态（C12——没有这些，时延指标是空的） |
| U3 | 详情：全 bin 对照表（命中高亮/未命中对照）+ 规则快照 + 动作按钮按 §4.1 矩阵**禁用并显示原因**（C5） |
| U4 | 规则编辑页文案写明「**占比 > 上限才举牌，恰好等于不举牌**」（C9） |
| U5 | 无生效规则时 Tab 顶部明示「当前无生效规则，不会举牌」（C10） |
| U6 | **现场台零改动**；处置动作只出现在管理端（A5，C14——这是设计而非漏做） |

---

## 7. 跨模块调用与铁律核对

| 调用 | 方式 | 依据 |
|------|------|------|
| Test → Hold | 注入 `HoldService`（`create` / `hasActive`） | **EDC Auto-Hold 同款先例**（`MesEdcCollectionServiceImpl.java:92,350-358`）；如审查认为应升 HoldFacade，plan 阶段加薄封装即可 |
| Test → Track | 注入 `TrackService.rework` | 方案 D9；权限闸在 Controller 层由 `test:advice-confirm` 承担，服务层断言原样生效 |
| Test → Lot | 读 lot 当前状态/当前站 | TD-1 读 lot 既有先例（§4.1 矩阵数据源） |
| Track/Hold → Test | **零反向依赖**；TestFacade 维持只读不变 | TD-1 工程约束延续 |

**tx 留痕口径**：HOLD 记 `hold_id`（Hold 单据本身已有 tx_log）；REWORK 的 `TrackTxnResultVO` 当前**不暴露 txId**（实测 `TrackTxnResultVO.java:8-25`），回写执行快照替代，plan 阶段评估是否给 VO 补 txId（Track 侧改动，非本切片必须）。

---

## 8. 决策表（D）

| # | 决策 | 理由 |
|---|------|------|
| D1 | 静态阈值，SBL 后置 | 数据不足（J1）；规则表留 level 列余地 |
| D2 | 规则四级回退、空串不 NULL | 对齐 `mes_bin_def`（回流方案 D14）惯例 |
| D3 | P0 仅 HARD bin 规则 | ΣHARD==total_qty 对账恒真，分母稳 |
| D4 | 判定同事务、异常即回滚 | 拦截完整率 100%；提交失败可重试 < 漏举牌不可逆 |
| D5 | 无手动重判入口 | 唯一触发点=记录创建事务 |
| D6 | 建议单 1:1 记录 + 明细行；**默认动作=明细严重度最高（HOLD>REWORK>RETEST）** | 整单一次拍板；废除「首条明细」（C3） |
| D7 | 终态不可逆 | 留痕严肃性；改判走新事实 |
| D8 | 建议动作默认可改选 | 规则建议是提示不是判决 |
| D9 | Rework/Hold 复用既有事务，不新造 | 方案 §6 第 2 条；EDC/Track 实测先例 |
| D10 | **终态四拆：执行/放行/忽略/转报废** | 忽略混装放行会毁掉误报统计与稽核口径（C2） |
| D11 | **角色绑定入规格正文**：拍板类仅质量侧，生产只读，规则维护=工艺/质量主管 | R3 定版；防线不能被生产侧放行掉（C7） |
| D12 | **规则变更审计表 rule_log（before/after 只追加）** | 阈值是质量资产，改宽可回溯；快照证明不了「限被怎么改」（C8） |
| D13 | **RETEST 口径=决定留痕 + 后续记录链**，不伪装闭环 | 稽核问「重测了吗」要能答（C4） |
| D14 | **拍板走 version CAS** | 并发双拍防线（C13） |

## 禁止表（P）

| # | 禁止 |
|---|------|
| P1 | 自动 Hold / 自动 Rework / 自动 scrap / 超期默认报废（P7；超期只提醒） |
| P2 | 现场台任何改动（A5） |
| P3 | Bin 与 Hold 原因码互相复用（P3） |
| P4 | 建议明细直接引用字典当前值（必须快照） |
| P5 | Track/Hold 反向依赖 Test |
| P6 | **生产/计划角色获得 confirm/release/ignore 权限**（C7） |
| P7 | 规则修改不写 rule_log（C8） |
| P8 | **把 C1/C2/C3/C4/C7/C8/C13 对应项下放 P1**（第 1 轮审查红线 C15） |

## 验收口径（可判真假）

1. 配规则（HARD bin3 上限 2%）：提交 bin3 占比 4.2% 记录 → 建议单 PENDING、明细含快照；**提交占比恰好 2.000000% → 不生成单**（严格大于，C9）；响应 `adviceHit=true` + adviceId。
2. 角标 = PENDING 数；列表默认 PENDING 倒序；无生效规则时明示「不会举牌」（C1/C10）。
3. 确认 HOLD → `mes_hold` 新增 active 行、原因码取自既有字典且预填生效、建议单 CONFIRMED 且 hold_id 回写；批已 Hold 时 **HOLD 按钮禁用+原因**，可改 RETEST/放行。
4. 确认 REWORK → `mes_tx_log` 出现 REWORK 事务，reworkCount 快照回写。
5. 放行 → RELEASED 且 release_reason 必填生效；忽略仅 MISJUDGE/DUPLICATE 可选，HIST_RELEASE 不再出现在忽略里（C2）。
6. **同一单两人并发确认 → 恰好一人成功，另一人收「该单已被处理」**（C13）。
7. 规则 2%→20% 修改 → `mes_test_rule_log` 有 before/after，操作人可查（C8）。
8. 作废源记录：PENDING 单自动 IGNORED/VOIDED_RECORD；**已确认 HOLD 的单保持 CONFIRMED、事务不回滚、详情打「源记录已作废」标**（C6）。
9. RETEST 详情：无后续记录明示「重测记录未提交」；补提交后新记录出现在链上且自动重判（C4）。
10. 角色验证：生产角色对 confirm/release/ignore 均 403（C7）；无权限账号 403；现场台代码零 diff。
11. 全链权限与回滚：模拟判定异常 → 记录创建整体回滚（D4）。

## 待 plan 阶段核实（不阻塞规格批准）

- `sys_permission` MAX(id) 实测续接（探针）；现有角色清单与 C7 的映射落点
- `HoldService.create` 返回 `MesHoldVO.id` 确认；Hold 原因码字典当前可用值（default_reason_code 下拉源）
- Hold×Rework 互斥行为（Track 侧断言实测，§4.1 矩阵落定）
- `TrackTxnResultVO` 是否补 txId；`/app/test` Tab 挂载点与角标数据源（独立接口 vs summary 合并）

---

## 审查记录（第 1 轮 · 2026-10-10 · 产品视角只审不改已批内容）

> 审查人：用户。结论：14 项发现**全部成立**，正文已按 C1–C14 修订；规格仍 draft，待批准。

| # | 事实修正（F） | 绑定约束（C） |
|---|---------------|---------------|
| F1 | 原稿无提交回执/角标/默认排序——上线后是「多一个要人去翻的 Tab」 | C1：响应带 adviceHit/adviceId；角标=PENDING 数；列表默认 PENDING 倒序（§5/§6 U1） |
| F2 | 「忽略」混装误判/重复/放行/报废去向，毁掉误报统计与稽核口径 | C2：终态四拆——执行(HOLD/REWORK/RETEST/TO_SCRAP)、放行(原因必填)、忽略(仅 MISJUDGE/DUPLICATE)；转报废记 TO_SCRAP 走既有流程（§2.2/§4） |
| F3 | 多 bin 命中默认「首条明细」顺序无定义，可能默认成 RETEST；详情缺未超限对照 | C3：默认动作=严重度 HOLD>REWORK>RETEST；详情带全 bin 对照 is_hit（§3.4/§2.3） |
| F4 | RETEST 有拍板无结果，CONFIRMED 假装闭环 | C4：口径写死「只证决定重测」；详情链同批后续记录、无则明示（§4.2） |
| F5 | 冲突处理只有「已 Hold 报错」，缺完工/出货/报废与界面禁用 | C5：§4.1 动作前置禁用矩阵 |
| F6 | 作废只处理 PENDING，已执行单去向未定义 | C6：已执行事务不回滚，单保持 CONFIRMED + record_voided 打标（§2.2/§4） |
| F7 | R3 角色映射悬空，测试/生产若能忽略则防线被放行 | C7：拍板类仅质量侧、生产只读、规则维护=工艺/质量主管，入规格正文（§2.4/P6） |
| F8 | 规则原地改无 before/after，「2% 改成 20%」无从回溯 | C8：新增 rule_log 只追加审计表（§2.1/P7） |
| F9 | 「> 不是 ≥」未向用户表达 | C9：规则页文案 + 验收含 2.000000% 不举牌（§3.3/§6 U4） |
| F10 | 冷启动无上线条件，「没配规则」会被当系统坏了 | C10：上线条件=质量已配 ≥1 条试点规则；无规则界面明示（§0/§6 U5） |
| F11 | HOLD 原因丢给整本字典空选 | C11：规则挂 default_reason_code 预填（§2.1/§4.2） |
| F12 | 列表/详情缺最小上下文，时延/悬单率指标无载体 | C12：U2 最小列清单（§6） |
| F13 | 并发确认未防，两人可各拍一次 | C13：主表 version CAS（§2.2/§4.2/D14） |
| F14 | 现场台定位未声明，可能被当成漏做 | C14：处置只发生管理端，写进非目标与 U6（§0/§6） |
| — | 分期红线 | C15：超期/Alarm/统计/SBL/二级限/共案/重测定向保持 P1/后置、不算本刀失败；但 C1/C2/C3/C4/C7/C8/C13 不得下放 P1（P8） |

## 关联

- 边界：`MES-封测测试数据与Bin回流方案.md` §6（A1/P7/A5/P3/D9 等沿用）· `TD-1-plan.md`
- 依据：`MES-业界调研-不良Bin处置与HoldRework联动.md`（J1–J11）· `MES-产品分析-不良Bin处置建议联动.md`（P0/R1–R6/指标）
- 代码实测：`MesEdcCollectionServiceImpl.java:92,350-358` · `HoldService.java:46` · `MesHoldCreateDTO.java:12-19` · `TrackServiceImpl.java:1147-1218` · `TrackTxnResultVO.java:8-25` · `MES-Test数据库设计.md`
