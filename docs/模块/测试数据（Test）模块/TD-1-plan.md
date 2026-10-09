---
type: plan

module: Test

status: done

slices: [TD-1]

aligns: [MES-封测测试数据与Bin回流方案.md, INT-0001-封测颗级追溯与测试数据回流.md]

updated: 2026-10-09
---

# TD-1 计划 — Strip 条级 + 测试记录与 Bin 汇总回流

> 对齐：`MES-封测测试数据与Bin回流方案.md` §2（层级模型）· §3（数据，含 §3.2.1 守卫表 / §3.7 软删口径）· §4（Facade）· §5（接口与 V1–V6 校验）· §7（客诉包延伸，三块）· §8（A1–A11）· §9（P1–P9）· §10（D1–D22）· 尾部补口径（2026-10-08 产品 D15–D18 / 架构 D19–D22）
>
> 上游意图：`INT-0001-封测颗级追溯与测试数据回流.md`（已采纳；本切片对应验收 1 的批级部分、验收 2、验收 4 的三块、验收 5）
>
> 状态：**done（2026-10-09 实现完 + R9 真机验收通过；EVAL-0003 已回流）** —— 治理铁律 A2
>
> **实施前置核对项（动手前必须实测，禁止凭记忆分配）**
>
> - **M1 权限 id 段**：只读探针实测现网 `sys_permission` MAX(id) = **332**（2026-09-23）；本切片拟用 **340–343**（4 条），实施第一步再跑一次 `SELECT MAX(id) FROM sys_permission` 复核，冲突则整体后移（禁止与既有冲突）。
> - **M2 字典空档取值口径（已定稿）**：规格 §3.1 已定 —— GLOBAL / PRODUCT / PROGRAM 档的**不适用维度一律填 `''`**（非 NULL；MySQL 唯一索引不收 NULL，用 `''` 五元 UK 才生效）。实施**照规格执行**，不再论证；若发现现网有更优先例，先回来改规格再动码。
> - **M3 `schema.sql` 同步方式（已定稿 2026-10-08）**：复审定案 = **每切片人工拼接**（`schema.sql` 已含 CP 切片 `mes_complaint_package*` 两表佐证，证据见 §8 通过项 5）。实施按既有 `CREATE TABLE IF NOT EXISTS` 风格人工追加新表，禁止自创第三种做法。
> - **M4 客诉包 README 行项数（已定稿 2026-10-08，R2-F2 裁定 + R3 对齐方案 §7）**：README 物理行数与「行项」口径不一致（R2-F2），**验收断言一律 contains**，不数行数。方案 §7 要求：现有封面**新增 4 行上限**（测试记录 / 每记录 Bin 档 / Strip / 客户映射），行项 20→24（文档叙述口径）；另有 2 处**改既有句子**（空数据句补三块、口径句改为「含测试分档、Strip 条清单、客户批号映射；三者挂在登记当时的 Lot，拆批后不复制到子批；不含 Wafer Map 图形与良率分析」）。

## 1. 目标与边界

**一句话：** 让测试分档第一次进系统并与批次绑定——管理端登记测试记录、Strip 与客户批号；客诉包里同时有分档、条清单、来料/出货批号。

**做：**

- **Test 模块（新增 `com.mes.test`）**：`mes_bin_def` 字典（含 `program_version` 维度）+ `mes_test_record` + `mes_test_bin_summary` + **守卫表 `mes_test_submit_guard`**（D19）+ 号段表 `mes_test_record_no_seq`；登记接口一次提交守卫 + 记录 + 汇总（同事务）；**作废接口**（A11，只软删头表）；查询接口；`TestFacade`（只读）。
- **Lot 模块扩展**：`mes_lot_strip`（条级身份登记与查询）、`mes_lot_customer_map`（来料 ↔ 出货映射登记与正 / 反查）。
- **客诉包延伸（三块，D16）**：`testSummaryByLot`（经 `TestFacade` 只读）+ `stripsByLot` / `customerMapsByLot`（经 Lot 既有 Service，一次 IN）；范围 = 包内**全部成员**；ZIP `README.txt` 封面增 4 行上限 + 改 2 句。
- **管理端**：新增「测试数据」页（记录登记 / 查询 + Bin 字典维护）；Lot 详情增「测试结果」区与「Mapping / 条级」区（D15：客户映射界面与 TD-1 同期）。
- **权限与菜单**：`test:view` / `test:create` / `test:void` / `test:edit-bin`（**全两级**，C2 裁决；挂管理端，现场台不挂 —— 见方案 §5）。
- **测试**：后端零依赖用例（对账 V1、字典**四级**回退 V2、汇总去重 V5、**守卫冲突转换 V6**、K7 快照）+ 前端重入用例；重建 `docs/INDEX.md`。

**不做（负面清单）：**

- ❌ 颗级 Die / ECID 序列号（TD-3）
- ❌ STDF / CSV 文件解析（TD-3；本切片仅 `source_type` + `source_ref` 留引用）
- ❌ 条级 Bin 细分（每条 Strip 各档多少颗，TD-3）
- ❌ 不良 Bin → Hold / Rework 建议联动（TD-2；本切片**零** Hold / Track 改动）
- ❌ 自动 Hold / 自动 Rework（含 TD-2 也只出建议）
- ❌ 良率算法 / Wafer Map 图形 / OEE / YMS 能力
- ❌ **现场台任何改动**（A5）——不新增录入、不新增确认、不加字段
- ❌ 把 Strip 建成 Lot；把 Strip / Die / 客户批号写进 `mes_lot_genealogy`
- ❌ Bin 字典与 `mes_hold_reason` 互相复用
- ❌ 客诉包 / 报表直连 `mes_test_*` 表或 mapper
- ❌ 出货 / 编带 / 委外工序
- ❌ 改动 CP-1～CP-6 既有导出格式（只在 JSON 增键、README 增行）

**约束：**

| #   | 约束           | 说明                                                                                                                                                                                                          |
| --- | ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| K1  | 状态真相不动       | 本切片不写 `mes_tx_log`、不改 `mes_lot.status`、不调 Hold / Track 写路径（A1 / A6）                                                                                                                                         |
| K2  | 跨模块走既有读入口 | 测试数据出模块只经只读 `TestFacade`。条与映射出模块只经 `MesLotService` 的只读方法。Test 不引 Hold / Track / complaint（A7 / P4）                                                                                                   |
| K3  | 记录与汇总同事务     | 一次请求内记录头 + 全部汇总行整体成功或整体失败（A8）；禁止先插头再逐行插且中途可失败                                                                                                                                                               |
| K4  | 写入即对账        | `Σ bin_qty(HARD) == total_qty`，不等则拒且**零落库**（V1 / D6 / A3）                                                                                                                                                   |
| K5  | 程序版本必填       | `program_name` / `program_version` / `test_time` 非空（V3 / A9）                                                                                                                                                |
| K6  | 允许重测         | 同批多条记录合法，**不覆盖**。**记录表**不加业务唯一键（A10）。同一 10 分钟桶内的重复提交由守卫表 UK 挡住（K12）                                                                                                                         |
| K7  | 快照不可漂        | 汇总行的 `bin_name` / `is_shippable` 为登记时快照；字典后改**不影响**历史（D7）                                                                                                                                                   |
| K8  | 包结构沿用现网      | 新模块用 `controller / dto / entity / mapper / service / service.impl / vo`（实测 lot / hold / complaint 一致）；`AGENTS.md` §3 已于 2026-09-23 同步为现网分层，二者一致                                                             |
| K9  | 权限 id 先复核    | 见 M1；禁止直接照抄本 plan 的数字落库                                                                                                                                                                                     |
| K10 | 错误口径         | 业务码在 `msg` 前缀（现网 `GlobalExceptionHandler` 恒 `code=500`）；前端取 `msg` 展示                                                                                                                                        |
| K11 | 软删口径与全库一致    | 有 `deleted` 的列一律 **TINYINT**、**UK 不含 `deleted`**、软删走 MP `@TableLogic`；**禁止手写软删 SQL**。「撤销后重建同键」用 `PUT` 改行表达（C1 / §3.7）。**例外（无 `deleted`，实体不继承 `BaseEntity`、不加 `@TableLogic`）**：号段表、守卫表、`mes_test_bin_summary` |
| K12 | 判重走守卫表（D19 取代先查后插） | **禁止 `SELECT` 判重再 `INSERT`**（两个并发事务都能通过查询）。`create` 同一事务内**先插守卫行** `mes_test_submit_guard`，UK `(lot_id, eqp_key, program_name, program_version, test_time, total_qty, window_bucket)` 冲突 → 整笔回滚 + `TEST_RECORD_DUPLICATE`。`eqp_key`：有设备写 `eqp_id`，无设备写 **`0`**（禁止 NULL —— MySQL 唯一索引不把两个 NULL 当重复）。`window_bucket = FLOOR(UNIX_TIMESTAMP(NOW())/600)`，按插入当时切桶。MP `eq(col, null)` 不跳过条件的教训（`AbstractWrapper:466-469`）仍适用于本模块**其他**可空条件查询，但 V6 不再走查询判重 |
| K13 | 乐观锁不手写       | Bin 字典 PUT 用 MP `@Version` + `updateById` 判影响行数，冲突即拒；**禁止手写 version 条件更新**（F2）                                                                                                                              |
| K14 | 并发重复靠 UK 转业务码（D20） | Strip / 客户映射的**跨请求**重复靠表 UK：捕获 `DuplicateKeyException` 转 `LOT_STRIP_DUPLICATE` / `LOT_MAP_DUPLICATE`；**禁止**把数据库唯一冲突原样变成 500。同请求内重复仍走前置校验（UX 友好），但前置检查不构成并发保证 |
| K15 | 取号同事务同连接（D21） | `bump()` 与 `lastInsertId()` 写在同一个 `@Transactional` 的 `create` 里，中间**不换连接、不开 `REQUIRES_NEW`**（`LAST_INSERT_ID()` 是连接级的；照现网 `MesLotServiceImpl` 124 行事务、166–167 行连着取号）。事务回滚后退号，允许 |
| K16 | 门面只读 / 作废只删头 / 不写 customer_lot（D22） | `TestFacade` 只读；校验（含 `assertBinDef`）留在 `MesTestRecordService`，**不上门面**；Test 模块**禁止注入 `MesLotMapper`**；作废只软删头表（汇总靠 join 头表消失、守卫行不删）；本切片**不写** `mes_lot.customer_lot`（新表为映射真相，旧列保留为便查冗余） |

## 2. 接口清单

| 方法   | 路径                             | 权限码             | 说明                                                                             |
| ---- | ------------------------------ | --------------- | ------------------------------------------------------------------------------ |
| POST | `/test/records`                | `test:create`   | 登记记录 + Bin 汇总（同事务，K3）                                                          |
| GET  | `/test/records`                | `test:view`     | 分页（`lotId` / `lotNo` / `stage` / `programName` / 时间范围）                         |
| GET  | `/test/records/{id}`           | `test:view`     | 详情（含汇总明细）                                                                      |
| PUT  | `/test/records/{id}/void`      | `test:void`     | 作废记录（原因必填；**只软删头表**，汇总靠 join 头表消失，**不删守卫**，`record_no` 不复用；A11 / K16）          |
| GET  | `/test/summary/by-lot/{lotId}` | `test:view`     | 按批摘要（Lot 详情页消费）；**Test 侧自有 controller，Lot 侧不代理此端点**（A7，避免 Lot 直连 `mes_test_*`） |
| GET  | `/test/bins`                   | `test:view`     | 字典查询（可筛 `productCode` / `programName` / `programVersion` / `binType`）          |
| POST | `/test/bins`                   | `test:edit-bin` | 字典新增                                                                           |
| PUT  | `/test/bins/{id}`              | `test:edit-bin` | 字典修改 / 停用（**乐观锁走 MP `@Version`**，影响行数为 0 即冲突 → `TEST_BIN_DEF_CONFLICT`；K13）    |
| POST | `/lots/{id}/strips`            | `lot:edit`      | Strip 批量登记（同事务整体成败；请求内 `strip_no` 重复**前置拒绝** `LOT_STRIP_DUPLICATE`；**跨请求撞 UK 也转 `LOT_STRIP_DUPLICATE`**，D20 / K14） |
| GET  | `/lots/{id}/strips`            | `lot:list`      | Strip 列表                                                                       |
| POST | `/lots/{id}/customer-maps`     | `lot:edit`      | 客户 Lot 映射登记（INBOUND / OUTBOUND；跨请求撞 UK 转 `LOT_MAP_DUPLICATE`，D20；**不写 `mes_lot.customer_lot`**，D22） |
| GET  | `/lots/{id}/customer-maps`     | `lot:list`      | 正查（本批 → 外部批号）                                                                  |
| GET  | `/lots/by-external-lot`        | `lot:list`      | 反查（外部批号 → 内部批，召回主路径）                                                           |

新增错误码（命名沿用现网 `模块_语义`）：`TEST_BIN_SUM_MISMATCH`（V1）、`TEST_BIN_DEF_NOT_FOUND`（V2）、`TEST_RECORD_FIELD_REQUIRED`（V3）、`TEST_BIN_CODE_DUPLICATED`（V5）、**`TEST_RECORD_DUPLICATE`**（V6 守卫 UK 冲突）、**`TEST_BIN_SCOPE_INCONSISTENT`**（`bin_scope` 与三维字段不一致）、**`TEST_BIN_DEF_CONFLICT`**（字典乐观锁冲突）、**`LOT_STRIP_DUPLICATE`**（同请求内前置拒绝 + 跨请求撞 UK 转换，D20）、**`LOT_MAP_DUPLICATE`**（映射跨请求撞 UK 转换，D20）。复用现网 Lot 404 / 状态错码。

## 3. 表变更

| 脚本                                                     | 内容                                                                                                                                                                                                                                         |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `server/src/main/resources/db/migrate_test.sql`（新增）    | 建 `mes_bin_def`（含 `program_version`，**UK 5 元**、`bin_scope` **4 档**）/ `mes_test_record` / `mes_test_bin_summary` / **`mes_test_submit_guard`**（方案 §3.2.1 / D19：`eqp_key BIGINT NOT NULL` 无设备写 0、`window_bucket BIGINT`，UK 7 元，**无软删无审计**）/ 号段表 `mes_test_record_no_seq`（D11）；`mes_bin_def` 演示种子（GLOBAL 的 HARD Bin1–Bin4）；权限 4 条（M1 复核后定 id）+ 管理端菜单 |
| `server/src/main/resources/db/migrate_lot_pkg.sql`（新增） | 建 `mes_lot_strip` / `mes_lot_customer_map`（方案 §3.4–§3.5）；两表 `deleted` 一律 **TINYINT**、**UK 不含 `deleted`**（C1 / §3.7）                                                                                                                        |
| `server/src/main/resources/db/schema.sql`（改）           | 按现网既有方式同步上述 **7 张表**：Test 侧 5 张（`mes_bin_def` / `mes_test_record` / `mes_test_bin_summary` / `mes_test_submit_guard` / `mes_test_record_no_seq`）+ Lot 侧 2 张（`mes_lot_strip` / `mes_lot_customer_map`）。无 `deleted` 的是汇总、守卫、号段（K11） |

**零改动**：`mes_lot`（**结构不变；本切片也不写其 `customer_lot` 列值**，D22 / K16 —— 既有值保留为便查冗余，真相在新表）、`mes_lot_genealogy`、`mes_tx_log`、`mes_hold*`、`mes_route*`、`mes_complaint_package*` 的**结构**均不变。

## 4. 影响的 Facade 与模块

| 项                                                           | 变更                                                                                                                                                     |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `TestFacade`（新增，`com.mes.test.facade`，**只读**）            | `listRecordsByLots(Collection<Long> lotIds, int capPerLot)`：返回这些记录**及其 Bin 汇总**（记录一次查询，汇总 `record_id IN (...)` 一次查询）。**`assertBinDef` 留在 `MesTestRecordService`，不上门面**（D22）；**唯一**对外读入口，Lot 详情摘要走 Test 自己的 controller，不经 Lot 代理 |
| `MesLotService`（扩展）                                         | `listStrips(lotId)` / `listCustomerMaps(lotId)` / `findLotsByExternalLot(no)`；客诉包另用 **`listStripsByLots(lotIds)` / `listCustomerMapsByLots(lotIds)`**（一次 IN，**禁止按 Lot 循环**；上限在 **SQL** 按批截断，禁止全量装内存再截）          |
| `ComplaintPackageAssembler`（改）                              | 增三块 `testSummaryByLot`（走 `TestFacade`）/ `stripsByLot` / `customerMapsByLot`（走 Lot 既有 Service，**禁止新 mapper 依赖**），一次 IN，失败按块隔离 + WARN；JSON 形状与现网 `historiesByLot` 相同（`Map<lotId, List>`）                                                       |
| `ComplaintPackageExporter`（改）                               | README 封面**增 4 行上限**（每 Lot 测试记录 / 每记录 Bin 档 / 每 Lot Strip / 每 Lot 客户映射）+ 空数据句补三块 + 口径句改写（M4 / 方案 §7）。**其余行项与 JSON 既有键不动**                                                                                     |
| `ComplaintPackageFacadeImpl`（改）                             | 把**四个**上限（新配置 key 或类内常量，实施时二选一并写明）传入 Exporter，与既有三上限同路径（`FacadeImpl:250`）                                                                                                  |
| 前端 `api/test.ts`（新增）/ `TestPage.tsx`（新增）/ `LotsPage.tsx`（改） | 测试数据页 + Lot 详情两区；权限码门禁                                                                                                                                 |
| 前端路由与侧栏                                                     | 注册「测试数据」菜单（管理端）                                                                                                                                        |
| **Hold / Track / WIP / Route / EDC / Alarm / SPC / Report** | **零改动**                                                                                                                                                |


## 5. 实现步骤

**顺序：a → b → c → d → e → f → g 收尾。禁止乱序项：d（客诉包）不得先于 b（Facade），b 不得先于 a（建表）。**

a. **建表与种子**：先执行 M1 复核 → 写 `migrate_test.sql` / `migrate_lot_pkg.sql` → 同步 `schema.sql`（M3）。确认脚本可重复执行（沿用现网 `IF NOT EXISTS` + `ON DUPLICATE KEY UPDATE` 风格）。

b. **Test 模块**（`com.mes.test`，按 K8 分层）：

1. entity / mapper（**4 张表**）；`MesBinDef`、`MesTestRecord`、`MesTestBinSummary`、`MesTestSubmitGuard`。汇总与守卫**不继承 `BaseEntity`**（无 `deleted`，K11）；守卫的 `eqp_key`、`window_bucket` 非空
2. `MesTestRecordService`：`create(dto)` **同一个 `@Transactional`** 内按序执行 —— ① V1–V5 校验（对账 → 字典解析 → 必填 → Lot 校验 → 档内去重），任一失败整体回滚且**零落库**（K4）；② **插守卫行**（K12 / D19：UK 冲突捕获 `DuplicateKeyException` 转 `TEST_RECORD_DUPLICATE`，整笔回滚）；③ 发号（K15）；④ 落头 + 汇总（K3）
3. 字典解析按**四级回退** `PROGRAM_VERSION → PROGRAM → PRODUCT → GLOBAL`（V2 / C4）；解析结果写入汇总行快照（K7）；`assertBinDef` 留在本 service（D22）
4. `TestFacade` + controller（§2 的 **Test 侧全部端点**，含作废 `PUT /test/records/{id}/void`：**只软删头表**、原因必填、**不删守卫**，A11 / K16）
5. 记录号 `TR-yyyyMMdd-序号`：用**号段表** `mes_test_record_no_seq` 发号（D11）—— `INSERT INTO mes_test_record_no_seq (seq_day, next_no) VALUES (#{seqDay}, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE next_no = LAST_INSERT_ID(next_no + 1)`，再取 `SELECT LAST_INSERT_ID()`（**取号是两步**：`bump()` 拿影响行数、`lastInsertId()` 拿值；**两步同一事务同一连接，中间不换连接、不开 `REQUIRES_NEW`**，K15 / D21，照现网 `MesLotServiceImpl` 124 行事务、166–167 行）。机制照抄现网 `MesLotNoSeqMapper:15-23`。**禁止** `COUNT(*)+1`、**禁止** `ORDER BY record_no DESC LIMIT 1`（无零填充时 `-9 > -10`）；`uk_record_no` 仍保留作最后兜底

c. **Lot 模块扩展**：Strip 批量登记（一次一批多条，**同事务整体成败**；请求内 `strip_no` 重复**前置拒绝** `LOT_STRIP_DUPLICATE`；**跨请求撞 UK 捕获 `DuplicateKeyException` 转 `LOT_STRIP_DUPLICATE`**，D20 / K14）、查询；客户 Lot 映射登记（跨请求撞 UK 转 `LOT_MAP_DUPLICATE`，D20）、正查、反查。均为主数据操作，**不写 tx_log**（K1）；**不写 `mes_lot.customer_lot`**（K16 / D22）；软删口径按 §3.7（TINYINT + UK 不含 `deleted`，**禁止**手写软删 SQL）。

d. **客诉包延伸（三块，D16 / D18）**：范围 = 包内**全部成员 Lot**；挂在**登记当时的批，拆批不复制**（子批无记录时其键为空列表；父批记录在父批键下，`up`/`both` 方向天然可见）。① `testSummaryByLot`：走 `TestFacade.listRecordsByLots`，每 Lot 最近 **20** 条（`test_time` 倒序）+ 每记录 ≤**50** 档；② `stripsByLot`：走 `MesLotService.listStripsByLots`，每 Lot **200** 条（`seq_no`/`id` 升序，**上限在 SQL 按批截断**）；③ `customerMapsByLot`：走 `listCustomerMapsByLots`，每 Lot **50** 条（`id` 升序，SQL 截断）。失败按块隔离 + WARN；JSON 三键形状 = `Map<lotId, List>`（与 `historiesByLot` 一致）→ Exporter README 增 4 行上限 + 改 2 句（M4）→ FacadeImpl 传入四上限。**禁止**在此路径直连 `mes_test_*` 或新增 mapper 依赖（K2 / P4）。

e. **前端**：`api/test.ts`；`TestPage.tsx`（记录登记表单 + 列表 + 详情；Bin 字典维护区）；`LotsPage` 详情增「测试结果」「Mapping / 条级」两区（按权限码显隐）；路由与侧栏菜单。登记表单提交**必须有重入闸**（同步 ref + 序号，对齐 `EVAL-0001` 的教训）—— 但**前端闸不是架构保证**，服务端守卫表 UK 才是兜底（D19 / K12）。

f. **测试用例**：

- 后端（零外部依赖）：V1 对账（相等通过 / 差一颗即拒）、V2 字典**四级**回退解析（`PROGRAM_VERSION` 优先 + 逐级降级）、V5 档内重复拒绝、**V6 守卫冲突转换**（mock mapper 抛 `DuplicateKeyException` → 断言转 `TEST_RECORD_DUPLICATE` 且整体回滚语义；**禁止先查后插**的反向断言：service 里不得出现判重 SELECT）、K7 快照不随字典漂。放在 `server/src/test`，与现网 12 用例同风格（**注意**：`@JsonTest` 不能加载 `MesApplication`；需要容器时用内嵌最小 `@SpringBootConfiguration`）
- 前端：登记表单重复点击只提交一次（`act()` 内连发 `dispatchEvent`，对齐 `EVAL-0001` 用例手法）
- **反向验证**：把对账校验临时改坏一次，确认用例真的变红（否则是假保护）；再**临时删掉守卫插入调用**，V6 用例必须变红

g. **文档收尾（同会话）**：新建 `docs/模块/测试数据（Test）模块/` 五件套中本切片需要的部分（功能文档 / 接口设计 / 数据库设计 / 已完成功能）；Lot 模块文档登记 Strip 与客户映射；`MES-客诉追溯包接口设计.md` §6.8 三块并入 §6.2 / §6.4；`MES-实施进度与下一步.md` 增 TD-1 行；`INT-0001` 切片表状态；重建 `docs/INDEX.md`（`python .workbuddy/scripts/add_frontmatter.py --reindex`）。

## 6. 测试与验收

**验收标准（可判真假）：**

1. 登记一条合法测试记录（3 档合计 = `total_qty`）→ 200；`GET /test/records/{id}` 返回程序名 / 版本 / 各档颗数与占比
2. `Σ bin_qty != total_qty`（差 1 颗）→ 拒 `TEST_BIN_SUM_MISMATCH`，且 **SQL 复核零落库**（头表与汇总表均无新行）
3. `program_version` 为空 → 拒 `TEST_RECORD_FIELD_REQUIRED`（V3 / A9）
4. 字典中不存在的 `bin_code` → 拒 `TEST_BIN_DEF_NOT_FOUND`（V2）
5. 同一请求内重复 `(bin_type, bin_code)` → 拒 `TEST_BIN_CODE_DUPLICATED`（V5）
6. 同批再登一条（重测）→ 200，两条记录并存；第一条内容**逐字段不变**（K6）
7. 字典改 `bin_name` / `is_shippable` 后 → 历史记录详情仍显示**登记时**的值（K7 / D7）
8. Strip 批量登记后 `GET /lots/{id}/strips` 返回全部；**同请求内重复 `strip_no` → 前置拒 `LOT_STRIP_DUPLICATE`**，且不产生半截数据
9. 客户 Lot 映射：正查返回全部映射；`GET /lots/by-external-lot?no=X` 能反查到内部批（含同一条外部批号映射到多批的情形）
10. 客诉包 `GET /complaint-packages/{id}` 响应含**三键** `testSummaryByLot` / `stripsByLot` / `customerMapsByLot`（形状 `Map<lotId, List>`，范围 = 全部成员）；`export?format=zip` 内 README 出现「每 Lot 测试记录上限: N」等 **4 行上限**（contains 断言，R2-F2 裁定弃行数计数；同步更新 `ComplaintPackageExporterTest` 与 Exporter javadoc 行序，R2-C1）
11. **回归**：`format=json` 除新增三键外，其余键与 CP-6 口径一致；ZIP 仍恰 2 个 entry
12. 权限（**两级码**，C2）：无 `test:view` → `GET /test/records` 403；无 `test:create` → `POST /test/records` 403；无 `test:void` → 作废 403；无 `test:edit-bin` → 改字典 403
13. `mes_tx_log` 与 `mes_lot.status` 在本切片所有操作前后**无变化**（K1）；Hold / Track 相关代码 diff 为空
14. 静态核对：`com.mes.test` 零 `com.mes.hold` / `com.mes.track` / `com.mes.complaint` 引用（K2）；complaint 包零 `mes_test` mapper 引用（P4）；Test 模块**零 `MesLotMapper` 注入**（K16 / D22）；客诉包条/映射零新增 mapper 依赖
15. CI 三闸门全绿：后端 `mvn -B test`、前端 `npx tsc -b && npm test`、文档 reindex 后 `git diff --exit-code docs/INDEX.md`
16. **判重守卫（V6 / D19）**：同 `(lot_id, eqp_key, program_name, program_version, test_time, total_qty)` 同一 10 分钟桶内二次提交 → 拒 `TEST_RECORD_DUPLICATE`，头表与汇总 **SQL 复核零落库**；**换一台设备（`eqp_key` 不同）提交 → 200**（双机并测不得误判）；**无设备（`eqp_key=0`）同桶两次提交同样被拒**；下一桶（10 分钟后）相同载荷可再登（A10）
17. **作废（A11 / K16）**：`PUT /test/records/{id}/void` 原因必填；作废后详情 / 列表不再返回该记录，`mes_test_bin_summary` 随 join 头表一并排除；`record_no` **不复用**；**守卫行不删** —— 作废后同桶原样再登仍拒 `TEST_RECORD_DUPLICATE`
18. **软删口径（C1）**：`mes_lot_strip` / `mes_lot_customer_map` 的 `deleted` 为 **TINYINT**、UK **不含** `deleted`；软删后**同键不可重建**（再登记被前置校验拒并提示改原行）
19. **Bin 字典四级回退（C4）**：同一 `bin_code` 在 `PROGRAM_VERSION` / `PROGRAM` / `PRODUCT` / `GLOBAL` 四级都有定义时**取最精确的一级**；只有粗粒度定义时逐级降级命中
20. **拆批不复制（D18）**：拆批后测试记录留在登记当时的批；子批锚点 + `direction=up/both` 的客诉包内能看到父批 `testSummaryByLot`；`direction=down` 不含父批记录

**验证方式：**

- 后端：`cd server && mvn -o test`（新增用例）；`mvn -o -DskipTests compile`
- 真机：`curl` 打 §2 各接口（取 token 后）→ 核对响应 `msg` 前缀与 HTTP 200；`SQL` 复核零落库（验收 2）
- 前端：`cd web && npx tsc -b && npm test`；页面手测登记 / 详情 / 两区展示
- 文档：reindex 后 `git diff --exit-code docs/INDEX.md`
- 字典停用后查历史：SQL 直查 `mes_test_bin_summary` 快照值（验收 7）

**副作用与并发（必测，不得只测正常路径）：**

| 场景                               | 期望                                                                                                      |
| -------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 双击「保存」                           | 前端重入闸只放行一次；服务端**守卫 UK 兜底** —— 第二次提交拒 `TEST_RECORD_DUPLICATE`（D19 / K12）                                 |
| **两台测试机并行测同一批**（同程序 / 同时间 / 同总量） | **各记一条**（守卫键 `eqp_key` 不同，D19）—— 不得误判为重复提交                                                                  |
| 两个请求同时登记同一 `(lot_id, strip_no)`  | 一行成功，另一行拒 `LOT_STRIP_DUPLICATE`（D20 / K14），不得 500                                                       |
| 两个请求同时登记同一客户映射键                  | 一行成功，另一行拒 `LOT_MAP_DUPLICATE`（D20 / K14），不得 500                                                       |
| 汇总与总量不符                          | 零落库（验收 2）；反复提交不产生幽灵行                                                                                    |
| 并发登记同一 Lot 的两条记录                 | 均成功；无 Lot 级写锁需求                                                                                         |
| 客诉包导出与登记并发                       | 现网隔离级别 = **REPEATABLE READ**（2026-09-23 实测）：导出走 MVCC 快照读，导出期间的新登记在导出事务内**不可见**（一致快照，即期望行为）；导出不加锁、不得阻塞登记 |
| 字典在登记请求进行中被停用                    | 请求内已解析的快照生效；不得出现「头已入库、汇总失败」的半截记录（K3）                                                                    |
| 会话切换 / 抽屉重开时在途请求收尾               | 旧请求返回不得改写新会话的列表与表单状态                                                                                    |

## 7. 回滚方式

**三选一并说明**：采用「**数据回退 + 代码回滚**」组合。

- **代码回滚**：本切片为新增（新表 + 新接口 + 新页面），代码回滚后功能消失，存量模块不受影响（Hold / Track / WIP 零改动 → 无回归风险）。
- **数据回退**：**7 张新表**（Test 侧 5 + Lot 侧 2；守卫与号段无种子）为纯新增，回滚时 `DROP TABLE`（含其权限与菜单种子行）；客诉包新增的三键与 README 行随代码回滚消失，**不需**回退 `mes_complaint_package*` 数据。
- **不需要配置开关**：本切片不引入灰度开关（无自动状态变更风险，K1）。**例外说明**：客诉包既有 `mes.complaint-package.enabled` 仍生效，与本切片正交。

---

## 8. 审查记录（只追加，不改写已批准内容）

> 体例：本轮编号带 `R2-` 前缀（R2-F* = 事实修正 / R2-C* = 绑定实现的约束），与方案 §9 的 F#/C# 编号互不混用。

**2026-10-08 复审（用户更新文档后，对码 + 探针实测）**

R2-F1（事实修正·轻微）：K12 引用的 MP 源码行号偏差 —— `AbstractWrapper#addCondition` 在 **mybatis-plus-core 3.5.9** sources jar 中实为 **466-469 行**（非 467-470）。机制结论实测不变：`eq(R,Object)` → `eq(true,…)` → `addCondition` 无条件追加 `= #{null}`；实施仍按 K12 走 null 分支。

R2-F2（事实澄清·影响验收断言，实施前必须定死）：`ComplaintPackageExporter` README（源码 110-133 行）实际输出 **23 行**（1 标题 + 16 字段行 + CONTAINING 说明 + 文件清单块 3 行 + 尾部说明 2 行）。既有验收口径「20 项行项」**无法从代码唯一推出**（23 − 标题 − 文件清单块 3 行 = 19；23 − 标题 − 尾注 2 行 = 20，两种减法都对得上）。验收 10 的「README 行项数 = 21 项」若按总行数实现断言必然翻车。**实施裁定**：放弃纯计数断言，改为 `readme.lines()` **contains「每 Lot 测试记录上限: N」行** + 既有 contains 断言不回归；M4 的「20→21」仅在文档层面表述。

R2-C1（绑定实现约束）：新增 README 行必须同步两处既有断言/注释，plan §4 表只写了类名未点名位置 —— ① `ComplaintPackageExporter.java:103-104` javadoc 固定行序（K14 口径）；② `ComplaintPackageExporterTest#readmeCarriesRequiredLinesAndFoldsRemark`（164-190 行，contains 断言清单）。漏改其一即假绿。

R2-C2（绑定实现约束）：号段表 `mes_test_record_no_seq` 照抄 `mes_lot_no_seq`（实测结构：`seq_day char(8) PK` + `next_no int`，**无 id、无 deleted、无逻辑删除**）——它是全库软删铁律的**特例**，migrate 脚本禁止顺手加 `deleted` 列或 `@TableLogic`。取号机制源码对照：`MesLotNoSeqMapper.java:15-23`（bump 15-20 / lastInsertId 22-23），与 plan §5.b.5 引用一致。

**本轮实测通过项（2026-10-08 探针/对码证据）：**

| #  | 断言                                                                                     | 证据                                                                                                         |
| -- | -------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| 1  | M1：`sys_permission` MAX(id)=332，340-343 无冲突                                            | 探针 `SELECT MAX(id)`=332，最高 `complaint:contain`=332；实施第一步仍按 K9 复核                                           |
| 2  | 两级权限码先例                                                                                | `alarm:ack` / `carrier:bind` / `complaint:view` 等大量在库                                                      |
| 3  | `mes_lot.customer_lot` 便查冗余列存在                                                         | `SHOW COLUMNS` → varchar(64) YES                                                                           |
| 4  | 6 张新表名零冲突                                                                              | `mes_test%` / `mes_bin%` / `mes_lot_strip%` / `mes_lot_customer%` 均空                                       |
| 5  | M3：`schema.sql` = 每切片**人工同步**（已含 CP 切片 `mes_complaint_package*` 两表，`IF NOT EXISTS` 风格） | 文件实读；实施按人工拼接追加 **7** 表（勘误：原写 6，未计守卫）                                                                 |
| 6  | K11/C1：全库 UK 均不含 deleted                                                               | information_schema 抽查 40 条 UK 全为业务列                                                                        |
| 7  | 隔离级别 REPEATABLE-READ（副作用表断言）                                                           | `@@transaction_isolation` 复测一致                                                                             |
| 8  | K13 乐观锁先例                                                                              | `@Version` 9 处（`MesLot.java:119` 等）；`MybatisPlusConfig.java:17` 乐观锁拦截器已注册                                  |
| 9  | 错误码 `模块_语义` 先例                                                                         | `ComplaintPackageFacadeImpl:66-80` `COMPLAINT_PACKAGE_*` 8 条                                               |
| 10 | `TestFacade` 落位 `com.mes.test.facade` 有先例                                              | `com.mes.history.facade` 子包在网                                                                              |
| 11 | 三上限同路径 + 开关 key                                                                        | `ComplaintPackageFacadeImpl:250`（historyPerLot/holdCap/alarmCap）· `:95-96` `mes.complaint-package.enabled` |
| 12 | 前端挂载点存在                                                                                | `web/src/api/lot.ts`、`web/src/pages/LotsPage.tsx`                                                          |

**结论：无阻断性问题，plan 可提交批准。** ~~批准前需用户确认 R2-F2 的验收断言改法（contains 替代计数）。~~ → **2026-10-08 用户确认「按 F2 改」**：M4 与验收 10 已按 contains 断言改写（见前置核对项 M4 / §6 验收 10），R2-F2 关闭。

---

### R3 同步轮（2026-10-08 · 用户指令「plan 按 D19–D22 同步」，实施启动前）

> 背景：方案于 2026-10-08 增补产品口径 D15–D18 与架构口径 D19–D22（§3.2.1 守卫表等），plan 原文停留在旧口径。本轮为**用户授权的正文修订**（先改 plan 再改码，治理铁律），逐条变更如下，均对齐方案现文：

| 变更 | 旧（plan 原文） | 新（对齐方案） | 依据 |
|------|----------------|----------------|------|
| 表数量 6→**7** | 无守卫表 | `migrate_test.sql` 增建 `mes_test_submit_guard`（7 元 UK、`eqp_key` 无设备写 0 禁 NULL、`window_bucket` 10 分钟桶、无软删无审计）；`schema.sql` 同步 | §3.2.1 / D19 |
| V6 判重 | MP 条件查询判重（先查后插，含 null 分支） | **废除先查后插**：同事务先插守卫行，UK 冲突整笔回滚 + `TEST_RECORD_DUPLICATE` | D19 |
| K12 改写 | 判重 null 分支写法 | 判重走守卫表；MP `eq(col,null)` 教训保留给本模块其他可空查询 | D19 |
| 新增 K14 / K15 / K16 | — | K14 = D20（跨请求撞 UK 转业务码，禁 500）；K15 = D21（取号两步同事务同连接，禁 `REQUIRES_NEW`）；K16 = D22（门面只读 / `assertBinDef` 留 service / 作废只删头 / 零 `MesLotMapper` 注入 / 不写 `mes_lot.customer_lot`） | D20 / D21 / D22 |
| `TestFacade` 收敛 | 4 方法（含 `assertBinDef` / `latestBinSummaryByLot`） | `listRecordsByLots(lotIds, capPerLot)` 返回记录+汇总；校验不上门面 | §4 / D22 |
| Lot 侧批量方法 | 无 | 增 `listStripsByLots` / `listCustomerMapsByLots`（一次 IN，SQL 按批截断，禁循环装内存） | §4 / §7 |
| 客诉包 1 块→**3 块** | 仅 `testSummaryByLot`，README 增 1 行 | 三键 + 范围全部成员 + 拆批不复制（D18）；README **增 4 行上限 + 改 2 句**（文档叙述 20→24，断言仍 contains）；FacadeImpl 传四上限 | D16 / D18 / §7 |
| Strip/映射并发 | 仅请求内前置拒绝 | 请求内前置拒绝保留 + **跨请求撞 UK 转 `LOT_STRIP_DUPLICATE` / `LOT_MAP_DUPLICATE`**（新错误码） | D20 |
| 作废口径 | 头 + 汇总一并软删 | **只软删头表**（汇总 join 头消失）；守卫行不删，同桶原样再登仍拒 | A11 / D19 / D22 |
| 验收与用例 | V6 null 分支反向验证 | 验收 16/17 重写（守卫 UK / 换设备 / 下一桶 / 作废不删守卫）；新增验收 20（D18 拆批）；用例改 mock `DuplicateKeyException`；反向验证改为「删守卫插入用例变红」 | D19 / D18 |

**未变更项确认**：M1（340–343，K9 已复核）、M2、M3、K1–K11、K13、权限 4 码、§2 端点路径、负面清单、回滚策略（表数更正为 7）均保持原样。

**R3 结论：plan 与方案（含 2026-10-08 补口径）一致，可以开工。** 剩余前置：K9 已复核（MAX=332，340–343 可用，2026-10-08）。

---

### R4 实施审查轮（2026-10-09 · 用户完成步骤 a/b 后，架构师视角审查）

> 审查对象：commit `8060574`（步骤 a 建表）+ `e56959c`（步骤 b Test 模块）。方法：逐文件对码到 plan/方案条款 + DB 探针实测落库结果 + `mvn -o -DskipTests compile`（EXIT=0）。

**实测通过项（对码证据）：**

| # | 条款 | 证据 |
|---|------|------|
| 1 | K8 分层 | `com.mes.test.{controller,dto,entity,facade,mapper,service,service.impl,vo}` 与现网一致 |
| 2 | 表结构 7 张 + schema.sql | migrate_test.sql / migrate_lot_pkg.sql 逐字段对上方案 §3.1–§3.5/§3.2.1；探针实测 7 表已建、UK 全不含 `deleted`、守卫/号段表无软删；schema.sql 7 表 DDL 齐 |
| 3 | M1/K9 + 种子 | 权限 340–343 已落库（列名与表结构实测匹配）；角色绑定 9 行正确（admin=1 全量 / process_eng=3 全量 / supervisor=4 只读 340），无重复授权；bin_def 演示种子 4 行在 |
| 4 | D19/K12 守卫 | 无先查后插；同事务先插守卫（Impl:95-103）；`window_bucket` SQL 内算（GuardMapper:14）；`DuplicateKeyException`→`TEST_RECORD_DUPLICATE`（Impl:306-315、318-330） |
| 5 | K15/D21 取号 | bump+lastInsertId 同一 `@Transactional` 同连接（Impl:73、105、333-338）；机制照抄号段先例 |
| 6 | K13 乐观锁 | `@Version`（MesBinDef:51）+ `updateById` 判 `n>0`（BinDefServiceImpl:74-80），先例 `MesEdcPlanServiceImpl` 同款 |
| 7 | scope 一致性 | `assertScope`（BinDefServiceImpl:119-128）四档全对上，拒 `TEST_BIN_SCOPE_INCONSISTENT` |
| 8 | V1–V5 | 校验齐全且顺序正确（Impl:75-93）；V1 只对 HARD 求和（:238-246）；零落库由 `@Transactional(rollbackFor)` 保证 |
| 9 | K16/D22 | Facade 只读仅 `listRecordsByLots`（TestFacadeImpl:20-22）；`assertBinDef` 留 service；零 `MesLotMapper` 注入（注入 `MesLotService`，与现网 complaint→MesLotService 先例一致）；作废只软删头表（Impl:180-194）；零 `customer_lot` 写入 |
| 10 | V2/C4 四级回退 | `rank` 4/3/2/1（Impl:286-303）+ 一次 IN 查询内存挑档（:249-265） |
| 11 | SQL 按批截断 | `selectRecentByLots` 用 `ROW_NUMBER() OVER (PARTITION BY lot_id …)`（Mapper:15-29），MySQL 8.4 支持 |
| 12 | 汇总实体 | 无 BaseEntity/无软删（MesTestBinSummary），守卫同（MesTestSubmitGuard）；头表 `@TableLogic` 自动过滤 |

**发现项（均不阻断，按处置分类）：**

| # | 级别 | 内容 | 处置 |
|---|------|------|------|
| R4-C1 | 建议改进 | **作废记录的档位过滤是「应用层保证」而非「SQL 保证」**：方案 A11 要求「汇总查询一律 join 头表过滤 `deleted`」；当前 `loadBins`（Impl:357-373）只按 `record_id IN` 查，安全性依赖所有入口都先查未删头（现状 5 个入口均满足，含 `selectRecentByLots` 的 `deleted=0`）。将来新增直查 summary 的调用点可能带出已作废记录的档位 | d/e 步骤不动；在 f 步骤为 `loadBins` 补注释明示前提，或改 JOIN；记入验收 17 复核 |
| R4-C2 | 建议改进 | **作废审计留痕较轻**：原因拼进 `remark`（Impl:187-193），无「作废人」字段（`update_time` 有、`update_by` 无）。方案 A11 未强制字段 → 合规，但审计链弱 | TD-1 接受；TD-2/增强时可加 `void_by`/`void_time`/`void_reason` 列 |
| R4-C3 | 风格 | **`sys_role_permission` 硬编码小 id**（1154-1157/1349-1352/1433）：该表现网为雪花 id（MAX≈2.08e18）。实测无冲突、内容正确；但若未来 id 被占，`ON DUPLICATE KEY UPDATE role_id=VALUES(role_id)` 会**篡改他人行** | 脚本头加注释警示；后续脚本角色绑定改用 (role_id, permission_id) 判重 |
| R4-F1 | 事实澄清 | `rank()` 空产品边界：`lot.productCode` 为空串时 PRODUCT 档（rank 2）与 GLOBAL 档（rank 1）的判定条件重叠，同一行会被判 rank 2。行为等价（选中的还是那一行），仅语义歧义 | 不改码；f 步骤用例覆盖空 productCode 即可 |
| R4-F2 | 实施取舍待定 | 每记录 Bin 档上限 **50 写死在 service**（`BIN_CAP`，Impl:62），而方案 §7 要求该上限属 FacadeImpl 四上限之一（配置 key 或类内常量） | **d 步骤实施时二选一**：提参或引用常量，并写明 |
| R4-F3 | 信息 | `create()` 强依赖登录态（`StpUtil.getLoginIdAsLong()`，Impl:120），与 `source_type=API/FILE` 的未来接入方式不匹配 | TD-1 手录为主可接受；接入 API/文件时改显式传操作者 |
| R4-F4 | 风格 | `MesTestSubmitGuard.id` 无 `@TableId` 注解，靠全局 `assign_id` + 手动 `IdWorker.getId()` 双保险（Impl:96） | 可加 `@TableId(type = ASSIGN_ID)` 明示，不强制 |
| R4-F5 | 性能备忘 | `page()` 的 `lotNo`/`programName` 用 `%like%`（Impl:152-160），量大走不了索引 | 记录即可；测试数据量大前无需动 |

**R4 结论：步骤 a/b 实现与 plan/方案一致，无阻断问题，可进入步骤 c。** C1/C2/F2 在后续步骤处置。

---

### R5 实施审查轮（2026-10-09 · 用户完成步骤 c 后，架构师视角审查）

> 审查对象：工作区未提交改动（`MesLotServiceImpl` +200 行、`MesLotController` +41 行、新增 2 实体 / 2 mapper / 2 DTO / 2 VO）。方法：对码到 plan c 步骤 / K14 / D20 / D22 + DB 探针核对两表实际列 + `mvn -o -DskipTests compile`（EXIT=0）。

**实测通过项：**

| # | 条款 | 证据 |
|---|------|------|
| 1 | §2 端点与权限码 | 5 端点全对上：`POST/GET /lots/{id}/strips`、`POST/GET /lots/{id}/customer-maps`、`GET /lots/by-external-lot`；权限 `lot:edit` / `lot:list` 与 plan §2 一致（Controller diff） |
| 2 | D20/K14 | 请求内前置拒绝（`seen.add` → `STRIP_DUP_REQ`，Impl:599-605）+ 跨请求 `DuplicateKeyException` → `LOT_STRIP_DUPLICATE` / `LOT_MAP_DUPLICATE`（Impl:621-627、676-680），无 500 泄漏 |
| 3 | K1 不写 tx_log | 新增方法零 Track/Hold/tx_log 调用，纯主数据 |
| 4 | D22 不写 customer_lot | c 新增路径零 `customer_lot` 写点（既有 `update()` 的写入是存量行为，非本切片引入） |
| 5 | 软删口径 | 两实体 `@TableLogic`；**`MesLotCustomerMap` 未继承 BaseEntity、自声明 `createTime`/`deleted`，与表列（无 `update_time`）精确对齐**（探针实测列清单）——避免了「BaseEntity 自动填充 update_time → Unknown column」的坑 |
| 6 | SQL 按批截断 | 两个 `selectByLots` 均 `ROW_NUMBER() OVER (PARTITION BY lot_id …)`；strip 的 `ORDER BY seq_no IS NULL, seq_no ASC` 把空序号沉底（Mapper:21-22），细节到位 |
| 7 | 反查语义 | `findLotsByExternalLot` 不过滤 lot 状态（召回主路径合理）、软删 map 自动排除、按首次映射序保序（Impl:706-727） |
| 8 | 上限默认值 | `listStripsByLots` 默认 200 / `listCustomerMapsByLots` 默认 50，与方案 §7 一致 |

**发现项：**

| # | 级别 | 内容 | 处置 |
|---|------|------|------|
| R5-C1 | 建议改进 | **预检查查不出已软删同键行**：`createStrips`/`createCustomerMap` 的前置校验走 MP `@TableLogic`（自动 `deleted=0`），软删同键实际由 **UK 兜底**拒绝——行为等价（同码同文案「请改原行」），但 Impl:85-86 常量注释写「含已软删同键」与实现不符；plan 验收 18 的「被前置校验拒」实际走的是 UK 路径 | 改注释；f 步骤验收 18 用例注明「软删同键走 UK 兜底」 |
| R5-C2 | 提请用户决策 | **Strip/映射登记无 lot 状态校验**：Test 登记拒 `merged`/`scrapped`（V4），但给已报废/已合批批登记 Strip / 映射当前放行——方案未要求，口径不一致 | 二选一：TD-1 对齐 V4 加校验，或显式接受（记遗留，TD-2 统一） |
| R5-F1 | 信息 | Strip 批量登记为循环单条 insert（N 条 = N 次往返），同事务整体成败保证正确；量大时可换批量插入 | 记录即可 |
| R5-F2 | 信息 | `listStrips` / `listCustomerMaps`（Lot 详情页）全量无上限——方案未限定，单批 Strip 量级可控；客诉包路径已有 cap | 记录即可 |

**R5 结论：步骤 c 实现与 plan/方案一致，无阻断问题，可进入步骤 d。** R5-C2 待用户拍板；R4-C1（loadBins 注释/JOIN）与 R4-F2（BIN_CAP 上限归属）仍在 d/f 步骤处置清单上。

---

### R6 实施审查轮（2026-10-09 · 用户完成步骤 d 后，架构师视角审查）

> 审查对象：工作区改动（Assembler +118 / Exporter +23 / FacadeImpl +4 / VO +9 / TestFacade 签名 + 测试 2 文件）。另：R5-C2 已由用户拍板「对齐 V4」并随 c 提交（`732801d`，已合批/已报废拒登记 Strip/映射）。方法：对码 + `mvn -o test -Dtest='ComplaintPackage*'`（12 用例全绿，BUILD SUCCESS）。

**实测通过项：**

| # | 条款 | 证据 |
|---|------|------|
| 1 | D16 三块 | Assembler 增 `loadTests` / `loadStrips` / `loadCustomerMaps`（一次 IN），VO 增三键（形状 `Map<lotId, List>`，成员键先占空桶，与 `historiesByLot` 同形） |
| 2 | K2/P4 | 测试走 `TestFacade`、Strip/映射走 `MesLotService`，**零** mes_test_* 直连、零新增 mapper 依赖 |
| 3 | 失败隔离 | 每块独立 try/catch + WARN（`block=testSummaryByLot` 等），失败该块空、其余块不受影响（R11 口径） |
| 4 | 四上限同源 | `TEST_RECORD_CAP=20 / BIN_CAP=50 / STRIP_CAP=200 / CUSTOMER_MAP_CAP=50` 为 Assembler 类内常量 + accessor；FacadeImpl 传四上限与既有三上限同路径 —— **R4-F2 关闭**（`binCap` 经 `TestFacade.listRecordsByLots(lotIds, capPerLot, binCap)` 提参，不再写死 service） |
| 5 | M4 / R2-C1 | README **增 4 行上限** + 空块说明句、口径句两处改写，与方案 §7 逐字一致；Exporter javadoc 行序注释同步（「三个上限→七个上限」）；`ComplaintPackageExporterTest#readmeCarriesRequiredLinesAndFoldsRemark` contains 断言同步 4 行 + 2 句 |
| 6 | D18 | 拆批不复制由「记录挂登记时的批 + 成员桶」自然满足；子批无记录即空列表；`direction=up/both` 时父批在成员里即带出 |
| 7 | Exporter 零依赖 | 仍只吃 VO + 入参（javadoc 更新），`toZipBytes` / `toReadme` 签名扩为七上限 |
| 8 | 回归 | `ComplaintPackageFormatWhitelistTest` 3 + `AssemblerCapsTest` 3（新增 4 断言）+ `ExporterTest` 6 全绿；`innerJsonIsByteIdenticalToJsonExport` 保证 ZIP 内 JSON 与 JSON 导出字节一致（验收 11 的结构不变性由此覆盖） |

**发现项：**

| # | 级别 | 内容 | 处置 |
|---|------|------|------|
| R6-F1 | 备注 | 「每记录 50 档」默认值存在**两处**：`Assembler.BIN_CAP`（同源主值）与 service 内防御默认（`binCap < 1 ? BIN_CAP`）。当前同值；若将来调整只改一处会漂移 | 后续调上限时两处同步；或收敛为单源 |
| R6-C1 | 遗留提醒 | R4-C1（`loadBins` 的 deleted 过滤靠应用层、建议补注释/JOIN）**本步骤未处理**——按计划属 f 步骤 | f 步骤处置清单 |

**R6 结论：步骤 d 实现与 plan/方案一致，测试全绿，无阻断问题，可进入步骤 e（前端）。** 待办链：f 步骤处置 R4-C1 + R4-F1（空 productCode 用例）+ R5-C1（注释修正）+ R6 验收项。

---

### R7 实施审查轮（2026-10-09 · 用户完成步骤 e 后，架构师视角审查）

> 审查对象：`web/src/api/test.ts`（新增）、`TestPage.tsx`（新增）、`LotTestMappingSection.tsx`（新增）、`LotsPage.tsx` / `App.tsx` / `AdminShell.tsx` / `api/lot.ts`（改）。方法：对码 plan e 步骤 / A5 / C2 裁决 + `npx tsc -b`（EXIT=0）+ `npm test`（2 用例通过）。

**实测通过项：**

| # | 条款 | 证据 |
|---|------|------|
| 1 | §2 端点封装 | `api/test.ts` 8 个函数与后端端点一一对应（records 分页/详情/登记/作废、by-lot 摘要、bins 三维护） |
| 2 | 重入闸（EVAL-0001 口径） | 登记表单 `createGate` ref + 序号（TestPage:141-142、240-266、300-303）；Strip/映射各有 `stripGate`/`mapGate`（LotTestMappingSection:46/50/95/119）；提交中 Drawer 关闭被禁 |
| 3 | 权限门禁 | `test:view/create/void/edit-bin` 四码 `hasPermission` 精确匹配（perm_code 不透明串口径 ✓）；字典查看并入 `test:view`（C2 裁决）✓ |
| 4 | A5 现场台零改动 | `LotTestMappingSection` 仅 `isAdmin`（`/app` 前缀）时渲染；现场台路径 Drawer 保持原 `width=560` 分支 |
| 5 | Lot 详情两区 | 「测试结果」走 `/test/summary/by-lot`（Test 侧自有端点，A7 ✓）；「Mapping / 条级」读写齐；`writable` 前端挡 merged/scrapped（与后端 V4 对齐双保险） |
| 6 | Bin 表单 scope 联动 | 按 `binScope` 清空不适用维度（TestPage:365-370），与后端 `assertScope` 口径一致；编辑携带 `version`（乐观锁闭环） |
| 7 | 路由与侧栏 | `/app/test` 注册（App.tsx）；`AdminShell` iconMap 增 `clipboard`，与权限种子 `icon='clipboard'` 对上（菜单图标可渲染） |
| 8 | 构建验证 | `npx tsc -b` EXIT=0；`npm test` 2 用例通过 |

**发现项：**

| # | 级别 | 内容 | 处置 |
|---|------|------|------|
| R7-F1 | 建议补齐 | **`submitBin` 缺同步重入闸**：四处提交里唯独 Bin 保存没有 ref 闸（只有异步 `savingBin` state）——双击窗口虽小且有 UK/乐观锁兜底（危害仅是多一条报错），但与 EVAL-0001 重入闸口径不一致 | e 步骤补一个 ref 闸（几行），或留 f 步骤与前端重入用例一起补 |
| R7-F2 | 低 | **批次号解析用 keyword 模糊搜索**（`listLotsApi({keyword, size:20})` 后 find 精确相等）：同关键字命中 >20 条且精确条不在前 20 时会误报「找不到该批次号」拦住登记 | 后续加按 `lotNo` 精确查询端点；不阻断 |
| R7-F3 | 低 | **前端对账口径与提交口径不一致**：`hardSum` 对全部行求和，提交时 `filter` 掉 binCode 为空的行——填了颗数没填档号的行会让前端显示「合计对得上」但后端拒 `TEST_BIN_SUM_MISMATCH` | 求和改为只算将提交的行 |
| R7-F4 | 信息 | 手录不解析 `eqpId`（只存 `eqpCode`）→ V6 守卫 `eqp_key` 恒 0：手录场景「同载荷同桶即重复」语义正确；设备选择器（解析 eqpId）后置，API 层验收 16 用 curl 验证 | 记录即可 |
| R7-F5 | 信息 | 「测试结果」区只展示最近 8 条并链接到 /app/test —— 摘要 UX 合理（端点本就全量） | 记录即可 |

**R7 结论：步骤 e 实现与 plan/方案一致，tsc/测试全绿，无阻断问题，可进入步骤 f（测试用例 + 反向验证 + 真机验收）。** f 步骤处置清单累计：R4-C1（loadBins 注释/JOIN）· R4-F1（空 productCode 用例）· R5-C1（注释修正）· R7-F1（submitBin 闸，或即时补）· R7-F3（求和口径）· 前端重入用例。

---

### R9 真机验收轮（2026-10-09 · 后端真环境 curl + SQL 复核，验收通过后 plan 流转 done）

> 环境：用户 8080 实例（f 之前代码）打主清单；发现缺陷修复后另起 8081/8082 第二实例（同库不重启用户实例）补验客诉包，验证完即清理。
> 结果汇总：**主清单 PASS 29 / 30**（1 条 FAIL 为验收脚本自身的类型比较 bug——反查返回的 lotId 是字符串，修正后确认实际 PASS）；客诉包补验 **7 项全 PASS**。

**验收明细（可判真假）：**

| 验收项 | 结果 | 证据 |
|--------|------|------|
| 1 合法登记 200 + 详情程序/版本/各档占比 | ✅ | `TR-20261009-001`，3 档含 ratio |
| 2 差 1 颗拒 + SQL 复核零落库 | ✅ | `TEST_BIN_SUM_MISMATCH`；record/summary 计数 0 增长 |
| 3 版本空拒 V3 | ✅ | `TEST_RECORD_FIELD_REQUIRED` |
| 4 未知档拒 V2 | ✅ | `TEST_BIN_DEF_NOT_FOUND` |
| 5 请求内重复档拒 V5 | ✅ | `TEST_BIN_CODE_DUPLICATED` |
| 6 重测并存 + 首条逐字段不变 | ✅ | 第二条 200；首条详情与登记时快照全等 |
| 7 字典改名后历史快照不漂（K7） | ✅ | Bin1 改名后历史记录仍显示「良品（最高档）」，随后还原 |
| 8 Strip 批量登记/查询 + 同请求前置拒 + 跨请求 UK 转 D20 | ✅ | 3 条登记；`LOT_STRIP_DUPLICATE` 两种路径均正确 |
| 9 映射登记 + 同键拒 + 同号异向可登 + 正查 2 条 + 反查命中 | ✅ | `LOT_MAP_DUPLICATE`；INBOUND/OUTBOUND 各一条；反查含本批 |
| 10/11 客诉包三键 + ZIP README | ✅ | build 200（`CP-20261009-*`）；GET 三键齐全且本批分档 4 条、Strip 3 条、映射 2 条；ZIP 恰 2 entry；README 四行上限 + D18 口径句 + 空块说明句全中 |
| 16 V6 守卫真 UK | ✅ | 同载荷同桶拒；换 `eqpId` 200；无设备（eqp_key=0）同桶二次拒 |
| 17 作废 A11 | ✅ | 200；详情/列表不再返回；**作废不删守卫**（同载荷同桶再登仍拒）+ 守卫计数不减少 |
| 13 K1 状态真相不动 | ✅ | 全程 `mes_tx_log` 330→330、`mes_lot.status` 不变 |
| 12 权限 403 | ⏸ 未打 | 无可用低权账号凭据；`@SaCheckPermission` 语义保证，后续有低权账号补打 |
| 19 四级回退真机 | ⏸ 未打 | 所选批 `productCode` 为空，无法构造 PRODUCT/PROGRAM_VERSION 档；逻辑已由 R8 单测 `v2_picksProgramVersionOverLowerScopes` 等覆盖 |
| 20 拆批 D18 真机 | ⏸ 未打 | 需 split 数据；README 口径句已真机验证 |

**验收期缺陷（已修复 + 回流）：** 真机首打客诉包 build 500 → 根因 `MesTestRecordMapper.selectRecentByLots` 注解动态 SQL 缺 `<script>`（mock 单测盲区，详见 `EVAL-0003`）。修复后第二实例复验全 PASS；**防复发回流点已写入 `AGENTS.md` §5**。

**R9 结论：验收通过（未打项 3 条已登记补做条件），plan 流转 `done`。** ⚠️ 修复代码在工作区未提交，且 8080 用户实例仍运行修复前代码——**需重启后端 + 提交后修复才对开发环境生效**。

---

### R8 实施审查轮（2026-10-09 · 步骤 f 测试用例 + 反向验证）

> 审查对象：`MesTestRecordServiceImplTest`（新增 11 例）、`TestPage.test.tsx`（重入 1 例）、`loadBins` / Strip·映射常量注释、`TestPage` R7-F1/F3 补丁。方法：对码 plan f + 反向探针 + `mvn -o test` + `npm test`。

**实测通过项：**

| # | 条款 | 证据 |
|---|------|------|
| 1 | V1 对账 | `v1_hardSumEqualsTotal_passes` / `v1_hardSumOffByOne_rejects` |
| 2 | V2 四级回退 | `v2_picksProgramVersionOverLowerScopes` / `fallsBackToGlobal` / `missingDef` |
| 3 | V5 档内重复 | `v5_duplicateBinCodeInRequest_rejects`；异类型同码允许 |
| 4 | V6 守卫转换 | `v6_guardDuplicate_*`：DuplicateKey → `TEST_RECORD_DUPLICATE`，头/汇总/号段零 insert；`verifyNoMoreInteractions` 守卫 mapper |
| 5 | K7 快照 | `k7_summarySnapshotsBinNameAndShippableAtCreate` 捕获 insert 时 binName/isShippable |
| 6 | R4-F1 | `r4f1_emptyProductCode_productAndGlobalBothRank2`：空产品时二者同为 rank 2 |
| 7 | R4-C1 / R5-C1 | `loadBins` javadoc 明示前置；Strip/映射常量注释改为「活行前置 + UK 兜底」 |
| 8 | 前端重入 + R7 | `TestPage.test.tsx` 连点只 1 次 create；`binGate`；`hardSum` 只计有档号硬档 |
| 9 | 反向验证 | ① 临时 `true \|\| hard == totalQty` → `v1_hardSumOffByOne` **Failures:1** 后还原；② 临时注释 `insertGuard(guard)` → `v6_guardDuplicate_*` **Failures:1** 后还原 |

**R8 结论：步骤 f 用例与反向验证通过，审查债 R4-C1 / R4-F1 / R5-C1 / R7-F1 / R7-F3 已关。** 可进入步骤 g（文档收尾）。真机 curl 验收仍建议在后端起来后补打。

---

### R9 文档收尾（2026-10-09 · 步骤 g）

> 交付：Test 四件套（功能 / 接口 / 库表 / 已完成）；Lot 功能·库表·已完成补 Strip/映射；客诉 §6.8 并入 §6.2/§6.4；进度表 / INT-0001 切片状态 ✅；`docs/INDEX.md` reindex。

**R9 结论：TD-1 步骤 a–g 闭环。** 下一步主线 = TD-2 规格。

### 勘误（2026-10-08，R3 之后）

| 项 | 改法 |
|----|------|
| K2 | 「客诉包只经 TestFacade」改为：测试数据走 `TestFacade`，条与映射走 `MesLotService` 只读方法 |
| K6 | 「不加唯一约束」限定为**记录表**不加业务唯一键；守卫表 UK 仍在 |
| K11 / 表数 | 7 张 = Test 5 + Lot 2。无 `deleted` 的是汇总、守卫、号段，实体不继承 `BaseEntity` |
| 文首验收范围 | 对齐 INT §2：验收 1 的批级部分、验收 2、验收 4 的三块、验收 5 |
| INDEX | plan 已是 `approved`，总账从 `draft` 改过来 |

---

**批准记录**：2026-10-08，批准人：用户（guocong）口头批准于会话；R2-F2 断言改法已确认。本提交即审计轨迹，批准后按 §5 步骤 a–g 实施。
