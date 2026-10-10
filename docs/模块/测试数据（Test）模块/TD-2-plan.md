---
type: plan
module: Test
status: approved
slices: [TD-2]
aligns: [MES-TD2规格-不良Bin处置建议联动.md, MES-封测测试数据与Bin回流方案.md, TD-1-plan.md]
updated: 2026-10-10
---
# TD-2 计划 — 不良 Bin 处置建议联动

> 对齐：`MES-TD2规格-不良Bin处置建议联动.md`（**approved**，C1–C32）· 状态：**approved**（可动码；顺序 a→g）
> 规格 3 轮 + plan 2 轮审查已收敛；与规格冲突处以规格为准并按 F# 追加。架构并发/内聚约束见 §5.1（动码红线）。

## 0. 实测记录（plan 起草与第 1 轮审查收口，全部有证据）

| # | 项 | 实测结果 | 证据 |
|---|-----|----------|------|
| 1 | `sys_permission MAX(id)` | **343** → TD-2 用 **344–348** | DB 探针（`probe_td2_plan.py`） |
| 2 | `MesHoldVO.id` | 存在（`MesHoldVO.java:9`），hold_id 回写可行 | 代码实测 |
| 3 | Hold 原因码字典 | `mes_hold_reason` 11 码（8001–8011）→ **新增种子 `8012 TEST_BIN_EXCEED`**（category=quality） | DB 探针 |
| 4 | Hold×Rework 互斥 | `rework` 首步 `assertNoActive`（`TrackServiceImpl.java:1154`）→ 已 Hold 时 HOLD/REWORK 均禁（规格 F27/C28） | 代码实测 |
| 5 | 前端 Tab 挂载点 | `web/src/pages/TestPage.tsx`（`:463 role="tab"` 自定义 tab）；API `web/src/api/test.ts` | 代码实测 |
| 6 | **现网角色** | 仅 4 个：`admin(1) / operator(2) / process_eng(3) / supervisor(4)`——**无质量/测试/生产计划角色** → 必须种子新增（见 §1） | DB 探针（`probe_td2_plan2.py`） |
| 7 | **lot status 枚举** | `created / wait / processing / held / completed / scrapped / merged`，**无 shipped**（规格 F29/C29） | 代码 `TrackServiceImpl.java:89-93` + DB DISTINCT |
| 8 | rework 边判定入口 | `TrackService.context(lotId)` → `reworkOptions`（`:1469`）**可用**；`canRework` 内含权限位**不可用**（见 §4，`:1620`） | 代码实测 |
| 9 | 业务错误码先例 | `TEST_BIN_DEF_CONFLICT: 中文`（`MesBinDefServiceImpl.java:60`）——msg 前缀大写枚举+冒号，TD-2 沿用 | 代码实测 |
| 10 | **created 可否 HOLD/REWORK** | **否**：`HoldServiceImpl.create:176` 仅 wait/processing 可锁批；`rework:1155` 同口径 → 矩阵 created 单列禁用（P-F13） | 代码实测 |
| 11 | **canRework 权限位** | `:1620` 内含 `StpUtil.hasPermission("track:rework")` → actionAvailability 忽略该字段（P-F14/C14） | 代码实测 |

## 1. C7 角色映射落盘（**不再「执行时探针」，本节即定稿**）

现网 4 角色覆盖不了质量拍板 → **种子新增角色 `quality`（质量工程师）**，映射表写死进迁移脚本：

| perm_code | id | admin | quality（新增） | process_eng | operator | supervisor |
|-----------|----|-------|------|------|------|------|
| `test:advice-view` | 344 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `test:advice-confirm` | 345 | ✅ | ✅ | — | — | — |
| `test:advice-release` | 346 | ✅ | ✅ | — | — | — |
| `test:advice-ignore` | 347 | ✅ | ✅ | — | — | — |
| `test:edit-rule` | 348 | ✅ | ✅ | ✅ | — | — |

- `sys_role` +1 行：`quality / 质量工程师 / C7 拍板主体`；`sys_role_permission` 按上表落行。
- **验收断言（合入门槛）**：345/346/347 必须存在 quality 绑定行；operator/supervisor/process_eng 对 345–347 必须无绑定（探针 SQL 断言，缺映射不合入）。
- 测试工程师提交侧沿用既有 `test:create`(341) 授权不动；「测试工程师看建议」由 344 全员只读覆盖（C20）。

## 2. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| GET | `/test/rules` | `test:advice-view` | 规则分页 |
| POST | `/test/rules` | `test:edit-rule` | 新建 + rule_log |
| PUT | `/test/rules/{id}` | `test:edit-rule` | 修改（@Version+updateById）+ rule_log before/after |
| DELETE | `/test/rules/{id}` | `test:edit-rule` | 软删 + rule_log |
| GET | `/test/advice` | `test:advice-view` | 分页，默认 PENDING 倒序 |
| GET | `/test/advice/{id}` | `test:advice-view` | 详情：全 bin 对照 + **defaultAction/defaultReasonCode（VO 计算值）+ actionAvailability**（reworkOptions 取自 `TrackService.context`，**忽略 canRework 权限位**）+ RETEST 后续链 |
| POST | `/test/advice/{id}/confirm` | `test:advice-confirm` | body 带 version；action=HOLD/REWORK/RETEST/TO_SCRAP |
| POST | `/test/advice/{id}/release` | `test:advice-release` | body 带 version |
| POST | `/test/advice/{id}/ignore` | `test:advice-ignore` | body 带 version |
| GET | `/test/advice/pending-count` | `test:advice-view` | 角标数据源 |
| POST | `/test/records`（既有） | `test:create` | 响应扩展 `adviceHit`/`adviceId` |

**主单不存 suggested_action 列**——列表/详情 VO 层按明细计算 `defaultAction`（严重度 HOLD>REWORK>RETEST）与 `defaultReasonCode`（C18/C26：默认动作对应明细中 ratio 最高者，并列取 bin_code 字典序最小，全空不预填）。

### 2.1 业务错误码清单（msg 前缀，沿 `TEST_BIN_DEF_CONFLICT` 先例）

| 错误码 | 触发 |
|--------|------|
| `TEST_RULE_UK_CONFLICT` | 规则 UK 冲突 |
| `TEST_ADVICE_ALREADY_PROCESSED` | CAS 影响行数=0 / version 过期（已被处理） |
| `TEST_ADVICE_INVALID_ACTION` | 动作与状态矩阵冲突（服务端禁用矩阵拒绝，§4） |
| `TEST_ADVICE_MISJUDGE_REMARK_REQUIRED` | MISJUDGE 无 remark |
| `TEST_ADVICE_TO_SCRAP_REMARK_REQUIRED` | TO_SCRAP 无 remark（P-C16） |
| `TEST_ADVICE_RELEASE_REASON_REQUIRED` | 放行无原因 / OTHER 无说明 |
| `TEST_ADVICE_HOLD_REASON_REQUIRED` | HOLD 无 reason_code |
| `TEST_ADVICE_REWORK_PARAM_REQUIRED` | REWORK 缺 toSortNo/reasonCode |
| `TEST_ADVICE_LOT_HELD` | 服务端兜底：hasActive 时确认 HOLD |
| `TEST_RULE_NOT_FOUND` / `TEST_ADVICE_NOT_FOUND` | id 不存在或已软删 |

## 3. 表变更（`migrate_test_advice.sql`）

| 表 | 变更 |
|----|------|
| `mes_test_advice_rule` | 新建（规格 §2.1；UK 四维度；default_reason_code；**@Version 乐观锁走 MP `updateById`**） |
| `mes_test_advice` | 新建（§2.2；UK record_id；version 列；**拍板不走 updateById，手写 `UPDATE ... SET status=终态 WHERE id=? AND status='PENDING' AND version=?` CAS**——与规则乐观锁口径分开，不混 TD-1 K13） |
| `mes_test_advice_item` | 新建（§2.3；**无软删、不继承 BaseEntity**，对齐 `mes_test_bin_summary` 先例） |
| `mes_test_rule_log` | 新建（只追加审计；**无软删、不继承 BaseEntity**，对齐 `mes_test_submit_guard` 先例） |
| `mes_hold_reason` | +1 行：`8012 / TEST_BIN_EXCEED / 测试Bin超限 / quality` |
| `sys_permission` | +5 行：344–348（perm_type=3，parent_id 对齐 341–343） |
| `sys_role` | +1 行：`quality / 质量工程师`（C7）；**行 id = 探针 `MAX(id)` 续接（现值 4 → 预期 5），脚本按实测续接、不写死**（P-C16） |
| `sys_role_permission` | 按 §1 映射表落行 |

schema.sql 同步。

## 4. 服务端禁用矩阵（与前端双重执行，**服务端拒是硬闸**）

confirm/release/ignore 服务端按规格 §4.1 矩阵校验（真实 status 枚举；**created 单列**——`HoldServiceImpl.create:176` 仅 wait/processing 可锁批、`rework:1155` 同口径，P-F13 实测）：

| 批 status | HOLD | REWORK | RETEST/放行/转报废 |
|-----------|------|--------|--------------------|
| created | `TEST_ADVICE_INVALID_ACTION` | `TEST_ADVICE_INVALID_ACTION` | 放行/转报废可；RETEST 拒（批未进站） |
| wait/processing | 可 | 可（无 rework 边拒；**Off-Flow 中拒**，`:1157`） | 可 |
| held | `TEST_ADVICE_INVALID_ACTION` | `TEST_ADVICE_INVALID_ACTION` | 可 |
| completed/scrapped/merged | `TEST_ADVICE_INVALID_ACTION` | `TEST_ADVICE_INVALID_ACTION` | 可（RETEST 拒） |

hasActive 检查保留为 HOLD 并发兜底（`TEST_ADVICE_LOT_HELD`；`create:175` 自带同款闸）。

**actionAvailability 口径（P-C14 定稿）**：详情返回的可用性**忽略 `TrackContextVO.canRework`**——它内含 `StpUtil.hasPermission("track:rework")` 权限位（`:1620`），quality 无此权限会把 REWORK 永远灰掉而服务端 confirm 又能跑通。只看：`reworkOptions` 非空 + 状态矩阵 + 非 Off-Flow + 非 hasActive。权限闸只认 `test:advice-confirm`（D9）。

### 4.1 事务边界与顺序（P-C16 定稿）

confirm(HOLD/REWORK) 单事务内**固定顺序**：①服务端矩阵校验 → ②外部写（`HoldService.create` / `TrackService.rework`）→ ③**CAS 收口**（`UPDATE ... SET status='CONFIRMED', action_taken=?, hold_id/exec_note=? WHERE id=? AND status='PENDING' AND version=?`）。CAS 影响行数=0 → 抛 `TEST_ADVICE_ALREADY_PROCESSED` → **同事务整体回滚（含已写的外部写）**——并发双拍时后到者的 Hold/Track 写随事务撤销，不产生孤儿事务。任一步失败同理整体回滚。release/ignore 无外部写，CAS 即终态。

## 5. 影响的 Facade 与模块

| 方向 | 调用 | 先例 |
|------|------|------|
| Test → Hold | `HoldService.create` / `hasActive` | EDC（`MesEdcCollectionServiceImpl.java:92,350-358`） |
| Test → Track | `TrackService.rework` / `TrackService.context`（**取 reworkOptions，忽略 canRework 权限位**，见 §4） | 方案 D9；`:1469` |
| Test → Lot | 读 lot status/currentSortNo | TD-1 先例 |
| Hold/Track/Lot → Test | 零反向依赖；`TestFacade` 不变 | TD-1 约束 |

### 5.1 架构实施红线（并发 / 内聚 / 耦合 · 动码必守）

> 来源：2026-10-10 架构审查。违反即返工，不另开规格。

| # | 红线 | 说明 |
|---|------|------|
| K1 | **拍板与外部写同一 `@Transactional(REQUIRED)`** | confirm(HOLD/REWORK) 禁止拆事务；**禁止**对 `HoldService.create` / `TrackService.rework` 包一层 `REQUIRES_NEW`（现网 create/rework 本身为 REQUIRED，加入父事务——保持即可） |
| K2 | **事务顺序固定** | ①矩阵校验 → ②外部写 → ③CAS 收口；禁止「先 CAS 再 Hold」且拆事务；CAS=0 必须整单回滚（含已写 Hold/Rework） |
| K3 | **三块拆类，禁上帝类** | `MesTestAdviceRuleService`（规则 CRUD+log）/ `TestAdviceEvaluator`（纯计算，无 Spring 写库副作用）/ `MesTestAdviceService`（拍板编排）。**禁止**把判定+拍板堆进 `MesTestRecordServiceImpl`——Record 创建事务内**只调 Evaluator**，落 advice 表可由 Evaluator 协作或极薄 helper，编排逻辑不进 Record |
| K4 | **零反向依赖** | Hold/Track/Lot **零**改代码、零依赖 `com.mes.test`；`TestFacade` 只读不变 |
| K5 | **权限委托写死** | `test:advice-confirm` 可调 `TrackService.rework` / `HoldService.create`，**有意绕过**控制器层 `track:rework`；**禁止**给 quality 另授 `track:rework` 来「修」availability |
| K6 | **availability 忽略 `canRework`** | 只认 `reworkOptions` 非空 + §4 矩阵 + 非 Off-Flow + 非 hasActive；服务端矩阵为真相，前端只消费 `actionAvailability`，禁止前端自造第二套状态判断 |
| K7 | **同批多单 HOLD** | 先到者 Hold 成功，后到者 `TEST_ADVICE_LOT_HELD` / INVALID，第二张建议单保持 PENDING——属预期产品态，详情须可解释，勿做成「自动连带关闭」 |
| K8 | **判定无远程 / 无外呼** | Evaluator 仅规则表+汇总只读计算；拉长 create 事务可接受，禁止引入 HTTP/MQ |
| K9 | **Hold 直注 Service 本刀接受** | 对齐 EDC 先例；本刀不强制 HoldFacade；若升薄封装不得改 K1/K2 事务边界 |

## 6. 实现步骤

**顺序 a→b→c→d→e→f→g。禁止乱序：c 不得先于 b；d 不得先于 c；e 不得先于 d。**

- **a. 脚本**：`migrate_test_advice.sql`（表 + 权限 344–348 + 原因码 8012 + **角色 quality 与 §1 映射**）+ `schema.sql` 同步 + dev 库执行探针复核（含 §1 合入门槛断言 SQL）。
- **b. 规则域**：`MesTestAdviceRule` 实体/Mapper/`MesTestAdviceRuleService`（CRUD + 四级回退 `findRule`）+ `MesTestRuleLog` 同事务写入；规则乐观锁 @Version+updateById（K3）。
- **c. 判定引擎（依赖 b）**：`TestAdviceEvaluator` 纯计算类（K3/K8）；挂接 `MesTestRecordServiceImpl` 创建事务（汇总落库后**只调 Evaluator**），异常整体回滚（D4）；响应组 adviceHit/adviceId。
- **d. 拍板域（依赖 c）**：`MesTestAdviceService`——confirm/release/ignore：**K1/K2 单事务顺序** + §4 服务端矩阵 + HOLD/REWORK 外部写；详情组装（全 bin 对照 + VO 计算 defaultAction/defaultReasonCode + **actionAvailability（K6）** + RETEST 后续链）；作废级联（PENDING→IGNORED/VOIDED_RECORD + record_voided=1，挂既有 void 事务）。
- **e. 前端（依赖 d）**：`api/test.ts` 扩展；`TestPage.tsx` 处置建议/阈值规则两 Tab——角标、默认 PENDING 倒序、U2 最小列、**只按 actionAvailability 渲染按钮**（K6）+ 放行「不解 Hold」提示、`>` 口径文案、无规则提示；提交回执跳详情。
- **f. 测试与反向验证**：单测（四级回退/严格大于/严重度+并列/服务端矩阵/CAS 并发/事务回滚——mock Hold 失败断言 advice 仍 PENDING/级联/rule_log/**K1 无 REQUIRES_NEW 静态核对**）；**反向验证**（改坏判定引擎与 CAS 条件确认变红）；前端 vitest（角标/排序/矩阵/回执）。
- **g. 文档收尾（同会话）**：Test 模块五件套 + `docs/架构/MES-实施进度与下一步.md` TD-2 状态 + `docs/intent/INT-0001-*.md` TD-2 交付状态 + **`python .workbuddy/scripts/add_frontmatter.py --reindex`（INDEX 零 diff）**。

## 7. 测试与验收

- **验收标准**：规格 14 条 + 本 plan 增补：
  1. **角色绑定断言**（§1 合入门槛：345–347 有 quality、其余三角色无绑定）；
  2. **服务端矩阵断言**：held 批直接 curl confirm(HOLD/REWORK) → `TEST_ADVICE_INVALID_ACTION`（非仅前端禁用）；
  3. **事务边界断言**：mock/注入 HoldService.create 抛异常 → confirm 请求整体回滚（advice 仍 PENDING、mes_hold 无新行）；
  4. **上线条件（C10）**：验收前 dev 库人工或种子 ≥1 条 enabled 试点规则——「功能上了、防线没上」不算验收通过。
  5. **架构红线**：`MesTestRecordServiceImpl` 无拍板编排；confirm 调用链无 `REQUIRES_NEW`；quality 角色无 `track:rework` 绑定（K1/K3/K5）。
- **验证方式**：`cd server && mvn -o test`；`cd web && npm test`；真机 curl 断言；DB 探针。
- **副作用与并发**：两用户并发拍同一单（CAS 恰好一人成功）；重复提交同批同参（守卫表挡）；作废×拍板并发（级联仅 PENDING）；同批第二张 HOLD → LOT_HELD 且单仍 PENDING（K7）；在途请求收尾不得改写新状态。

## 8. 回滚方式

无配置开关（D4 判定同事务不可开关）。回滚 = 部署上一版本 + 回退 SQL（DROP 4 新表；DELETE sys_permission 344–348；DELETE mes_hold_reason 8012；DELETE quality 角色及其 sys_role_permission 行）。P0 阶段数据随表删；P1 起改停用不删。

---

**批准记录**：2026-10-10 用户批准（经 2 轮审查 P-C1–C16 + 表述对齐；该提交即审计轨迹）。

## 审查记录（第 1 轮 · plan · 2026-10-10）

> 审查人：用户。结论：12 项发现**全部成立**，已按 P-C1…P-C12 修订；角色/状态码/错误码均以实测落盘。

| # | 事实修正 | 修订 |
|---|----------|------|
| P-F1 | C7 角色映射推到执行时，现网无质量角色 | P-C1：§1 映射表落盘 + 种子 quality 角色 + 合入门槛断言 |
| P-F2 | 拍板与 Hold/Rework 事务边界未定义 | P-C2：§4.1 同一 @Transactional、失败整体回滚 |
| P-F3 | 禁用矩阵只有前端 + hasActive | P-C3：§4 服务端矩阵硬闸 + 详情 actionAvailability |
| P-F4 | 「已出货」状态不存在 | P-C4：真实枚举矩阵（F29/C29 回填规格正文） |
| P-F5 | 无业务错误码清单 | P-C5：§2.1 九码清单，沿 TEST_BIN_DEF_CONFLICT 先例 |
| P-F6 | 主单无 suggested_action，VO 计算未写明 | P-C6：§2/步骤 d 明确 VO 计算 defaultAction/defaultReasonCode |
| P-F7 | 详情无法判 rework 边 | P-C7：复用 `TrackService.context`（canRework/reworkOptions） |
| P-F8 | txId 悬置 | P-C8：本刀不做，exec_note 够用；规格待核实节关闭（F30/C30） |
| P-F9 | 乐观锁两套口径未分 | P-C9：规则 @Version+updateById；拍板手写 status+version CAS |
| P-F10 | item/rule_log 软删与 BaseEntity 未定 | P-C10：均无软删、不继承 BaseEntity（对齐守卫表/汇总表） |
| P-F11 | 步骤 g 缺 INT-0001 与 INDEX reindex | P-C11：已补 |
| P-F12 | C10 上线条件未进验收 | P-C12：验收 4 之试点规则断言 |

### 审查记录（第 2 轮 · plan · 2026-10-10）

> 审查人：用户。结论：4 项必补 + 4 项顺手**全部成立**（含 2 项代码断言实测坐实），已按 P-C13–P-C16 修订；规格矩阵同步回填 F31/C31、F32/C32。

| # | 事实修正 | 修订 |
|---|----------|------|
| P-F13 | created 捆进「可 HOLD/REWORK」——实测 `create:176` 仅 wait/processing、`rework:1155` 同口径，上会即 INVALID_ACTION 或被原样拒 | P-C13：矩阵 created 单列禁 HOLD/REWORK/RETEST（规格 §4.1 已回填，F31/C31） |
| P-F14 | 直接用 canRework 判可用性——`:1620` 内含 `track:rework` 权限位，quality 无权限 → 详情永远灰 REWORK 而服务端可跑通 | P-C14：actionAvailability 忽略 canRework，只看 reworkOptions 非空+状态矩阵+非 Off-Flow+非 hasActive；权限闸 test:advice-confirm（规格 F32/C32） |
| P-F15 | Off-Flow 禁 REWORK（`:1157`）未写进矩阵/availability | P-C15：§4 矩阵与 availability 口径已写明 |
| P-F16 | 事务内顺序未定；TO_SCRAP remark 缺错误码；sys_role 行 id 未写续接 | P-C16：§4.1 定序「矩阵校验→外部写→CAS 收口（带 hold_id/exec_note），CAS=0 整体回滚」；+`TEST_ADVICE_TO_SCRAP_REMARK_REQUIRED`；sys_role id 探针续接不写死 |

## 实施记录

| 步骤 | 状态 | 登记 |
|------|------|------|
| a | ✅ 2026-10-10 | 探针 perm_max=343→344–348；role_max=4→5；rp 小 id 用 3101–3114。已写并执行 `migrate_test_advice.sql`，`schema.sql` 已同步。合入门槛：345–347 仅 role 1+5；role 2/3/4 对 345–347 绑定=0；试点规则 7301 enabled。证据：`.workbuddy/tmp/probe_td2_a.py` / `run_td2_a.py` |
| b | ✅ 2026-10-10 | 规则域 CRUD + rule_log + findRule 四级回退；接口 `/test/rules`；单测 `MesTestAdviceRuleServiceImplTest` |
| c–g | ⏳ | — |
