---
type: plan
module: History
status: draft
slices: [CP-5]
aligns: [MES-客诉追溯包接口设计.md]
updated: 2026-09-21
---
# CP-5 计划 — contain 遏制：对影响面批量 Hold（8D D3）

> 对齐：`MES-客诉追溯包接口设计.md` §3 / §6.5 / §7 / §8.2 / §9 / §10 / §12 · 状态：draft（**未批不动码**）  
> 前置：CP-3 ✅（build/get/list）· CP-4 ✅（export + 入口，验收闭环）  
> 已核实现网契约：`HoldService.create` 自带 `@Transactional`；**仅 wait/processing 可锁批**；`mes_hold_reason` 字典存在且校验启用；`GET /holds/reasons` + `listHoldReasonsApi` 可作下拉数据源

## 1. 目标与边界

**一句话：** 在追溯包详情里对影响面（或其子集）发起批量锁批——每批独立事务、部分成功可接受、同包串行防并发，把"查清楚"升级为"拦得住"（8D D3 遏制）。

**做：**
- 后端：`POST /complaint-packages/{id}/contain`（`complaint:contain`）——占位 CAS → 逐 Lot `HoldService.create` → 结束 CAS → 三段结果
- 种子：`mes_hold_reason` 增 `CUSTOMER_COMPLAINT`（启用），归 Hold 字典（本包不自建第二字典，§14）
- 上游扩展：`HoldService.assertReasonUsable(code)`（存在且启用；跨模块走门面，不碰 `MesHoldReasonMapper`）
- 前端：抽屉 `built` 态加「遏制」区——原因码下拉（默认 `CUSTOMER_COMPLAINT`）+ 二次确认（显示将锁批次数）+ 结果三段表（成功/跳过/失败）

**不做（负面清单）：**
- ❌ contain 改 genealogy / 禁派规则 / 出货主数据（§7 红线）
- ❌ "全部成功才提交"的大事务（P8：一笔失败回滚全部 = 违规）
- ❌ 本包直写 `mes_hold` / `mes_lot` / `mes_tx_log`（锁批唯一入口 = `HoldService.create`）
- ❌ contain 并行化（同包必须串行；多线程无序写 = 死锁温床）
- ❌ VOID 拦截（P0 无 VOID 态）、contain 结果导出、自动解除、遏制审批流
- ❌ 前端绕过二次确认直接 contain

**约束：**

| # | 约束 | 说明 |
|---|------|------|
| K1 | 锁批唯一入口 | 对批次的 Hold 只经 `HoldService.create`；contain 自己零业务表写入（A4 延续，依赖检查项不变） |
| K2 | 每 Lot 独立事务 | Facade **无 `@Transactional`**，循环调 `holdService.create`（跨 Bean 代理，各开各的事务）；一笔失败不回滚其它（P8） |
| K3 | 锁序固定 | 目标 `lotId` **升序**排序后逐个处理；禁并行（§8.2 与 Track/Hold 现网锁序对齐，不引入第二锁序） |
| K4 | 占位 CAS | `UPDATE ... SET status='CONTAINING', update_time=NOW() WHERE id=? AND (status IN ('READY','CONTAINED') OR (status='CONTAINING' AND update_time < NOW() - INTERVAL :n SECOND))`；affected=0 且当前 CONTAINING → `CONTAIN_IN_PROGRESS`；残留阈值 n 默认 60s 可配（`mes.complaint-package.contain-rescue-seconds`） |
| K5 | 原状态恢复判别式 | **`contain_time` 即原状态**：空 → 无 succeeded 结束回 `READY`；非空 → 回 `CONTAINED`（零 DDL 实现 §8.2「改回原状态」：CONTAINED 必有 contain_time，READY 必无） |
| K6 | 结束 CAS | succeeded>0 → `SET status='CONTAINED', contain_by=IFNULL(contain_by,:uid), contain_time=IFNULL(contain_time,NOW()) WHERE id=? AND status='CONTAINING'`；结束 CAS affected=0（被抢）→ **WARN 不抛**（遏制已完成，状态由抢占者收敛） |
| K7 | skip 前置 | 循环内先 `holdService.hasActive(lotId)` → 已锁进 `skipped`，**不让 create 抛"已存在生效锁批"污染 failed** |
| K8 | reasonCode 整单前置 | `assertReasonUsable(code)` 不通过 → 整单拒（入参错误不逐 Lot 重复失败）；OTHER 备注必填仍由 create 兜底 |
| K9 | lotIds 越界拒整单 | 非空 `lotIds` 必须 ⊆ 成员集合，否则 `COMPLAINT_PACKAGE_LOT_NOT_IN_PACKAGE`；null/空 = 全成员 |
| K10 | 结果 VO | `succeeded[] / skipped[] / failed[]`（failed 含 lotId+lotNo+code+message）+ 包终态 status + 三段计数 |
| K11 | remark 透传 + 拼包号 | 入参 remark 可空；落 Hold 时自动拼 `"[CP-包号] " + remark`，保证审计可追 |
| K12 | 前端按钮分级 | `complaint:contain && hold:create` 才渲染（§4.4 建议）；二次确认必须显示将锁批次数；原因码下拉复用 `GET /holds/reasons`（仅启用的） |
| K13 | 幂等 | 重复 contain 安全：已锁全 skipped、状态不变；`CONTAINED` 包可再次 contain（补充遏制） |

**失败语义（对齐 §7）：** `merged/scrapped/created` 等不可锁批次由 create 拒绝 → 进 `failed`（消息可读），**不阻断**其余批次；这就是"部分成功"的设计意图。

## 2. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| POST | `/complaint-packages/{id}/contain` | `complaint:contain` | 批量遏制；body：`{ lotIds?: string[], reasonCode: string, remark?: string }` |

复用：`GET /holds/reasons`（下拉）、既有 build/get/list/export。**新错误码消费**：`LOT_NOT_IN_PACKAGE`、`CONTAIN_IN_PROGRESS`（§9 已定义，本切片首次落地实现）。

## 3. 表变更

**零新表、零改表。** 种子一份：

```sql
-- migrate_complaint_contain.sql（已有库）；schema.sql 同步追加
INSERT INTO mes_hold_reason (..., reason_code, reason_name, status, ...)
VALUES (..., 'CUSTOMER_COMPLAINT', '客诉遏制', 1, ...);
```

权限无新增（`complaint:contain` = 332 已种）。包状态机字段（status/contain_by/contain_time）CP-1 已备。

## 4. 影响的 Facade 与模块

| 项 | 变更 |
|----|------|
| `HoldService`（Hold） | **新增** `assertReasonUsable(String code)`（查 `mes_hold_reason`：存在且启用，否则抛业务错）——跨模块门面扩展，Hold 模块文档同步登记 |
| `ComplaintPackageFacade` | 新增 `contain(Long id, ComplaintPackageContainDTO dto)` → `ComplaintContainResultVO` |
| `ComplaintPackageFacadeImpl` | 占位/结束 CAS 调 Mapper；循环 `hasActive → skip / create`；无事务注解 |
| `MesComplaintPackageMapper` | 新增两条 `@Update` CAS（占位 / 结束） |
| 前端 | `api/complaint.ts` 增 contain + 类型；`ComplaintPackageDrawer` built 态增遏制区（ConfirmDialog + 结果表） |
| 后端零新增依赖 | 不改 Lot / Alarm / History / Track |

## 5. 实现步骤

a. DTO/VO：`ComplaintPackageContainDTO`（lotIds 可空 List\<String\>、reasonCode @NotBlank、remark @Size(512)）；`ComplaintContainResultVO`（三段 + status + 计数）
b. `HoldService.assertReasonUsable` + Impl（查字典，`AssertUtil` 抛"原因码不存在/已停用"）；Hold 模块文档登记
c. Mapper 两条 CAS `@Update`（占位含残留救援窗口；结束含 IFNULL 首写 contain_by/time）
d. Facade `contain`：
   1. `assertEnabled` → `assertReasonUsable(reasonCode)` → 查包（NOT_FOUND）
   2. 查成员表 → 目标集 = lotIds 非空 ? 校验 ⊆ 成员（越界拒）: 全成员；**升序排序**
   3. 占位 CAS（K4）——affected=0 → 读当前 status，CONTAINING → `CONTAIN_IN_PROGRESS`
   4. 逐 Lot：`hasActive` → skipped；否则 `holdService.create`（K2/K3）→ succeeded / failed（捕获 BusinessException 取 code+message，**继续下一个**）
   5. 结束 CAS（K5/K6）：succeeded>0 → CONTAINED；否则按 contain_time 判别回 READY / CONTAINED
   6. 组装结果 VO（含包终态）
e. Controller 端点 + `@SaCheckPermission("complaint:contain")`
f. 前端：contain api；抽屉 built 态遏制区——原因码下拉（`listHoldReasonsApi()` 默认选 CUSTOMER_COMPLAINT）+ 「遏制」按钮（K12 权限）→ ConfirmDialog（"将对 N 个批次发起锁批，原因：X"）→ 调 contain → 三段结果表 + 状态徽标更新；failed 组可展开看 message
g. 种子 SQL + schema.sql 同步；`docs/INDEX.md` 重建；文案自查（沿用 C6 红线：称「遏制」，禁「自动处置」等越界词）

顺序：a/b/c 可并行 → d → e → f；g 收尾。**禁止**把 d.4 的循环包进任何外层事务（K2）。

## 6. 测试与验收

- 验收标准（可判真假）：
  1. 开关关 → contain 拒 `COMPLAINT_PACKAGE_DISABLED`
  2. 混合成员包（含已 held、wait、merged）contain 全员：wait → `succeeded`（DB：mes_hold 新 active 行 + lot.status=held）；已 held → `skipped`；merged → `failed` 且 message 含"仅等待加工或加工中"；三段互不污染
  3. `lotIds` 越界（含 1 个非成员）→ 整单拒 `LOT_NOT_IN_PACKAGE`，DB 零新增 Hold；`lotIds=null` → 全成员
  4. reasonCode 不存在 / 停用 → 整单拒（`assertReasonUsable` 报"原因码不存在/已停用"），零 Hold
  5. 并发两请求 contain 同包 → 恰一侧 `CONTAIN_IN_PROGRESS`；无死锁（MySQL 无锁等待超时报错）
  6. 状态机：任一 succeeded → 包 `CONTAINED` 且 `contain_by/contain_time` 首写；全 skipped → **READY 仍 READY、CONTAINED 仍 CONTAINED**（K5 判别式）；全 failed → 同左
  7. 重复 contain 同包（第二次全员）→ 全 skipped、状态不变（K13）
  8. remark 落库形如 `[CP-20260921-2] 人工遏制`（K11）
  9. `grep -r "mes_tx_log\|mes_lot_genealogy\|MesHoldMapper\|MesAlarmMapper\|MesHoldReasonMapper" server/src/main/java/com/mes/complaint/` **零命中**（K1：reason 校验走 HoldService 门面）
  10. 前端：无 `complaint:contain` 或无 `hold:create` → 遏制按钮不渲染；二次确认显示批次数；结果三段正确渲染；遏制后包状态显示 CONTAINED
  11. Track 侧回归：被 contain 的 lot `TrackIn` 被拒（既有 Hold 门禁，非新逻辑，抽 1 例验证）
- 验证方式：
  - curl：`POST /complaint-packages/{id}/contain`（token 走 `-K` 配置文件方式，避免命令行敏感串）
  - SQL：`SELECT lot_id, status, reason_code, remark FROM mes_hold WHERE remark LIKE '[CP-%'`；`SELECT status, contain_by, contain_time FROM mes_complaint_package WHERE id=?`
  - 并发：两个终端同时 curl 同一包
  - 页面：抽屉遏制流全走一遍

## 7. 回滚方式

三层：① `enabled=false` 整体下线；② 收回角色-权限绑定 332（`sys_role_permission`）即封 contain 入口；③ 已产生的 Hold 是**业务事实不回滚**（如需解除走 Hold 正常 release 流程，留审计痕迹）。

## 8. 遗留（本切片不做）

| 项 | 现状 | 触发条件 | 方案 |
|----|------|----------|------|
| VOID 拦截 | 无 VOID 态 | 真做作废 | contain/export 拒 `COMPLAINT_PACKAGE_VOID` |
| 遏制审批流 | 直接执行 | 合规要求双人复核 | 加审批中间态 |
| contain 结果进导出 | 结果仅界面展示 | 客户要 8D 附件含遏制记录 | export VO 增 containResult 块 |
| 残留救援阈值 | 固定 60s 可配 | 实际出现长时间占位误抢 | 记录 contain 开始时间戳替代 update_time 判别 |

---

## 审查记录（绑定实现，禁止回退）

| # | 约束 |
|----|------|
| K1~K13 | 见 §1 约束表 |

**批准记录**：`status` 改为 `approved` 时，在此行写明批准人与日期（该提交即审计轨迹）。
