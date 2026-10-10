---
type: 方案
module: Test
status: approved
slices: [TD-2]
aligns: [MES-封测测试数据与Bin回流方案.md, MES-业界调研-不良Bin处置与HoldRework联动.md, MES-产品分析-不良Bin处置建议联动.md, INT-0001-封测颗级追溯与测试数据回流.md]
updated: 2026-10-10
---

# MES TD-2 规格 — 不良 Bin 处置建议联动

> 撰写：AI 会话 / 状态：**approved（2026-10-10 用户批准，经 3 轮审查 C1–C27）**；下一步切片 `docs/模块/测试数据（Test）模块/TD-2-plan.md`（plan approved 前仍不动码）
> 定位：把方案 §6 锁定的 TD-2 边界（6 条口径）落成可实施的规格；业界依据见调研文档（J1–J11），产品依据见产品分析（R1–R6 / P0 清单）。
> 一句话：**测试记录落库即判定 → 超 bin 阈值生成建议单（快照留证）→ 事找人（回执/角标/默认待办）→ 质量侧拍板（执行/放行/忽略/转报废）→ 全程留痕可稽核。绝不自动改状态（P7）。**
> 产品红线（第 1 轮审查定版）：**功能上线 ≠ 防线上线。**「谁知道（回执+角标）、谁拍板（角色绑定）、拍的是什么（终态四拆）、能不能拿去审厂（规则审计+快照）」四件事属 P0，不得下放 P1（C15）。

---

## 0. 范围与分期

| 分期 | 交付 | 出处 |
|------|------|------|
| **P0（本规格）** | 阈值规则 CRUD（静态值，四级回退，**默认原因码**，**变更审计**）· 记录落库同步判定 · 建议单主表+明细（**全部 HARD 行快照**）· **终态四拆**（执行 HOLD/REWORK/RETEST/转报废 · 放行 · 忽略）· **提交回执 + 建议角标 + 默认 PENDING 列表** · **动作前置禁用矩阵** · **并发 CAS（body 带 version）** · 作废级联（PENDING，record_voided 同步置 1）与已执行单打标 · 管理端「处置建议」Tab · 权限 5 业务码 + **角色绑定** | 产品分析 P0 + 审查第 1/2 轮 C1–C23 |
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
       └─ 命中 → 生成建议单（主表 1 张 + 明细=该记录**全部 HARD 汇总行**快照，is_hit=0/1 标记命中，C16）
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
| ignore_remark | varchar(255) | MISJUDGE 时**必填**说明（C21/C24） |
| **record_voided** | tinyint | 源记录作废标记：任何终态均可置 1（已执行事务**不回滚**，详情打标，C6） |
| confirmed_by/at · released_by/at · ignored_by/at | 审计 | — |

索引：`(status)` · `(lot_id)`。

### 2.3 `mes_test_advice_item`（明细，无软删，随主表生命周期）

> **明细口径定版（C16，第 2 轮）**：触发生成时**快照该记录全部 HARD 汇总行**（不只命中行），`is_hit` 标记命中与否——证据链自洽：拍板时看到的对照就是触发那一刻的完整事实，不依赖「详情时从记录汇总现拼」（记录可能已被作废/字典可能已改）。

| 字段 | 说明 |
|------|------|
| advice_id / bin_id | — |
| bin_type / bin_code / **bin_name** | bin_name 为**触发时快照**（防字典改名污染证据链，J4） |
| qty / ratio | 该 bin 数量与 `qty/total_qty`，decimal(7,6) |
| rule_id / rule_scope / **rule_max_ratio** | 规则快照：命中时按哪一级 scope 的哪条规则、上限多少（未命中行置空） |
| **is_hit** | 0/1：该行是否命中（明细=全部 HARD 行，未命中行作对照，拍板不盲选，C3） |

UK：`(advice_id, bin_id)`。行数 = 该记录 HARD 汇总行数（验收项）。

### 2.4 权限与角色绑定（5 个业务码 + 绑定是规格正文，C7）

> 码数统一口径（C22，第 2 轮）：**业务码 5 个**——`test:advice-view / advice-confirm / advice-release / advice-ignore / edit-rule`；菜单权限**复用既有 `test:view`(340)**，不新增菜单码。id 自 **344** 起——plan 阶段探针实测 `sys_permission MAX(id)` 续接；**种子 SQL 按 C7 绑定授角色，缺角色映射即不合入**。

| perm_code | 授予角色约束（**正文定版，非 plan 探针**） |
|-----------|------------------------------------------|
| `test:advice-view` | 质量、生产/计划、工艺、**测试工程师**（提交回执跳详情要用的只读权——回执能到、详情 403 就是断头路，C20；生产只读——看得到进度，拍不了板） |
| `test:advice-confirm` | **仅质量侧角色**（确认执行/转报废） |
| `test:advice-release` | **仅质量侧角色**（放行，与确认同级敏感） |
| `test:advice-ignore` | **仅质量侧角色**（忽略——比确认更敏感，单列） |
| `test:edit-rule` | **工艺工程师或质量主管**（规则=质量资产） |

---

## 3. 判定规则

1. **时机**：测试记录创建事务内、汇总行插入完成后同步判定；**判定异常 → 记录创建整体回滚**（拦截完整率 100%；宁可提交失败可重试，不可漏举牌——防流出优先，D4）。判定为纯计算 + 规则表/汇总表只读查询，无外部调用。
2. **匹配**：对记录的每个 HARD 汇总行，按 `product_code, program_name, program_version` 四级回退找 `enabled=1` 的规则（最具体 scope 唯一命中）；无规则 → 无建议（响应 hit=false，**不报错**）。
3. **口径**：`ratio = qty / total_qty`；`ratio > max_ratio` **严格大于**（恰好等于不举牌，C9）。
4. **默认动作 = 明细中严重度最高者**：**HOLD > REWORK > RETEST**（C3——「首条明细」顺序无定义，废除该口径）；确认时可改选，改选留痕。
   **默认原因码预填（C18，第 2 轮）**：取「默认动作对应明细」中 **ratio 最高那条**规则的 `default_reason_code`；**ratio 并列时取 bin_code 字典序最小**（C26，第 3 轮）；对应明细全空则不预填（用户面对字典自选）。
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

### 4.1 动作前置禁用矩阵（C5；C28/C29 按 plan 实测定版，第 4 轮审查回填正文）

现网 lot status 枚举（实测，`TrackServiceImpl.java:89-93` + DB DISTINCT 一致）：`created / wait / processing / held / completed / scrapped / merged`——**无 `shipped`**，初稿「已出货」用词作废（C29）；merged 为合批终态（`:93` 不可再 Track），按完工侧禁用。

| 批 status | HOLD | REWORK | RETEST / 放行 / 转报废 |
|-----------|------|--------|------------------------|
| created | **禁用**（`HoldServiceImpl.create:176` 仅 wait/processing 可锁批） | **禁用**（`rework:1155` 同口径） | 放行/转报废可；RETEST 禁（批未进站，C31） |
| wait / processing | 可 | 可（须有 rework 边；**Off-Flow 中禁**，`:1157`） | 可 |
| held | **禁用**（提示跳原 Hold 单，可改 RETEST/放行） | **禁用**（`rework` 首步 `assertNoActive` 必拒，`TrackServiceImpl.java:1154`） | 可 |
| completed / scrapped / merged | **禁用** | **禁用** | 可（放行/转报废仍有意义；RETEST 禁用） |

- **服务端与前端双重执行**：confirm/release/ignore 服务端按同一矩阵校验拒绝（**不能只靠前端按钮灰掉**）；详情返回 `actionAvailability` 供前端渲染，rework 边判定复用 `TrackService.context(lotId)` 的 `reworkOptions`——**忽略 `canRework` 字段**：它内含 `StpUtil.hasPermission("track:rework")` 权限位（`:1620`），quality 角色无此权限会把 REWORK 永远灰掉而服务端又能跑通（F32/C32）；权限闸只认 `test:advice-confirm`（D9）。

### 4.2 各动作口径

- **HOLD**：`reason_code` 必填，**预填口径见 §3.4（C18）**，从 Hold 既有字典选（P3 不混用）；`hasActive` 检查保留为服务端最后防线（EDC 先例 `MesEdcCollectionServiceImpl.java:351`）；成功回写 `hold_id`。
- **REWORK**：`to_sort_no` + `reason_code` 必填；调 `TrackService.rework(lotId, toSortNo, reasonCode, remark)`（`TrackServiceImpl.java:1152`，既有事务 + rework 边 + maxReworkCount 全复用，D9）；回写 `exec_note`。
- **RETEST（C4，口径写死）**：本刀只证明「**质量决定重测**」，不证明「已经重测」——CONFIRMED(RETEST) **不是**闭环态的伪装。详情页展示同批**后续测试记录**（有则链上可跳，无则明示「重测记录未提交」）；后续记录落库会自动重判、可能产生新建议单（新事实新单）。
- **TO_SCRAP**：`remark` 必填；系统不执行报废，仅留「质量决定转报废」痕，报废走既有流程。
- **放行 RELEASED**：`release_reason` 必填；即业界 use-as-is——调查后判定可接受的正式处置记录，稽核可答。**只关闭建议单，不调用 Hold release、不解批**——已有活跃 Hold 的批点放行后仍是 Hold 态，解 Hold 走既有 Hold 流程（C19，§4.1 矩阵同款提示）。
- **忽略 IGNORED**：仅 MISJUDGE / DUPLICATE；MISJUDGE 说明**必填**（C21——放行 OTHER、忽略纪律同级，P1 调阈值看板的数据源）。
- **并发（C13）**：全部拍板动作走 version CAS；**请求体必带 `version`**（C17），version 过期 → 收「该单已被处理」；两人同时拍同一单恰好一人成功。
- **作废级联（C23）**：记录作废 → PENDING 建议单置 `IGNORED / VOIDED_RECORD` **同时 `record_voided=1`**（与「任何终态可打标」同一口径，列表不出现两套标记），操作人记 system。

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
| 7 | POST `/test/advice/{id}/confirm` | `test:advice-confirm` | `{version, action: HOLD/REWORK/RETEST/TO_SCRAP, reasonCode?, toSortNo?, remark?}`（**version 必填**，CAS；按 §4 校验） |
| 8 | POST `/test/advice/{id}/release` | `test:advice-release` | `{version, releaseReason, releaseRemark?}`（C2 拆出，C17） |
| 9 | POST `/test/advice/{id}/ignore` | `test:advice-ignore` | `{version, ignoreReason: MISJUDGE/DUPLICATE, ignoreRemark?}`（C2 收窄，C17；MISJUDGE 时 remark 必填 C21） |
| 10 | GET `/test/advice/pending-count` | `test:advice-view` | 角标数据源（或并入既有 summary 接口，plan 定） |

**提交回执（C1）**：`POST /test/records` 成功响应**扩展** `adviceHit: boolean` + `adviceId: Long?`（向后兼容，TD-1 接口文档同步更新）；前端提交成功后即时提示并可跳建议详情——「提交后立刻知道这批有没有问题」的落点。

无「手动重判」接口（D5：判定只挂记录事务，杜绝第二判定入口）。

---

## 6. 管理端 UI 最小要求（P0，处置时延/悬单率指标的载体）

| # | 要求 |
|---|------|
| U1 | `/app/test` 加「处置建议」Tab：**角标 = PENDING 数**；列表默认 PENDING + 生成时间倒序（C1） |
| U2 | 列表最小列：生成时间、PENDING 时长、批号、产品、命中 bin 摘要、建议动作、批当前站、是否已 Hold、状态（C12——没有这些，时延指标是空的） |
| U3 | 详情：全 bin 对照表（命中高亮/未命中对照）+ 规则快照 + 动作按钮按 §4.1 矩阵**禁用并显示原因**（C5）；**放行按钮旁明示「只关闭建议单，不解 Hold」**（C19 的 UI 落点，C25） |
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
5. 放行 → RELEASED 且 release_reason 必填生效；忽略仅 MISJUDGE/DUPLICATE 可选，HIST_RELEASE 不再出现在忽略里（C2）；**MISJUDGE 无 remark → 拒绝**（C27）。
6. **同一单两人并发确认 → 恰好一人成功，另一人收「该单已被处理」**（C13）。
7. 规则 2%→20% 修改 → `mes_test_rule_log` 有 before/after，操作人可查（C8）。
8. 作废源记录：PENDING 单自动 IGNORED/VOIDED_RECORD；**已确认 HOLD 的单保持 CONFIRMED、事务不回滚、详情打「源记录已作废」标**（C6）。
9. RETEST 详情：无后续记录明示「重测记录未提交」；补提交后新记录出现在链上且自动重判（C4）。
10. 角色验证：生产角色对 confirm/release/ignore 均 403；**测试角色 advice-view 可读、拍板 403**（C20）；无权限账号 403；现场台代码零 diff。
11. 全链权限与回滚：模拟判定异常 → 记录创建整体回滚（D4）。
12. **明细行数 = 该记录 HARD 汇总行数（含未命中行）**；拍板请求缺 version 或 version 过期 → 拒绝/「该单已被处理」（C16/C17）。
13. 多规则命中预填：默认动作对应明细中 ratio 最高者的 `default_reason_code` 生效，全空不预填（C18）。
14. 已活跃 Hold 的批点放行 → 建议单 RELEASED 但**批仍 Hold**（不解批）；作废级联后 `record_voided=1`（C19/C23）。

## 待核实 → 已全部收口（TD-2-plan §0 实测，2026-10-10）

- `sys_permission` MAX(id)=343 → **344–348**；`MesHoldVO.id` 存在；`mes_hold_reason` 11 码 + plan 新增 `8012 TEST_BIN_EXCEED`；Hold×Rework 互斥落定（F27/C28，矩阵已回填 §4.1）；角色映射落盘 plan（现网 4 角色实测，新增 quality 种子）；状态枚举校正（无 shipped，F29/C29）。
- **TrackTxnResultVO 补 txId：本刀不做**（F30/C30）——exec_note 执行快照已满足留痕与稽核；txId 后置评估。

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

### 审查记录（第 2 轮 · 2026-10-10 · 实施卡点与口径收尾）

> 审查人：用户。结论：8 项发现**全部成立**，正文已按 C16–C23 修订。

| # | 事实修正（F） | 绑定约束（C） |
|---|---------------|---------------|
| F15 | 明细口径互斥：§1「每命中规则 1 条」vs §2.3/详情「全部 HARD 行 + is_hit」 | C16：定版=触发时快照**该记录全部 HARD 汇总行**（is_hit=0/1），证据链自洽，不依赖详情时现拼（记录可能已作废/字典可能已改）（§1/§2.3） |
| F16 | CAS 有锁无入参：confirm/release/ignore body 没写 version，前端无从 CAS | C17：三个拍板接口 body **version 必填**，过期 → 「该单已被处理」（§5/§4.2） |
| F17 | 多规则命中时 default_reason_code 取哪条未定义 | C18：取「默认动作对应明细」中 ratio 最高那条的 default_reason_code；全空则不预填（§3.4/§4.2） |
| F18 | 已活跃 Hold 时仍可 RELEASED，质量会误以为点放行=解批 | C19：RELEASED 只关闭建议单，不调 Hold release、不解批；解 Hold 走既有流程（§4.2） |
| F19 | 测试工程师无 advice-view：回执能跳详情但 403，断头路 | C20：测试角色加 advice-view 只读（§2.4） |
| F20 | MISJUDGE 说明「建议必填」太软，P1 调阈值看板缺料 | C21：改为**必填**，与放行 OTHER 同级（§4.2） |
| F21 | §0「权限 4 码」与 §2.4「5/6 码」矛盾，plan 会数错种子 | C22：统一口径=5 个业务码 + 菜单复用 `test:view`(340)（§0/§2.4） |
| F22 | 级联 IGNORED(VOIDED_RECORD) 未置 record_voided，列表两套口径 | C23：级联时同步 record_voided=1（§4.2/验收 14） |

### 审查记录（第 3 轮 · 2026-10-10 · 残留口径收口）

> 审查人：用户。结论：4 项发现**全部成立**（残留口径/表述打架类），正文已按 C24–C27 修订。

| # | 事实修正（F） | 绑定约束（C） |
|---|---------------|---------------|
| F23 | §2.2 ignore_remark 仍写「建议必填」，与 C21「必填」打架 | C24：改为「MISJUDGE 时必填」（§2.2/验收 5） |
| F24 | 已 Hold 时点放行，§4.2 有口径但 U3 只写禁用原因，UI 无提示 | C25：放行按钮旁明示「只关闭建议单，不解 Hold」（§6 U3） |
| F25 | C18 同动作、同 ratio 并列时取哪条未定义 | C26：并列取 bin_code 字典序最小（§3.4） |
| F26 | 验收 5 缺「MISJUDGE 无 remark → 拒绝」断言 | C27：验收 5 补齐（验收 5） |

### 实测回填（TD-2-plan 起草时 · 2026-10-10 · 只追加）

| # | 事实修正（F） | 绑定约束（C） |
|---|---------------|---------------|
| F27 | §4.1 矩阵「已 Hold → REWORK 以 Track 断言为准」悬而未决 | C28：实测落定——`TrackServiceImpl.rework` 首步即 `holdService.assertNoActive(lotId)`（`TrackServiceImpl.java:1154`），且仅 WAIT/PROCESSING 可返工（:1155）→ **已 Hold 时 HOLD 与 REWORK 均禁用**；矩阵其余格与 `requireExecutableLot` 断言一致。证据与全部 4 项「待核实」收口见 `TD-2-plan.md` §0 |
| F29 | 矩阵用「已出货」但现网无 shipped 状态（实测 created/wait/processing/held/completed/scrapped/merged） | C29：矩阵按真实枚举重写（§4.1 已回填正文）；merged 按完工侧禁用 |
| F30 | TrackTxnResultVO 补 txId 悬置 | C30：本刀不做，exec_note 够用；规格待核实节关闭（见上） |
| F31 | plan 审查实测：`HoldServiceImpl.create:176` 仅 wait/processing 可锁批、`rework:1155` 同口径——created 不能 HOLD/REWORK，初稿矩阵把 created 捆进「可」 | C31：矩阵拆出 created 行（上表已回填）；Off-Flow 禁 REWORK（`:1157`）一并写入 |
| F32 | `TrackContextVO.canRework` 内含 `track:rework` 权限位（`:1620`），quality 无此权限 → 详情永远灰 REWORK 而服务端可跑通 | C32：actionAvailability 忽略 canRework 权限位，只看 reworkOptions 非空 + 状态矩阵 + 非 Off-Flow + 非 hasActive；权限闸只认 `test:advice-confirm`（§4.1 已回填） |

## 关联

- 边界：`MES-封测测试数据与Bin回流方案.md` §6（A1/P7/A5/P3/D9 等沿用）· `TD-1-plan.md`
- 依据：`MES-业界调研-不良Bin处置与HoldRework联动.md`（J1–J11）· `MES-产品分析-不良Bin处置建议联动.md`（P0/R1–R6/指标）
- 代码实测：`MesEdcCollectionServiceImpl.java:92,350-358` · `HoldService.java:46` · `MesHoldCreateDTO.java:12-19` · `TrackServiceImpl.java:1147-1218` · `TrackTxnResultVO.java:8-25` · `MES-Test数据库设计.md`
