---
type: plan
module: Test
status: draft
slices: [TD-1]
aligns: [MES-封测测试数据与Bin回流方案.md, INT-0001-封测颗级追溯与测试数据回流.md]
updated: 2026-09-24
---

# TD-1 计划 — Strip 条级 + 测试记录与 Bin 汇总回流

> 对齐：`MES-封测测试数据与Bin回流方案.md` §2（层级模型）· §3（数据，含 §3.7 软删口径）· §4（Facade）· §5（接口与 V1–V6 校验）· §7（客诉包延伸）· §8（A1–A11）· §9（P1–P9 / D1–D14）
> 上游意图：`INT-0001-封测颗级追溯与测试数据回流.md`（已采纳；本切片对应验收 2、4 的分档部分，与验收 5）
> 状态：**draft（未批不动码）** —— 治理铁律 A2
> **实施前置核对项（动手前必须实测，禁止凭记忆分配）**
> - **M1 权限 id 段**：只读探针实测现网 `sys_permission` MAX(id) = **332**（2026-09-23）；本切片拟用 **340–343**（4 条），实施第一步再跑一次 `SELECT MAX(id) FROM sys_permission` 复核，冲突则整体后移（禁止与既有冲突）。
> - **M2 字典空档取值口径（已定稿）**：规格 §3.1 已定 —— GLOBAL / PRODUCT / PROGRAM 档的**不适用维度一律填 `''`**（非 NULL；MySQL 唯一索引不收 NULL，用 `''` 五元 UK 才生效）。实施**照规格执行**，不再论证；若发现现网有更优先例，先回来改规格再动码。
> - **M3 `schema.sql` 同步方式**：现网 `schema.sql` 为新库全量路径。实施前确认它是**人工拼接**还是**由 migrate 脚本汇总生成**，按其既有方式追加新表，禁止自创第三种做法。
> - **M4 客诉包 README 行项数**：CP-6 现验收为「20 项行项齐」。本切片新增一行，相关验收断言须同步改为 21 项（见 §6）。

## 1. 目标与边界

**一句话：** 让测试分档数据第一次进系统并与批次绑定——管理端登记测试记录（批 / 阶段 / 程序版本 / 设备 / 时间 / 总颗数 + 各 Bin 颗数），Lot 侧登记 Strip 条级与客户 Lot 映射，客诉追溯包的证据里能出现「这批分了几档、各多少颗」。

**做：**

- **Test 模块（新增 `com.mes.test`）**：`mes_bin_def` 字典（含 `program_version` 维度）+ `mes_test_record` + `mes_test_bin_summary` + 号段表 `mes_test_record_no_seq`；登记接口一次提交记录与汇总（同事务）；**作废接口**（A11）；查询接口；`TestFacade`（只读）。
- **Lot 模块扩展**：`mes_lot_strip`（条级身份登记与查询）、`mes_lot_customer_map`（来料 ↔ 出货映射登记与正 / 反查）。
- **客诉包延伸**：装配块新增 `testSummaryByLot`（经 `TestFacade` 只读）；ZIP `README.txt` 封面增一行。
- **管理端**：新增「测试数据」页（记录登记 / 查询 + Bin 字典维护）；Lot 详情增「测试结果」区与「Mapping / 条级」区。
- **权限与菜单**：`test:view` / `test:create` / `test:void` / `test:edit-bin`（**全两级**，C2 裁决；挂管理端，现场台不挂 —— 见方案 §5）。
- **测试**：后端零依赖用例（对账 V1、字典**四级**回退 V2、汇总去重 V5、**判重窗口 V6 含 null 分支**）+ 前端重入用例；重建 `docs/INDEX.md`。

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

| # | 约束 | 说明 |
|---|------|------|
| K1 | 状态真相不动 | 本切片不写 `mes_tx_log`、不改 `mes_lot.status`、不调 Hold / Track 写路径（A1 / A6） |
| K2 | 跨模块只走 Facade | 客诉包只经 `TestFacade` 读；Test 模块不引 Hold / Track / complaint 依赖（A7 / P4） |
| K3 | 记录与汇总同事务 | 一次请求内记录头 + 全部汇总行整体成功或整体失败（A8）；禁止先插头再逐行插且中途可失败 |
| K4 | 写入即对账 | `Σ bin_qty(HARD) == total_qty`，不等则拒且**零落库**（V1 / D6 / A3） |
| K5 | 程序版本必填 | `program_name` / `program_version` / `test_time` 非空（V3 / A9） |
| K6 | 允许重测 | 同批多条记录合法，**不覆盖**、不加唯一约束（A10） |
| K7 | 快照不可漂 | 汇总行的 `bin_name` / `is_shippable` 为登记时快照；字典后改**不影响**历史（D7） |
| K8 | 包结构沿用现网 | 新模块用 `controller / dto / entity / mapper / service / service.impl / vo`（实测 lot / hold / complaint 一致）；`AGENTS.md` §3 已于 2026-09-23 同步为现网分层，二者一致 |
| K9 | 权限 id 先复核 | 见 M1；禁止直接照抄本 plan 的数字落库 |
| K10 | 错误口径 | 业务码在 `msg` 前缀（现网 `GlobalExceptionHandler` 恒 `code=500`）；前端取 `msg` 展示 |
| K11 | 软删口径与全库一致 | `deleted` 一律 **TINYINT**、**UK 不含 `deleted`**、软删走 MP `@TableLogic`；**禁止手写软删 SQL**；「撤销后重建同键」用 `PUT` 改行表达（C1 / §3.7） |
| K12 | 判重 null 分支 | V6 判重键含**可空** `eqp_id`：必须 `.eq(eqpId != null, MesTestRecord::getEqpId, eqpId)`，为空走 `.isNull(...)`；**禁止** `.eq(col, null)` —— MP 不跳过条件（源码 `AbstractWrapper:467-470`），会生成 `= NULL` 恒不成立 → **判重静默失效**（C3 / §5） |
| K13 | 乐观锁不手写 | Bin 字典 PUT 用 MP `@Version` + `updateById` 判影响行数，冲突即拒；**禁止手写 version 条件更新**（F2） |

## 2. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| POST | `/test/records` | `test:create` | 登记记录 + Bin 汇总（同事务，K3） |
| GET | `/test/records` | `test:view` | 分页（`lotId` / `lotNo` / `stage` / `programName` / 时间范围） |
| GET | `/test/records/{id}` | `test:view` | 详情（含汇总明细） |
| PUT | `/test/records/{id}/void` | `test:void` | 作废记录（原因必填；头 + 汇总一并软删，`record_no` 不复用；A11） |
| GET | `/test/summary/by-lot/{lotId}` | `test:view` | 按批摘要（Lot 详情页消费）；**Test 侧自有 controller，Lot 侧不代理此端点**（A7，避免 Lot 直连 `mes_test_*`） |
| GET | `/test/bins` | `test:view` | 字典查询（可筛 `productCode` / `programName` / `programVersion` / `binType`） |
| POST | `/test/bins` | `test:edit-bin` | 字典新增 |
| PUT | `/test/bins/{id}` | `test:edit-bin` | 字典修改 / 停用（**乐观锁走 MP `@Version`**，影响行数为 0 即冲突 → `TEST_BIN_DEF_CONFLICT`；K13） |
| POST | `/lots/{id}/strips` | `lot:edit` | Strip 批量登记（同事务整体成败；请求内 `strip_no` 重复**前置拒绝** `LOT_STRIP_DUPLICATE`，不靠 UK 报错兜底） |
| GET | `/lots/{id}/strips` | `lot:list` | Strip 列表 |
| POST | `/lots/{id}/customer-maps` | `lot:edit` | 客户 Lot 映射登记（INBOUND / OUTBOUND） |
| GET | `/lots/{id}/customer-maps` | `lot:list` | 正查（本批 → 外部批号） |
| GET | `/lots/by-external-lot` | `lot:list` | 反查（外部批号 → 内部批，召回主路径） |

新增错误码（命名沿用现网 `模块_语义`）：`TEST_BIN_SUM_MISMATCH`（V1）、`TEST_BIN_DEF_NOT_FOUND`（V2）、`TEST_RECORD_FIELD_REQUIRED`（V3）、`TEST_BIN_CODE_DUPLICATED`（V5）、**`TEST_RECORD_DUPLICATE`**（V6 判重窗口）、**`TEST_BIN_SCOPE_INCONSISTENT`**（`bin_scope` 与三维字段不一致）、**`TEST_BIN_DEF_CONFLICT`**（字典乐观锁冲突）、**`LOT_STRIP_DUPLICATE`**（同请求内 `strip_no` 重复）。复用现网 Lot 404 / 状态错码。

## 3. 表变更

| 脚本 | 内容 |
|------|------|
| `server/src/main/resources/db/migrate_test.sql`（新增） | 建 `mes_bin_def`（含 `program_version`，**UK 5 元**、`bin_scope` **4 档**）/ `mes_test_record` / `mes_test_bin_summary`（方案 §3.1–§3.3）+ **号段表 `mes_test_record_no_seq`**（D11）；`mes_bin_def` 演示种子（GLOBAL 的 HARD Bin1–Bin4）；权限 4 条（M1 复核后定 id）+ 管理端菜单 |
| `server/src/main/resources/db/migrate_lot_pkg.sql`（新增） | 建 `mes_lot_strip` / `mes_lot_customer_map`（方案 §3.4–§3.5）；两表 `deleted` 一律 **TINYINT**、**UK 不含 `deleted`**（C1 / §3.7） |
| `server/src/main/resources/db/schema.sql`（改） | 按现网既有方式同步上述 **6 张表**（5 业务表 + 1 号段表）（M3） |

**零改动**：`mes_lot`（含既有 `customer_lot` 保留为便查冗余）、`mes_lot_genealogy`、`mes_tx_log`、`mes_hold*`、`mes_route*`、`mes_complaint_package*` 的**结构**均不变。

## 4. 影响的 Facade 与模块

| 项 | 变更 |
|----|------|
| `TestFacade`（新增，`com.mes.test.facade`） | `listRecordsByLots(Collection<Long>, int cap)` / `listBinSummaryByRecord(Long)` / `latestBinSummaryByLot(Long)` / `assertBinDef(...)`。只读为主；**唯一**对外读入口 |
| `MesLotService`（扩展） | `listStrips(lotId)` / `listCustomerMaps(lotId)` / `findLotsByExternalLot(no)` |
| `ComplaintPackageAssembler`（改） | 增装配块 `testSummaryByLot`（走 `TestFacade`，一次 IN，失败按块隔离 + WARN） |
| `ComplaintPackageExporter`（改） | README 封面增一行「每 Lot 测试记录上限: N」；口径句补分档说明。**其余 20 项行项与 JSON 结构不动** |
| `ComplaintPackageFacadeImpl`（改） | 把测试记录上限（新配置 key 或类内常量，实施时二选一并写明）传入 Exporter，与既有三上限同路径 |
| 前端 `api/test.ts`（新增）/ `TestPage.tsx`（新增）/ `LotsPage.tsx`（改） | 测试数据页 + Lot 详情两区；权限码门禁 |
| 前端路由与侧栏 | 注册「测试数据」菜单（管理端） |
| **Hold / Track / WIP / Route / EDC / Alarm / SPC / Report** | **零改动** |

## 5. 实现步骤

**顺序：a → b → c → d → e → f → g 收尾。禁止乱序项：d（客诉包）不得先于 b（Facade），b 不得先于 a（建表）。**

a. **建表与种子**：先执行 M1 复核 → 写 `migrate_test.sql` / `migrate_lot_pkg.sql` → 同步 `schema.sql`（M3）。确认脚本可重复执行（沿用现网 `IF NOT EXISTS` + `ON DUPLICATE KEY UPDATE` 风格）。

b. **Test 模块**（`com.mes.test`，按 K8 分层）：
   1. entity / mapper（3 张表）；`MesBinDef`、`MesTestRecord`、`MesTestBinSummary`
   2. `MesTestRecordService`：`create(dto)` 内**同一事务**落头 + 汇总（K3）；落库前依次跑 V1–V6 校验（对账 → 字典解析 → 必填 → Lot 校验 → 档内去重 → **判重窗口**），任一失败整体回滚且**零落库**（K4）；判重查询的 `eqp_id` 必须走 null 分支（K12）
   3. 字典解析按**四级回退** `PROGRAM_VERSION → PROGRAM → PRODUCT → GLOBAL`（V2 / C4）；解析结果写入汇总行快照（K7）
   4. `TestFacade` + controller（§2 的 **Test 侧全部端点**，含作废 `PUT /test/records/{id}/void`：头 + 汇总一并软删、原因必填，A11）
   5. 记录号 `TR-yyyyMMdd-序号`：用**号段表** `mes_test_record_no_seq` 发号（D11）—— `INSERT INTO mes_test_record_no_seq (seq_day, next_no) VALUES (#{seqDay}, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE next_no = LAST_INSERT_ID(next_no + 1)`，再取 `SELECT LAST_INSERT_ID()`（**取号是两步**：`bump()` 拿影响行数、`lastInsertId()` 拿值）；机制照抄现网 `MesLotNoSeqMapper:15-23`。**禁止** `COUNT(*)+1`、**禁止** `ORDER BY record_no DESC LIMIT 1`（无零填充时 `-9 > -10`）；`uk_record_no` 仍保留作最后兜底

c. **Lot 模块扩展**：Strip 批量登记（一次一批多条，**同事务整体成败**；请求内 `strip_no` 重复**前置拒绝** `LOT_STRIP_DUPLICATE`，不依赖 UK 报错兜底）、查询；客户 Lot 映射登记、正查、反查。均为主数据操作，**不写 tx_log**（K1）；软删口径按 §3.7（TINYINT + UK 不含 `deleted`，**禁止**手写软删 SQL）。

d. **客诉包延伸**：Assembler 增块（上限：每 Lot 最近 20 条记录、每记录 ≤50 档；失败隔离 + WARN）→ Exporter README 增行与口径句 → FacadeImpl 传入上限值。**禁止**在此路径直连 `mes_test_*`（K2）。

e. **前端**：`api/test.ts`；`TestPage.tsx`（记录登记表单 + 列表 + 详情；Bin 字典维护区）；`LotsPage` 详情增「测试结果」「Mapping / 条级」两区（按权限码显隐）；路由与侧栏菜单。登记表单提交**必须有重入闸**（同步 ref + 序号，对齐 `EVAL-0001` 的教训）—— 但**前端闸不是架构保证**，服务端 V6 判重窗口才是兜底（D12 / C3）。

f. **测试用例**：
   - 后端（零外部依赖）：V1 对账（相等通过 / 差一颗即拒）、V2 字典**四级**回退解析（`PROGRAM_VERSION` 优先 + 逐级降级）、V5 档内重复拒绝、**V6 判重窗口（含 `eqp_id` 为空的 null 分支，K12）**、K7 快照不随字典漂。放在 `server/src/test`，与现网 12 用例同风格（**注意**：`@JsonTest` 不能加载 `MesApplication`；需要容器时用内嵌最小 `@SpringBootConfiguration`）
   - 前端：登记表单重复点击只提交一次（`act()` 内连发 `dispatchEvent`，对齐 `EVAL-0001` 用例手法）
   - **反向验证**：把对账校验临时改坏一次，确认用例真的变红（否则是假保护）

g. **文档收尾（同会话）**：新建 `docs/模块/测试数据（Test）模块/` 五件套中本切片需要的部分（功能文档 / 接口设计 / 数据库设计 / 已完成功能）；Lot 模块文档登记 Strip 与客户映射；`MES-客诉追溯包接口设计.md` §6.2 增块 + §6.4 封面行项；`MES-实施进度与下一步.md` 增 TD-1 行；`INT-0001` 切片表状态；重建 `docs/INDEX.md`（`python .workbuddy/scripts/add_frontmatter.py --reindex`）。

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
10. 客诉包 `GET /complaint-packages/{id}` 响应含 `testSummaryByLot`；`export?format=zip` 内 README 出现「每 Lot 测试记录上限」，且 README 行项数 = **21 项**（M4，同步更新 CP-6 相关断言）
11. **回归**：`format=json` 除新增 `testSummaryByLot` 键外，其余键与 CP-6 口径一致；ZIP 仍恰 2 个 entry
12. 权限（**两级码**，C2）：无 `test:view` → `GET /test/records` 403；无 `test:create` → `POST /test/records` 403；无 `test:void` → 作废 403；无 `test:edit-bin` → 改字典 403
13. `mes_tx_log` 与 `mes_lot.status` 在本切片所有操作前后**无变化**（K1）；Hold / Track 相关代码 diff 为空
14. 静态核对：`com.mes.test` 零 `com.mes.hold` / `com.mes.track` / `com.mes.complaint` 引用（K2）；complaint 包零 `mes_test` mapper 引用（P4）
15. CI 三闸门全绿：后端 `mvn -B test`、前端 `npx tsc -b && npm test`、文档 reindex 后 `git diff --exit-code docs/INDEX.md`
16. **判重窗口（V6 / C3）**：同 `(lot_id, eqp_id, program_name, program_version, test_time, total_qty)` 10 分钟内二次提交 → 拒 `TEST_RECORD_DUPLICATE`；**换一台 `eqp_id` 提交 → 200**（双机并测不得误判）；`eqp_id` 为空时两次提交同样被拒（**null 分支生效** —— 反向验证：把 `eq(eqpId != null, …)` 改回无条件 `eq(…)`，此条必须变红）
17. **作废（A11）**：`PUT /test/records/{id}/void` 原因必填；作废后详情 / 列表不再返回该记录，`mes_test_bin_summary` 随头一并排除；`record_no` **不复用**
18. **软删口径（C1）**：`mes_lot_strip` / `mes_lot_customer_map` 的 `deleted` 为 **TINYINT**、UK **不含** `deleted`；软删后**同键不可重建**（再登记被前置校验拒并提示改原行）
19. **Bin 字典四级回退（C4）**：同一 `bin_code` 在 `PROGRAM_VERSION` / `PROGRAM` / `PRODUCT` / `GLOBAL` 四级都有定义时**取最精确的一级**；只有粗粒度定义时逐级降级命中

**验证方式：**

- 后端：`cd server && mvn -o test`（新增用例）；`mvn -o -DskipTests compile`
- 真机：`curl` 打 §2 各接口（取 token 后）→ 核对响应 `msg` 前缀与 HTTP 200；`SQL` 复核零落库（验收 2）
- 前端：`cd web && npx tsc -b && npm test`；页面手测登记 / 详情 / 两区展示
- 文档：reindex 后 `git diff --exit-code docs/INDEX.md`
- 字典停用后查历史：SQL 直查 `mes_test_bin_summary` 快照值（验收 7）

**副作用与并发（必测，不得只测正常路径）：**

| 场景 | 期望 |
|------|------|
| 双击「保存」 | 前端重入闸只放行一次；服务端**V6 判重窗口兜底** —— 第二次提交拒 `TEST_RECORD_DUPLICATE`（D12 / C3） |
| **两台测试机并行测同一批**（同程序 / 同时间 / 同总量） | **各记一条**（判重键含 `eqp_id`，C3）—— 不得误判为重复提交 |
| 汇总与总量不符 | 零落库（验收 2）；反复提交不产生幽灵行 |
| 并发登记同一 Lot 的两条记录 | 均成功；无 Lot 级写锁需求 |
| 客诉包导出与登记并发 | 现网隔离级别 = **REPEATABLE READ**（2026-09-23 实测）：导出走 MVCC 快照读，导出期间的新登记在导出事务内**不可见**（一致快照，即期望行为）；导出不加锁、不得阻塞登记 |
| 字典在登记请求进行中被停用 | 请求内已解析的快照生效；不得出现「头已入库、汇总失败」的半截记录（K3） |
| 会话切换 / 抽屉重开时在途请求收尾 | 旧请求返回不得改写新会话的列表与表单状态 |

## 7. 回滚方式

**三选一并说明**：采用「**数据回退 + 代码回滚**」组合。

- **代码回滚**：本切片为新增（新表 + 新接口 + 新页面），代码回滚后功能消失，存量模块不受影响（Hold / Track / WIP 零改动 → 无回归风险）。
- **数据回退**：**6 张新表**（5 业务表 + 1 号段表）为纯新增，回滚时 `DROP TABLE`（含其权限与菜单种子行）；客诉包新增的 `testSummaryByLot` 键与 README 行随代码回滚消失，**不需**回退 `mes_complaint_package*` 数据。
- **不需要配置开关**：本切片不引入灰度开关（无自动状态变更风险，K1）。**例外说明**：客诉包既有 `mes.complaint-package.enabled` 仍生效，与本切片正交。

---

**批准记录**：`status` 改为 `approved` 时，在此行写明批准人与日期（该提交即审计轨迹）。
