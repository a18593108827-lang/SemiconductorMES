---
type: plan
module: History
status: approved
slices: [CP-5]
aligns: [MES-客诉追溯包接口设计.md]
updated: 2026-09-21
---
# CP-5 计划 — contain 遏制：对影响面批量 Hold（8D D3）

> 对齐：`MES-客诉追溯包接口设计.md` §3 / §4 / §6.5 / §7 / §8.2 / §9 / §10 / §11 / §12 · 状态：draft（**未批不动码**）  
> 前置：CP-3 ✅（build/get/list）· CP-4 ✅（export + 入口，验收闭环）  
> 已吸收架构审查 R1～R12（2026-09-21）+ 二轮 F1～F3（对码核验通过：@Version 仅返 0 不抛乐观锁异常、mes_hold.remark=512、seed 列清单/id 8011/category 全符；修正 K18 异常面、登记 skip 文案契约、验证方式去单测化——项目无 src/test）+ 三轮 F4（实测发现：`HoldServiceImpl.create` 断言顺序「先状态后已锁」导致重跑 held 批次先进 failed；修正为「先已锁后状态」，MSG_ALREADY_HELD 才能在 held 场景触发，skipped 分支复活）  
> 已核实现网契约：`HoldService.create` 带 `@Transactional`，**无** Lot `FOR UPDATE`，靠 `mes_lot.version` 乐观锁，失败文案「数据已被他人修改，请刷新后重试」；仅 wait/processing 可锁批；`mes_hold` **无** active 唯一索引；`mes_hold_reason` 字典存在且校验启用；`GET /holds/reasons` + `listHoldReasonsApi` 可作下拉数据源

## 1. 目标与边界

**一句话：** 在追溯包详情里对影响面（或其子集）发起批量锁批——每批独立事务、部分成功可接受、同包 token 占位串行，把「查清楚」升级为「拦得住」（8D D3 遏制）。

**做：**
- 后端：`POST /complaint-packages/{id}/contain`（`complaint:contain` **且** `hold:create`）——token 占位 CAS → 逐 Lot `HoldService.create` → token 结束 CAS → 三段结果
- DDL：`mes_complaint_package` 增可空 `contain_token VARCHAR(36)`（占位所有权）
- 种子：`mes_hold_reason` 增 `CUSTOMER_COMPLAINT`（启用、幂等），归 Hold 字典（本包不自建第二字典，§14）
- 上游扩展：`HoldService.assertReasonUsable(code)`（存在且启用；跨模块走门面，不碰 `MesHoldReasonMapper`）
- 包内拆 `ComplaintPackageContainWriter`（占位 / 心跳 / 结束 CAS）；Facade 只编排、**无** `@Transactional`
- 前端：抽屉 `built` 态加「遏制」区——原因码下拉（默认 `CUSTOMER_COMPLAINT`）+ 二次确认（显示将锁批次数）+ 结果三段表

**不做（负面清单）：**
- ❌ contain 改 genealogy / 禁派规则 / 出货主数据（§7 红线）
- ❌ 「全部成功才提交」的大事务（P8）
- ❌ 本包直写 `mes_hold` / `mes_lot` / `mes_tx_log`
- ❌ contain 并行化；❌ 外层事务挂包行 `FOR UPDATE` 罩整段循环（会把 `create` 并进大事务，违反 K2）
- ❌ 作废流程 / contain 结果导出 / 自动解除 / 遏制审批流 / 异步 contain
- ❌ 前端绕过二次确认直接 contain
- ❌ 循环内预读 `hasActive` 当 skip 依据（A3 / A9：写后真相只认 `create` 结果）

**约束：**

| # | 约束 | 说明 |
|---|------|------|
| K1 | 锁批唯一入口 | 对批次的 Hold 只经 `HoldService.create`；contain 自己只写本包头状态机（token/status/contain_by/time） |
| K2 | 每 Lot 独立事务 | Facade **无 `@Transactional`**，循环调跨 Bean `holdService.create`（各开各的事务）；一笔失败不回滚其它（P8） |
| K3 | 锁序固定 | 目标 `lotId` **升序**逐个处理；禁并行。现网 Hold **不是** Track 那种 `FOR UPDATE` 行锁，只是短事务 + `@Version`；版本冲突进 `failed` 继续，不当死锁 |
| K4 | token 占位 CAS | 请求生成 UUID `token`。`UPDATE ... SET status='CONTAINING', contain_token=:token, update_time=NOW() WHERE id=? AND (status IN ('READY','CONTAINED') OR (status='CONTAINING' AND (update_time IS NULL OR update_time < NOW() - INTERVAL :n SECOND)))`。affected=0 分支见 K16。n 默认 60，配置 `mes.complaint-package.contain-rescue-seconds` |
| K5 | 原状态恢复判别式 | **`contain_time` 即原状态**：空 → 无 succeeded 结束回 `READY`；非空 → 回 `CONTAINED` |
| K6 | token 结束 CAS | `WHERE id=? AND status='CONTAINING' AND contain_token=:token`：succeeded>0 → `SET status='CONTAINED', contain_by=IFNULL(contain_by,:uid), contain_time=IFNULL(contain_time,NOW()), contain_token=NULL`；否则按 K5 回 READY/CONTAINED 并 `contain_token=NULL`。affected=0 → **WARN 不抛**（所有权已丢，禁止改状态） |
| K7 | skip 只认 create | 循环内**只**调 `create`。文案含「已存在生效中的锁批」→ `skipped`。禁止 `hasActive` 预读。**依赖 Hold create「先已锁后状态」断言顺序契约**（F4）——顺序被改回则 held 批次报状态错，skipped 失效（Hold 文档 §6.6 已登记） |
| K8 | reasonCode 整单前置 | `assertReasonUsable(code)` 不通过 → 整单拒；OTHER 备注必填仍由 create 兜底 |
| K9 | lotIds 越界拒整单 | 非空 `lotIds` 必须 ⊆ 成员集合，否则 `COMPLAINT_PACKAGE_LOT_NOT_IN_PACKAGE`；null/空 = 全成员 |
| K10 | 结果 VO 重读包头 | `succeeded[] / skipped[] / failed[]`（failed 含 lotId+lotNo+code+message）+ **结束 CAS 后 SELECT 包头** 的 status/containBy/containTime + 三段计数。禁用本地推算终态 |
| K11 | remark 拼包号 | 落 Hold：`"[CP-包号] " + remark`（remark 可空则仅前缀去尾空格）。拼接后 >512：**保留前缀，截断尾部** |
| K12 | 双权限前后端都拦 | Controller：`@SaCheckPermission(value={"complaint:contain","hold:create"}, mode=AND)`。前端按钮同条件才渲染。二次确认必须显示将锁批次数；原因码下拉复用 `GET /holds/reasons`（仅启用） |
| K13 | 幂等 | 重复 contain 安全：已锁全 skipped、状态不变；`CONTAINED` 包可再次 contain（补充遏制） |
| K14 | 心跳与丢权中止 | 每处理完一 Lot（成功/跳过/失败后）`UPDATE ... SET update_time=NOW() WHERE id=? AND status='CONTAINING' AND contain_token=:token`。affected=0 → **立即停止后续 create**，置 lostOwner，finally 仍走结束 CAS（必失败、不改状态） |
| K15 | try/finally | 占位成功后整段循环包在 try/finally；finally **仅当仍持有 token 意图时**调结束 CAS（lostOwner 也调一次，靠 K6 匹配失败变成 no-op） |
| K16 | 占位失败分支 | affected=0 后读当前行：无行 → `COMPLAINT_PACKAGE_NOT_FOUND`；`CONTAINING` → `COMPLAINT_PACKAGE_CONTAIN_IN_PROGRESS`；`VOID` → `COMPLAINT_PACKAGE_VOID`；其它 → `COMPLAINT_PACKAGE_NOT_FOUND`。禁止把 VOID/空行报成进行中 |
| K17 | Writer 拆分 | 占位/心跳/结束 CAS 只在 `ComplaintPackageContainWriter`；Facade 不直接发这三条 SQL。Mapper `@Update` 只给 Writer 用 |
| K18 | 失败捕获面 | 每 Lot：catch 非 `Error` 的 `RuntimeException`。`BusinessException` 按 K7 分 skipped/failed——**注意 @Version 失败不抛乐观锁异常**（仅 `updateById` 返 0，Hold 侧已转为 BusinessException「数据已被他人修改」→ 归 failed）；其余异常进 `failed`（message 可读）并继续。禁止只 catch `BusinessException` 导致循环被打断、CONTAINING 残留 |

**失败语义（对齐 §7）：** `merged/scrapped/created` 等不可锁批次由 create 拒绝 → 进 `failed`，**不阻断**其余批次。

**现网锁事实（禁止写进代码注释当行锁）：** Track 多 Lot 才 `id` 升序 `FOR UPDATE`；Hold.create 是 select + insert + `updateById`（`@Version`）。contain 只保证升序、一笔一事务、不并行。

## 2. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| POST | `/complaint-packages/{id}/contain` | `complaint:contain` **AND** `hold:create` | 批量遏制；body：`{ lotIds?: string[], reasonCode: string, remark?: string }` |

复用：`GET /holds/reasons`（下拉）、既有 build/get/list/export。**本切片落地的错误码**（全称，禁缩写）：`COMPLAINT_PACKAGE_LOT_NOT_IN_PACKAGE`、`COMPLAINT_PACKAGE_CONTAIN_IN_PROGRESS`、`COMPLAINT_PACKAGE_VOID`（仅行已是 VOID 时）。

## 3. 表变更

**零新表。** 改一列 + 种子一份：

```sql
-- migrate_complaint_contain.sql（已有库）；schema.sql 同步
ALTER TABLE mes_complaint_package
  ADD COLUMN contain_token VARCHAR(36) NULL COMMENT 'contain占位所有权，结束CAS匹配后清空';

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8011, 'CUSTOMER_COMPLAINT', '客诉遏制', 'customer', 1, '追溯包contain默认原因', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();
```

权限无新增（`complaint:contain` = 332 已种）。`contain_by` / `contain_time` CP-1 已备。

配置：`mes.complaint-package.contain-rescue-seconds` 默认 `60`（写入规格 §11）。

## 4. 影响的 Facade 与模块

| 项 | 变更 |
|----|------|
| `HoldService`（Hold） | **新增** `assertReasonUsable(String code)`；Hold 模块文档同步登记 |
| `ComplaintPackageFacade` | 新增 `contain(Long id, ComplaintPackageContainDTO dto)` → `ComplaintContainResultVO` |
| `ComplaintPackageFacadeImpl` | 编排 only：校验 → 目标集 → occupy → 循环 create → finally finish → 重读组装；无事务注解 |
| `ComplaintPackageContainWriter` | **新增** Bean：occupy / heartbeat / finish（三条 CAS，无业务循环） |
| `MesComplaintPackageMapper` | 三条 `@Update`：occupy / heartbeat / finish |
| `MesComplaintPackage` | 增 `containToken` |
| 前端 | `api/complaint.ts` 增 contain + 类型；`ComplaintPackageDrawer` built 态增遏制区 |
| 后端零新增依赖 | 不改 Lot / Alarm / History / Track |

## 5. 实现步骤

a. DTO/VO：`ComplaintPackageContainDTO`（lotIds 可空 List\<String\>、reasonCode @NotBlank、remark @Size(512)）；`ComplaintContainResultVO`（三段 + 重读后的 status/containBy/containTime + 计数）
b. `HoldService.assertReasonUsable` + Impl；Hold 模块文档登记**两件契约**：① 新门面方法；② skip 判据文案「该批次已存在生效中的锁批」是 contain 依赖的**稳定契约**（K7），Hold 侧改动须同步 CP-5
c. DDL `contain_token` + 实体字段 + Mapper 三条 CAS（occupy 含救援窗口并写入 token；heartbeat 只刷新 `update_time`；finish 必须 token 匹配并清空 token）
d. `ComplaintPackageContainWriter`：occupy / heartbeat / finish 三个 public 方法（**不要**给 Writer 加罩整单的 `@Transactional`）
e. Facade `contain`：
   1. `assertEnabled` → `assertReasonUsable` → 查包（NOT_FOUND）
   2. 当前 status=`VOID` → `COMPLAINT_PACKAGE_VOID`（占位前也拦，避免无意义 CAS）
   3. 查成员 → 目标集 = lotIds 非空 ? 校验 ⊆ 成员 : 全员；**升序**
   4. `token = UUID`；occupy（K4）——失败走 K16
   5. try：逐 Lot `holdService.create`（K2/K3/K7/K18）→ 每笔后 heartbeat（K14，丢权则 break）
   6. finally：finish（K5/K6/K15）
   7. **SELECT 包头** 组装 VO（K10）
f. Controller：`@SaCheckPermission(value={"complaint:contain","hold:create"}, mode=AND)`
g. 前端：contain api；抽屉 built 态遏制区——原因码下拉默认 `CUSTOMER_COMPLAINT` + 按钮（K12）→ ConfirmDialog（「将对 N 个批次发起锁批，原因：X」）→ 调 contain → 三段表 + 状态徽标用响应里重读的 status；请求超时建议 ≥120s（同步 HTTP，本切片不做异步）
h. 种子 SQL + schema.sql 同步；`docs/INDEX.md` 重建；文案称「遏制」，禁「自动处置」

顺序：a/b/c/d 可并行 → e → f → g；h 收尾。**禁止**把 e.5 包进任何外层事务。

## 6. 测试与验收

- 验收标准（可判真假）：
  1. 开关关 → contain 拒 `COMPLAINT_PACKAGE_DISABLED`
  2. 混合成员包（已 held / wait / merged）全员 contain：wait → `succeeded`（`mes_hold` 新 active + lot.status=held）；已 held → `skipped`；merged → `failed` 且 message 含「仅等待加工或加工中」；三段互不污染
  3. `lotIds` 含 1 个非成员 → 整单拒 `COMPLAINT_PACKAGE_LOT_NOT_IN_PACKAGE`，零新增 Hold；`lotIds=null` → 全成员
  4. reasonCode 不存在 / 停用 → 整单拒，零 Hold
  5. 并发（必须可复现，禁止「两个终端同时 curl」当唯一手段）：
     - 同包两请求：恰一侧 `COMPLAINT_PACKAGE_CONTAIN_IN_PROGRESS` 或一侧成功一侧进行中；`contain_token` 同一时刻至多一个非空值被成功心跳
     - 占位成功后持续心跳：`rescue-seconds` 内第二请求不得抢占
     - 占位后停止心跳超过 n 秒：第二请求可抢；第一请求后续 heartbeat/finish affected=0，**不再继续 create**
  6. 状态机：任一 succeeded → 包 `CONTAINED` 且 `contain_by/contain_time` 首写、`contain_token` 空；全 skipped / 全 failed → READY 仍 READY、CONTAINED 仍 CONTAINED（K5）；token 空
  7. 重复 contain 同包（第二次全员）→ 全 skipped、状态不变（K13）
  8. remark 落库形如 `[CP-20260921-2] 人工遏制`；超长拼接 ≤512 且以 `[CP-` 开头
  9. `grep -r "mes_tx_log\|mes_lot_genealogy\|MesHoldMapper\|MesAlarmMapper\|MesHoldReasonMapper" server/src/main/java/com/mes/complaint/` **零命中**
  10. 仅有 `complaint:contain`、无 `hold:create`：接口 403、按钮不渲染；双权限才通
  11. 前端：二次确认显示批次数；三段渲染；响应 status=CONTAINED 时徽标更新
  12. Track 回归：被 contain 的 lot `TrackIn` 被拒（既有 Hold 门禁，抽 1 例）
  13. 行 status=VOID → `COMPLAINT_PACKAGE_VOID`，零新增 Hold
- 验证方式：
  - curl：`POST /complaint-packages/{id}/contain`（token 走 `-K`）
  - SQL：`SELECT lot_id, status, reason_code, remark FROM mes_hold WHERE remark LIKE '[CP-%'`；`SELECT status, contain_by, contain_time, contain_token FROM mes_complaint_package WHERE id=?`
  - 并发（可复现；**项目尚无测试基建**——src/test 无，Doc-4 后置，禁写"单测"当手段）：
    ① SQL 手工置行 `status='CONTAINING', contain_token='x', update_time=两分钟前` → 调 contain 应**成功抢占**（K4 救援窗口）
    ② SQL 置 `update_time=NOW()` → 调 contain 应 `CONTAIN_IN_PROGRESS`
    ③ DB 直接执行 heartbeat / finish 语句带**错误 token** → affected=0（验证 WHERE 语义与丢权中止，无需 mid-flight 干预）
    ④ 双终端同时 curl 仅作冒烟补充，不作判定依据
  - 页面：抽屉遏制流全走一遍

## 7. 回滚方式

三层：① `enabled=false` 整体下线；② 收回角色-权限绑定 332 即封 contain 入口；③ 已产生的 Hold 是**业务事实不回滚**（解除走 Hold 正常 release）。`contain_token` 列可留，无运行时副作用。

## 8. 遗留（本切片不做）

| 项 | 现状 | 触发条件 | 方案 |
|----|------|----------|------|
| 作废流程 | 仅 contain 遇 VOID 行拒 | 真做作废 | export 也拒；加作废 API |
| 遏制审批流 | 直接执行 | 合规双人复核 | 加审批中间态 |
| contain 结果进导出 | 结果仅界面 | 8D 附件要遏制记录 | export VO 增 containResult |
| 异步 contain | 同步 HTTP | 成员近上限、网关超时 | 任务表 + 查询；本切片用心跳续命 + 前端超时 ≥120s |
| 集群 / 读写分离适配 | 现网单体单库，CAS 前提"同一包行写单点受理"成立 | 上读写分离（从库延迟）或 PXC/Galera/MGR 多主 | ① 写路径与**判定读**（`rejectOccupyMiss` 确认读、K10 终态重读）锁主库；② 多主下并发同行的落败方表现为**锁冲突异常**（1213/认证失败）而非 0 行 → occupy 视同 false、heartbeat 视同丢权、finish 视同 CAS miss（三条 try/catch）；③ CAS 语句天然幂等，代理重放安全 |

---

## 审查记录（绑定实现，禁止回退）

| # | 约束 |
|---|------|
| K1~K18 | 见 §1 |
| R1 | CONTAINING 必须有 owner token；结束 CAS 认 token |
| R2 | 循环 try/finally；捕获面含乐观锁等非 BusinessException |
| R3 | 禁止 hasActive 预读；skip 只映射 create「已存在生效中的锁批」 |
| R4 | 禁止宣称 Hold.create 自带 Lot 行锁 |
| R5 | 后端 AND `hold:create`，不只藏按钮 |
| R6 | CAS 进 ContainWriter，Facade 只编排 |
| R7 | 错误码全称 `COMPLAINT_PACKAGE_*` |
| R8 | VO 终态必须重读包头 |
| R9 | remark 拼接后截断保前缀 |
| R10 | 原因码种子 ON DUPLICATE KEY |
| R11 | 占位失败按行状态分支，VOID 不报进行中 |
| R12 | 并发验收用心跳/救援可复现，不用手工双 curl 碰运气 |
| F1 | K18 异常面修正：`@Version` 失败仅 `updateById` 返 0、由 Hold 转 BusinessException，**不存在** `OptimisticLockerException` |
| F2 | K7 的 skip 判据文案契约登记进 Hold 模块文档（改动须同步 CP-5） |
| F3 | 验证方式去单测化——项目无 `src/test`（Doc-4 后置）：SQL 造场景 + 双 curl 仅作冒烟 |
| F4 | **`HoldServiceImpl.create` 断言顺序契约：先已锁、后状态**——已 held 批次必须命中 `MSG_ALREADY_HELD`，否则 `skipped` 分支失效。三轮**运行时实测**发现旧顺序下重跑全部落 `failed`（验收 7 不通过），已修并登记 Hold 文档；顺序再改须同步 CP-5 |

**批准记录**：2026-09-21 用户批准（经两轮架构审查：R1~R12 + F1~F3 全量吸收；现网契约已对码核验）。**批准后暂不动码，等用户开工指令。**
