# MES 配方（Recipe）— 数据库设计

> 对齐：`MES-Recipe功能文档.md`  
> 状态：**已落地**（`migrate_recipe.sql` / `schema.sql`）  
> 更新：2026-07-30

---

## 1. 主表 `mes_recipe`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | 雪花 |
| recipe_code | VARCHAR(64) UK | N | 短名 / 编码 |
| recipe_name | VARCHAR(128) | N | |
| enabled | TINYINT | N | 1启用 |
| remark | VARCHAR(256) | Y | |
| version | INT | N | 乐观锁，默认 0 |
| create_by / update_by | BIGINT | Y | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | |

索引：`uk_recipe_code (recipe_code)`。

---

## 2. 版本表 `mes_recipe_version`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | 雪花 |
| recipe_id | BIGINT | N | → mes_recipe |
| version_no | INT | N | 同 recipe 内递增 |
| status | VARCHAR(16) | N | draft / active / obsolete |
| body_json | TEXT | Y | 一期可选；参数 JSON |
| body_object_key | VARCHAR(256) | Y | MinIO 键；与 json 二选一或并存 |
| remark | VARCHAR(256) | Y | |
| published_at | DATETIME | Y | |
| create_by / update_by | BIGINT | Y | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | |

约束：

- `uk_recipe_ver (recipe_id, version_no)`
- 应用层保证：同 `recipe_id` 至多一条 `status=active`

---

## 3. 绑定表 `mes_recipe_binding`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| step_id | BIGINT | N | → mes_step |
| eqp_id | BIGINT | Y | 具体机；与 eqp_type 二选一 |
| eqp_type | VARCHAR(64) | Y | 类型级绑定 |
| recipe_id | BIGINT | N | 逻辑配方 |
| recipe_version_id | BIGINT | Y | 可空=跟随该 recipe 当前 active |
| enabled | TINYINT | N | |
| create_time / update_time | DATETIME | | |
| deleted | TINYINT | N | |

规则：`eqp_id` 与 `eqp_type` **不可同时空**；同时有时以 `eqp_id` 优先（解析顺序见功能文档 §4.3）。

索引：`idx_bind_step (step_id, enabled)`、`idx_bind_eqp (eqp_id)`、`idx_bind_type (step_id, eqp_type)`。

---

## 4. 履历扩展 `mes_tx_log`

| 字段 | 说明 |
|------|------|
| recipe_id | 可空；TrackIn 解析命中时写入 |
| recipe_version_id | 可空；**追溯锚点**，禁止只存 code 字符串 |

一期已加列：`recipe_id`、`recipe_version_id`（TrackIn 解析命中时写入）。

---

## 5. 权限种子（已落地）

| id | 码 | 说明 |
|----|-----|------|
| 246 | `recipe:view` | 菜单 `/app/recipe` |
| 247 | `recipe:edit` | 主数据 / 草稿 / 启停 |
| 248 | `recipe:publish` | 发布 |
| 249 | `recipe:bind` | 绑定 |

角色：admin / process_eng 全量；supervisor 仅 view。  
DDL：`migrate_recipe.sql` · `schema.sql`。

---

## 6. 与他表关系

| 动作 | recipe 表 | 他模块 |
|------|-----------|--------|
| Dispatch 候选 | 只读 binding | 不 join 写业务 |
| TrackIn | 只读 resolve | lot/wip 不强制存 recipe；履历必记 version_id |
| Route 发布 | 无 | 不拷贝 body |

**禁止：** `mes_lot` / `mes_route_*` 上建指向 `mes_recipe` 的硬 FK（便于日后迁库）。

---

## 7. 后置表（不建）

| 表 | 用途 |
|----|------|
| `mes_recipe_chamber_map` | Chamber 级 |
| `mes_recipe_eqp_path` | 机台目录 ↔ 短名 |
| `mes_recipe_lot_override` | DOE / 实验覆盖 |
| `mes_recipe_download_log` | Adapter 上下传 |

---

## 8. 关联

- `MES-Recipe功能文档.md`
- DDL：`migrate_recipe.sql` · `schema.sql`
