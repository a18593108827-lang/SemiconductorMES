---
type: 数据库设计
module: Dispatch
status: done
slices: []
aligns: []
updated: 2026-07-29
---

# MES 派工（Dispatch）— 数据库设计

> 对齐：`MES-Dispatch功能文档.md`  
> 状态：**DDL 已写**（含 slot 唯一坑 + version）  
> 更新：2026-07-29

---

## 1. 一期表策略

| 能力 | 是否建表 | 说明 |
|------|----------|------|
| 候选 / 推荐 | ❌ | 纯查询 |
| Reserve | ✅ | `mes_dispatch_reserve` |
| 排序规则 | ❌ 二期 | `mes_dispatch_rule` |

脚本：

- `migrate_dispatch.sql` — 建表 + 权限  
- `migrate_dispatch_reserve_slot.sql` — 已有库补 slot + 清脏 + UNIQUE  
- `migrate_dispatch_reserve_version.sql` — 已有库补 version  
- `schema.sql` — 全量同步  

---

## 2. 预约表 `mes_dispatch_reserve`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | 雪花 |
| lot_id | BIGINT | N | 批次 |
| lot_slot | BIGINT | Y | **active 时 = lot_id，否则 NULL**；UNIQUE 防同批双约 |
| eqp_id | BIGINT | N | 设备 |
| eqp_slot | BIGINT | Y | **active 时 = eqp_id，否则 NULL**；UNIQUE 防同机双约 |
| status | VARCHAR(16) | N | active / released / expired / consumed |
| expire_time | DATETIME | N | 超时时刻 |
| reserve_user_id | BIGINT | Y | 预约人 |
| consume_tx_id | BIGINT | Y | 消费关联 `mes_tx_log.id` |
| remark | VARCHAR(256) | Y | |
| version | INT | N | 乐观锁，默认 0 |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | 0 正常 / 1 逻辑删 |

### 2.1 索引

| 名 | 列 | 说明 |
|----|-----|------|
| `uk_reserve_eqp_slot` | `eqp_slot` | UNIQUE；多 NULL 允许 |
| `uk_reserve_lot_slot` | `lot_slot` | UNIQUE |
| `idx_reserve_lot_status` | `(lot_id, status)` | |
| `idx_reserve_eqp_status` | `(eqp_id, status)` | |

### 2.2 slot 维护规则

| 动作 | status | eqp_slot / lot_slot |
|------|--------|---------------------|
| 新建预约 | active | = eqp_id / lot_id |
| 释约 | released | NULL |
| 超时 | expired | NULL |
| TrackIn 消费 | consumed | NULL |

应用用 `LambdaUpdateWrapper.set(..., null)` 清空（`updateById` 默认不写 null）。

### 2.3 status 枚举

| 英文 | 中文 |
|------|------|
| `active` | 生效中 |
| `released` | 已释约 |
| `expired` | 已超时 |
| `consumed` | 已消费 |

超时：读路径惰性翻态；配置 `mes.dispatch.reserve-ttl-minutes`（默认 30）。

### 2.4 并发约定

1. UNIQUE(slot) 兜底双约  
2. 写路径 `FOR UPDATE` 锁 active 行  
3. 出坑：`WHERE status='active' AND version=?`，成功则 `version+1`  

---

## 3. 规则表 `mes_dispatch_rule`（二期，未建）

| 字段 | 说明 |
|------|------|
| rule_code | IDLE_FIRST / FIFO / PRIORITY / LOAD_BALANCE 等 |
| weight | 权重 |
| enabled | 1/0 |

---

## 4. 与现有表（只读）

| 表 | 用法 |
|----|------|
| `mes_lot` | 站、状态、负载 |
| `mes_step` | `eqp_type` |
| `mes_eqp` | 候选 |
| `mes_hold` | active 拦截 |
| `mes_tx_log` | consume_tx_id 关联 |

**禁止** Dispatch 直接改 Lot 加工态。

---

## 5. 权限种子

| id | 码 | 说明 |
|----|-----|------|
| 244 | `dispatch:view` | 菜单 `/app/dispatch` |
| 245 | `dispatch:reserve` | 预约/释约 |

---

## 6. 扩展预留

| 项 | 用途 |
|----|------|
| 规则表 | 排序外置 |
| Recipe | 过滤条件 |
| APS 窗 | 软约束列或旁表 |
