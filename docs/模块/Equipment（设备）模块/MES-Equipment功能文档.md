# MES 设备（Equipment）功能文档

> 定位：可派工 / 可开工的**资源目录**——主数据 + 业务态；不替代 Track，不做 SECS/GEM  
> 对齐：`docs/架构/半导MES架构设计.md` §5.7、§3.2；业界 SEMI E10（稼动后置）/ Adapter 外置  
> 一期状态：**文档已定（最小集），待落地**  
> 更新：2026-07-28  
> 查验：`MES-Equipment已完成功能.md`

---

## 1. 目标（最小集）

- 维护设备主数据（编码、名称、类型、区域、启停）
- 维护业务状态（人工 / 管理端改态；一期无 Adapter）
- TrackIn 前校验：设备存在、已启用、状态允许开工
- 列表 / 详情供 Admin 与现场选机

**产品一句话：** Equipment 回答「这台机能不能接这批货」；不负责跟机台说话。

---

## 2. 边界

```
Equipment = 主数据 + 业务态（供 Track / 日后 Dispatch 消费）
Adapter   = SECS/GEM 协议桥（独立；回写状态 / 事件）← 一期不做
Track     = 执行引擎；TrackIn 调 Eqp.assertUsable
Dispatch  = 选机（过滤类型 + 状态）← 后置，只读 Equipment
Recipe    = 配方资格 ← 后置
SEMI E10  = 稼动细态 / OEE 报表 ← 后置，不推翻业务态
```

**一期做：**

| 能力 | 说明 |
|------|------|
| 设备主数据 | CRUD；`eqp_code` 唯一；`eqp_type` 对齐 `mes_step.eqp_type` |
| 业务状态 | 人工改态；见 §4 |
| 启停 | `enabled`；停用不可选、不可 TrackIn |
| Track 钩子 | TrackIn（有 eqpId 时）→ `assertUsable` |
| 列表 / 详情 | Admin `/app/equipment`；现场台下拉消费 |

**一期不做（后置不难，见 §9）：**

- SECS/GEM Adapter / EAP
- Chamber / Port / FOUP 建模
- 完整 SEMI E10 稼动采集与 OEE
- Recipe 资格绑定
- PM 工单系统
- WebSocket 实时推送（可二期）
- Dispatch 自动选机

---

## 3. 角色与权限

| 权限码 | 用途 | 种子 |
|--------|------|------|
| `eqp:list` | 列表 / 详情 `/app/equipment` | ✅ |
| `eqp:add` | 新建设备 | ✅ |
| `eqp:edit` | 改主数据 | ✅ |
| `eqp:status` | 改业务状态 | ✅ |

现场 TrackIn 选机只需能读列表（或内嵌启用设备接口）；改态建议仅工程师 / 班组长。

---

## 4. 状态模型

### 4.1 业务态（MES，一期用这个）

| 英文 | 中文 | TrackIn | 说明 |
|------|------|---------|------|
| `idle` | 空闲 | ✅ | 默认可开工 |
| `running` | 加工中 | ✅* | 允许同机续派场景；一期与 idle 同等可开工 |
| `down` | 故障 | ❌ | 不可开工 |
| `pm` | 保养 | ❌ | 不可开工 |
| `eng` | 工程 | ❌ | 一期默认禁生产；二期可配 |
| `offline` | 离线 | ❌ | 不可开工 |

\* 一期简化：`assertUsable` = `enabled=1` 且 `status ∈ {idle, running}`。  
多腔并发、独占锁批后置。

**与前端 mock 对齐：** Idle/Running/Down/PM/Offline；`eng` 为架构 Engineering 的落地码。

### 4.2 SEMI E10（后置，勿与业务态混用）

Productive / Standby / Engineering / Scheduled Down / Unscheduled Down / Nonscheduled → 稼动报表。  
一期**不建** E10 明细表；Adapter 落地后再投影。

### 4.3 状态谁写

| 来源 | 一期 | 二期+ |
|------|------|-------|
| Admin / API 改态 | ✅ | ✅ |
| TrackIn/Out 自动改 running/idle | ❌ 可选后置 | 可做 |
| Adapter 事件回写 | ❌ | ✅ |

---

## 5. 接口草案

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/equipments` | `eqp:list` | 分页；keyword / status / eqpType / enabled |
| GET | `/equipments/{id}` | `eqp:list` | 详情 |
| GET | `/equipments/options` | `eqp:list` 或 `track:view` | 启用且可开工，供下拉 |
| POST | `/equipments` | `eqp:add` | 新建 |
| PUT | `/equipments/{id}` | `eqp:edit` | 改主数据（不含状态） |
| PUT | `/equipments/{id}/status` | `eqp:status` | `{ status }` |
| PUT | `/equipments/{id}/enabled` | `eqp:edit` | `{ enabled: 0\|1 }` |

包建议：`com.mes.equipment`；Track → `EquipmentService.assertUsable(eqpId)`。  
统一响应：`{ code, msg, data }`；Token：Bearer。  
雪花 ID 前端**禁止** `Number(id)`。

---

## 6. 页面

| 入口 | 说明 |
|------|------|
| Admin `/app/equipment` | 列表 + 新建/编辑抽屉 + 改态 |
| 现场台 Track | TrackIn 选设备（options）；无权限则隐藏或只读 |

状态 pill：色点 + 文案 + 图标（已有 `EqpStatusPill`，落地时对齐 §4.1 枚举）。

---

## 7. 验收要点（最小集）

1. 无设备主数据不能稳定 TrackIn（有 eqpId 时）  
2. `down` / `pm` / `eng` / `offline` 或停用 → TrackIn 失败  
3. `eqp_type` 可与工序类型对照（一期可只存储，强校验后置）  
4. Admin 可 CRUD + 改态  
5. 不出现业务服务直连 SECS  

---

## 8. 与已落地模块

| 模块 | 关系 |
|------|------|
| Track | TrackIn 可选/必填 eqpId（一期：传了则校验；可后续改为必填） |
| Lot / WIP | `current_eqp_id` 已有；列表展示设备编码需 join |
| Route / Step | `mes_step.eqp_type` 预留；Dispatch 前对齐 |
| Hold | 无关；Hold 拦 Lot，不拦设备目录 |
| Auth | `eqp:list` 已有；增补 add/edit/status |

---

## 9. 后续扩展难吗？

**不难——前提是最小集按「设备实体 + 业务态 + Track 钩子」落地。**

| 扩展 | 改动面 | 难度 |
|------|--------|------|
| Chamber / Port | 子表挂 `eqp_id`；Track 仍认机台 | 低 |
| E10 / OEE | Adapter 事件明细 → 聚合；不改主路径 | 低～中 |
| SECS/GEM Adapter | 独立服务回写 `status`；MES 只收事件 | 中（协议） |
| Recipe 资格 | 关联表；TrackIn 多一道校验 | 低 |
| Dispatch 选机 | 读 type + status 过滤 | 低 |
| PM 工单 | 独立模块，改态即可 | 中 |
| Track 自动改态 | In→running / Out→idle | 低 |

**扩展友好约定（一期就遵守）：**

1. Equipment **实体化**（`mes_eqp`），禁止只在 Lot 写死机台字符串当主数据  
2. Track **只调** `EquipmentService.assertUsable(eqpId)`，不写协议  
3. 业务态与 E10 稼动态分离；E10 后置叠加  
4. Adapter 永不进业务包；只通过 API/MQ 回写状态  

---

## 10. 关联

- `MES-Equipment数据库设计.md`  
- `MES-Equipment已完成功能.md`  
- Track / Lot / WIP / Route  
- 进度：`docs/架构/MES-实施进度与下一步.md`
