---
type: 数据库设计
module: Hold
status: done
slices: []
aligns: []
updated: 2026-07-28
---

# MES 锁批（Hold）— 数据库设计

> 对齐：`MES-Hold功能文档.md`  
> 状态：**DDL + 后端已落地**（`migrate_hold.sql` / `schema.sql` / `com.mes.hold`）  
> 更新：2026-07-28

---

## 1. 原因码 `mes_hold_reason`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| reason_code | VARCHAR(32) UK | N | 编码 |
| reason_name | VARCHAR(64) | N | 名称 |
| category | VARCHAR(32) | Y | 分类预留：quality / eng / customer / other |
| status | TINYINT | N | 1启用 0停用 |
| remark | VARCHAR(256) | Y | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | 逻辑删 |

### 1.1 种子原因码（示例）

| reason_code | reason_name | 注释 |
|-------------|-------------|------|
| `Q_PENDING` | 待检/待判 | 质量等待 |
| `Q_ABNORMAL` | 量测/良率异常 | 质量调查 |
| `E_REVIEW` | 工程评审 | 工程 |
| `M_MATERIAL` | 缺料/物料问题 | 制造 |
| `C_REQUEST` | 客户要求 | 客规 |
| `OTHER` | 其它 | 须填备注 |

---

## 2. 锁批记录 `mes_hold`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| lot_id | BIGINT | N | |
| lot_no | VARCHAR(64) | Y | 冗余 |
| reason_id | BIGINT | N | → mes_hold_reason |
| reason_code | VARCHAR(32) | N | 冗余，履历可读 |
| status | VARCHAR(16) | N | 见 §2.1 |
| prev_status | VARCHAR(32) | Y | Hold 前 Lot.status（wait/processing） |
| remark | VARCHAR(512) | Y | 上锁备注 |
| release_remark | VARCHAR(512) | Y | 解锁备注 |
| hold_user_id | BIGINT | Y | |
| hold_user_name | VARCHAR(64) | Y | |
| hold_time | DATETIME | N | |
| release_user_id | BIGINT | Y | |
| release_user_name | VARCHAR(64) | Y | |
| release_time | DATETIME | Y | |
| create_time / update_time | DATETIME | | |

索引：`idx_hold_lot_status (lot_id, status)`、`idx_hold_status_time (status, hold_time)`。

### 2.1 mes_hold.status 枚举

| 英文 | 中文 | 注释 |
|------|------|------|
| `active` | 生效中 | Track 须拦截 |
| `released` | 已解锁 | 历史行，只读 |

一期约束（应用层）：同一 `lot_id` 最多一条 `active`。  
表不建唯一索引卡死多条，便于二期多层 Hold。

---

## 3. 与 Lot / WIP / Track

| 动作 | mes_lot | mes_hold | mes_wip_lot | mes_tx_log |
|------|---------|----------|-------------|------------|
| Hold | status→held；记 prev | insert active | sync held | HOLD |
| ReleaseHold | status→prev_status | active→released | sync | RELEASE_HOLD |
| TrackIn/Out | — | 查 active | — | 失败不写或写失败由业务定（一期可不写失败履历） |

---

## 4. 权限种子（已有）

| id | 码 | 路径 |
|----|-----|------|
| 260 | `hold:list` | `/app/hold` |
| 261 | `hold:create` | 按钮 |
| 262 | `hold:release` | 按钮 |

DDL 脚本：`server/src/main/resources/db/migrate_hold.sql` · `schema.sql`。

---

## 5. 扩展预留（二期列，可不建）

| 字段 | 用途 |
|------|------|
| release_code | 解锁码 |
| release_perm | 原因级解锁权限 |
| label | Mass 筛选用标签 |
| step_id / sort_no | 站级 Hold |

一期**不建**这些列；需要时 ALTER 即可。

---

## 6. Future Hold（P0 已落地）

表 `mes_future_hold`（pending → activated / cancelled），激活后写现有 `mes_hold`。  
DDL：`migrate_future_hold.sql`。设计与后置项：`MES-FutureHold接口设计.md`。  
**本期不做：** Route 挂点模板（FH-4）、通知 / Matrix / SPC（FH-5）。
