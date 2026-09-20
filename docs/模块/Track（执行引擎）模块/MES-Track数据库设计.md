---
type: 数据库设计
module: Track
status: done
slices: []
aligns: []
updated: 2026-08-11
---

# MES 执行引擎（Track）— 数据库设计（草案）

> 对齐：`MES-Track功能文档.md`、`MES-Lot数据库设计.md`、`MES-LotScrap接口设计.md`、`MES-LotBonus接口设计.md`、`MES-ProcessTime接口设计.md`  
> 状态：**DDL 已写**（`migrate_track.sql` / `migrate_process_time.sql` / `schema.sql`）；Bonus 见 `migrate_lot_bonus.sql`  
> 更新：2026-08-11

---

## 1. Lot 运行态字段（扩展 `mes_lot`）

一期在现有 `mes_lot` 上增加（或确认）执行字段，避免 WIP 另存真相：

| 字段 | 类型 | 说明 |
|------|------|------|
| status | VARCHAR(32) | 见下表；默认 `created` |
| current_sort_no | INT | 当前站顺序号（快照内）；未进站可空 |
| current_step_id | BIGINT | 当前工序 ID（冗余，便于查询） |
| current_eqp_id | BIGINT | 当前设备（TrackIn 后；一期可空） |
| route_version_id | BIGINT | 已有：放行快照 |
| version | INT | 已有：乐观锁 |
| process_started_at | DATETIME(3) NULL | Process Time 开计时；TrackIn 写、Out/Rework 清；见 `migrate_process_time.sql` |

### 1.1 Lot.status 枚举

| 英文 | 中文 |
|------|------|
| `created` | 已创建 |
| `released` | 已放行（过渡；推荐放行后直接 `wait`） |
| `wait` | 等待加工（在站待 TrackIn） |
| `processing` | 加工中（已 TrackIn） |
| `held` | 锁批 |
| `completed` | 已完工 |
| `scrapped` | 已报废 |
| `merged` | 已合批 |

说明：若暂不拆 `wait/processing`，可用 `released` + `current_sort_no`；**推荐落地用 wait/processing**。`scrapped`/`merged`/`completed` 移出 WIP。

---

## 2. 事务履历 `mes_tx_log`（必建）

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| lot_id | BIGINT | N | |
| lot_no | VARCHAR(64) | Y | 冗余 |
| tx_type | VARCHAR(32) | N | 见下表 |
| from_status | VARCHAR(32) | Y | 同 Lot.status |
| to_status | VARCHAR(32) | Y | 同 Lot.status |

### 2.1 tx_type 枚举

| 英文 | 中文 | 状态 |
|------|------|------|
| `RELEASE` | 放行 | ✅ |
| `MOVE` | 移站 | ✅ 独立接口（不经加工） |
| `TRACK_IN` | 开工 | ✅ |
| `TRACK_OUT` | 完工 | ✅ |
| `HOLD` | 锁批 | ✅ |
| `RELEASE_HOLD` | 解锁 | ✅ |
| `SKIP` | 跳站 | ✅ |
| `REWORK` | 返工 | ✅ |
| `OFF_FLOW` / `OFF_FLOW_RESUME` | 旁路 / 回锚 | ✅ |
| `SPLIT` | 分批 | ✅ |
| `MERGE` | 合批 | ✅ |
| `SCRAP` | 报废 | ✅ |
| `BONUS` | 数量调整 | ✅ |
| `ABORT` | 加工中止 | ✅ |

### 2.2 其余字段

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| from_sort_no | INT | Y | |
| to_sort_no | INT | Y | |
| step_id | BIGINT | Y | |
| eqp_id | BIGINT | Y | |
| route_version_id | BIGINT | Y | 快照 |
| remark | VARCHAR(512) | Y | |
| ext_json | VARCHAR(512) | Y | 结构化扩展（Scrap：mode/qty/reason；Bonus：delta/qty/reason；Abort：reasonCode；Move：moveKind/from·to） |
| oper_user_id | BIGINT | Y | |
| oper_user_name | VARCHAR(64) | Y | 冗余 |
| create_time | DATETIME | N | 只追加，无 update |

索引：`idx_tx_lot_time (lot_id, create_time)`、`idx_tx_type_time (tx_type, create_time)`、`idx_tx_eqp_time (eqp_id, create_time)`（设备反查；已有库 `migrate_history.sql`）。

原则：**只插入不改删**（软删仅合规场景）。

---

## 3. WIP 投影（可选一期简表 / 或视图）

短期：WIP 查询可直接扫 `mes_lot`（status in wait/processing/held）。  
**已拍板（WIP 模块）**：一期即建 `mes_wip_lot`，由 Track 同事务维护；详见 `docs/模块/WIP（在制）模块/MES-WIP数据库设计.md`。

| 字段（示意） | 说明 |
|--------------|------|
| lot_id | PK |
| lot_no / product_code / priority | 冗余 |
| status / current_sort_no / step_id / eqp_id | 与 Lot 同步 |
| update_time | |

**禁止** WIP 页面反写状态。

---

## 4. Hold（独立模块，见 Hold 文档）

最小集表：`mes_hold_reason` / `mes_hold`。  
Track 校验：存在 active Hold → 拒绝 TrackIn/Out/Move。  
详情：`docs/模块/Hold（锁批）模块/MES-Hold数据库设计.md`。

---

## 5. 与现有表关系

```
mes_lot.route_version_id → mes_route_version
mes_route_step (version_id) → 合法下一站
mes_tx_log.lot_id → mes_lot
```

不在 Track 模块复制一套 RouteStep。

---

## 6. 种子 / 权限

| id | 码 | 说明 |
|----|-----|------|
| 290 | `track:view` | 菜单 `/track`（已有） |
| 291 | `track:track-in` | 按钮（已有） |
| 292 | `track:track-out` | 按钮（已有） |
| 293 | `track:release` | 放行 |
| 294 | `track:move` | 独立移站 |

脚本：`migrate_track.sql`（已有库执行）；新库走 `schema.sql`。
