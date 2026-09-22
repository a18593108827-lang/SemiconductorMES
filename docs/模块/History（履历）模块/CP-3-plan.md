---
type: plan
module: History
status: done
slices: [CP-3]
aligns: [MES-客诉追溯包接口设计.md]
updated: 2026-09-20
---
# CP-3 计划 — build 落库 + 包头审计 + 装配 VO + list 分页

> 对齐：`MES-客诉追溯包接口设计.md` §3/§4/§6.2/§6.3/§6.6/§6.7/§8/§9  
> 状态：done（2026-09-20 用户批准 → 2026-09-21 完成，完成态见 `MES-History已完成功能.md` §6）· 已吸收架构审查 R1~R14（R8 双遍历由 R13 废止）  
> 二轮：Writer 冲突捕获移出事务代理、序号冲突改重读 max+抖动、Alarm 批量定序、装配失败必记日志  
> 三轮：max(seq) 按数字后缀取值（禁字符串排序）；`build()` 不得带事务；Hold 拆 `active`/`released`；设计 §8.3 与本计划对齐

## 1. 目标与边界

**一句话：** preview 确认影响面后 `build` 落包头/成员两张表，提交后现查装配 genealogy / 履历 / Hold / Alarm 四块返回；补 `get` 详情与 `list` 分页两个读端点。

**做：**
- `POST /complaint-packages`（build）：**事务只包** insert 包头 + 批量 insert 成员；提交后装配；幂等策略 = 每次新 `packageId`
- `GET /complaint-packages/{id}`：成员以表为准，装配块现查；与 build 响应同形（**单一 `ComplaintPackageVO`**，R3）
- `GET /complaint-packages`（list）：包头表分页摘要，Admin 入口数据源（R6a）；`size` 默认 20、**截成 100**（不 400）
- 装配四块（Assembler，提交后、按块/按 Lot 失败隔离，R11）：
  - `genealogy`：build **复用**本次 `flattenImpact` 已建的树（R13）；get 再调 `MesLotService.genealogy`（成员已冻结，树现查允许漂）
  - `historiesByLot`：`HistoryFacade.listByLot` 返回 ASC 后取**尾端** N 条（N=`history-per-lot` 默认 100，R4）；单 Lot 失败 → 该 Lot 空列表
  - `holdsByLot`：`HoldService.listByLot` 后按 `status` 拆 **`active` / `released`**，各截 20；单 Lot 失败 → 空
  - `alarmsByLot`：`AlarmFacade.listUnclearedForLots` 一次 IN，按 Lot 分组、每 Lot 截 20（R12）；摘要 `openAlarmCount` 仍用 `countUnclearedForLots`
- `package_no`：`CP-yyyyMMdd-序号`；日期 = JVM `LocalDate.now()`（与现网 DATETIME 同一时钟，单时区）。候选 = 当日 **max(seq)+1**；UK 冲突则 **重读当日 max(seq)+1，并叠加整型抖动 `ThreadLocalRandom.nextInt(3)`（即 +0..2）** 后重开写入事务（含成员），≤3，耗尽抛 `COMPLAINT_PACKAGE_NO_CONFLICT`（R2/R10）。
  - max(seq) **必须按数字后缀**：`MAX(CAST(SUBSTRING_INDEX(package_no,'-',-1) AS UNSIGNED))` + `package_no LIKE 'CP-{ymd}-%'`，或查出当日号后在 Java 解析后缀取 max。
  - **禁止** `COUNT(*)+1`（回滚/删行后 COUNT ≠ 序号）。
  - **禁止** `ORDER BY package_no DESC LIMIT 1`（无零填充时 `…-9` > `…-10`）。
  - 冲突后重读 MAX **必须**（不是 COUNT）；禁止无 UK 保护的 max+1 落库；禁止纯 seq++ 不重读（同频 herd）。抖动加在整型序号上，不是 sleep。

**不做（负面清单）：**
- ❌ contain / export / ZIP / 前端 Admin 按钮（CP-4/CP-5/CP-6 后续切片）
- ❌ 装配查询进未提交事务（R1）
- ❌ 同类自调用 / 私有方法 `@Transactional`；**`build()` / Facade 编排方法不得带 `@Transactional`**（外层事务会使 catch UK 后仍 rollback-only）（R9）
- ❌ 导出落磁盘文件（P0 流式，留给 CP-4）
- ❌ 装配并行化（成员 ≤200，先串行；备注留优化口）
- ❌ 包内存履历全文（§4.3：全文只现查，包只存成员快照 + 元数据）
- ❌ `HistoryFacade.listByLot` 加 limit 参数（500→100 的截断浪费可接受，P1 再下沉）
- ❌ Complaint 包引用 `MesAlarmMapper` / `mes_tx_log` / `mes_lot_genealogy` / `MesHoldMapper`
- ❌ build 同请求再走一遍 `genealogy()`（R8 废止，见 R13）

## 2. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| POST | `/complaint-packages` | `complaint:build` | build 落库 + 装配 VO 返回 |
| GET | `/complaint-packages/{id}` | `complaint:view` | 包详情（成员以表为准，装配块现查） |
| GET | `/complaint-packages` | `complaint:view` | 分页摘要；anchorLotId / status / from / to 可选筛；create_time 倒序；size 默认 20、上限截 100 |

开关关一律 `COMPLAINT_PACKAGE_DISABLED`；id 无效 `COMPLAINT_PACKAGE_NOT_FOUND`；成员 > 上限 `COMPLAINT_PACKAGE_TOO_LARGE`（沿用 CP-2 错码常量）；`uk_complaint_package_no` 重试耗尽 `COMPLAINT_PACKAGE_NO_CONFLICT`。成员 UK `(package_id, lot_id)` 冲突视为算法 bug，**不得**冒充 `NO_CONFLICT`。

**HTTP 成功边界（R11）：** 包头+成员提交成功即 build 成功。装配是只读装饰；单块/单 Lot 失败不得把已落库的包变成 500，也不得诱导客户端再 build（会按 A9 再生成一包）。

## 3. 表变更

**零新表、零改表。** CP-1 的 `mes_complaint_package` / `mes_complaint_package_member` 原样启用：
- 包头：package_no / anchor / direction / depth / member_count / truncated / reason_code / remark / status=READY / create_by / create_time
- 成员：package_id / lot_id / lot_no / relation / depth_from_anchor / qty_snapshot / status_snapshot；UK (package_id, lot_id) 由算法层去重保证
- `create_by` 来源：`StpUtil.getLoginIdAsLong()` 安全包装（与 `HoldServiceImpl` 第 267 行同款约定，R5）

种子数据：无新增（权限 330/331/332 已有）。

## 4. 影响的 Facade 与模块

A1 约束的是**对外边界**（编排只经 `ComplaintPackageFacade`），不是禁止包内拆类。跨模块只读；允许下列**小而明确**的扩展（禁止 Complaint 直查他人表）：

| 被调用 / 扩展 | 方法 | 用途 |
|------|------|------|
| `MesLotService`（Lot） | `flattenImpact` | 影响面 + **把已建树挂到 `MesLotImpactFlatVO.tree`**（R13；preview 可忽略该字段） |
| `MesLotService`（Lot） | `genealogy` | **仅 get / 日后现查**；build 同请求禁止再走 |
| `HistoryFacade`（History） | `listByLot` | 每成员履历（最近 500 ASC，本侧切尾端 N）；**不改签名** |
| `HoldService`（Hold） | `listByLot` | 每成员 Hold 全历史，本侧拆 status 再截；**不改签名** |
| `AlarmFacade`（Alarm） | `listUnclearedForLots(Collection<Long>)` | **本切片新增**只读批量（一次 IN，OPEN+ACK 且 entity=LOT）；Assembler 按 Lot 分组 |
| `AlarmFacade`（Alarm） | `countUnclearedForLots` | 摘要计数（沿用 CP-2） |

**包内拆分（R9/R14，均不出 `com.mes.complaint`）：**

| 组件 | 职责 | 禁止 |
|------|------|------|
| `ComplaintPackageFacade` / Impl | 编排：开关、flatten、调 Writer、调 Assembler、分页 | **方法不得** `@Transactional`；不拼 SQL 当 SSOT |
| `ComplaintPackageNoAllocator` | 当日数字后缀 max(seq)+1；冲突后重读 max + `nextInt(3)` | 不碰装配、不调 History/Hold/Alarm；不按 `package_no` 字符串排序 |
| `ComplaintPackageWriter` | **唯一**写入事务：insert 包头 + 批量 insert 成员 | 不调只读 Facade；不装配 |
| `ComplaintPackageAssembler` | 提交后四块装配 + summary | 不写表、不加锁 |

Writer 事务实现：**独立 Spring Bean 的 public `@Transactional(rollbackFor=Exception.class)`，或 `TransactionTemplate.execute`。** 禁止 Facade 同类自调用，禁止私有方法上的 `@Transactional`，禁止 `build()` 自身带事务（Spring 代理无效或外层 rollback-only → 包头已插、成员失败不会回滚 / catch 后仍提交失败）。

**禁止（依赖检查项）：** Complaint 包零 `mes_tx_log` / `mes_lot_genealogy` / `MesHoldMapper` / `MesAlarmMapper` 引用；Track 包零 Complaint 依赖。

## 5. 实现步骤

a. DTO/VO：`ComplaintPackageBuildDTO` **继承** `ComplaintPackagePreviewDTO`（校验复用）+ reasonCode/remark 可选（长度 64/512）；`ComplaintPackageVO`（packageId/packageNo + members + 四装配块，build/get 共用）；`ComplaintPackageListVO`（包头摘要）；`ComplaintPackageQuery`（分页 + 筛选）。Lot：`MesLotImpactFlatVO` 增加 `tree`（`MesLotGenealogyNodeVO`，可空）。
b. Alarm：`AlarmFacade.listUnclearedForLots`（空入参 → 空列表；口径与 count 相同：OPEN+ACK + entity=LOT；**`lastRaiseAt DESC, id DESC`**——与现网 `listUncleared` 一致，保证每 Lot 截 20 的确定性）。
c. 包内三组件 + Facade `build` / `get` / `page`（**均不加 `@Transactional`**）。build 内部顺序（R1 铁序）：
   1. assertEnabled → `flattenImpact`（同 preview 算法，超限拒）；保留 `flat.tree`
   2. Allocator 生成候选 `package_no`（当日数字后缀 max(seq)+1；无行则 1）
   3. **Writer 事务**：insert 包头 + 循环/批量 insert 成员（本仓库尚无 `saveBatch` 先例，同事务内逐条 insert 即可，≤200）。**冲突捕获与重试在 Facade（Writer 代理外）**：先 catch `DuplicateKeyException`，若无则从 `PersistenceException.getCause()` 取；再于父类 `DataIntegrityViolationException` 之前处理。按异常消息是否包含约束名区分：
      - 含 `uk_complaint_package_no` → Allocator **重读数字后缀 max(seq)+1 + `nextInt(3)`**，只改实体上的 `package_no`（雪花 id 可保留，上一笔已回滚），**再次调用 Writer public 方法**（新事务，含成员），≤3
      - 含 `uk_complaint_pkg_lot` → 算法错误，直接失败，不重试号
      - 其它完整性错误 → 原样抛
      - **禁止**在 Writer `@Transactional` 方法内部 catch 后继续/重插——首次冲突后事务已 rollback-only，会撞 `UnexpectedRollbackException`（R9 陷阱）
   4. 提交后：`Assembler.assemble(package, members, treeForBuild)`；build 传入本次 tree，get 传 `null`（Assembler 内再 `genealogy`）。**按块 try/catch**：genealogy / 每 Lot 履历 / Hold / 整批 Alarm 任一失败 → 该块或该 Lot 空，其它块继续。build **不得**因装配失败向外抛业务错（系统级 Error 除外）。每次装配失败**必须记 WARN 日志**（packageNo + lotId + 块名 + 异常摘要），否则静默空块与真无数据不可区分（R11）。
   - get：assertEnabled → 查包头（无则 NOT_FOUND）→ 查成员表（**排序固定：relation → depth_from_anchor → lot_no**，R7）→ `assemble(..., tree=null)`
   - page：包头表条件分页，create_time 倒序；size≤0 当 20；size>100 截 100
d. Controller：三个端点 + `@SaCheckPermission`（list 与 get 均 `complaint:view`）
e. 配置：`mes.complaint-package.history-per-lot`（默认 100）读进 Assembler

顺序依赖：a / b 可并行 → c → d；e 与 a 并行。**禁止把 c.4 装配挪进 c.3 事务。** UK 重试只包 c.3。

## 6. 测试与验收

- 验收标准（可判真假）：
  1. 开关关：build/get/list 均拒 `COMPLAINT_PACKAGE_DISABLED`
  2. 纯 Split 链 build 后，member 表成员与同参数 preview 完全一致；随后他处再 Split **不改**已落包成员
  3. build 返回含 packageNo（`CP-yyyyMMdd-序号`）、成员、四装配块；无 Yield/OEE 字段
  4. get 与 build 响应同形（同一 VO）；履历块条数 ≤ `history-per-lot`；members 排序稳定
  5. 同锚点重复 build：生成新 packageId（幂等语义正确）
  6. 并发同日 build ≥5：`package_no` **全部唯一**（硬条件）。`NO_CONFLICT` 偶发可接受（客户端再调即成功）；**不**把「至多 1 个 NO_CONFLICT」当不变量
  7. list 按 create_time 倒序；anchorLotId / status / 时间筛选生效；size>100 **截成 100**
  8. reason_code P0 无白名单表，按可空 + 长度校验处理（降级已在设计注明）
  9. 成员 insert 失败（人为后半批抛错）：库中**无**对应包头（事务回滚）
  10. 某成员 `listByLot` 失败（Lot 已删或 404）：build 仍 200，包可 get，members 完整，该 Lot 履历/Hold 为空
  11. 依赖检查：Complaint 包零 `mes_tx_log` / `mes_lot_genealogy` / `MesHoldMapper` / `MesAlarmMapper`；Track 包无 Complaint 依赖
- 验证方式：
  - SQL：`SELECT package_no, member_count, status FROM mes_complaint_package WHERE anchor_lot_id=?`
  - 接口：curl build → 再 Split 一批 → curl get 比对成员不变；多终端同日并发 build 验号唯一
  - 依赖检查：`grep -r "mes_tx_log\|mes_lot_genealogy\|MesHoldMapper\|MesAlarmMapper" server/src/main/java/com/mes/complaint/` 零命中

## 7. 回滚方式

配置开关：`mes.complaint-package.enabled=false` 即整体下线，现网零行为差；已落包头/成员表数据无副作用（纯审计快照，不参与任何门禁），无需数据回退。

## 8. 遗留（本切片不做，实测后另立切片）

| 项 | 现状 | 触发条件 | 方案 |
|----|------|----------|------|
| Writer 逐条 INSERT（≤200） | 同事务内逐条插入，提交仅 1 次；估算本机 DB ≤60ms、跨机房 DB ≈400ms | build 实测 insert 段 > 500ms（尤其 DB 跨机房时） | 改一条多值 `@Insert` + `<foreach>`（参照 `HistoryTxLogMapper` 先例；需自行赋雪花 id）或 `saveBatch` |
| 装配并行化 | 履历/Hold/Alarm 均已批量化（各 1 次 IN）；仅谱系遍历逐节点 | assemble 段实测 > 500ms 且成员数常贴近上限 | 界内线程池并行；**改谱系遍历需动 Lot 共用组件，风险高，不建议** |

**决策（2026-09-20）**：先上线按真实负载观测，不预先优化。观测方式 = build 内加三段耗时日志（flatten / insert / assemble），跑 200 成员极端用例取数。

---

## 审查记录（绑定实现，禁止回退）

| ID | 约束 |
|----|------|
| R1 | 装配禁入未提交事务 |
| R2 | `package_no` UK 重试整笔写入事务；耗尽 `NO_CONFLICT` |
| R3 | build / get 单一 `ComplaintPackageVO` |
| R4 | 履历取 `listByLot` 尾端 N 条 |
| R5 | `create_by` 走 StpUtil 安全包装 |
| R6a | list 端点并入本切片 |
| R7 | 成员排序：relation → depth_from_anchor → lot_no |
| R8 | **废止**（由 R13 取代） |
| R9 | 写入只用 Writer Bean 或 `TransactionTemplate`；禁止同类自调用 / 私有 `@Transactional` / **`build()` 带事务**；**冲突捕获与重试在 Facade（Writer 代理外）**，重试 = 改 `package_no` 再调 Writer public 方法 |
| R10 | 候选 = 当日**数字后缀** max(seq)+1；冲突后**重读 max + `nextInt(3)`** 再插；禁止 COUNT(*)+1；禁止 `ORDER BY package_no DESC`；仅 `uk_complaint_package_no` 走此重试 |
| R11 | 提交成功即 build 成功；装配按块/按 Lot 失败隔离；失败必记 WARN 日志（packageNo + lotId + 块名） |
| R12 | `alarmsByLot` 走新增 `AlarmFacade.listUnclearedForLots`；禁止 Complaint 碰 Alarm Mapper |
| R13 | build 同请求复用 flatten 已建树；禁止再调 `genealogy()` |
| R14 | 包内拆 Allocator / Writer / Assembler；Facade 只编排 |

**批准记录**：2026-09-20 用户批准（经三轮架构审查：R1~R14 全量吸收，上游 Lot/Alarm 文档已双向登记）。
