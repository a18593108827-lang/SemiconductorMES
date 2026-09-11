# MES 载具（Carrier）— 一期功能清单

> 前提：Lot / Track / History 一期已齐；片级 Wafer、SECS Adapter、MCS **未齐**  
> 对齐：`MES-Carrier架构设计.md` · 业务清单 §7 · Lot L2-7 · Track T2-3 / T2-5b · 总册 §5.13  
> 更新：2026-09-10  
> 状态：**Car-1～3 ✅；Car-4～5 ⏳**

---

## 0. 目标

交付**薄 Carrier（C0+C1）**：系统认识盒子、Lot 能绑/解、TrackIn 可按配置强制已绑，履历可查 `carrier_id`。

原则：

- 对外只 `CarrierFacade`；Track **只读**闸，不写绑定
- 一 Lot ↔ 一 Carrier；双唯一约束 + 锁序 Lot→Carrier
- 禁 `PUT` 改 `carrier_id`；禁宣称 E87 / AMHS / Carrier Management
- `mes.carrier.track-in-required` 默认 **false**（存量不挡车）
- 不挡未开闸产线；不开 SlotMap 强校验、不调度 OHT

产品口径：对外称「TrackIn 载具闸 / prerequisite」。

---

## 1. 范围总览

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | 表 `mes_carrier` + `mes_carrier_binding`；`mes_lot.carrier_id` | ✅ |
| P0 | `CarrierFacade` 台账 CRUD / 改态 | ✅ |
| P0 | bind / unbind；同步 Lot.`carrier_id`；tx_log | ✅ |
| P0 | 权限 `carrier:view` / `edit` / `bind`；HTTP `/carrier` | ⏳ Car-1 权限✅；HTTP→Car-4 |
| P0 | TrackIn 闸 + context `carrierId`/`carrierRequired` | ⏳ |
| P0 | Admin `/app/carrier`；Lot/现场展示绑定 | ⏳ |
| P1 | 现场扫码比对 CarrierCode（L2） | 后置 |
| P1 | 位置 Store/Retrieve（C3） | 后置；随 Stocker |
| P2 | SlotMap 强校验 / 一盒多 Lot / MCS / E87 | 后置 |

---

## 2. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| Car-1 | DDL + 权限种子 + 配置项；实体/Mapper | ✅ |
| Car-2 | `CarrierFacade` 台账 + 改态状态机 | ✅ |
| Car-3 | bind / unbind + 锁序 + UK + tx_log + Lot 同步 | ✅ |
| Car-4 | HTTP `/carrier`（+ 可选 `/lots/{id}/carrier` 委托） | ⏳ |
| Car-5 | TrackIn 闸 + context；Admin 页 + 菜单 | ⏳ |

顺序：**Car-1 → 2 → 3 → 4 → 5**。  
**禁止** Car-3 先于 Car-1（无 UK 必脏绑）。  
**禁止** Car-5 闸默认 true。  
**禁止** 任一切片引入 MCS 任务表 / E87 / 一盒多 Lot / SlotMap 强拒。  
**禁止** Track / LotService 写 `mes_carrier*`。

已有库上线前执行 `migrate_carrier.sql`（表 + `mes_lot.carrier_id` + 权限）；并入 `schema.sql`。

---

### Car-1（表 / 权限 / 配置）✅

#### 1.1 交付

- `mes_carrier`、`mes_carrier_binding`（字段锁死架构 §5）✅
- `mes_lot.carrier_id` 可空列 + 索引 ✅
- 约束：`uk_carrier_code`；`uk_binding_lot(lot_id)`；`uk_binding_carrier(carrier_id)` ✅
- 权限种子：
  - `carrier:view`（320）
  - `carrier:edit`（321）
  - `carrier:bind`（322）
- 配置：
  - `mes.carrier.enabled` 默认 true ✅
  - `mes.carrier.track-in-required` 默认 **false** ✅
- 实体 / Mapper；**无**业务绑解、无 HTTP、无前端 ✅

落地：`migrate_carrier.sql` · `schema.sql` · `MesCarrier` / `MesCarrierBinding` · `MesCarrierMapper` / `MesCarrierBindingMapper` · `MesLot.carrierId` · `application.yml`

#### 1.2 本切片不做

- Facade 业务、Track 改动、Admin 页
- `mes_carrier_slot` 可建可不建；**建了也不校验**

#### 1.3 验收（Car-1）

- [x] migrate 可执行（`ALTER` 列已存在时报错可忽略该句）
- [x] 双 UK 写在 DDL
- [x] 配置键可读；默认 `track-in-required=false`

---

### Car-2（台账 Facade）✅

#### 2.1 交付

- 包 `com.mes.carrier`：`CarrierFacade` / `CarrierFacadeImpl` ✅
- `create` / `update`（不含绑定）/ `changeStatus` / `get` / `getByCode` / `list` ✅
- 状态机：`AVAILABLE` / `IN_USE` / `QUARANTINE` / `SCRAPPED` ✅
- 乐观锁 `version`：台账非绑定更新 ✅
- `mes.carrier.enabled=false` → 写接口拒 `CARRIER_DISABLED` ✅
- **本切片无 bind/unbind**（Car-3）✅

落地：`CarrierFacade` · `CarrierFacadeImpl` · `CarrierCreateDTO` / `UpdateDTO` / `Query` · `CarrierVO`

#### 2.2 口径锁死

| 项 | 锁死 |
|----|------|
| 业务键 | `carrier_code` 唯一；类型默认 `FOUP`；capacity 默认 25 |
| 改态 | SCRAPPED 终态；QUARANTINE/SCRAPPED 前若已有绑定 → 拒（须先解） |
| IN_USE | **仅** bind 路径写入；禁止手改 IN_USE；IN_USE 态须先解绑再改 |

#### 2.3 本切片不做

- 绑解、Track、前端、HTTP（Car-4）

#### 2.4 验收（Car-2）

- [x] Facade 可 create/list/get
- [x] 非法迁态 / 手改 IN_USE 拒绝
- [x] version 冲突 → `CARRIER_CONCURRENT_MOD`

---

### Car-3（绑 / 解）✅

#### 3.1 交付

- `bind(lotId, carrierRef)` / `unbind(lotId)` / `unbindByCarrier` ✅
- 事务内：`SELECT Lot FOR UPDATE` → `SELECT Carrier FOR UPDATE` → 写 binding + `lot.carrier_id` + `carrier.status` ✅
- 幂等：已是目标绑定 → 成功；未绑 unbind → 成功 ✅
- 禁止静默换绑：已绑其它目标 → `LOT_ALREADY_BOUND` / `CARRIER_ALREADY_BOUND` ✅
- `tx_log`：`CARRIER_BIND` / `CARRIER_UNBIND` ✅
- AFTER_COMMIT 可消费的 `CarrierChangedEvent`（已 publish）✅
- 只读：`getCarrierId` / `isBound` / `assertBound` / `getBinding` / `resolveCodes` ✅

落地：`CarrierFacade` 扩展 · `CarrierFacadeImpl` · `CarrierBindingVO` · `CarrierChangedEvent`

#### 3.2 口径锁死

| 项 | 锁死 |
|----|------|
| 模型 | 一 Lot 一盒；一盒一 Lot |
| 可绑状态 | Carrier 仅 `AVAILABLE`（幂等：已是本 Lot 的 `IN_USE` 也可） |
| Lot | 不改工艺状态/站别/qty；merged/scrapped 拒绑 |
| 锁序 | **先 Lot 后 Carrier** |
| assertBound | 供 Track 同事务调用；模块关闭空操作；Track 接线在 Car-5 |

#### 3.3 本切片不做

- HTTP（Car-4）；Track 开闸（Car-5）；扫码比对

#### 3.4 验收（Car-3）

- [x] 绑后同步 `lot.carrier_id`；盒 `IN_USE`
- [x] 解后清空；盒 `AVAILABLE`（非隔离）
- [x] 双 UK + 行锁防并发双挂
- [x] tx_log BIND/UNBIND
- [ ] 联调并行压测（可选）

---

### Car-4（HTTP）⏳

#### 4.1 交付

- `MesCarrierController` 前缀 `/carrier`
- 台账：list/get/create/update/changeStatus
- 绑解：`POST /carrier/bind`、`POST /carrier/unbind`（或 REST 子资源）
- 兼容：`POST /lots/{id}/carrier`、`DELETE /lots/{id}/carrier` → **只委托 Facade**
- 权限：view / edit / bind 分拆；Facade 供 Track 方法不鉴权
- 禁止 `PUT /lots/{id}` body 带 `carrierId` 生效（若有字段忽略或 400）

#### 4.2 本切片不做

- Admin 完整页（Car-5 最小页可同发或本切片只 API）
- Track 行为变更

#### 4.3 验收（Car-4）

- [ ] 无权限 → 403
- [ ] 绑解 API 与 Facade 行为一致
- [ ] Lot PUT 无法偷改 `carrier_id`

---

### Car-5（Track 闸 + 前端）⏳

#### 5.1 交付

- TrackIn：当 `mes.carrier.track-in-required=true` → `carrierFacade.assertBound(lotId)`；未绑 → `CARRIER_REQUIRED`
- 与 TrackIn **同事务**；开关 false → 零行为差
- TrackIn `mes_tx_log` 记 `carrier_id`（有则写）
- context（T2-5b）：`carrierId` / `carrierRequired`；无绑不伪造 required=true（除非开关开）
- Admin `/app/carrier`：台账列表、改态、绑解入口（或从 Lot 抽屉绑）
- 菜单：挂 **主数据 / 生产支撑** 类目录（与 Eqp 同级思路）；**禁止**塞进「复盘」
- Lot 详情 / 现场过站区只读展示 carrierCode（有则显示）

#### 5.2 口径锁死

| 项 | 锁死 |
|----|------|
| 闸语义 | 仅「非空已绑」；不做码比对、不做 SlotMap |
| 默认 | `track-in-required=false` |
| Track | 不调用 Carrier Mapper；不解绑、不换盒 |

#### 5.3 本切片不做

- L2 扫码比对、位置、MCS、E87
- 一盒多 Lot

#### 5.4 验收（Car-5）

- [ ] 开关关 + 未绑 In → 通过（与现网一致）
- [ ] 开关开 + 未绑 In → 拒绝 `CARRIER_REQUIRED`
- [ ] 开关开 + 已绑 In → 通过；履历含 carrier_id
- [ ] context 字段与开关/绑定一致
- [ ] Admin 可完成建盒→绑 Lot→解绑

---

## 3. 明确不做（一期）

| 项 | 说明 |
|----|------|
| E87 / E99 / GEM Adapter | 设备侧握手后置 |
| MCS / OHT / 空盒调度 | 物流后置 |
| 一盒多 Lot | 放开须改 UK + 新设计 |
| SlotMap 强校验 | 无片表不开 |
| 现场强制扫码比对 | L2 = P1 |
| 清洗寿命闭环 | P2 |
| Store/Retrieve 调度 | 随 Stocker；仅记位属 C3 |
| 对外宣称「Carrier Management 已支持」 | 口径禁止 |

---

## 4. 错误码（一期）

| code | 切片 |
|------|------|
| `CARRIER_NOT_FOUND` | Car-2+ |
| `CARRIER_STATUS_INVALID` | Car-2/3 |
| `CARRIER_ALREADY_BOUND` | Car-3 |
| `LOT_ALREADY_BOUND` | Car-3 |
| `CARRIER_REQUIRED` | Car-5 |
| `CARRIER_CONCURRENT_MOD` | Car-2/3 |
| `CARRIER_DISABLED` | enabled=false |

---

## 5. 依赖与接线

```
Car-1 DDL
  → Car-2 台账 Facade
    → Car-3 绑解（写 lot.carrier_id + tx_log）
      → Car-4 HTTP
        → Car-5 TrackIn 钩子 + Admin/现场展示
```

Track 清单对应：T2-3（闸）、T2-5b（context）随 **Car-5** 关闭。  
Lot 清单 L2-7 随 **Car-3/4** 关闭（绑解 API）。

---

## 6. 文档入口

| 文档 | 路径 |
|------|------|
| 架构 | `MES-Carrier架构设计.md` |
| 本清单 | `MES-Carrier一期功能清单.md` |
| Track 闸 | `docs/模块/Track（执行引擎）模块/MES-Track二期功能清单.md` §2.4 |
| Lot 绑定 | `docs/模块/Lot（批次）模块/MES-Lot二期功能清单.md` §3.2 |
| 进度 | `docs/架构/MES-实施进度与下一步.md` |
