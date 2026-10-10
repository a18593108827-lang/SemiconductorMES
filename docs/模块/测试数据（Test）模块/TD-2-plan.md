---
type: plan
module: Test
status: draft
slices: [TD-2]
aligns: [MES-TD2规格-不良Bin处置建议联动.md, MES-封测测试数据与Bin回流方案.md, TD-1-plan.md]
updated: 2026-10-10
---
# TD-2 计划 — 不良 Bin 处置建议联动

> 对齐：`MES-TD2规格-不良Bin处置建议联动.md`（**approved**，C1–C27）· 状态：draft（**未批不动码**）
> 规格 3 轮审查已收敛，本 plan 不重新决策，只落实施顺序与验证方式；与规格冲突处以规格为准并按 F# 追加。

## 0. 实测记录（plan 起草时已完成，规格「待核实」项全部收口）

| # | 项 | 实测结果 | 证据 |
|---|-----|----------|------|
| 1 | `sys_permission MAX(id)` | **343** → TD-2 用 **344–348**（5 业务码） | DB 探针 2026-10-10，`.workbuddy/tmp/probe_td2_plan.py` |
| 2 | `MesHoldVO.id` | **存在**（`MesHoldVO.java:9`），`holdService.create` 返回 VO 含 id/lotId/reasonCode/status → `hold_id` 回写可行 | 代码实测 |
| 3 | Hold 原因码字典 | `mes_hold_reason` 现有 11 码（8001–8011，含 Q_ABNORMAL/EDC_OOS/ALARM_POLICY 等）；**无「测试 bin 超限」专用码** → plan 决策新增种子 `8012 TEST_BIN_EXCEED 测试Bin超限`（category=quality），default_reason_code 默认指向它（兼容选既有码，P3 不违反——新增 Hold 码≠复用 Bin 码） | DB 探针 |
| 4 | Hold×Rework 互斥 | **已定**：`TrackServiceImpl.rework` 第一步即 `holdService.assertNoActive(lotId)`（`TrackServiceImpl.java:1154`），且仅 WAIT/PROCESSING 可返工（:1155）、Off-Flow 中不可（:1157）→ 规格 §4.1 矩阵回填：**已 Hold → HOLD 与 REWORK 均禁用**（F27） | 代码实测 |
| 5 | 前端 Tab 挂载点 | `web/src/pages/TestPage.tsx` 自定义 tab（`:463 role="tab"`），API 层 `web/src/api/test.ts`（`createTestRecordApi:83` 等） | 代码实测 |

**F27（追加进规格审查记录，只追加不改已批内容）**：§4.1 矩阵「已 Hold → REWORK 以 Track 断言为准」落定为**禁用**；完工/出货/报废禁用矩阵与 `rework` 的 `requireExecutableLot` + 状态断言一致。

## 1. 目标与边界

**目标**：测试记录落库同步判定 bin 阈值 → 生成建议单（全 HARD 行快照）→ 质量侧拍板（执行 HOLD/REWORK/RETEST/TO_SCRAP · 放行 · 忽略）→ 全程留痕可稽核；提交回执 + 角标 + 默认 PENDING 列表（事找人）。

**不做什么（负面清单）**：SBL 统计限 / 返工次数限额 / Lot Commonality / 重测指定程序重定向 / SOFT bin 规则 / Alarm raise / 超期升级 / 二级限值（均 P1 或后置）；自动 Hold/自动报废（P7）；现场台任何改动（A5）；TestFacade 改动；Hold/Track/Lot 模块**代码零改动**（只调用，不改）。

## 2. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| GET | `/test/rules` | `test:advice-view` | 规则分页 |
| POST | `/test/rules` | `test:edit-rule` | 新建 + rule_log |
| PUT | `/test/rules/{id}` | `test:edit-rule` | 修改（乐观锁）+ rule_log before/after |
| DELETE | `/test/rules/{id}` | `test:edit-rule` | 软删 + rule_log |
| GET | `/test/advice` | `test:advice-view` | 分页，默认 PENDING 倒序 |
| GET | `/test/advice/{id}` | `test:advice-view` | 详情：全 bin 对照 + lot 现状 + RETEST 后续链 |
| POST | `/test/advice/{id}/confirm` | `test:advice-confirm` | body 带 version；action=HOLD/REWORK/RETEST/TO_SCRAP |
| POST | `/test/advice/{id}/release` | `test:advice-release` | body 带 version；release_reason 必填 |
| POST | `/test/advice/{id}/ignore` | `test:advice-ignore` | body 带 version；MISJUDGE/DUPLICATE |
| GET | `/test/advice/pending-count` | `test:advice-view` | 角标数据源 |
| POST | `/test/records`（既有） | `test:create` | **响应扩展** `adviceHit`/`adviceId`（向后兼容） |

错误口径：业务错走 `msg` 前缀（GlobalExceptionHandler code 恒 500）。

## 3. 表变更（`migrate_test_advice.sql`，前缀 mes_test_* / mes_hold_reason）

| 表 | 变更 |
|----|------|
| `mes_test_advice_rule` | 新建（规格 §2.1；UK `(product_code,program_name,program_version,bin_type,bin_code)`；`default_reason_code`；version 乐观锁；deleted tinyint） |
| `mes_test_advice` | 新建（§2.2；UK `record_id`；status PENDING/CONFIRMED/RELEASED/IGNORED；action_taken 含 TO_SCRAP；hold_id/exec_note/release_*/ignore_*/record_voided；version） |
| `mes_test_advice_item` | 新建（§2.3；**全部 HARD 行快照**，is_hit 0/1；UK `(advice_id,bin_id)`） |
| `mes_test_rule_log` | 新建（只追加审计：rule_id/action/before_json/after_json/op_by/op_at） |
| `mes_hold_reason` | **+1 行**：`8012 / TEST_BIN_EXCEED / 测试Bin超限 / quality / 备注：建议单确认 HOLD 默认原因` |
| `sys_permission` | **+5 行**：344 `test:advice-view` / 345 `test:advice-confirm` / 346 `test:advice-release` / 347 `test:advice-ignore` / 348 `test:edit-rule`（perm_type=3 按钮，parent_id 对齐 341–343 同款） |
| 角色绑定（C7） | 种子 SQL：345/346/347 仅质量侧角色；348 仅工艺/质量主管；344 额外授测试/生产/计划。**执行时先探针 `sys_role` 现网角色清单再写映射，写死在脚本内** |

schema.sql 同步（新库一致性，TD-1 惯例）。

## 4. 影响的 Facade 与模块

| 方向 | 调用 | 先例 |
|------|------|------|
| Test → Hold | `HoldService.create(MesHoldCreateDTO)` / `hasActive(lotId)` | EDC Auto-Hold（`MesEdcCollectionServiceImpl.java:92,350-358`） |
| Test → Track | `TrackService.rework(lotId,toSortNo,reasonCode,remark)` | 方案 D9；互斥断言在 Track 侧原样生效 |
| Test → Lot | 读 lot 状态/当前站（禁用矩阵数据源） | TD-1 读 lot 先例 |
| Hold/Track/Lot → Test | **零反向依赖**；`TestFacade` 不变 | TD-1 约束 |

## 5. 实现步骤

- **a. 脚本**：`migrate_test_advice.sql`（表 + 权限 + 原因码 + 角色绑定）+ `schema.sql` 同步；本机 dev 库执行并探针复核。
- **b. 规则域**：`MesTestAdviceRule` 实体/Mapper/Service（CRUD + 四级回退查询 `findRule(product,program,pv,binCode)`）+ `MesTestRuleLog` 只追加写入（@OperLog 同步挂）。
- **c. 判定引擎（步骤 b 完成后）**：`TestAdviceEvaluator`——纯计算类：输入记录+HARD 汇总行，输出明细行（is_hit/ratio/规则快照）与默认动作（严重度 HOLD>REWORK>RETEST，ratio 并列取 bin_code 字典序最小）；挂接 `MesTestRecordServiceImpl` 创建事务内（汇总落库后调用），**判定异常整体回滚**（D4）；响应组装 `adviceHit/adviceId`。
- **d. 拍板域（步骤 c 完成后）**：`TestAdviceService`——confirm/release/ignore（version CAS：`update ... set status=终态 where id=? and status='PENDING' and version=?` 判影响行数）；HOLD 分支 `hasActive` 检查 + `HoldService.create` 回写 hold_id；REWORK 分支 `TrackService.rework` 回写 exec_note；作废级联（挂接既有 void 事务：PENDING → IGNORED/VOIDED_RECORD + record_voided=1）；详情组装（全 bin 对照 + lot 现状/hold 状态 + RETEST 同批后续记录查询）。
- **e. 前端（步骤 d 完成后）**：`web/src/api/test.ts` 扩展；`TestPage.tsx` 新增「处置建议」「阈值规则」Tab——角标=pending-count、默认 PENDING 倒序、U2 最小列、U3 详情（禁用矩阵+放行提示「不解 Hold」）、U4 `>` 口径文案、U5 无规则提示；提交成功回执提示 + 跳详情。
- **f. 测试与反向验证**：单测（判定四级回退/严格大于/严重度/并列/CAS 并发/级联/rule_log）+ **反向验证**（临时改坏判定引擎与 CAS 条件，确认用例真变红）；前端 vitest 用例（角标/默认排序/禁用矩阵/回执跳转）。
- **g. 文档同步（同会话）**：Test 模块五件套更新（功能文档/接口设计/数据库设计/已完成功能/功能清单）+ `docs/架构/MES-实施进度与下一步.md` TD-2 状态 + 规格审查记录 F27 回填。

## 6. 测试与验收

- **验收标准**：规格「验收口径」14 条全部通过（含 2.000000% 不举牌、并发恰好一人成功、角色 403 矩阵、明细行数=HARD 行数、放行不解批、级联 record_voided=1、rule_log before/after）。
- **验证方式**：`cd server && mvn -o test`；`cd web && npm test`；真机 `curl` 断言（登录 admin → 提交记录 → 断言 adviceHit/建议单/hold 行/tx_log REWORK 行）；DB 探针核对种子与快照行。
- **副作用与并发**：两用户并发拍同一单（CAS 恰好一人成功）；重复提交同批同参（守卫表挡，advice 不重复生成）；作废与拍板并发（级联仅 PENDING，已执行单只打标）；**在途请求收尾不得改写新状态**（终态 CAS 二次兜底）。

## 7. 回滚方式

无配置开关（D4 判定同事务不可开关，保持简单）。回滚 = 部署上一版本 + 执行回退 SQL（DROP 3+1 新表、DELETE sys_permission 344–348、DELETE mes_hold_reason 8012、清 sys_role_permission 对应行）。建议单数据随表删除（P0 阶段无历史包袱；P1 起改为停用不删）。

---

**批准记录**：`status` 改为 `approved` 时，在此行写明批准人与日期（该提交即审计轨迹）。

## 实施记录

（执行中登记：环境阻塞 / 验收条目未执行原因 / 实际步骤偏离）
