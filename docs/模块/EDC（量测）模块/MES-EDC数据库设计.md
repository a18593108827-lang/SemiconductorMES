---
type: 数据库设计
module: EDC
status: done
slices: []
aligns: []
updated: 2026-08-12
---

# MES 量测采集（EDC）— 数据库设计

> 对齐：`MES-EDC功能文档.md`  
> 状态：**设计已定 · DDL 已建**  
> 更新：2026-08-12  
> 落地脚本：`server/src/main/resources/db/migrate_edc.sql`（已合入 `schema.sql`）

---

## 1. 特性 `mes_edc_param`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | 雪花 |
| param_code | VARCHAR(64) UK | N | 特性编码 |
| param_name | VARCHAR(128) | N | |
| unit | VARCHAR(32) | Y | |
| value_type | VARCHAR(16) | N | 一期固定 `NUMBER` |
| enabled | TINYINT | N | 1启用 |
| remark | VARCHAR(256) | Y | |
| version | INT | N | 乐观锁，默认 0 |
| create_by / update_by | BIGINT | Y | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | |

索引：`uk_edc_param_code (param_code)`。

---

## 2. 规格 `mes_edc_spec`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| param_id | BIGINT | N | → mes_edc_param |
| product_code | VARCHAR(64) | Y | 空=全产品默认 |
| version_no | INT | N | 同 param+product 内递增 |
| status | VARCHAR(16) | N | draft / active / obsolete |
| usl | DECIMAL(20,8) | Y | 上限；可与 lsl 只配一侧 |
| lsl | DECIMAL(20,8) | Y | 下限 |
| target | DECIMAL(20,8) | Y | 目标（门禁用不到，SPC 预留） |
| remark | VARCHAR(256) | Y | |
| published_at | DATETIME | Y | |
| version | INT | N | 乐观锁，默认 0 |
| create_by / update_by | BIGINT | Y | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | |

约束：

- `uk_edc_spec_ver (param_id, product_code, version_no)`（`product_code` 空用 `''` 参与唯一）
- 应用层：同 `(param_id, product_code)` 至多一条 `status=active`
- 校验：`usl` / `lsl` 不能同时空；若都有则 `usl >= lsl`

判定：`value < lsl` 或 `value > usl` → OOS（缺侧不检该侧）。

---

## 3. 站计划 `mes_edc_plan`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| step_id | BIGINT | N | → mes_step（逻辑引用，不建硬 FK） |
| required | TINYINT | N | 1=TrackOut 门禁启用 |
| enabled | TINYINT | N | |
| remark | VARCHAR(256) | Y | |
| version | INT | N | 乐观锁 |
| create_by / update_by | BIGINT | Y | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | |

索引：`uk_edc_plan_step (step_id)`（一 step 一计划；软删注意唯一策略）。

**不**用 `step_type=量测` 隐式生成 Plan。

---

## 4. 计划项 `mes_edc_plan_item`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| plan_id | BIGINT | N | → mes_edc_plan |
| param_id | BIGINT | N | |
| spec_id | BIGINT | Y | 可空=解析该 param（+lot.product）当前 active Spec |
| sort_no | INT | N | 录入顺序 |
| mandatory | TINYINT | N | 1=必采；默认 1 |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | |

索引：`uk_edc_plan_param (plan_id, param_id)`、`idx_edc_plan_item (plan_id, sort_no)`。

---

## 5. 采集头 `mes_edc_collection`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| lot_id | BIGINT | N | |
| lot_no | VARCHAR(64) | Y | 冗余展示 |
| route_version_id | BIGINT | N | 在途快照 |
| sort_no | INT | N | |
| step_id | BIGINT | N | |
| track_in_tx_id | BIGINT | N | **本趟访问**；→ mes_tx_log.id |
| plan_id | BIGINT | N | 提交时 Plan |
| result | VARCHAR(16) | N | PASS / FAIL |
| source | VARCHAR(16) | N | MANUAL / AUTO |
| eqp_id | BIGINT | Y | 量测机（可空） |
| remark | VARCHAR(256) | Y | |
| collected_by | BIGINT | Y | |
| collected_at | DATETIME | N | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | |

索引：

- `idx_edc_col_visit (lot_id, track_in_tx_id, collected_at)`
- `idx_edc_col_lot_step (lot_id, step_id, collected_at)`

门禁：同 `track_in_tx_id` 取 `collected_at` 最新一条；`result=PASS` 才 clear。

---

## 6. 采集点 `mes_edc_collection_item`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| collection_id | BIGINT | N | |
| param_id | BIGINT | N | |
| spec_id | BIGINT | Y | 判定所用 Spec |
| usl_snap | DECIMAL(20,8) | Y | 落点快照（可选但推荐） |
| lsl_snap | DECIMAL(20,8) | Y | |
| value_num | DECIMAL(20,8) | N | 一期数值 |
| item_result | VARCHAR(16) | N | PASS / OOS |
| create_time | DATETIME | | |
| deleted | TINYINT | N | |

索引：`idx_edc_col_item (collection_id)`。

头 `result`：任一项 OOS 或缺必采 → FAIL；否则 PASS。

---

## 7. 履历（可选）

| 方式 | 说明 |
|------|------|
| 推荐 | `mes_tx_log.tx_type = EDC_COLLECT`；`ref_id = collection_id`（或 context JSON） |
| 禁止 | 在 `mes_lot` / `mes_wip_lot` 增「最近量测结果」作为真相 |

T2-7 **不**因 EDC 失败写 TRACK_OUT。

---

## 8. 权限种子

| 码 | 说明 |
|----|------|
| `edc:view` | 菜单 `/app/edc` |
| `edc:edit` | Param / Plan / Spec 草稿 |
| `edc:publish` | Spec 发布 |
| `edc:collect` | 提交采集 |

角色：admin / process_eng 全量；supervisor view；operator view+collect。  
permission id：`253–256`（`migrate_edc.sql`）。

---

## 9. 与他表关系

| 动作 | edc 表 | 他模块 |
|------|--------|--------|
| TrackOut 门禁 | 只读 Facade | 不 join 写 lot/wip |
| TrackIn | 不写 edc | 提供 track_in_tx_id |
| Route 发布 | 无 | **不**拷贝 Spec/Plan 进快照 |
| SPC（后置） | 只读 collection* | 自建控制限表，不改写点 |

**禁止：** `mes_lot` / `mes_route_*` 上建指向 `mes_edc_*` 的硬 FK（便于迁库）。

---

## 10. 后置表（一期不建）

| 表 | 用途 |
|----|------|
| `mes_edc_wafer_value` | 片级点 |
| `mes_edc_control_limit` | SPC 控制限（属 SPC 模块） |
| `mes_edc_auto_map` | 机台参数映射 |
| `mes_edc_bypass` | 强行放行审计 |

---

## 11. 关联

- `MES-EDC功能文档.md`
- `MES-EDC一期功能清单.md`
- DDL：`migrate_edc.sql`
