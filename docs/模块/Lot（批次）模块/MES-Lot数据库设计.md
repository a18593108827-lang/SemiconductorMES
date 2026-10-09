---
type: 数据库设计
module: Lot
status: done
slices: [TD-1]
aligns: []
updated: 2026-10-09
---

# MES 批次（Lot）— 数据库设计

> 对齐：`MES-Lot功能文档.md`、`MES-LotSplit接口设计.md`、`MES-LotMerge接口设计.md`、`MES-LotGenealogy接口设计.md`、`MES-LotScrap接口设计.md`、`MES-LotBonus接口设计.md`  
> 状态：**DDL 已写**（`migrate_lot.sql` / `migrate_lot_split.sql` / `migrate_lot_merge.sql` / `migrate_lot_scrap.sql` / `migrate_lot_bonus.sql` / **`migrate_lot_pkg.sql`（TD-1 Strip + 客户映射）** / `schema.sql`）  
> 更新：2026-10-09（TD-1：`mes_lot_strip` / `mes_lot_customer_map`）

---

## 1. 表：`mes_lot`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| lot_no | VARCHAR(64) | N | 批次号，唯一；默认 `LOT-yyyyMMdd-###` |
| product_code | VARCHAR(64) | Y | 产品编码 |
| qty | INT | N | 数量，默认 0 |
| scrap_qty | INT | N | 累计报废，默认 0 |
| priority | INT | N | 优先级 1–100，默认 50 |
| hot_flag | TINYINT | N | Hot Lot 0/1，默认 0 |
| customer_lot | VARCHAR(64) | Y | 客户 Lot |
| parent_lot_id | BIGINT | Y | Split 直系父 |
| merged_to_lot_id | BIGINT | Y | Merge 后指向主 Lot |
| route_id | BIGINT | Y | 目标/已绑路线 |
| route_version_id | BIGINT | Y | 放行快照；Release 后锁定 |
| status | VARCHAR(32) | N | 见下表；默认 `created` |
| remark | VARCHAR(512) | Y | 备注 |
| version | INT | N | 乐观锁 |
| create_by / update_by | BIGINT | Y | |
| create_time / update_time | DATETIME | Y | |
| deleted | TINYINT | N | 软删，默认 0 |

### 1.0 `mes_lot_no_seq`

| 字段 | 类型 | 说明 |
|------|------|------|
| seq_day | CHAR(8) PK | yyyyMMdd |
| next_no | INT | 当日已分配最大流水 |

### 1.1 status 枚举

| 英文 | 中文 |
|------|------|
| `created` | 已创建 |
| `released` | 已放行（过渡兼容） |
| `wait` | 待加工 |
| `processing` | 加工中 |
| `held` | 锁批 |
| `completed` | 已完工 |
| `scrapped` | 已报废 |
| `merged` | 已合批（源批终态） |

约束：`uk_lot_no`；`merged`/`scrapped`/`completed` 不可 TrackIn。

---

## 2. 与 Route 约定

未 Release：`route_id` 可空可改；`route_version_id` 空。  
Release：取 Route 当前 `active` 写入 `route_version_id`。  
Track：只读该快照下的 `mes_route_step`。

---

## 3. `mes_lot_genealogy`（已建）

| 字段 | 说明 |
|------|------|
| txn_type | `split` / `merge` |
| parent_lot_id | Split=父；Merge=主 Lot |
| child_lot_id | Split=子；Merge=被吞源 |
| qty | 本次转移数量 |
| tx_id | 关联 `mes_tx_log.id` |

脚本：`migrate_lot_split.sql`。

后置：`mes_lot_wafer`（P1）、`mes_lot_carrier`（P1）。

---

## 3.1 `mes_lot_strip`（TD-1 ✅）

| 字段 | 说明 |
|------|------|
| lot_id / strip_no | 所属批 + 条号；UK `(lot_id, strip_no)` |
| seq_no / die_qty | 批内序、本条颗数（可空） |
| bin_code / status | TD-1 仅登记，条级分档后置 |
| deleted | TINYINT；UK **不含** deleted |

脚本：`migrate_lot_pkg.sql`。写路径：`POST /lots/{id}/strips`（`lot:edit`）；合批/报废拒写。

---

## 3.2 `mes_lot_customer_map`（TD-1 ✅）

| 字段 | 说明 |
|------|------|
| lot_id / map_type / external_lot_no | INBOUND/OUTBOUND；UK 三元 |
| lot_no | 内部批号快照 |
| external_source / customer_code / qty | 可空 |
| deleted | TINYINT；UK **不含** deleted |

**不写** `mes_lot.customer_lot`（旧列便查冗余；映射真相在本表）。  
正查 `GET /lots/{id}/customer-maps`；反查 `GET /lots/by-external-lot`。

---

## 4. 权限种子

| id | 码 | 说明 |
|----|-----|------|
| 220–223 | `lot:*` | 列表/增/改/放行 |
| 298 | `track:split` | 分批 |
| 299 | `track:merge` | 合批 |
| 300 | `track:scrap` | 报废 |
| 301 | `track:bonus` | 数量调整 |

脚本：`migrate_lot.sql` / `migrate_lot_split.sql` / `migrate_lot_merge.sql` / `migrate_lot_scrap.sql` / `migrate_lot_bonus.sql`；新库 `schema.sql`。

说明：`scrap_qty` 仅由 `POST /track/scrap` 累加；报废履历在 `mes_tx_log`（`tx_type=SCRAP`），**不写** genealogy。  
Bonus 只改 `qty`，履历 `tx_type=BONUS`，**不写** genealogy、**不碰** `scrap_qty`。
