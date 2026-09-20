---
type: 数据库设计
module: WIP
status: done
slices: []
aligns: []
updated: 2026-07-27
---

# MES 在制（WIP）— 数据库设计

> 对齐：`MES-WIP功能文档.md`、`MES-Track数据库设计.md`  
> 状态：**一期即用投影表 `mes_wip_lot`**（Track 同事务同步）  
> DDL：`server/src/main/resources/db/migrate_wip.sql` · `schema.sql`  
> 更新：2026-07-27

---

## 1. 拍板：一期用投影表

| 方案 | 结论 |
|------|------|
| 先扫 `mes_lot` 再切投影 | 能切，但易漏同步路径、索引与「在制集合」语义纠缠 |
| **一期 `mes_wip_lot`** | **采用**：读模型稳定；API 不改源；在制集合小；Track 已是唯一写运行态 |

**原则：**

- WIP API **只读** `mes_wip_lot`（+ 可选联查 step 名）  
- 仅 Track（及将来 Hold）在改 Lot 运行态的**同一 DB 事务**内 upsert/delete 投影  
- 禁止 WIP / Lot 业务接口直接改投影  

---

## 2. 表 `mes_wip_lot`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| lot_id | BIGINT PK | N | 同 `mes_lot.id` |
| lot_no | VARCHAR(64) UK | N | 批次号冗余 |
| product_code | VARCHAR(64) | Y | 产品编码 |
| qty | INT | N | 数量 |
| priority | INT | N | 优先级 1–100 |
| customer_lot | VARCHAR(64) | Y | 客户 Lot |
| status | VARCHAR(32) | N | 见 §2.1（仅在制子集） |
| current_sort_no | INT | Y | 当前站顺序号 |
| current_step_id | BIGINT | Y | 当前工序 ID |
| current_eqp_id | BIGINT | Y | 当前设备（TrackIn 后；可空） |
| route_id | BIGINT | Y | 路线 ID |
| route_version_id | BIGINT | Y | 放行快照版本 ID |
| update_time | DATETIME | N | 投影更新时间 |

索引：`PRIMARY KEY (lot_id)` · `uk_wip_lot_no (lot_no)` · `idx_wip_status_sort (status, current_sort_no)` · `idx_wip_product (product_code)`。

### 2.1 status 枚举（投影内）

| 英文 | 中文 | 注释 |
|------|------|------|
| `wait` | 待加工 | 在站等待 TrackIn（含放行进站、完工进下一站） |
| `processing` | 加工中 | 已 TrackIn，待 TrackOut |
| `held` | 锁批 | Hold 二期；列预留，一期可不产生 |

**不进本表（注释）：**

| 英文 | 中文 | 说明 |
|------|------|------|
| `created` | 已创建 | 未放行，非在制 |
| `released` | 已放行 | Lot 过渡兼容名；放行后写 `wait` 进投影 |
| `completed` | 已完工 | TrackOut 末站后 **DELETE** 投影行 |
| `scrapped` | 已报废 | 离开在制，删除投影 |

列 COMMENT（与 DDL 一致）：`在制状态: wait待加工/processing加工中/held锁批`。

---

## 3. 同步规则（与 Track 同事务）

| Track 事务 | 投影动作 |
|------------|----------|
| RELEASE → wait | upsert 整行 |
| TRACK_IN → processing | update status / eqp / update_time |
| TRACK_OUT → 下一站 wait | update status / sort / step / eqp=null |
| TRACK_OUT → completed | **delete** 投影行 |
| HOLD / RELEASE_HOLD（二期） | update status |

Lot 在制时改主数据：同步投影冗余列。  
`migrate_wip.sql` 含从 `mes_lot` 回填 + 清理非在制行（可重复执行）。

---

## 4. 权限种子

已有：`wip:list` → `/app/wip`（id=230）。

---

## 5. 关系

```
Track ──写──► mes_lot（真相）
     └同事务──► mes_wip_lot（在制读模型）
WIP   ──只读──► mes_wip_lot (+ mes_step 名称)
```
