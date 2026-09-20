---
type: 接口设计
module: History
status: done
slices: [CP-1, CP-2, CP-3]
aligns: []
updated: 2026-09-20
---

# MES 客诉追溯包（Complaint Trace Package）— 接口设计

> 定位：以锚点 Lot 组装「影响面 + 履历摘要 + 可选遏制」的可交证据包；支撑客诉 / 8D D3 / 客户审计  
> 归属：**History 编排**（只读装配）；谱系真相在 Lot；锁批真相在 Hold；**禁止**本包写工艺状态  
> 对齐：`MES-LotGenealogy接口设计.md` GN-6 · `MES-History一期功能清单.md` P2 · 业务清单 §10  
> 业界：Critical Manufacturing Genealogic（正反向 + 多 Lot 履历）；GE Vernova as-built + recall 缩面；8D D3 Containment  
> 前提：Genealogy P0 ✅ · History H-1～5 ✅ · Hold 最小集 ✅  
> 更新：2026-09-20（CP-3 对齐：包号数字后缀 max + UK 重读 max 带抖动、装配失败隔离、Alarm 批量只读、build 复用 flatten 树）  
> 状态：**CP-1 ✅ · CP-2 ✅ · CP-3 ✅** · CP-4+ 未做  
> **易混：** 客诉包 ≠ YMS；≠ 片级 / SEMI T23；≠ 8D 全流程系统；≠ 跨厂联邦数据

---

## 0. 目标与产品边界

**一句话：** 从嫌疑 Lot 一键圈出家族影响面，打包可交证据；可选对影响面批量 Hold 做 interim containment。

产品口径：对外称「客诉追溯包 / Investigation Package」——**不**称 Genealogic / eDHR / SEMI T23 Device History。

| 做（本设计） | 不做（明确后置 / 禁宣称） |
|-------------|---------------------------|
| 锚点 Lot → 上下游影响面 Lot 清单（复用 genealogy） | 片 / Die / ECID / SlotMap |
| 每批履历摘要 + 锚点完整时间线 | 跨 Foundry / OSAT 联邦查询 |
| Hold / Alarm / Scrap 摘要（只读 Facade） | Yield 分析 / PAT / GDBN |
| 导出 JSON（P0）/ ZIP+清单 PDF 文（P1） | 邮件推送客户、企微机器人 |
| 可选：影响面批量 Hold（遏制） | 自动 CAPA / 完整 8D 工作流 |
| 包头审计（谁、何时、锚点、成员快照） | AI 预测遏制、规则引擎 |

对标大厂拆解：

```
Genealogic 反向 RCA     → 本包 §5 preview / §6 package.genealogy
Genealogic 正向缩召回面 → 本包 impactLots（down）
as-built / 审计证据     → 本包 histories + holds + alarms + scraps
8D D3 Containment       → 本包 §7 contain（可选）
「一扫生命周期报告」    → 本包 export（P0 JSON；P1 文件）
```

---

## 1. 架构约束（必须遵守）

| # | 约束 | 说明 |
|---|------|------|
| A1 | 高内聚 | 圈人、装配、导出、触发遏制的**编排**全部经 `ComplaintPackageFacade`；禁止 Controller / 前端拼装多源当真相。包内可拆 Allocator / Writer / Assembler，不算出界 |
| A2 | 低耦合 | Facade **只**依赖：`MesLotService`（或 Lot 只读谱系门面）、`HistoryFacade`、`HoldService`、`AlarmFacade`；**零** `mes_tx_log` / `mes_lot_genealogy` / `MesHoldMapper` / `MesAlarmMapper` |
| A3 | 读 SSOT | 谱系边只认 genealogy；履历只认 HistoryFacade；锁态只认 Hold；禁止本包缓存「是否 held」当写后真相 |
| A4 | 写正交 | 本包**永不**改 Lot.status / WIP / Route / Track 门禁；遏制**只**经 `HoldService.create` |
| A5 | 装配无工艺写副作用 | `preview` / 装配 / `export` 不改 Lot.status / WIP / Track；不得因导出隐式 Hold。`build` **只写**本包审计表（包头+成员），不算工艺写 |
| A6 | 影响面算法唯一 | 成员 Lot 集合**只**由 genealogy 遍历得到；禁止前端传任意 lotId 列表冒充影响面（contain 可再收窄，见 §7） |
| A7 | 深度上限 | `depth` 默认 5、上限 20（与 Genealogy 对齐）；超限截断并在 VO 标 `truncated=true` |
| A8 | 成员上限 | 单包成员 Lot ≤ **200**；超限拒绝并提示缩小 depth / 换锚点（防拖垮导出与批量 Hold） |
| A9 | 幂等 | 同锚点重复 `build` 生成新 `packageId`（每次一包）；`contain` 对已 active Hold 的 Lot **跳过**，不抛、计入 `skipped` |
| A10 | 灰度 | 功能开关 `mes.complaint-package.enabled` 默认 **false**；关则 HTTP 拒 `COMPLAINT_PACKAGE_DISABLED` |
| A11 | 并发 | 见 §8；读无长锁；写遏制锁序 LotId 升序；禁止无序并行 `HoldService.create` |

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | History / Complaint 包直改 `mes_lot` / 自插 `mes_hold` | 锁批语义分裂 |
| P2 | Track / LotController 内嵌打包逻辑 | 旁路 Facade；A1 崩 |
| P3 | 导出事务内持有多 Lot 行锁拼 JSON | 长事务；与现场过站死锁 |
| P4 | 前端自选 ton 级 lotId 列表强制写进包 | 影响面可伪造；与 A6 冲突 |
| P5 | 宣称 eDHR / SEMI T23 / Device-level DHR | 口径越界 |
| P6 | 把 Scrap/Bonus 画进谱系树当边 | 与 Genealogy 原则冲突 |
| P7 | 包内存「冻结履历全文」当唯一真相且不允许按现网重算 | 真相在 tx_log；包只存成员快照 + 元数据 |
| P8 | contain 失败回滚已成功 Hold 的其它 Lot（要求全局原子） | 大厂 interim containment 允许部分成功；须返回明细 |
| P9 | Facade 同类自调用 / 私有方法 `@Transactional` / **`build()` 自身带事务** / Writer 事务内 catch 后继续插 | 代理无效或 rollback-only：包头已插成员失败不回滚，或撞 `UnexpectedRollbackException` |

---

## 2. 模块边界

```
ComplaintPackageFacade（编排；包内 Allocator / Writer / Assembler）
  ├── MesLotService.flattenImpact（含已建树）/ genealogy（仅 get 现查）
  ├── HistoryFacade.listByLot / query（只读）
  ├── HoldService.listByLot 或等价只读 + create（遏制时）
  ├── AlarmFacade.countUnclearedForLots / listUnclearedForLots（只读）
  └── 本地：mes_complaint_package + member（包头审计，非履历真相）

Track   = 不感知本包；继续只写 tx_log / genealogy
Lot     = 谱系 SSOT；不负责打包
Hold    = 锁批 SSOT；contain 唯一写入口
Alarm   = 可选摘要；不因本包 raise
Report  = 不替代本包；本包是事件驱动调查，非窗统计
UI      = History 调查台 / Lots 详情「生成追溯包」；只调 Facade HTTP
```

**原则延续**

- 图归身份（Genealogy），线归履历（History），刹归 Hold  
- 状态只由 Track / Hold 既有管道改；本包是**编排与证据**  
- 单库强一致；读侧直读主库；不引入独立 History 库（后置）

---

## 3. 范围总览与切片

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | DDL 包头 + 成员；开关；权限 | ✅ CP-1 |
| P0 | `preview`：影响面 + 计数摘要（不落库） | ✅ CP-2 |
| P0 | `build`：落包头/成员 + 装配 VO | ✅ CP-3 |
| P0 | `list`：包分页查询（Admin 入口数据源） | ✅ CP-3 |
| P0 | `export`：JSON 下载（含清单） | ⏳ |
| P0 | Admin：History / Lot 入口生成与下载 | ⏳ |
| P1 | `contain`：对成员（或子集）批量 Hold | ⏳ |
| P1 | ZIP（JSON + 简易 PDF/HTML 封面） | ⏳ |
| P2 | 家族一键 Hold 深链、客诉编号对接 QMS | 后置 |
| P2 | 片级成员、出货客户映射 | 后置（依赖片表 / 出货） |

| 切片 | 交付 | 依赖 |
|------|------|------|
| CP-1 | DDL + 权限 + `enabled`；空 Facade 骨架 | ✅ |
| CP-2 | 影响面展开算法 + `preview` | ✅ |
| CP-3 | `build` + 包头审计 + 装配 VO + `list` 分页 | ✅ History / Hold 只读；Alarm `listUnclearedForLots`；Lot flatten 回带树 |
| CP-4 | `GET export` JSON；Admin 按钮 | CP-3 |
| CP-5 | `contain` + 锁序 + 部分成功明细 | Hold |
| CP-6 | ZIP / 封面（可选） | CP-4 |

顺序：**CP-1 → 2 → 3 → 4**；**CP-5 不得先于 2**（无影响面不能遏制）；CP-6 可后于 4。

---

## 4. 数据

### 4.1 `mes_complaint_package`（包头 · 审计）

| 字段 | 说明 |
|------|------|
| id | PK = `packageId` |
| package_no | 业务号 `CP-yyyyMMdd-序号`；UK |
| anchor_lot_id | 锚点 Lot |
| anchor_lot_no | 冗余便查（生成时快照） |
| direction | `up` / `down` / `both`（生成时参数） |
| depth | 生成时 depth |
| member_count | 成员数快照 |
| truncated | 0/1 是否触达 depth/成员上限截断 |
| reason_code | 客诉/调查原因码（白名单，可空） |
| remark | 备注 |
| status | `READY` / `CONTAINING` / `CONTAINED` / `VOID`（P0 仅 READY；contain 后 CONTAINED） |
| create_by / create_time | 审计 |
| contain_by / contain_time | 可选；首次 contain 成功写入 |

**真相顺序：** 成员身份关系仍以 `mes_lot_genealogy` 为准；包头只记录「当时圈了谁」。

### 4.2 `mes_complaint_package_member`

| 字段 | 说明 |
|------|------|
| id | PK |
| package_id | FK |
| lot_id / lot_no | 成员 |
| relation | `ANCHOR` / `ANCESTOR` / `DESCENDANT` |
| depth_from_anchor | 0=锚点 |
| qty_snapshot / status_snapshot | 生成时快照（展示用，非运行态真相） |

UK：`(package_id, lot_id)`。

### 4.3 不落库

- 履历全文、Alarm 全文：每次 `get` / `export` **现查** Facade（避免双真相）  
- 可选：export 文件落磁盘 **不做**（P0 流式响应）

### 4.4 权限种子

| code | 说明 |
|------|------|
| `complaint:view` | preview / get / export |
| `complaint:build` | build |
| `complaint:contain` | contain（强权限；建议与 `hold:create` 同时具备才开放按钮） |

---

## 5. 影响面算法

输入：`anchorLotId`、`direction`、`depth`。

```
1. 校验 Lot 存在；开关开启
2. 调谱系：与 GET /lots/{id}/genealogy 同语义展开
3. 展平为 member 集合（去重 lotId）
4. 锚点 relation=ANCHOR depth=0；其余按 up/down 标记
5. size > 200 → 业务错 COMPLAINT_PACKAGE_TOO_LARGE
6. depth 触顶仍有边 → truncated=true（仍返回已圈成员）
```

**禁止**自行 DFS Mapper；必须走 Lot 已有谱系实现（或抽 `LotGenealogyQuery` 只读组件供 LotService 与 Facade 共用——抽公共时仍零 Mapper 泄漏到 Complaint 包）。

预览与 build **同一算法**（`flattenImpact`）；build 将结果落入 member 表。`flattenImpact` 须把已建树挂到返回值（`MesLotImpactFlatVO.tree`），build 同请求装配 `genealogy` **复用该树**，禁止再走一遍 `genealogy()`。get 的 `genealogy` 块现查 `MesLotService.genealogy`（成员以表为准，树允许与快照略漂）。

---

## 6. API

前缀：`/complaint-packages`  
开关关：一律 `COMPLAINT_PACKAGE_DISABLED`。

### 6.1 Preview（不落库）

`POST /complaint-packages/preview`  
权限：`complaint:view`

```json
{
  "anchorLotId": 100,
  "direction": "both",
  "depth": 5
}
```

响应：`ComplaintPackagePreviewVO`

| 字段 | 说明 |
|------|------|
| anchorLotId / lotNo | 锚点 |
| members[] | lotId/lotNo/relation/depth/status/qty |
| memberCount / truncated | |
| summary | activeHoldCount、openAlarmCount、scrapLotCount（只读聚合） |

### 6.2 Build（落库）

`POST /complaint-packages`  
权限：`complaint:build`

Body：preview 字段 + 可选 `reasonCode` / `remark`。

响应：`ComplaintPackageVO`（含 `packageId` / `packageNo` + members + 可选摘要块）。

装配块（现查，可空列表）：

| 块 | 来源 | 上限 |
|----|------|------|
| `genealogy` | build 复用本次 flatten 树；get 现查 `genealogy()` | depth 同包 |
| `historiesByLot` | 每成员 `HistoryFacade.listByLot`（本侧切尾端） | 每 Lot 最近 **100** 条 |
| `holdsByLot` | `HoldService.listByLot`，本侧按 `active` / `released` 拆 | 各 20 |
| `alarmsByLot` | `AlarmFacade.listUnclearedForLots`（一次 IN；`lastRaiseAt DESC, id DESC`） | 每 Lot 未关闭 20 |

### 6.3 Get

`GET /complaint-packages/{id}`  
权限：`complaint:view`  
现查装配块（与 build 响应同形）；成员以表为准。

### 6.4 Export

`GET /complaint-packages/{id}/export?format=json`  
权限：`complaint:view`  
`Content-Disposition: attachment; filename="{packageNo}.json"`  

JSON 根对象 = Get VO + `exportedAt` + `exportedBy`。  
P1：`format=zip` → JSON + `README.txt`（包号、锚点、成员数、生成时间）。

### 6.5 Contain（P1）

`POST /complaint-packages/{id}/contain`  
权限：`complaint:contain` + 实际执行时仍走 `HoldService` 鉴权语义（服务层以系统/当前用户写 create）。

```json
{
  "lotIds": null,
  "reasonCode": "CUSTOMER_COMPLAINT",
  "remark": "客诉遏制 CP-…"
}
```

| 字段 | 说明 |
|------|------|
| lotIds | `null`/空 = 包内全部成员；非空则必须 ⊆ 成员集合，否则拒 |
| reasonCode | Hold 原因码（须已启用）；种子建议 `CUSTOMER_COMPLAINT` |
| remark | 建议带 `packageNo` |

响应：`ComplaintContainResultVO`：`succeeded[]` / `skipped[]`（已 held）/ `failed[]`（lotId + code + message）。

### 6.6 List（分页）

`GET /complaint-packages`  
权限：`complaint:view`

查询参数（均可选）：`anchorLotId`、`status`（READY/CONTAINING/CONTAINED）、`from`/`to`（create_time 范围）、`page`（默认 1）、`size`（默认 20、上限 **截成** 100，不 400）。

响应：`PageResult<ComplaintPackageListVO>` —— 包头摘要（packageId / packageNo / anchorLotNo / direction / depth / memberCount / truncated / status / createTime），**不含成员明细**（点开走 §6.3 Get）；按 create_time 倒序。

### 6.7 说明

build 与 Get 响应**同形**（单一 `ComplaintPackageVO`）；装配块（genealogy / historiesByLot / holdsByLot / alarmsByLot）在 **insert 事务提交后**组装——事务只包包头+成员写入，禁止把装配查询裹进未提交事务（见 §8.1）。

写入事务必须落在独立 Writer Bean 的 public `@Transactional` 或 `TransactionTemplate` 上；禁止 Facade 同类自调用 / 私有方法事务 / **`build()` 带 `@Transactional`**（外层事务会使 catch UK 后仍 rollback-only）。UK 冲突必须在 Writer 代理外捕获，只改 `package_no` 再调 Writer public 方法；禁止在 Writer 事务内 catch 后继续插。

**HTTP 成功边界：** 包头+成员提交成功即 build 成功。装配按块、按 Lot 失败隔离（该块/该 Lot 空列表，其它继续）；失败必记 WARN（packageNo + lotId + 块名）。不得因某成员 `listByLot` 404 把已落库的包打成 500（否则客户端按 A9 再 build 会留下孤儿包）。

---

## 7. 遏制语义（Containment）

对齐 8D D3：**先隔离嫌疑，再查根因**；与 Track 门禁正交（仍只认 active Hold）。

```
preview/build（不遏制） → 人确认影响面
       ↓
contain → 对每个目标 Lot：HoldService.create
       ↓
已 held → skip；create 成功 → succeeded；业务/并发错 → failed（继续下一个）
```

**禁止** contain 改 genealogy、禁派、禁出货主数据。  
**禁止** 要求「全部成功才提交」的大事务（见 P8 / §8）。

包状态：任一次 contain 存在 succeeded → `CONTAINED`；仅 skipped 保持原状；全 failed 不改 status。

---

## 8. 并发与一致性

### 8.1 只读路径（preview / build 装配 / export）

| 项 | 约定 |
|----|------|
| 锁 | **不加** Lot / Hold 行锁 |
| 隔离 | 默认读已提交；允许成员在导出瞬间被他处 Split（包内 status_snapshot 允许与现态略漂） |
| 超时 | 单请求软限：成员×每 Lot 履历条数；超成员上限在算法层拒 |
| 与现场 | 导出不得阻塞 TrackIn/Out |

build 写包头/成员：单事务插入 package + members；**不**锁业务 Lot。

### 8.2 contain 写路径

| 项 | 约定 |
|----|------|
| 锁序 | 目标 `lotId` **升序**逐个处理；每个 `HoldService.create` 自带该 Lot 锁（复用 Hold 实现） |
| 事务 | **每 Lot 独立事务**（`REQUIRES_NEW` 或 Facade 循环调带事务的 create）；一笔失败不回滚其它成功 |
| 死锁 | 禁止多线程无序并行 contain 同一包；同包 contain **串行**（包行 `SELECT … FOR UPDATE` 或 status=`CONTAINING` 乐观占位） |
| 占位 | 进入 contain：`READY|CONTAINED` → `CONTAINING`；结束改回 `CONTAINED` 或原状态；崩溃残留 `CONTAINING` → 下次 contain 允许抢（超时阈值秒可覆盖） |
| 幂等 | 已 active → skip；重复 contain 安全 |
| 与 Track | Hold.create 与 Track 锁序遵循现网 Hold 契约；本包不引入第二锁序 |

### 8.3 包号并发

`package_no` 格式 `CP-yyyyMMdd-序号`；日期取 JVM `LocalDate.now()`（与现网 DATETIME 同一时钟，单时区）。

候选 = 当日 **数字后缀** max(seq)+1（无行则 1）。取数：

```sql
SELECT MAX(CAST(SUBSTRING_INDEX(package_no, '-', -1) AS UNSIGNED))
FROM mes_complaint_package
WHERE package_no LIKE CONCAT('CP-', #{ymd}, '-%')
```

或查出当日号后在 Java 解析后缀。**禁止** `ORDER BY package_no DESC LIMIT 1`（无零填充时 `…-9` > `…-10`）。**禁止** `COUNT(*)+1`。

序列化手段 = `INSERT` 遇 `uk_complaint_package_no` 时，在 Writer 代理外 **重读 max(seq)+1 + `ThreadLocalRandom.nextInt(3)`**，只改 `package_no` 后重开写入事务，≤3，耗尽 `COMPLAINT_PACKAGE_NO_CONFLICT`。冲突后重读 MAX **必须**（不是 COUNT；纯 seq++ 会同频 herd）。

**禁止** 无 UK 保护的 max+1 落库。成员 UK `(package_id, lot_id)` 不得走此重试（算法 bug，换错码）。

---

## 9. 错误码（业务）

| code | 场景 |
|------|------|
| `COMPLAINT_PACKAGE_DISABLED` | 开关关 |
| `COMPLAINT_PACKAGE_NOT_FOUND` | id 无效 |
| `COMPLAINT_PACKAGE_TOO_LARGE` | 成员 > 200 |
| `COMPLAINT_PACKAGE_LOT_NOT_IN_PACKAGE` | contain 的 lotIds 越界 |
| `COMPLAINT_PACKAGE_CONTAIN_IN_PROGRESS` | 他请求正在 contain 同包 |
| `COMPLAINT_PACKAGE_NO_CONFLICT` | `uk_complaint_package_no` 重试耗尽（≤3）；成员 UK 不得用此码 |
| `COMPLAINT_PACKAGE_VOID` | 已作废不可 contain/export（若做 VOID） |

Lot 不存在等复用现网 404 / Lot 错码。

---

## 10. 前端

| 入口 | 行为 |
|------|------|
| `/app/history` | Lot 模式：按钮「生成追溯包」→ preview 抽屉 → 确认 build → 下载 |
| Lots 详情 | 同；可深链 `?packageId=` |
| 文案 | 「追溯包 / 影响面 / 下载证据」；禁止「良率包」「eDHR」 |
| contain | 二次确认；展示将 Hold 的批次数；结果表 succeeded/skipped/failed |

---

## 11. 配置

| key | 默认 | 说明 |
|-----|------|------|
| `mes.complaint-package.enabled` | `false` | 总开关 |
| `mes.complaint-package.max-members` | `200` | 可配，硬上限建议 ≤500 |
| `mes.complaint-package.history-per-lot` | `100` | 装配履历条数 |

---

## 12. 验收

1. 开关关：一切 API 拒；现网零行为差  
2. 纯 Split 链：锚点子批 preview 含父；depth 截断时 `truncated=true`  
3. Merge 源为锚点：up 回到主批（与 Genealogy 最新边语义一致）  
4. build 后 member 与当时 preview 一致；随后他处再 Split **不改**已落包成员  
5. export JSON 含 packageNo、锚点、成员、履历块；无 Yield/OEE 字段  
6. contain：已 held 进 skipped；未 held 进 succeeded；非法 lotId 拒整单  
7. 两请求同时 contain 同包：一侧 `CONTAIN_IN_PROGRESS` 或串行成功；无死锁  
8. Complaint 包 **零** tx_log / genealogy / Hold / Alarm Mapper 引用（依赖检查）  
9. Track 包无 Complaint 依赖  
10. list 按 create_time 倒序；anchorLotId / status / 时间范围筛选生效；size>100 截成 100  
11. 成员 insert 失败则无孤立包头；同日并发 build `package_no` **唯一**（`NO_CONFLICT` 偶发可接受）  
12. 某成员履历/Hold 查询失败：包仍可 get，members 完整；装配失败有 WARN 日志  

---

## 13. 与现网文档挂钩

| 文档 | 关系 |
|------|------|
| `MES-LotGenealogy接口设计.md` | GN-6 由本设计承接 |
| `MES-History一期功能清单.md` | P2 客诉包 |
| `MES-History功能文档.md` | 调查台入口 |
| `MES-半导体业务清单.md` §10 | 客诉追溯包导出 |

---

## 14. 实施备注（给开发）

- 包名：现网已是 `com.mes.complaint`；Facade 名 `ComplaintPackageFacade`  
- 包内拆 `ComplaintPackageNoAllocator` / `ComplaintPackageWriter` / `ComplaintPackageAssembler`；Facade 只编排；`build()` 不加 `@Transactional`  
- 装配并行：成员履历可用有界线程池并行查（CP-3 先串行），但 **contain 禁止并行写**  
- 大包 export：流式写 JSON，避免一次性巨型 VO 撑爆堆  
- 原因码种子 `CUSTOMER_COMPLAINT` 归 Hold 字典；本包不自建第二字典  
