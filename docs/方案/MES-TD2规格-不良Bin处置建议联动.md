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
> 一句话：**测试记录落库即判定 → 超 bin 阈值生成建议单（快照留证）→ 质量侧人工确认（走既有事务）/忽略（记原因）→ 全程留痕。绝不自动改状态（P7）。**

---

## 0. 范围与分期

| 分期 | 交付 | 出处 |
|------|------|------|
| **P0（本规格）** | 阈值规则 CRUD（静态值，四级回退）· 记录落库同步判定 · 建议单主表+明细（快照）· 确认（HOLD 全自动 / REWORK 调既有事务 / RETEST 留痕）· 忽略（原因枚举必填）· 作废级联 · 管理端「处置建议」Tab · 权限 4 码 | 产品分析 P0 |
| P1 | 超期升级提醒 · Alarm 复用 raise · 忽略原因统计视图 · 二级限值（level） | J2/J7 |
| 后置 | SBL 统计限 · 返工次数限额扩展 · Lot Commonality · 重测重定向 · SOFT bin 规则 | J1/J8/J10 / §4 越界清单 |

非目标（重申）：现场台零改动（A5）；scrap 不进建议类型（J5）；绝不自动 impound / 自动 scrap（P7）。

---

## 1. 核心流程

```
测试记录提交（TD-1 既有 /test/records）
  └─ 同一事务：记录+汇总落库成功后 → 判定（§3）
       ├─ 无命中 → 无副产物，流程结束
       └─ 命中 → 生成建议单（主表 1 张 + 每命中规则 1 条明细，快照）
            ├─ 确认（test:advice-confirm）
            │    ├─ HOLD   → holdService.create（EDC 先例）→ 回写 hold_id
            │    ├─ REWORK → trackService.rework（既有事务）→ 回写执行快照
            │    └─ RETEST → 仅留痕（执行 = 再提交一条测试记录，天然重判）
            ├─ 忽略（test:advice-ignore）→ 原因枚举必填 + OTHER 须填说明
            └─ 源记录作废 → 级联置 IGNORED / VOIDED_RECORD（系统动作）
```

---

## 2. 库表设计（新增 2 表，脚本 `migrate_test_advice.sql`）

硬约束沿用：id 雪花 19 位；`deleted` TINYINT 软删且 **UK 一律不含 deleted**；不适用维度写**空串**不写 NULL（对齐 `mes_bin_def` 惯例）。

### 2.1 `mes_test_advice_rule`（阈值规则）

| 字段 | 类型 | 说明 |
|------|------|------|
| product_code / program_name / program_version | varchar | 维度组合，四级：`(P,PN,PV)`→`(P,PN,'')`→`(P,'','')`→`('','','')`；不适用填空串 |
| bin_type | varchar(8) | **P0 仅 HARD**（分母 ΣHARD==total_qty 恒定，口径稳）；SOFT 后置 |
| bin_code | varchar(32) | 规则盯的 bin |
| max_ratio | decimal(7,6) | 占比上限（0~1），判定口径 `ratio > max_ratio` 严格大于 |
| suggested_action | varchar(16) | SUGGEST_HOLD / SUGGEST_REWORK / SUGGEST_RETEST，确认时可改选（默认值） |
| enabled | tinyint | 启停；停用不删行 |
| remark | varchar(255) | — |
| version | 乐观锁 | @Version + updateById（全库惯例） |
| deleted | tinyint | 软删 |

UK：`(product_code, program_name, program_version, bin_type, bin_code)` —— 同一 scope 下每 bin 一条规则，镜像 `mes_bin_def` UK 形状。索引：`(enabled, bin_type)`。

### 2.2 `mes_test_advice`（建议单主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| record_id | bigint **UK** | 1:1 —— 判定与记录创建同事务，一记录至多一张单 |
| lot_id / lot_no | 快照 | — |
| product_code / program_name / program_version | 快照 | 取自记录 |
| total_qty | int | 触发时快照（J4） |
| status | varchar(16) | PENDING / CONFIRMED / IGNORED |
| action_taken | varchar(16) | 确认后：HOLD / REWORK / RETEST |
| hold_id | bigint 可空 | 确认 HOLD 回写（`HoldService.create` 返回的 MesHoldVO id） |
| exec_note | varchar(255) 可空 | REWORK 存执行快照（toSortNo、reworkCount/maxReworkCount）；RETEST 存说明 |
| confirm_remark | varchar(255) 可空 | 确认备注 |
| ignore_reason | varchar(32) 可空 | MISJUDGE / DUPLICATE / HIST_RELEASE / VOIDED_RECORD / OTHER |
| ignore_remark | varchar(255) | OTHER 时必填 |
| confirmed_by / confirmed_at · ignored_by / ignored_at | 审计 | — |

索引：`(status)` · `(lot_id)`。

### 2.3 `mes_test_advice_item`（明细，无软删，随主表生命周期）

| 字段 | 说明 |
|------|------|
| advice_id / bin_id | — |
| bin_type / bin_code / **bin_name** | bin_name 为**触发时快照**（防字典改名污染证据链，J4，与 D7 双轨同理） |
| qty / ratio | 该 bin 数量与 `qty/total_qty`，decimal(7,6) |
| rule_id / rule_scope / **rule_max_ratio** | 规则快照：命中时按哪一级 scope 的哪条规则、上限多少 |

UK：`(advice_id, bin_id)`。

### 2.4 权限种子（4 码，挂菜单 `/app/test` 之下，按钮级）

| 语义 | perm_code |
|------|-----------|
| 建议单查询（列表/详情） | `test:advice-view` |
| 确认（含改选动作） | `test:advice-confirm` |
| 忽略 | `test:advice-ignore` |
| 阈值规则维护 | `test:edit-rule` |

id 自 **344** 起——**plan 阶段须探针实测 `sys_permission MAX(id)` 续接**（TD-1 用至 343，中间是否被其他切片占用未核实）。

---

## 3. 判定规则

1. **时机**：测试记录创建事务内、汇总行插入完成后同步判定；**判定异常 → 记录创建整体回滚**（拦截完整率 100% 的代价；宁可提交失败可重试，不可漏举牌——防流出优先，D4）。判定为纯计算 + 规则表/汇总表只读查询，无外部调用，风险可控。
2. **匹配**：对记录的每个 HARD 汇总行，按 `product_code, program_name, program_version` 四级回退找 `enabled=1` 的规则（最具体 scope 唯一命中）；无规则 → 无建议。空串语义与 `mes_bin_def` 一致（PROGRAM_VERSION 档 = 三维度全匹配）。
3. **口径**：`ratio = qty / total_qty`（total_qty 为记录头，ΣHARD==total_qty 已对账恒真）；`ratio > max_ratio` 严格大于才命中。
4. **主单建议动作**：明细动作不同时，主单不设 suggested_action 字段；确认时整单选一个动作（默认 = 首条明细的建议动作）。
5. **幂等**：无独立判定 API；判定只挂在记录创建事务内，重入被守卫表 `mes_test_submit_guard` 挡（TD-1 V6），advice 天然不重复生成。

---

## 4. 状态机与确认/忽略口径

```
PENDING ──确认──> CONFIRMED(action_taken)
   │  └──源记录作废──> IGNORED(VOIDED_RECORD, 系统动作)
   └──忽略──> IGNORED(ignore_reason)
CONFIRMED / IGNORED 为终态，不可逆（改判 = 新事实新记录，不翻旧单）
```

- **确认 HOLD**：入参 `reason_code` 必填（**从 Hold 既有原因码字典选，不新增 bin 专用码**——P3 两维度不混用）；若 `holdService.hasActive(lotId)` 为真 → 业务错「批已存在活跃 Hold」（EDC 同款前置检查，`MesEdcCollectionServiceImpl.java:351`）；成功回写 `hold_id`。
- **确认 REWORK**：入参 `to_sort_no` + `reason_code` 必填；调 `TrackService.rework(lotId, toSortNo, reasonCode, remark)`（`TrackServiceImpl.java:1152`，既有事务 + rework 边 + maxReworkCount 校验全部复用，**不新造第二套返工路径**，D9）；回写 `exec_note`（含 reworkCount/maxReworkCount）。无可用 rework 边时由 Track 侧断言报业务错。
- **确认 RETEST**：`remark` 必填；无系统动作——执行 = 再提交一条测试记录（守卫表按 600s 窗口判重，重测数据不同即通过），新记录落库自动重判。
- **忽略**：`ignore_reason` 枚举必填；`OTHER` 时 `ignore_remark` 必填；留痕同等重要（方案 §6 第 5 条）。
- **作废级联**：记录作废（TD-1 `PUT /test/records/{id}/void`）→ 同事务把该记录 PENDING 建议单置 `IGNORED / VOIDED_RECORD`，操作人记 system。

---

## 5. 接口设计（前缀 `/test`，响应 R 包装；无 `/api` 前缀）

| # | 方法与路径 | 权限 | 说明 |
|---|-----------|------|------|
| 1 | GET `/test/rules` | `test:advice-view` | 规则分页（product/bin/enabled 过滤） |
| 2 | POST `/test/rules` | `test:edit-rule` | 新建（UK 冲突报业务错） |
| 3 | PUT `/test/rules/{id}` | `test:edit-rule` | 修改（乐观锁） |
| 4 | DELETE `/test/rules/{id}` | `test:edit-rule` | 软删 |
| 5 | GET `/test/advice` | `test:advice-view` | 建议单分页（status / lot_no / 时间段） |
| 6 | GET `/test/advice/{id}` | `test:advice-view` | 详情含明细与快照 |
| 7 | POST `/test/advice/{id}/confirm` | `test:advice-confirm` | `{action, reasonCode?, toSortNo?, remark}`（按 §4 校验） |
| 8 | POST `/test/advice/{id}/ignore` | `test:advice-ignore` | `{ignoreReason, ignoreRemark?}` |

无「手动重判」接口（D5：判定只挂记录事务，杜绝第二判定入口）。

---

## 6. 跨模块调用与铁律核对

| 调用 | 方式 | 依据 |
|------|------|------|
| Test → Hold | 注入 `HoldService`（`create` / `hasActive`） | **EDC Auto-Hold 同款先例**（`MesEdcCollectionServiceImpl.java:92,350-358`）；铁律 4 列举的 Facade 清单不含 Hold，沿用先例；如审查认为应升 HoldFacade，plan 阶段加薄封装即可 |
| Test → Track | 注入 `TrackService.rework` | 方案 D9：复用手持事务；权限闸在 Controller 层由 `test:advice-confirm` 承担，服务层断言（边存在/次数上限）原样生效 |
| Track/Hold → Test | **零反向依赖**；TestFacade 维持只读不变 | TD-1 工程约束延续 |

**tx 留痕口径**：方案 §6 第 3 条「确认后记 tx_id」落地为——HOLD 记 `hold_id`（Hold 单据本身已有 tx_log）；REWORK 的 `TrackTxnResultVO` 当前**不暴露 txId**（实测 `TrackTxnResultVO.java:8-25`），回写执行快照替代，plan 阶段评估是否给 VO 补 txId（改动在 Track 侧，非本切片必须）。

---

## 7. 决策表（D）

| # | 决策 | 理由 |
|---|------|------|
| D1 | 静态阈值，SBL 后置 | 数据不足（J1）；规则表结构留有余地，P1 加 level 列即可 |
| D2 | 规则四级回退、空串不 NULL | 对齐 `mes_bin_def` D14 惯例；BIN 语义随版本变 |
| D3 | P0 仅 HARD bin 规则 | ΣHARD==total_qty 对账恒真，分母稳；SOFT 语义后议 |
| D4 | 判定同事务、异常即回滚 | 拦截完整率 100%（产品指标）；提交失败可重试 < 漏举牌不可逆 |
| D5 | 无手动重判入口 | 唯一触发点=记录创建事务；防两套判定真相 |
| D6 | 建议单 1:1 记录 + 明细行 | 整单一次拍板（用户体验），逐 bin 明细保证据（J3/J4） |
| D7 | 忽略/确认为终态不可逆 | 留痕的严肃性；改判走新事实 |
| D8 | 建议动作默认可改选 | 规则建议是提示不是判决；符合「人工拍板」产品原则 |
| D9 | Rework/Hold 复用既有事务，不新造 | 方案 §6 第 2 条 + D9（回流方案）；EDC/Track 实测先例 |

## 禁止表（P，沿方案 §6 + 调研越界清单）

| # | 禁止 |
|---|------|
| P1 | 自动 Hold / 自动 Rework / 自动 scrap / 超期默认报废（P7；超期只提醒） |
| P2 | 现场台任何改动（A5） |
| P3 | Bin 与 Hold 原因码互相复用（P3） |
| P4 | 建议明细直接引用字典当前值（必须快照） |
| P5 | Track/Hold 反向依赖 Test |

## 验收口径（可判真假）

1. 配一条规则（如 HARD bin3 阈值 2%），提交一条 bin3 占比 4.2% 的记录 → 建议单 PENDING 出现，明细含 bin_name/ratio/规则快照；同一事务内模拟判定异常 → 记录创建失败回滚。
2. 确认 HOLD → `mes_hold` 新增 active 行、原因码为既有字典值、建议单 CONFIRMED 且 hold_id 回写；批已 Hold 时确认报业务错。
3. 确认 REWORK → `mes_tx_log` 出现 REWORK 事务（Track 侧既有链路），reworkCount 快照回写。
4. 忽略不留原因 → 拒绝；OTHER 无说明 → 拒绝。
5. 作废源记录 → PENDING 建议单自动 IGNORED/VOIDED_RECORD。
6. 规则停用后新提交的记录不再举牌；改 bin_name 后旧建议单明细仍显示旧名（快照）。
7. 全链权限：无 `test:advice-confirm` 的账号确认 403；现场台代码零 diff。

## 待 plan 阶段核实（不阻塞本规格批准）

- `sys_permission` MAX(id) 实测续接（探针）
- `HoldService.create` 返回 `MesHoldVO` 的 id 字段确认；`TrackTxnResultVO` 是否补 txId
- Hold 原因码字典当前可用值清单（确认 HOLD 的前端下拉数据源）
- 管理端 `/app/test` 页现有 Tab 结构（「处置建议」Tab 挂载点）

## 关联

- 边界：`MES-封测测试数据与Bin回流方案.md` §6（A1/P7/A5/P3/D9 等沿用）· `TD-1-plan.md`
- 依据：`MES-业界调研-不良Bin处置与HoldRework联动.md`（J1–J11）· `MES-产品分析-不良Bin处置建议联动.md`（P0/R1–R6/指标）
- 代码实测：`MesEdcCollectionServiceImpl.java:92,350-358` · `HoldService.java:46` · `MesHoldCreateDTO.java:12-19` · `TrackServiceImpl.java:1147-1218` · `TrackTxnResultVO.java:8-25` · `MES-Test数据库设计.md`
