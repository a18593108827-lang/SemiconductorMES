---
type: 数据库设计
module: Equipment
status: done
slices: []
aligns: []
updated: 2026-07-28
---

# MES 设备（Equipment）— 数据库设计

> 对齐：`MES-Equipment功能文档.md`  
> 状态：**DDL 已写**（`migrate_eqp.sql` / `schema.sql`）  
> 更新：2026-07-28

---

## 1. 设备主表 `mes_eqp`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | 雪花 |
| eqp_code | VARCHAR(64) UK | N | 设备编码 |
| eqp_name | VARCHAR(128) | N | 名称 |
| eqp_type | VARCHAR(64) | Y | 类型；对齐 `mes_step.eqp_type` |
| area | VARCHAR(64) | Y | 区域 / Bay |
| status | VARCHAR(16) | N | 业务态，见 §2 |
| enabled | TINYINT | N | 1启用 0停用 |
| remark | VARCHAR(256) | Y | |
| version | INT | N | 乐观锁，默认 0 |
| create_by / update_by | BIGINT | Y | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | 逻辑删 |

索引：`uk_eqp_code (eqp_code)`、`idx_eqp_status (status, enabled)`、`idx_eqp_type (eqp_type)`。

一期**不建**：chamber、port、current_lot_id（当前批从 WIP/Lot 反查即可）、SECS 连接串。

---

## 2. status 枚举（业务态）

| 英文 | 中文 | TrackIn |
|------|------|---------|
| `idle` | 空闲 | ✅ |
| `running` | 加工中 | ✅ |
| `down` | 故障 | ❌ |
| `pm` | 保养 | ❌ |
| `eng` | 工程 | ❌ |
| `offline` | 离线 | ❌ |

默认新建：`idle` + `enabled=1`。

---

## 3. 与 Lot / Track / WIP

| 动作 | mes_eqp | mes_lot / mes_wip_lot | mes_tx_log |
|------|---------|------------------------|------------|
| TrackIn(eqpId) | 只读校验 usable | `current_eqp_id` | TRACK_IN 带 eqp_id |
| TrackOut | 一期不强制改态 | 可清空 eqp | TRACK_OUT |
| Admin 改态 | update status | — | 可选写操作日志（非 tx_log） |

`current_eqp_id` 已在 Lot/WIP/履历存在；设备编码展示 join `mes_eqp`。

---

## 4. 权限种子（待补）

| id | 码 | 说明 |
|----|-----|------|
| 240 | `eqp:list` | ✅ 菜单 |
| 241 | `eqp:add` | ✅ 按钮 |
| 242 | `eqp:edit` | ✅ 按钮 |
| 243 | `eqp:status` | ✅ 按钮 |

DDL：`migrate_eqp.sql` · `schema.sql`。

---

## 5. 种子设备（示例，可选）

| eqp_code | eqp_name | eqp_type | area | status |
|----------|----------|----------|------|--------|
| EQP-ETCH-A1 | 刻蚀机 A1 | ETCH | 刻蚀 | idle |
| EQP-ETCH-A2 | 刻蚀机 A2 | ETCH | 刻蚀 | idle |
| EQP-CMP-B2 | 抛光机 B2 | CMP | CMP | idle |
| EQP-PHOTO-E1 | 光刻机 E1 | PHOTO | 光刻 | pm |

类型字符串与 `mes_step.eqp_type` 保持同约定即可（一期不强校验匹配）。

---

## 6. 扩展预留（二期列 / 表，可不建）

| 项 | 用途 |
|----|------|
| `mes_eqp_chamber` | 腔体 |
| `mes_eqp_port` | 端口 / FOUP |
| `mes_eqp_state_log` | 改态履历 / E10 投影源 |
| `max_concurrent_lots` | 并发能力 |
| `adapter_id` / `secs_device_id` | 对接 Adapter |

一期**不建**；需要时 ALTER / 新表即可。
