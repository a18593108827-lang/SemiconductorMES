# MES 载具（Carrier）— 架构设计

> 定位：FOUP/Carrier 的**台账 + Lot 绑定 + 过站闸数据源**；物理容器真相在本模块，工艺状态仍只由 Track 写  
> 范式：**Carrier 拥有盒；Lot 只挂引用；Track 只读校验；MCS/Adapter 后置消费事件或 Facade**  
> 产品口径：「TrackIn 载具闸 / prerequisite」——**不**宣称 Carrier Management / 对标 E87 / AMHS  
> 对齐：`docs/架构/半导MES架构设计.md`；业务清单 §7；Lot L2-7；Track T2-3 / T2-5b；产品分析（薄 Carrier = C0+C1）  
> 前提：Lot / Track / History 一期已齐；片级 Wafer、SECS Adapter、MCS **未齐**  
> 状态：**Car-1～3 已落地 · 架构稿**  
> 更新：2026-09-11

---

## 1. 边界

```
Carrier = 台账 + 绑/解 +（可选）位置投影 + SlotMap 占位；对外 CarrierFacade
Lot     = 可冗余 carrier_id 只读投影；禁 PUT 改绑定；绑/解只走 CarrierFacade
Track   = TrackIn 可选强制「已绑」；履历记 carrier_id；不写绑定、不写台账
History = 绑/解 / 位置变更写 mes_tx_log（或 carrier 域审计表 + TrackIn 记引用）
WIP     = 列表可展 carrierCode；不持有绑定逻辑
Dispatch= 可读 carrier 位置作候选过滤（P1 后）；不调度 OHT
Equipment = Port 主数据属 Eqp；Carrier 只记「当前在某 Port/Stocker」引用
MCS/AMHS= P2；本期无搬运任务表、无路径
Adapter = E87/E99 P2；本期无设备侧 ID/SlotMap 强校验
Sorter  = 换盒/理片后置；本期人工/API 绑解即可
```

**本切片做什么（C0+C1）**

- 包 `com.mes.carrier`；对外 **仅** `CarrierFacade`
- 台账：创建 / 改态 / 查；类型 FOUP（可扩）
- 绑/解：一 Lot 一 Carrier；写绑定关系 + 同步 Lot.`carrier_id`
- TrackIn 闸数据：`requireBound` 查询；Track **只调 Facade**
- 履历：BIND / UNBIND（及后续 LOC_CHANGE）可追溯
- Admin `/app/carrier`；现场 Track context 暴露 `carrierId` / `carrierRequired`

**本切片不做什么**

- E87 状态机、RFID 比对（L2）、SlotMap 强一致（L3）
- 一盒多 Lot、空盒池智能调度、清洗寿命闭环
- Store/Retrieve 调度本体、OHT 任务、MCS 对接
- 片表驱动的 ContentMap 校验（无 Wafer 表则 SlotMap 仅占位）

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Track / LotService 直接写 `mes_carrier*` 或旁路绑解 | 容器真相分裂；并发无法统一加锁 |
| P2 | `PUT /lots/{id}` 改 `carrier_id` | 与 Track「状态/关键关系禁 PUT」同构；绑解必须事务化 |
| P3 | Carrier 包改 Lot 工艺状态 / 站别 / qty | 破坏「状态只由 Track 写」 |
| P4 | Track 内实现绑解或「顺带换盒」 | 过站与物流耦合；回滚语义混乱 |
| P5 | 一期引入 MCS 任务表或假装已有 AMHS | 范围绑架硬件；Carrier 排不上 |
| P6 | 对外文案写「E87 / Carrier Management 已支持」 | 未到 L2–L4；交付诚信风险 |
| P7 | 无锁绑解 / 只靠前端防双提交 | 双人解绑+绑定必脏数据 |
| P8 | Dispatch/WIP 注入 Carrier Mapper | 跨域表耦合；迁库改不动 |
| P9 | 绑定表无唯一约束靠应用「先查后写」 | 并发下双 Lot 挂同一盒（一期模型禁止） |

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 形态 | 同进程 `com.mes.carrier`；单体模块化 | 与 Hold/EDC 一致；不拆微服务 |
| D2 | 对外 | 只暴露 `CarrierFacade`；Controller 薄封装 | 高内聚：绑定规则集中一处 |
| D3 | Lot 关系 | **一 Lot ↔ 一 Carrier**（一期）；表唯一约束兜底 | SiView 一盒多 Lot 后置；先可控 |
| D4 | Lot 冗余 | `mes_lot.carrier_id` 由 Facade **同步维护** | TrackIn/列表免每次 join；SSOT 仍在绑定事务 |
| D5 | Track 关系 | Track **只读** `isBound` / `getCarrierId`；闸开关在配置 | 低耦合；Track 清单 T2-3 已定 |
| D6 | 写路径 | 绑/解/改态走 Facade + `@Transactional`；单盒单 Lot 加锁顺序固定 | 并发安全，见 §6 |
| D7 | 位置 | C0 可空；C3 起 `location_type`+`location_ref`；**不**推进工艺站 | 对齐 Store/Retrieve 语义后置 |
| D8 | SlotMap | 表可建、校验默认关；有 Wafer 后再开 L3 | 避免假校验 |
| D9 | 履历 | 绑/解写 `mes_tx_log`（`CARRIER_BIND`/`CARRIER_UNBIND`）或 `mes_carrier_tx`；**定：优先 tx_log 与 Track 同查** | History 调查台一条链 |
| D10 | 事件 | **发布侧** `TransactionSynchronization.afterCommit` 再 `publishEvent`；监听方可用普通 `@EventListener`（不必再 AFTER_COMMIT） | 回滚不发假事件；与 AlarmWs 同模式 |
| D11 | 权限 | HTTP 鉴权；Facade 系统方法不鉴权（Track 调用） | 同 EdcFacade / Hold |
| D12 | 开关 | `mes.carrier.track-in-required` 默认 **false** | 灰度；存量线不挡车 |
| D13 | 标识 | 业务键 `carrier_code` 唯一；内部雪花 `id` | 现场扫码对 code |
| D14 | 状态 | 台账：`AVAILABLE` / `IN_USE` / `QUARANTINE` / `SCRAPPED`（最小集） | 绑定仅允许 AVAILABLE→IN_USE；QUARANTINE/SCRAPPED 拒绑 |

**刻度**：一期只交付 **MES 层 L1**（台账+绑定+TrackIn 非空闸）。L2 读码、L3 SlotMap、L4 E87/多 Lot = 后置里程碑，本架构预留表字段与事件，不预支实现。

---

## 3. 依赖与数据流

```
Admin/现场 ──► CarrierController ──► CarrierFacade
                                      ├── 台账 CRUD / 改态
                                      ├── bind(lotId, carrierId|code)
                                      ├── unbind(lotId)
                                      └── assertBound(lotId) / getBinding(lotId)

TrackIn ──► CarrierFacade.assertBound(lotId)   // 仅当 mes.carrier.track-in-required=true
        └── mes_tx_log 记 carrier_id（Track 写履历，值来自 Facade.getCarrierId）

Lot 列表 / WIP ──► 读 mes_lot.carrier_id（或 Facade 批量 resolve）；禁止写

（后置）MCS / Adapter ──► 听 CarrierChangedEvent 或调 Facade.updateLocation
```

**模块依赖方向（只允许实线）**

```
        Lot(entity) ◄──── CarrierFacade（写 carrier_id）
           ▲
Track ──────┴──── 读 CarrierFacade
WIP / Dispatch ────── 读 Facade 或 Lot 投影
History ────── 读 tx_log（Carrier 写入）

禁止：Track → Carrier Mapper
禁止：Carrier → TrackService（绑解不得触发过站）
禁止：Carrier → Dispatch / MCS 实现类
```

---

## 4. 门面契约

```
CarrierFacade

  // 台账
  create(cmd) → CarrierVO
  update(id, cmd) → CarrierVO          // 不含绑定
  changeStatus(id, toStatus, remark?)  // 状态机校验
  get(id|code) → CarrierVO
  list(query) → Page<CarrierVO>

  // 绑定（写）
  bind(lotId, carrierRef) → BindingVO  // carrierRef = id 或 code
  unbind(lotId) → void                 // 幂等：未绑则成功返回
  unbindByCarrier(carrierId) → void    // 管理端；一期仍一 Lot，行为等同对该 Lot unbind

  // 只读（Track / 其它域）
  getCarrierId(lotId) → Long?          // 未绑 null
  isBound(lotId) → boolean
  assertBound(lotId)                    // 未绑抛 CARRIER_REQUIRED
  getBinding(lotId) → BindingVO?
  resolveCodes(lotIds) → Map<Long,String>  // 列表展码，防 N+1

  // 位置（C3，可空实现）
  updateLocation(carrierId, locationType, locationRef)
```

**错误码（建议）**

| code | 何时 |
|------|------|
| `CARRIER_NOT_FOUND` | id/code 不存在 |
| `CARRIER_STATUS_INVALID` | QUARANTINE/SCRAPPED 上绑；非法迁态 |
| `CARRIER_ALREADY_BOUND` | 盒已绑其它 Lot |
| `LOT_ALREADY_BOUND` | Lot 已绑其它盒（须先解） |
| `CARRIER_REQUIRED` | TrackIn 闸：未绑 |
| `CARRIER_CONCURRENT_MOD` | 乐观锁/版本冲突，请重试 |
| `LOT_NOT_FOUND` / 沿用 Lot 码 | |

Controller 前缀建议：`/carrier`；绑解亦可挂 `POST /lots/{id}/carrier` **但必须委托 Facade**，LotController 内零业务。

---

## 5. 数据模型

### 5.1 `mes_carrier`

| 字段 | 说明 |
|------|------|
| id | 雪花 PK |
| carrier_code | **UK**；扫码/人读 |
| carrier_type | 默认 `FOUP` |
| capacity | 默认 25 |
| status | `AVAILABLE` / `IN_USE` / `QUARANTINE` / `SCRAPPED` |
| clean_status | 可选：`CLEAN` / `DIRTY` / `UNKNOWN`（P2 清洗用） |
| location_type | 可选：`NONE` / `STOCKER` / `PORT` / `OHB` / `MANUAL` |
| location_ref | 可选：库位/Port 编码或 id 字符串 |
| version | **乐观锁** |
| remark / 审计 / deleted | 软删：已绑禁止删，先解再删 |

索引：`uk_carrier_code`；`idx_carrier_status`；`idx_carrier_location (location_type, location_ref)`。

### 5.2 `mes_carrier_binding`（当前绑定；历史靠 tx_log）

| 字段 | 说明 |
|------|------|
| id | 雪花 |
| carrier_id | |
| lot_id | |
| bind_time / bind_by | |
| 审计 | |

约束（并发安全的根）：

- **`uk_binding_lot (lot_id)`** — 一 Lot 一盒  
- **`uk_binding_carrier (carrier_id)`** — 一期一盒一 Lot（多 Lot 放开时改掉此 UK，另开设计）

### 5.3 `mes_lot.carrier_id`

- 可空；**仅** Facade 绑/解时维护；与 `mes_carrier_binding` 同事务一致  
- 读多写少；列表用；冲突以 binding 表为准（启动校验任务可后置）

### 5.4 SlotMap 占位（可同期建表，默认不校验）

`mes_carrier_slot (carrier_id, slot_no, wafer_id?, lot_id?, occupy_status)`  
一期可不写业务；禁止 TrackIn 因空 SlotMap 误拒。

### 5.5 履历

`mes_tx_log`：

| tx_type | payload 要点 |
|---------|----------------|
| `CARRIER_BIND` | lotId, carrierId, carrierCode |
| `CARRIER_UNBIND` | 同上 |
| `CARRIER_LOC` | carrierId, from, to（C3） |

TrackIn 现有日志 **附加** `carrier_id` 字段/JSON，不另造过站类型。

---

## 6. 并发安全

### 6.1 威胁模型

| 场景 | 风险 | 对策 |
|------|------|------|
| 两请求同时 bind 同 Lot 到不同盒 | 双绑定 / 后写覆盖 | Lot 行锁 + `uk_binding_lot` |
| 两请求同时 bind 不同 Lot 到同盒 | 一盒两 Lot（违反一期） | Carrier 行锁 + `uk_binding_carrier` |
| bind 与 unbind 交错 | 状态与 Lot.carrier_id 不一致 | 同事务；先锁后改；状态机 |
| TrackIn 与 unbind 并发 | In 通过时盒已被解 | TrackIn 事务内 `assertBound`；必要时短锁 Lot |
| 双人改台账状态 | 脏状态上绑 | `version` 乐观锁；绑时再次校验 status |
| 重复提交 bind | 双插 | UK；幂等：已是目标绑定则成功返回 |

### 6.2 锁顺序（死锁预防）

**全局约定：先 Lot，后 Carrier**（按 `lot_id`、`carrier_id` 数值序亦可，但跨接口必须同一套）。

`bind(lotId, carrierId)` 伪流程：

```
@Transactional
1. SELECT mes_lot WHERE id=? FOR UPDATE
2. SELECT mes_carrier WHERE id=? FOR UPDATE
3. 校验 Lot 可绑（存在、未合并等，规则与 Lot 域对齐）
4. 校验 Carrier.status == AVAILABLE（或 IN_USE 且已是本 Lot——幂等）
5. 若 Lot 已绑其它盒 → 拒 LOT_ALREADY_BOUND（或不自动换绑；一期禁止静默换绑）
6. 若 Carrier 已绑其它 Lot → 拒 CARRIER_ALREADY_BOUND
7. INSERT binding；UPDATE lot.carrier_id；carrier.status=IN_USE；carrier.version++
8. 写 tx_log CARRIER_BIND
9. AFTER_COMMIT 发事件
```

`unbind(lotId)`：

```
1. SELECT lot FOR UPDATE
2. 若无 carrier_id / 无 binding → return（幂等）
3. SELECT carrier FOR UPDATE
4. DELETE binding；lot.carrier_id=null；carrier.status=AVAILABLE（若非 QUARANTINE）
5. tx_log CARRIER_UNBIND；事件
```

### 6.3 与 Track 并发

- Track 事务已对 Lot `FOR UPDATE`（现网模式）时：`assertBound` 同事务内读 `lot.carrier_id` 或 Facade 只读即可，**不必再开外层锁盒**  
- 若 `assertBound` 独立 REQUIRES_NEW：**禁止**——会导致 In 提交前被解绑的窗口放大；须与 Track **同一事务**  
- 配置关闭闸时：零 Carrier I/O，行为与现网一致

### 6.4 乐观锁 vs 悲观锁

| 资源 | 策略 |
|------|------|
| 绑/解涉及的 Lot + Carrier | **悲观行锁**（冲突高频、要强一致） |
| 台账改 remark/clean、非绑定改态 | **乐观 version** |
| 列表查询 | 无锁；允许短时与绑定轻微延迟（以 binding 为准的读接口可提供） |

唯一约束是最后防线：即使锁顺序 bug，UK 冲突转 `CARRIER_CONCURRENT_MOD` / 已存在码，不可吞掉。

### 6.5 幂等与重试

- 客户端带 `Idempotency-Key`（可选，P1）：同键重复 bind 返回首次结果  
- 死锁 / 版本冲突：Fail Fast + 明确错误码；由前端/现场重试一次；**禁止** Facade 内静默死循环重试超过 1 次

---

## 7. 高内聚 / 低耦合设计要点

**内聚（本包内聚）**

- 台账状态机、绑定不变式、锁顺序、履历类型 **全部在 carrier 包**  
- SlotMap/位置/清洗后续能力仍进本包，不散落到 Lot

**耦合（依赖倒置）**

| 关系 | 方式 |
|------|------|
| Carrier → Lot | 只更新 `carrier_id` 字段或极窄 `LotCarrierPort`；不调 Track |
| Track → Carrier | 只依赖 Facade 接口；禁止 Mapper |
| History | 只消费 tx_log；不依赖 Carrier 实体 |
| 未来 MCS | 只订 AFTER_COMMIT 事件；Carrier 不引用 MCS API |

**稳定抽象**

```
com.mes.carrier.facade.CarrierFacade          // 对外
com.mes.carrier.facade.CarrierBindCommand
com.mes.carrier.domain.*                     // 状态机、不变式
com.mes.carrier.infra.mapper.*               // 仅本包注入
```

其它模块 **允许** 依赖 `facade` + VO；**禁止** 依赖 `infra`。

---

## 8. 状态机（台账）

```
AVAILABLE ──bind──► IN_USE ──unbind──► AVAILABLE
    │                   │
    ├──quarantine──► QUARANTINE ──release──► AVAILABLE
    └──scrap──────► SCRAPPED（终态）

IN_USE ──quarantine──► QUARANTINE（须先业务确认；建议强制先 unbind 或同事务解绑）
```

一期建议：**QUARANTINE / SCRAPPED 前必须无绑定**；降低「脏盒仍挂 Lot」的事故面。

---

## 9. HTTP / 权限 / 配置

| 项 | 选择 |
|----|------|
| 前缀 | `/carrier`；兼容 `POST/DELETE /lots/{id}/carrier` |
| 台账读 | `carrier:view` |
| 台账写 / 改态 | `carrier:edit` |
| 绑解 | `carrier:bind`（可与 `lot:edit` 二选一，**定：独立 `carrier:bind`**） |
| Track 闸 | 无额外权限；随 `track:track-in` |
| 菜单 | Admin `/app/carrier`；现场不进台账主菜单，只在过站区显示 |
| 配置 | `mes.carrier.track-in-required` 默认 false |
| 配置 | `mes.carrier.enabled` 默认 true；false 时 Facade 绑解拒写、assertBound 空操作（应急） |

---

## 10. 行为清单（实现必须遵守）

1. 一切绑解进 `CarrierFacade`；唯一写手。  
2. 锁顺序 **Lot → Carrier**；同事务写 binding + lot.carrier_id + carrier.status。  
3. 一期 `uk_binding_lot` + `uk_binding_carrier` 双唯一。  
4. 禁止静默换绑：已绑其它目标必须先 unbind。  
5. `unbind` / 目标相同 `bind` 幂等成功。  
6. Track `assertBound` 与 TrackIn **同事务**；开关默认关。  
7. 写 tx_log；AFTER_COMMIT 再发领域事件。  
8. Facade 供 Track 的方法不鉴权；HTTP 层鉴权。  
9. Soft delete 前检查无绑定。  
10. 不在 Carrier 包调用 TrackService / 改站别。

---

## 11. 分期

| 阶段 | 范围 | 出口标准 |
|------|------|----------|
| **C0** | 表 + Facade 台账 + bind/unbind + Lot.carrier_id + Admin 最小页 | 绑得上、解得下、UK 防双挂 |
| **C1** | TrackIn 闸 + context 字段 + tx_log 带 carrier | 开关开时未绑拒 In；履历可查 |
| **C2** | 现场扫码比对 CarrierCode | 防拿错盒（L2） |
| **C3** | location + Store/Retrieve 记位 | 货在哪 |
| **C4** | SlotMap 与片表一致 | L3 |
| **C5** | 事件 → MCS；E87 Adapter | 物流/联机 |

开工顺序：C0 → C1（建议同迭代）→ 按客户要 C2/C3 → 片级后 C4 → Adapter 后 C5。

---

## 12. 验收（架构级）

| # | 场景 | 期望 |
|---|------|------|
| A1 | 开关关，未绑 Lot TrackIn | 与现网一致通过 |
| A2 | 开关开，未绑 | `CARRIER_REQUIRED` |
| A3 | 并行两请求绑同 Lot 到两盒 | 仅一成功；另一业务错或并发错 |
| A4 | 并行两 Lot 绑同盒 | 仅一成功 |
| A5 | 绑后 Lot.carrier_id 与 binding 一致 | 同事务可读 |
| A6 | Track/Lot 无 Carrier Mapper 引用 | 编译期/包规依赖检查 |
| A7 | 解绑后再绑 | 状态 AVAILABLE→IN_USE 正确 |
| A8 | QUARANTINE 盒 bind | 拒绝 |

---

## 13. 风险

| 风险 | 缓解 |
|------|------|
| 冗余 `lot.carrier_id` 漂移 | 同事务写；解绑双写对齐并收回漂移盒；`assertBound`/`isBound` 认 binding；孤儿 IN_USE 可再绑收回 |
| 过早开闸挡生产 | 默认 false；按产线配置 |
| 一盒多 Lot 需求提前 | 本架构 UK 为一期不变量；放开须新设计评审 |
| 与 MCS 双写位置 | 位置只 Facade 写；MCS 回传也走 updateLocation |
| 文档/销售超卖 E87 | 产品口径写进发布说明；本设计扉页已钉死 |

---

## 14. 文档入口

| 文档 | 路径 |
|------|------|
| 本架构 | `docs/模块/Carrier（载具）模块/MES-Carrier架构设计.md` |
| 一期清单 | `docs/模块/Carrier（载具）模块/MES-Carrier一期功能清单.md` |
| 业务清单 §7 | `docs/业务清单/MES-半导体业务清单.md` |
| Track 闸 | `docs/模块/Track（执行引擎）模块/MES-Track二期功能清单.md` §2.4 |
| Lot 绑定口 | `docs/模块/Lot（批次）模块/MES-Lot二期功能清单.md` §3.2 |
| 总架构 | `docs/架构/半导MES架构设计.md` |

落地时另补：DDL、`CarrierFacade` 接口设计（方法级错误码与 VO）；切片见 `MES-Carrier一期功能清单.md`。
