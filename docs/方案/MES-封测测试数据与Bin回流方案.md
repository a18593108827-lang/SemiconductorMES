---  
type: 方案  
module:   
status: draft  
slices: [TD-1, TD-2, TD-3]  
aligns: [INT-0001-封测颗级追溯与测试数据回流.md, MES-厂型选型分析.md, BK-0001-测试与Bin分档.md]  
updated: 2026-09-23  
---

# MES 封测测试数据与 Bin 回流 — 方案设计（跨模块）

> 定位：把 CP / FT 测试分档结果纳入系统并与批次绑定；把追溯颗粒度从「批」推进到「条」；把客诉证据链延伸到分档构成  
> 归属：新增 **Test（测试数据）模块** + Lot 模块扩展（Strip / 客户 Lot 映射）；状态拦截仍归 Hold / Track  
> 对齐：`INT-0001-封测颗级追溯与测试数据回流.md`（已采纳）· `MES-厂型选型分析.md` §5 / §6（后道需补清单·第一批）· `BK-0001-测试与Bin分档.md`  
> 前提（均已落地）：Track 唯一真相 · Lot + Genealogy · Hold 最小集 · Rework（`mes_route_edge`，含 `rework` 边与 `reason_codes`）· 客诉包 CP-1～CP-6 · CI 三闸门  
> 更新：2026-09-23（架构审查修订：登记幂等 / 发号方式 / 隔离级别口径 / 软删与 UK / 字典乐观锁）  
> 状态：**草案**（待批准；TD-1 的 plan 另行出，plan 未批不动码）  
> **易混：** 本方案 ≠ YMS 良率分析 · ≠ Wafer Map 图形分析 · ≠ SEMI T23 / eDHR · ≠ EAP 设备直连 · ≠ 前道片级 SlotMap



---

## 0. 范围与分期

| 切片       | 交付                                                                 | 阻塞关系         |
| -------- | ------------------------------------------------------------------ | ------------ |
| **TD-1** | Strip 条级 + 测试记录与 Bin 汇总落库 + Bin 独立字典 + 客户 Lot 映射数据模型 + 客诉包带 Bin 摘要 | —            |
| TD-2     | 不良 Bin → Hold / Rework **建议**联动（阈值规则 + 人工确认 + 既有事务留痕）              | 依赖 TD-1 有数据  |
| TD-3     | 颗级 Die 序列号 / 条级 Bin 细分 / 测试文件（STDF、CSV）解析                          | 依赖 TD-1 层级就位 |

**顺序铁律**：TD-1 是地基，**禁止**跳过直接做 TD-2 的自动拦截（无数据即无判据）。本方案详述 TD-1 口径，TD-2 / TD-3 只给边界与预告（另行出规格）。

---

## 1. 目标与边界

**一句话：** 测试结果进系统、与批绑定、可查可证；追溯从「批」下探到「条」；客诉包能回答「这批分了几档、各多少颗」。

| 做（TD-1）                              | 不做（明确后置 / 禁宣称）                         |
| ------------------------------------ | -------------------------------------- |
| Strip 条级身份登记与查询（颗数、序号、位置）            | 颗级 Die 序列号（TD-3）                       |
| 测试记录（批次 / 阶段 / 程序版本 / 设备 / 时间 / 总颗数） | STDF / CSV 自动解析（TD-3；本切片为手工或 API 结构提交） |
| Bin 汇总落库（hard bin 计数 + 占比）           | soft bin 明细分档入库（只留引用，见 D2）             |
| Bin 独立字典（产品 / 程序维度、失效模式、可出货性）        | YMS 良率分析与 Wafer Map 图形（属 YMS 范畴）       |
| 客户 Lot 映射数据模型（来料 ↔ 出货），界面后置          | 出货 / 编带作业、委外工序管理                       |
| 客诉包装配块新增测试分档摘要                       | 由 Bin 自动 Hold / 自动 Rework（TD-2 也只出建议）  |
| 管理端登记与查询页                            | **现场台任何改动**（A5）                        |

---

## 2. 层级模型（本方案的核心决策）

```
外部来料                          本系统（封装批 mes_lot）                外部出货
─────────────────────────  ─────────────────────────────────────  ─────────────────
Wafer Lot ──┐                                                       ┌── 客户批号
            ├─► mes_lot_customer_map ──►  Lot ─┬─ Strip（mes_lot_strip，条级）    │
客户料号 ───┘                (INBOUND)          │      └─（TD-3）Die 颗级          │
                                               └─ 测试记录 mes_test_record        │
                                                     └─ Bin 汇总 mes_test_bin_summary
                                                        ▲ 字典 mes_bin_def
                           mes_lot_customer_map (OUTBOUND) ─────────┘
```

### 2.1 Strip 与 Lot 的关系（**关键**）

| 判定                    | 结论      | 理由                                                         |
| --------------------- | ------- | ---------------------------------------------------------- |
| Strip 建成独立 Lot？       | **否**   | 一条批可含数百条 Strip，若各自成 Lot 会污染 WIP / Route / Track（在制数与过站语义崩） |
| Strip 参与 Track 门禁？    | **否**   | 过站以 Lot 为单位；A5 要求现场 3 步不变                                  |
| Strip 写 `mes_tx_log`？ | **否**   | Strip 登记属**主数据登记**（同 Lot 创建、Release 语义），非状态变更              |
| Strip 归属模块？           | **Lot** | 它是 Lot 的下级实物单元，谱系 / 报表 / 客诉均自 Lot 出发                       |

> 一句话：**Lot 是执行与状态的单位，Strip 是身份与位置的单位。** 二者不可互相替代，也不可混层。

### 2.2 测试记录与 Bin 汇总的挂靠

- 测试记录挂在 **Lot**（不挂 Strip）——INT 验收 2 的口径是「任取一条测试记录 → 所属批 / 程序版本 / 各 Bin 数量与占比」。
- Bin 汇总挂在 **测试记录**（一次测试作业一份分档构成）。
- **允许同批多次测试（重测 / 复测）**：多条记录并存，后者**不覆盖**前者（A10）；「当前有效」由查询侧按时间取最新或由人指定，**不在写入时改写历史**。
- 条级 Bin 细分（每条 Strip 各档多少颗）**不在 TD-1**：需要颗级数据才有意义，属 TD-3。

---

## 3. 数据设计（TD-1）

> 表前缀按模块分：`mes_test_*`（Test 模块）/ `mes_lot_*`（Lot 模块）。命名与字段风格对齐现网（`mes_hold_reason` / `mes_lot_genealogy`）。  
> 脚本命名对齐现网：`server/src/main/resources/db/migrate_test.sql`，并同步进 `schema.sql`（新库路径）。

### 3.1 `mes_bin_def` — Bin 字典（Test 模块，独立于 Hold 原因码）

| 字段                                  | 类型           | 空 | 说明                                                |
| ----------------------------------- | ------------ | - | ------------------------------------------------- |
| id                                  | BIGINT PK    | N |                                                   |
| bin_scope                           | VARCHAR(16)  | N | `GLOBAL` / `PRODUCT` / `PROGRAM`（适用范围的声明，便于人读与校验） |
| product_code                        | VARCHAR(64)  | N | 适用产品；**全局档填 `''`**（非 NULL，避免 MySQL 唯一索引不收 NULL）   |
| program_name                        | VARCHAR(64)  | N | 适用测试程序；全局档填 `''`                                  |
| bin_type                            | VARCHAR(8)   | N | `HARD` / `SOFT`                                   |
| bin_code                            | VARCHAR(32)  | N | Bin 号（如 `1` / `3`）                                |
| bin_name                            | VARCHAR(64)  | N | 名称（如「良品（最高档）」）                                    |
| failure_mode                        | VARCHAR(128) | Y | 失效模式（开路 / 短路 / 参数超限…）——失效分析入口                     |
| is_shippable                        | TINYINT      | N | 按规格可出货 0/1；默认 0                                   |
| status                              | TINYINT      | N | 1 启用 / 0 停用                                       |
| version                             | INT          | N | 乐观锁版本号：PUT 条件更新，冲突即拒（§5）                  |
| remark                              | VARCHAR(256) | Y |                                                   |
| create_time / update_time / deleted |              |   | 审计与软删（deleted BIGINT，作废置为主键 id，见 §3.7）  |

UK：`(product_code, program_name, bin_type, bin_code, deleted)`（deleted 口径见 §3.7）。

**写入校验（服务层）**：`bin_scope` 必须与 `product_code` / `program_name` 取值一致——GLOBAL ⇒ 两字段均 `''`；PRODUCT ⇒ product_code 非空且 program_name `''`；PROGRAM ⇒ 两字段均非空；不一致拒（`TEST_BIN_SCOPE_INCONSISTENT`）。`bin_scope` 仅作人读与校验声明，**不参与解析回退**（V2 回退只看两 code 字段）。

**为何独立于 `mes_hold_reason`**（D3）：Bin 是「产品 / 程序」维度且**随程序版本可变**，Hold 原因是「处置」维度；混表会让两侧历史语义互相污染，且 Hold 原因码的 `category` 语义装不下 Bin 的失效模式。禁止双向复用（P3）。

### 3.2 `mes_test_record` — 测试记录头（Test 模块）

| 字段                                              | 类型           | 空 | 说明                                   |
| ----------------------------------------------- | ------------ | - | ------------------------------------ |
| id                                              | BIGINT PK    | N |                                      |
| record_no                                       | VARCHAR(32)  | N | 业务号 `TR-yyyyMMdd-序号`；UK；**号段表发号**：新增 `mes_test_record_no_seq`，`INSERT ... ON DUPLICATE KEY UPDATE next_no=LAST_INSERT_ID(next_no+1)`，同现网 `MesLotNoSeqMapper` 机制（D11） |
| lot_id                                          | BIGINT       | N | 所属批（真相）                              |
| lot_no                                          | VARCHAR(64)  | Y | 冗余便查（登记时快照）                          |
| test_stage                                      | VARCHAR(8)   | N | `CP` / `FT` / `OTHER`                |
| program_name                                    | VARCHAR(64)  | N | 测试程序名（A9 不可空）                        |
| program_version                                 | VARCHAR(32)  | N | 程序版本（A9 不可空）                         |
| eqp_id                                          | BIGINT       | Y | 测试设备（可为空：外协 / 手工）                    |
| eqp_code                                        | VARCHAR(64)  | Y | 冗余便查                                 |
| test_time                                       | DATETIME     | N | 测试完成时间（业务时间，非入库时间）                   |
| total_qty                                       | INT          | N | 本次测试颗数（基数）                           |
| source_type                                     | VARCHAR(16)  | N | `FILE` / `API` / `MANUAL`（数据来源，便于审计） |
| source_ref                                      | VARCHAR(256) | Y | 文件引用 / 外部标识（不落文件本体）                  |
| remark                                          | VARCHAR(512) | Y |                                      |
| create_by / create_time / update_time / deleted |              |   | 审计与软删                                |

索引：`lot_id`、`(lot_id, test_time DESC)`、`uk_record_no`。**不加「同批同程序唯一」约束**——重测合法（A10）。防重复提交**不靠 UK**（重新提交生成新 record_id，汇总 UK 不设防），靠 V6 业务判重窗口（D12）。**不提供 DELETE 接口**：录入错误走「作废」（§5，须填原因、留审计），作废后 `record_no` 不复用、Bin 汇总随头一并排除（A11）。

### 3.3 `mes_test_bin_summary` — Bin 汇总（Test 模块）

| 字段           | 类型          | 空 | 说明                             |
| ------------ | ----------- | - | ------------------------------ |
| id           | BIGINT PK   | N |                                |
| record_id    | BIGINT      | N | 所属测试记录                         |
| bin_type     | VARCHAR(8)  | N | `HARD` / `SOFT`（TD-1 主要落 HARD） |
| bin_code     | VARCHAR(32) | N | Bin 号                          |
| bin_name     | VARCHAR(64) | Y | 名称快照（登记时取自字典，字典后改不影响历史）        |
| bin_qty      | INT         | N | 颗数                             |
| is_shippable | TINYINT     | N | **可出货性快照**（登记时字典值，D7）          |

UK：`(record_id, bin_type, bin_code)`。无独立审计 / 软删字段：生命周期完全随头记录；**查询一律 join 头表过滤头记录的 `deleted`**（A11）。

### 3.4 `mes_lot_strip` — Strip 条级（Lot 模块）

| 字段                                              | 类型           | 空 | 说明                            |
| ----------------------------------------------- | ------------ | - | ----------------------------- |
| id                                              | BIGINT PK    | N |                               |
| lot_id                                          | BIGINT       | N | 所属批（真相）                       |
| strip_no                                        | VARCHAR(64)  | N | 条号 / 条标识                      |
| seq_no                                          | INT          | Y | 批内序号（位置次序）                    |
| die_qty                                         | INT          | Y | 本条颗数（切筋成型后 = 成品数）             |
| bin_code                                        | VARCHAR(32)  | Y | 该条最终判定档（TD-3 才细分到条，TD-1 允许留空） |
| status                                          | VARCHAR(16)  | Y | 条级状态（TD-1 仅登记，不做流程）           |
| remark                                          | VARCHAR(256) | Y |                               |
| create_by / create_time / update_time / deleted |              |   | 审计与软删（deleted BIGINT，删除置为主键 id，见 §3.7） |

UK：`(lot_id, strip_no, deleted)`（deleted 口径见 §3.7）。索引：`lot_id`。

### 3.5 `mes_lot_customer_map` — 客户 Lot 映射（Lot 模块）

| 字段                                | 类型           | 空 | 说明                                      |
| --------------------------------- | ------------ | - | --------------------------------------- |
| id                                | BIGINT PK    | N |                                         |
| lot_id                            | BIGINT       | N | 内部批（真相）                                 |
| lot_no                            | VARCHAR(64)  | Y | 冗余便查                                    |
| map_type                          | VARCHAR(16)  | N | `INBOUND`（来料 → 本批）/ `OUTBOUND`（本批 → 出货） |
| external_lot_no                   | VARCHAR(64)  | N | 外部批号（来料 wafer lot / 客户批号）               |
| external_source                   | VARCHAR(64)  | Y | 供应商 / 客户编码                              |
| customer_code                     | VARCHAR(64)  | Y | 客户编码                                    |
| qty                               | INT          | Y | 映射数量（可空）                                |
| remark                            | VARCHAR(256) | Y |                                         |
| create_by / create_time / deleted |              |   | 审计与软删（deleted BIGINT，删除置为主键 id，见 §3.7）      |

UK：`(lot_id, map_type, external_lot_no, deleted)`（deleted 口径见 §3.7）。索引：`external_lot_no`（反向查「哪批用了这个 wafer lot」是召回主路径）。

**与 `mes_lot.customer_lot` 的关系**（P9 / D4）：现网 `mes_lot` 已有 `customer_lot VARCHAR(64)` 单字段。本方案**不复用**它作映射真相——单字段无法表达一对多 / 双向 / 数量 / 审计；它**保留为便查冗余**（兼容既有页面），真相在新表。二者冲突时以新表为准。

**为何不进 `mes_lot_genealogy`**（P2 / D4）：Genealogy 的边类型是封闭的 `split` / `merge`（`MES-LotGenealogy接口设计.md` §3.1、§8 边界矩阵，并已明令「禁止把 Scrap/Bonus 画进谱系」）。外部批号映射是**身份映射**而非分合批事件，混入会破坏「图归身份、线归履历」原则与既有遍历算法。

### 3.6 种子数据

| 内容            | 说明                                                                                                                |
| ------------- | ----------------------------------------------------------------------------------------------------------------- |
| `mes_bin_def` | 至少一组演示档：`GLOBAL` 的 `HARD` Bin1（可出货）/ Bin2（参数次档）/ Bin3（失效）/ Bin4（失效）——**仅为演示**，真实档位因厂因产品而异（`BK-0001` 误区 1），不宣称行业惯例 |
| 权限            | 见 §5 权限码；挂管理端                                                                                                     |
| 菜单            | 管理端「测试数据」入口                                                                                                       |

### 3.7 删除与软删策略（UK 与 `deleted` 共存）

现网 UK 均不含 `deleted`，软删后同键重建会撞 UK（EDC 模块已踩坑，靠「物理删再插躲开软删唯一键冲突」绕过，`MesEdcPlanServiceImpl`）。本方案逐表定口径（D13）：

| 表 | 策略 |
|----|------|
| `mes_test_record` | **不删只作废**：软删 + 必填原因（留审计）；`record_no` 不复用，无同键重建需求，`deleted` 维持 TINYINT |
| `mes_test_bin_summary` | 无独立软删；生命周期随头记录，查询一律 join 头表过滤 |
| `mes_lot_strip` / `mes_lot_customer_map` | `deleted` 用 BIGINT：未删为 0，删除时置为主键 id；**UK 含 `deleted`** → 同键仅一行活跃，删后可重新登记 |
| `mes_bin_def` | 不删只停用（`status=0`）；误建走「作废」（同 strip 手法，deleted BIGINT 置主键 id）；历史汇总行有 `bin_name` / `is_shippable` 快照，不依赖字典行存活（D7） |

---

## 4. 模块边界与 Facade

```
Test（新模块 com.mes.test）       = 测试记录 / Bin 汇总 / Bin 字典 的唯一写入口 + 只读 TestFacade
Lot（扩展）                       = Strip 与客户 Lot 映射的主数据；谱系真相不变
Hold / Track                      = 【TD-1 零改动】仍只认 active Hold；Rework 仍走既有事务
EDC                               = 不参与；EDC 是工艺参数点，测试分档是另一域
Alarm                             = 【TD-1 零改动】TD-2 可由建议联动 raise
History（客诉包，com.mes.complaint）= 装配块新增测试分档摘要，**经 TestFacade 只读**
WIP / Route / Report              = TD-1 不改读模型；报表用 Bin 出良率口径属后续
UI 管理端（Admin Light）          = 测试记录登记与查询、Bin 字典维护、Lot 详情「测试结果」区
UI 现场台（Field Dark）           = **零改动**（A5）
```

| Facade                | 方法（TD-1）                                                                                                                     | 调用方                         |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------------- | --------------------------- |
| `TestFacade`（新增，只读为主） | `listRecordsByLots(lotIds, cap)` / `listBinSummaryByRecord(recordId)` / `latestBinSummaryByLot(lotId)` / `assertBinDef(...)` | 客诉包 Assembler、Lot 详情、报表（后续） |
| Lot 侧（既有 Service 扩展）  | `listStrips(lotId)` / `listCustomerMaps(lotId)`                                                                              | 客诉包、Lot 详情                  |

**约束**：客诉包与报表**禁止**直连 `mes_test_*` 表或其 mapper（A7 / P4）；Test 模块**禁止**引用 Hold / Track 的写路径（A1）。

**包结构（重要）**：沿用**现网实际分层** `com.mes.test.{controller,dto,entity,mapper,service,service.impl,vo}`（与 `lot` / `hold` / `complaint` 一致，2026-09-23 实测）。`AGENTS.md` §3 已同步为现网分层（同日决定：按现网、代码零改动），新模块**不与存量分叉**。

---

## 5. 接口清单（TD-1）

| 方法   | 路径                         | 权限码                  | 说明                                                     |
| ---- | -------------------------- | -------------------- | ------------------------------------------------------ |
| POST | `/test/records`            | `test:record:create` | 登记测试记录 **+ Bin 汇总**（一次提交、同事务，A8）                       |
| GET  | `/test/records`            | `test:record:view`   | 分页查询（`lotId` / `lotNo` / `stage` / 程序 / 时间范围）          |
| GET  | `/test/records/{id}`       | `test:record:view`   | 详情（含 Bin 明细）                                           |
| GET  | `/test/summary/by-lot/{lotId}` | `test:record:view` | 按批的测试摘要（Lot 详情页消费）；**Test 模块自有 controller**，Lot 侧不代理此端点（A7，避免 Lot 直连 `mes_test_*`） |
| PUT  | `/test/records/{id}/void`  | `test:record:create` | 作废测试记录（原因必填，留审计；头与 Bin 汇总一并软删，A11）                    |
| GET  | `/test/bins`               | `test:bin:view`      | Bin 字典查询（可选 `productCode` / `programName` / `binType`） |
| POST | `/test/bins`               | `test:bin:edit`      | Bin 字典新增                                               |
| PUT  | `/test/bins/{id}`          | `test:bin:edit`      | Bin 字典修改 / 停用（**乐观锁**：请求携带 `version` 条件更新，冲突拒 `TEST_BIN_DEF_CONFLICT`，禁止静默覆盖） |
| POST | `/lots/{id}/strips`        | `lot:edit`           | Strip 批量登记（一次一条批的多条 Strip；**同事务整体成败**，请求内 `strip_no` 重复前置拒绝 `LOT_STRIP_DUPLICATE`，不靠 UK 报错兜底） |
| GET  | `/lots/{id}/strips`        | `lot:list`           | Strip 列表                                               |
| POST | `/lots/{id}/customer-maps` | `lot:edit`           | 客户 Lot 映射登记（INBOUND / OUTBOUND）                        |
| GET  | `/lots/{id}/customer-maps` | `lot:list`           | 映射查询（正查）                                               |
| GET  | `/lots/by-external-lot`    | `lot:list`           | 按外部批号反查内部批（召回主路径）                                      |

**权限码新增**：`test:record:view`、`test:record:create`、`test:bin:view`、`test:bin:edit`（种子入 `sys_permission`，挂管理端菜单；现场台不挂）。错误码随实现定义，命名沿用现网 `<模块>_<语义>` 风格（如 `TEST_BIN_SUM_MISMATCH`），错误响应口径沿用「业务码在 `msg` 前缀」（见项目记忆与 `MES-客诉追溯包接口设计.md` §6.4）。

**登记校验（写入即拒，不留半截数据）**：

| #  | 校验                                                           | 失败                                                |
| -- | ------------------------------------------------------------ | ------------------------------------------------- |
| V1 | `Σ bin_qty(HARD) == total_qty`                               | 拒（`TEST_BIN_SUM_MISMATCH`）—— A3「不依赖人工补录」的落地手段（D6） |
| V2 | 每个 `bin_code` 在字典中可解析（按 `PROGRAM` → `PRODUCT` → `GLOBAL` 回退） | 拒；提示先维护字典                                         |
| V3 | `program_name` / `program_version` / `test_time` 非空          | 拒（A9：无版本则 Bin 语义不可追溯）                             |
| V4 | `lot_id` 存在且非 `merged` / `scrapped`                          | 拒                                                 |
| V5 | 同一请求内 `(bin_type, bin_code)` 不重复                             | 拒（否则汇总不可信）                                        |
| V6 | 业务判重窗口：**10 分钟**内已存在同 `(lot_id, program_name, program_version, test_time, total_qty)` 的记录 | 拒（`TEST_RECORD_DUPLICATE`）——防重复点击 / 重复提交（D12）；合法重测须改 `test_time` 或窗外提交 |

---

## 6. 拦截联动口径（TD-2 预告，**TD-1 不做**）

INT-0001 验收 3（不良 Bin → Hold / Rework 建议 + 留痕）由 TD-2 承接，此处只锁边界，避免 TD-1 顺手写成自动拦截：

| # | 口径                                                                                                                   |
| - | -------------------------------------------------------------------------------------------------------------------- |
| 1 | **只出建议**：由 Bin 阈值规则（如「hard bin 3 占比 > 阈值」）产生**处置建议**，**绝不自动改状态**（A1 / P7）                                            |
| 2 | 人工在界面确认后才生效；Hold 走既有 `HoldService.create`，Rework 走既有事务（`mes_route_edge` 的 `rework` 边 + `reason_codes` **已就绪，复用不新造**） |
| 3 | 建议单落库（`mes_test_advice`，TD-2 建表），确认后记 `tx_id`，形成留痕                                                                   |
| 4 | 可同时 raise 一条 Alarm 复用告警台可见性（**不新增告警通道**）                                                                             |
| 5 | 建议可以「忽略」，忽略须记原因 —— 留痕同样重要                                                                                            |



---

## 7. 客诉证据链延伸（TD-1 做）

沿用 CP-1～CP-6 的装配口径（`MES-客诉追溯包接口设计.md` §6.2），**只增一个块**，不新造导出格式（D8）：

| 块 | 来源 | 上限 | 备注 |
|----|------|------|------|
| `testSummaryByLot` | `TestFacade`（只读，一次 IN） | 每 Lot 最近 **20** 条测试记录 + 各记录 Bin 汇总（每记录 ≤ 50 档） | 现查，**不落库**（§4.3 口径延续）；失败按块隔离并记 WARN |

- **JSON**：根对象增 `testSummaryByLot`，其余键不变。
- **ZIP `README.txt` 封面**：增一行「每 Lot 测试记录上限: 20」，口径句补「含测试分档构成；不含 Wafer Map 图形与良率分析」。
- 权限沿用 `complaint:view`，不新增权限码。
- **越界红线**：客诉包**禁止**直连 `mes_test_*`（P4）；`TestFacade` 是唯一读入口。

> 这一块是厂型决策的直接兑现：客户审厂问「这批分了几档、各多少颗、失效什么模式」，证据包里当场可答。

---

## 8. 约束（A#）

| # | 约束 | 说明 |
|---|------|------|
| A1 | 状态唯一真相仍在 Track | 测试数据与后续建议**不得**直改 Lot 状态；Hold / Rework 必须走既有事务 |
| A2 | 接入先用文件 / API | SECS/GEM 属远期；本期不得因设备协议阻塞主线（TD-1 为手工或 API 结构提交） |
| A3 | 追溯链不得依赖人工补录 | 落地手段 = 写入时 `Σ bin_qty == total_qty` 强校验（V1 / D6） |
| A4 | 导出沿用既有口径 | 不落盘、不缓存；权限沿用 `complaint:view` |
| A5 | 不破坏现场 3 步过站 | 现场台零改动；测试数据登记只在管理端 |
| A6 | Strip 不是 Lot | 不建 WIP、不绑 Route、不过站、不写 `mes_tx_log`（§2.1） |
| A7 | 跨模块只走 Facade | Test / Lot / History 之间只经 `TestFacade` 与 Lot 既有门面 |
| A8 | 记录与汇总同事务 | 测试记录 + Bin 汇总整体成功或整体失败，禁止半截记录 |
| A9 | 程序版本不可空 | Bin 语义随程序版本变化（`BK-0001` 待核实 4），无版本则拒绝登记 |
| A10 | 允许重测、不改写历史 | 同批可有多条测试记录，后者不覆盖前者；「当前有效」由查询侧表达 |
| A11 | 测试记录只可作废，不可删改 | 作废 = 软删 + 必填原因（留审计）；Bin 汇总随头记录；A10「不改写历史」的延续 |

---

## 9. 禁止（P#）

| # | 禁止 | 理由 |
|---|------|------|
| P1 | 把 Strip 建成 Lot | 污染 WIP / Route / Track 语义 |
| P2 | 把 Strip / Die / 客户批号画进 `mes_lot_genealogy` 当边 | 图归身份，只认 `split` / `merge` |
| P3 | Bin 定义与 Hold 原因码互相复用 | 两个维度，混用污染历史语义（D3） |
| P4 | 客诉包 / 报表直连 `mes_test_*` 表或 mapper | 破坏 Facade 铁律 |
| P5 | 对外宣称 YMS 良率分析 / Wafer Map 图形 / eDHR / SEMI T23 | 口径越界（与 `MES-厂型选型分析.md` §8 禁止一致） |
| P6 | 现场台过站路径插入任何测试录入 / 确认 | A5 |
| P7 | 由 Bin 自动 Hold / 自动 Rework | 必须人工确认（TD-2 亦然） |
| P8 | TD-1 落逐颗明细 | 保留策略见 D2；TD-3 再评估 |
| P9 | 用 `mes_lot.customer_lot` 当映射真相 | 单字段表达力不足（D4） |

---

## 10. 决策记录（D#）

| # | 决策 | 理由 |
|---|------|------|
| D1 | 颗粒度自 **Strip + Bin 汇总**起步，颗级后置 | `BK-0001` 误区 6：批级 + 汇总 + 条级已能支撑良率 / 审厂 / 拦截 |
| D2 | 入库 = **测试记录头 + Bin 汇总**；soft bin 与逐颗明细不入库（留引用） | 量级可控；审厂口径要「分了几档、各多少颗」；与 A2 一致 |
| D3 | Bin **独立字典**，带产品 / 程序维度与失效模式 | 见 §3.1 |
| D4 | 客户 Lot 映射 **独立表**，界面后置；不复用 `customer_lot`、不进 genealogy | 见 §3.5 |
| D5 | Strip 作 Lot 下级单元，**不建 Lot** | 见 §2.1 |
| D6 | Bin 汇总与 `total_qty` **写入即对账** | A3 的唯一可靠落地方式；事后核对等于允许脏数据先入库 |
| D7 | 可出货性 **双轨**：汇总行存登记时快照，字典存当前值 | 客诉问的是「当时按规格能否出货」，报表看的是「现在按规格」 |
| D8 | 客诉包用「**增块 + 上限**」延伸，不新造格式 | 沿用 CP 装配与 ZIP 契约，零新增导出类型 |
| D9 | TD-2 的建议**复用手持事务**（`HoldService.create` / 既有 rework 事务） | `mes_route_edge` 的 `rework` 边与 `reason_codes` 已就绪，不新造第二套返工路径 |
| D10 | 切片自 TD-1 起编号；plan 落 `docs/模块/测试数据（Test）模块/TD-1-plan.md` | 与「切片 plan 归所属模块目录」约定一致 |
| D11 | `record_no` 用**号段表**发号（`mes_test_record_no_seq`），与 Lot 单号同机制 | 并发防撞号；复用现网 `MesLotNoSeqMapper` 先例，不新造第二套发号路径 |
| D12 | 防重复提交靠**业务判重窗口**（V6），不靠 UK 或前端重入闸 | 重新提交生成新 record_id，`(record_id, ...)` UK 完全不设防；前端重入闸不是架构保证 |
| D13 | 主数据软删用 **deleted 置主键 id** 手法，UK 含 `deleted` | 现网 UK 均不含 deleted → 软删后同键重建撞 UK（EDC 已踩坑）；此手法允许重新登记且保留审计 |

---

## 11. 验收口径（对 INT-0001 §2 五条）

| INT 验收 | 由谁达成 | TD-1 可判真假的陈述 |
|----------|----------|---------------------|
| 1 分钟级链路 | TD-1 + TD-3 | TD-1：任取一个 Lot，能查到其 Strip 清单与客户 Lot 映射（正查 + 反查），无需翻 Excel |
| 2 测试结果进系统并与批次绑定 | **TD-1 完整覆盖** | 任取一条测试记录，可查到所属批、程序名与版本、各 Bin 数量与占比；`Σ(HARD) == total_qty` 恒成立 |
| 3 不良 Bin → 建议 + 留痕 | TD-2 | — |
| 4 客诉包带分档信息 | TD-1（分档）/ TD-3（颗级） | 导出的 JSON / ZIP 里含 `testSummaryByLot`；README 封面出现「每 Lot 测试记录上限」与分档口径句 |
| 5 现场不退化 | TD-1 | 现场台代码零改动；TrackIn/Out 步数不变 |

**副作用与并发（必测）**：

| 场景 | 期望 |
|------|------|
| 同一 Lot 重复提交相同测试记录 | 允许（重测语义，A10）；两条记录并存，互不覆盖 |
| 重复点击「保存」 | 前端重入闸 + **V6 业务判重窗口兜底**：第二次提交拒（`TEST_RECORD_DUPLICATE`）；UK 挡不住重复记录（D12） |
| Bin 汇总与 `total_qty` 不等 | 拒绝且**零落库**（不留半截记录，A8） |
| 并发登记同一 Lot 的不同测试记录 | 均成功（无 Lot 级写锁需求，同 CP §8.1 只读口径） |
| 客诉包导出与测试记录登记并发 | 现网 MySQL 默认 **REPEATABLE READ**（未显式配置隔离级别）：导出走 MVCC 快照读，导出期间的新登记在导出事务内**不可见**（一致快照，即为期望行为）；导出不加锁、不得阻塞登记 |
| 登记测试记录与同 Lot scrap / merge 并发 | V4 为「写入时点校验、不加 Lot 级锁」；**接受 TOCTOU 窗口**——校验通过后状态才变更的，记录仍为历史事实，不追溯处理 |
| 两管理员并发 PUT 同一 Bin 字典 | 乐观锁 `version` 条件更新，后到者拒（`TEST_BIN_DEF_CONFLICT`），不得静默覆盖 |
| 字典停用后查历史记录 | 历史 `bin_name` / `is_shippable` 快照不变（D7） |

---

## 12. 遗留与后置

| 项 | 现状 | 触发条件 | 方案 |
|----|------|----------|------|
| ~~`AGENTS.md` 分层与现网不符~~ **已闭环（2026-09-23）** | `AGENTS.md` §3 原写 `api/application/domain/infrastructure`（目标态，现网从未采用；实测 lot / hold / complaint 为 controller / dto / entity / mapper / service / vo / facade / support） | — | **用户决策（2026-09-23）：按现网分层，改文档、代码零改动** —— `AGENTS.md` §3 已同步为现网实际分层 |
| 颗级 Die / ECID 序列号 | 无 | 客户要求颗级召回 | TD-3；依赖 wafer map / die mapping 数据源 |
| STDF / CSV 自动解析 | 手工或 API 结构提交 | 测试机可导出 STDF | TD-3；先核对 STDF 记录字段（`BK-0001` 待核实 1） |
| 条级 Bin 细分 | 只到 Lot 级汇总 | 需要按条定位不良分布 | TD-3 |
| 良率 / Wafer Map 图形 | 无 | 需要图形化失效分布分析 | YMS 范畴，另评估 |
| 「当前有效」测试记录 | 由查询侧取最新 | 需要人工指定重测结论 | 增设 `is_current` 标志（须带审计，勿静默改历史） |
| 拆批后测试记录查询语义 | 未定义（子批是否经 genealogy 回溯父批测试记录） | 客诉包 / Lot 详情需在拆批场景出分档证据 | 明确查询侧口径（建议沿 genealogy 遍历、限深度）；写入侧不改 |
| 出货 / 编带 / 委外工序 | 无 | 后道第三批 | `MES-厂型选型分析.md` §6 第三批 |
| EAP 试点（测试机 / 分选机直连） | 无 | 设备侧具备 SECS/GEM | 远期；A2 允许先用文件过渡 |

---

## 13. 关联

- `docs/intent/INT-0001-封测颗级追溯与测试数据回流.md` — 上游意图与决策记录（§6）
- `docs/业务知识/BK-0001-测试与Bin分档.md` — soft / hard bin、Bin Map、常见误区（本方案的口径依据）
- `docs/方案/MES-厂型选型分析.md` §3.1 / §5 / §6 — 后道三大硬骨头与「后道需补」清单
- `docs/modules` → `docs/模块/Lot（批次）模块/MES-LotGenealogy接口设计.md` §3.1 / §8 — 为何外部映射不进谱系
- `docs/模块/Hold（锁批）模块/MES-Hold数据库设计.md` — 原因码字典（与 Bin 字典的分工）
- `docs/模块/History（履历）模块/MES-客诉追溯包接口设计.md` §6.2 / §6.4 — 装配块与 ZIP 封面契约
- `server/src/main/resources/db/migrate_rework.sql` — `mes_route_edge`（`rework` 边 + `reason_codes`，TD-2 复用）
- `docs/业务清单/MES-半导体业务清单.md` §16 — 封测特化（P0）

---

## 审查记录（第 1 轮 · 2026-09-23 · 只审不动码）

> 审查对象：**2026-09-23 架构审查修订后的版本**（登记幂等 / 发号方式 / 隔离级别口径 / 软删与 UK / 字典乐观锁 五项修订）。
> 方法：把本文档的**事实断言**逐条对到真实代码与真实库——读源码行号、只读 DB 探针（`SELECT @@transaction_isolation` / `information_schema`）。
> 结论分 `F#`（事实与修正）与 `C#`（绑定实现的约束 / 待决策）。**本轮未改任何代码，未改任何设计结论**（除 F1 的格式修复）。

### F 事实与修正

| # | 事实 | 证据（可复核） |
|---|------|----------------|
| **F1** | **本文档 frontmatter 曾被格式化器破坏，已修复**：首字符变成 `\`（`\---`）、首尾行带尾随空格 | 修复前 `text.startswith('---') == False`、首行 `repr` = `'\\---  '`。已按字节修复，现 `startswith('---') == True` |
| F1.1 | 后果（不可忽略，非洁癖）：① `status` 流转**不再生效**——把 status 改成 `approved`，INDEX 仍显示启发式推出的值；② 下次 `--apply` 会给本文档**再插一个 frontmatter**（重复头） | 索引脚本 `.workbuddy/scripts/add_frontmatter.py:100` 用 `body.startswith(b"---")`、`:138` 用 `text.startswith("---")`。本次 reindex 恰好无差异属**巧合**（启发式推出同值），不等于没坏 |
| F2 | **乐观锁有现成机制，无需手写条件更新** | `config/MybatisPlusConfig.java:23` 注册 `OptimisticLockerInnerInterceptor`；7 个实体已用 `@Version`（`MesCarrier:61` / `MesDispatchReserve:37` / `MesEdcParam:34` / `MesEdcPlan:32` / `MesEdcSpec:44` / `MesEqp:31` / `MesLot:119`）；判冲突现网写法见 `MesEdcPlanServiceImpl:141-142`（`updateById` 后判影响行数） |
| F3 | 号段发号机制描述**准确**；补一处细节：取号是**两步** | `lot/mapper/MesLotNoSeqMapper.java:15-23`：`INSERT INTO mes_lot_no_seq (seq_day,next_no) VALUES (#{seqDay}, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE next_no = LAST_INSERT_ID(next_no + 1)`，另有 `@Select("SELECT LAST_INSERT_ID()")`——`bump()` 只拿影响行数，值要再取一次 |
| F4 | EDC 软删先例引用**准确** | `edc/service/impl/MesEdcPlanServiceImpl.java:201-205`：注释「物理删再插，躲开软删唯一键冲突」+ `mesEdcPlanItemMapper.physicalDeleteByPlanId(id)` |
| F5 | **DB 实测（MySQL 8.4.8 / 库 `mes`）：隔离级别 = REPEATABLE-READ** | `SELECT @@transaction_isolation` → `REPEATABLE-READ`；`application*.yml` 亦无 isolation 配置 → §10 并发口径成立 |
| F6 | **DB 实测：全库 `deleted` 列 27 处，全部 `tinyint`**；**唯一索引含 `deleted` 的表 = 0 个** | `information_schema.COLUMNS` / `STATISTICS` → §3.7 的前提断言成立；但 **D13 偏离了全库惯例**，见 C1 |
| F7 | **DB 实测：`sys_permission` MAX(id) = 332**；库内**不存在** `mes_test* / mes_bin* / strip / customer_map / wafer` 任何表 | → plan M1 拟用的 340–343 无冲突；「封测特化 = 零」成立 |
| F8 | **DB 实测：`mes_route_edge.edge_type` 实际取值** `rework`(1) / `normal`(27) / `skip_allow`(5) / `time_link`(4) / `off_flow`(2) | TD-2 复用 `rework` 边 ✔；枚举比本文档提到的更宽（`time_link` / `off_flow` 未提及），建议后续补全（低优先） |

### C 待决策与绑定约束

| # | 约束 | 说明 |
|---|------|------|
| **C1** | **D13（deleted 用 BIGINT 置主键 id）与现网全局逻辑删除机制冲突，需重新拍板** | 证据：`application.yml:14-18`（`id-type: assign_id` 雪花 + `logic-delete-field: deleted` + `logic-delete-value: 1`）· `common/BaseEntity.java:25-27`（`@TableLogic private Integer deleted`）· 25 个实体 `extends BaseEntity`。两处硬冲突：**(a)** MP 的 `logic-delete-value` 是**固定值**，**不支持「置为主键 id」** → 要实现 D13 必须**手写 UPDATE**，等于该两表放弃 MP 逻辑删除自动化；**(b)** `Integer` 上限 21.4 亿，而 `assign_id` 是**雪花 ID（19 位）** → 「deleted 置主键 id」**会整数溢出**，除非该实体不继承 `BaseEntity` 并自行声明 `Long deleted`（现网有 2 处自声明先例 `MesEdcCollectionItem:39` / `MesFutureHold:58`，但均为 `Integer`）。**三选一**：① 回 `tinyint` + UK 不含 `deleted`（与全库 27 处一致），「撤销后重建同键」改用 UPDATE 表达（条号写错本就是改，不是删+建）；② 保留 BIGINT 置 id → 必须自声明 `Long deleted` + 手写软删 + 写明「**全仓唯一例外**及理由」；③ 该两表物理删（丢审计，与 A6 冲突）。**建议 ①** |
| **C2** | 权限码 `test:record:view` 等为**全库首个三级码** | 实测 `SELECT perm_code FROM sys_permission WHERE perm_code LIKE '%:%:%'` → **空**（现网 50+ 码全为两级，如 `lot:list` / `complaint:view` / `track:rework`）。不是硬错误（Sa-Token 支持任意串），但应与惯例对齐：**保留三级并写明这是首例及理由**（本模块确有两组资源：记录 + 字典），或收敛为两级 |
| **C3** | V6 判重键**不含 `eqp_id`** → 双机并测会被误拒 | 场景真实存在：同批 / 同程序 / 同时间 / 同总量，但两台测试机并行测（应各记一条）。建议判重键加 `eqp_id`，或窗口缩到 60 秒 + 依赖前端重入闸 |
| **C4** | **Bin 字典缺「程序版本」维度 → 与 A9 存在一致性缺口** | A9 要求 `program_version` 不可空，理由是「Bin 语义随版本变化」（`BK-0001` 待核实 4）；但 `mes_bin_def` 的维度只有 `program_name`，V2 解析也只按 program_name 回退 → **同一程序 v1/v2 若 Bin3 含义不同，解析出的名称不可信**。二选一：① 字典加 `program_version`（维护成本上升）；② 显式写明假设「字典按程序名维护、版本间 Bin 语义差异由人工保证」，并把 D7 快照作为兜底 |

### 本轮未覆盖（待后续轮次）

| 项 | 原因 |
|----|------|
| `mes_test_record_no_seq` 并发取号的行为实测 | 需写库，本轮只读；留待 TD-1 实施期的探针（含并发压测） |
| `GET /test/summary/by-lot/{lotId}` 的前端消费路径 | 属 plan 阶段实现细节，规格层只需契约 |
| 拆批（split）后测试记录的查询语义 | 已登记进 §12 遗留，属 TD-1 规格待补口径 |
