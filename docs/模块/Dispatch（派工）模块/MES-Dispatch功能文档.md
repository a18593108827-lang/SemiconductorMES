---
type: 功能文档
module: Dispatch
status: done
slices: []
aligns: []
updated: 2026-07-29
---

# MES 派工（Dispatch）功能文档

> 定位：短周期**选机 / 建议机**——过滤 + 排序 + Reserve；不替代 Track，不是 APS  
> 对齐：`docs/架构/半导MES架构设计.md` §5.5、§6.1；业界 What-Next / Reserve  
> 一期状态：**候选 / Reserve / Track 选机 / Admin 派工页已落地**  
> 更新：2026-07-29  
> 查验：`MES-Dispatch已完成功能.md`  
> APS 后置见：`docs/方案/MES-APS高级计划与排程方案.md`

---

## 1. 目标（最小集）

- 按 Lot 当前站给出**候选设备列表**（可开工 + 类型匹配）
- 按规则**排序**（一期写死：空闲 → 低负载 → 编码）
- 现场 TrackIn **默认推荐第一台**，仍允许人工改选
- **Reserve** 预约设备；TrackIn 校验预约一致并消费

**产品一句话：** Dispatch 回答「这批货下一步建议上哪台机」；Track 负责真正开工。

---

## 2. 边界

```
Equipment = 资源目录 + 业务态（Dispatch 只读）
Dispatch  = 过滤 + 排序 + Reserve
Track     = TrackIn 绑 eqpId；assertUsable + 预约校验/消费
APS       = 天/班次级排程 ← 独立系统，本期不做
Recipe    = 配方资格 ← 后置
AMHS      = 搬运 ← 后置
```

**一期已做：**

| 能力 | 说明 |
|------|------|
| 候选机 | `eqp_type`（有则滤）+ `enabled` + `idle/running`；他批 active 预约剔除 |
| 排序 | 空闲优先 → 负载低 → `eqp_code` |
| 推荐 | `recommendedEqpId` = 排序第一 |
| 现场消费 | Track 开工下拉调 `/dispatch/candidates` |
| Reserve | 占机；默认 30 分钟；In 须同机；成功后 `consumed` |
| Admin 页 | `/app/dispatch` 选批 → 候选表 → 预约/释约 |

**一期不做：** APS / What-Next / Recipe / AMHS / 规则表可视化

---

## 3. 角色与权限

| 权限码 | 用途 | 种子 |
|--------|------|------|
| `dispatch:view` | 看候选 / 推荐 / Admin 页 | ✅ 244 |
| `dispatch:reserve` | 预约 / 释约 | ✅ 245 |

现场：`track:view` **或** `dispatch:view` 可读候选（OR）。

---

## 4. 选机模型

### 4.1 过滤（硬条件）

1. `enabled = 1`  
2. `status ∈ { idle, running }`（与 `assertUsable` 一致）  
3. 当前站 `eqp_type` 非空时设备类型须相等  
4. active Hold → 空候选 + `held=true`  
5. 其他 Lot 未过期 active 预约占用的机台剔除  
6. （P1）未关闭 CRITICAL 告警禁派 → 见 `MES-Dispatch-Critical禁派功能清单.md`  

### 4.2 排序与负载

| 优先级 | 规则 |
|--------|------|
| 1 | `idle` 优于 `running` |
| 2 | 负载低优先 |
| 3 | `eqp_code` 升序 |

负载：`mes_lot.current_eqp_id` 且状态 `wait`/`processing`/`held` 的数量。

### 4.3 Reserve

| 状态 | 说明 |
|------|------|
| `active` | 未过期；占机 |
| `released` | 人工释约 |
| `expired` | 超时（惰性翻态） |
| `consumed` | TrackIn 成功消费 |

- TTL：`mes.dispatch.reserve-ttl-minutes`，默认 **30**  
- 无预约 → TrackIn 不强制预约  
- 有 active → `eqpId` 必须一致，否则拒绝；成功后翻 `consumed`  

### 4.4 并发（已加固）

| 手段 | 作用 |
|------|------|
| `eqp_slot` / `lot_slot` UNIQUE | 同机/同批同时仅一条 active |
| 出坑清 slot | released / expired / consumed |
| `SELECT … FOR UPDATE` | 写路径串行读 active |
| `version` + 条件更新 | 防状态覆盖 |

### 4.5 与 Track

```
候选 / 推荐 ──► 现场选机（可改）──► TrackIn(eqpId)
                      │                    │
                      └─► 可选 Reserve ─────┴─ assertReserveMatch → consumeOnTrackIn
```

Dispatch **不写** Lot 加工态。

---

## 5. 接口

| 方法 | 路径 | 权限 | 状态 |
|------|------|------|------|
| GET | `/dispatch/candidates?lotId=` | `dispatch:view` 或 `track:view` | ✅ |
| POST | `/dispatch/reserve` | `dispatch:reserve` | ✅ `{ lotId, eqpId, remark? }` |
| POST | `/dispatch/reserves/{id}/release` | `dispatch:reserve` | ✅ `{ remark? }` |
| GET | `/dispatch/reserves` | 同上 OR | ✅ `lotId`/`eqpId` 至少一；`status` 默认 active |

包：`com.mes.dispatch`。雪花 ID 前端禁止 `Number(id)`。

**Track 内部（非 HTTP）：** `assertReserveMatch` / `consumeOnTrackIn`

---

## 6. 页面

| 入口 | 说明 | 状态 |
|------|------|------|
| `/track` | 候选下拉、推荐提示、预约/释约、剩余时间 | ✅ |
| `/app/dispatch` | 搜批 → 候选表 → 预约/释约 → 生效预约列表 | ✅ |

前端 API：`web/src/api/dispatch.ts`

---

## 7. 验收要点

1. 停用 / 非 idle·running 不进候选  
2. `eqp_type` 不一致不进候选  
3. 有候选时稳定排序 + `recommendedEqpId`  
4. TrackIn 仍 `assertUsable`  
5. Reserve：占机；他批不可再约同机；In 必须同机  
6. 同机/同批并发双约被唯一约束挡住  
7. 不做 APS / SECS  

---

## 8. 与已落地模块

| 模块 | 关系 |
|------|------|
| Equipment | 只读；复用 usable |
| Track | In 钩子校验+消费预约 |
| Lot / WIP | 读当前站、负载 |
| Route / Step | `eqp_type` |
| Hold | active → 不派 |

---

## 9. 后续扩展

| 扩展 | 难度 |
|------|------|
| 规则表权重 | 低 |
| Recipe 资格 | 低 |
| What-Next | 中 |
| APS 计划窗 | 中 |
| AMHS | 高 |

---

## 10. 关联

- `MES-Dispatch数据库设计.md`  
- `MES-Dispatch已完成功能.md`  
- `docs/方案/MES-APS高级计划与排程方案.md`  
- `docs/架构/MES-实施进度与下一步.md`
