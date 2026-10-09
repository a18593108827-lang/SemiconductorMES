---
type: 数据库设计
module: Test
status: done
slices: [TD-1]
aligns: [MES-封测测试数据与Bin回流方案.md, TD-1-plan.md]
updated: 2026-10-09
---

# MES 测试数据（Test）— 数据库设计

> 脚本：`migrate_test.sql`（Test 5 表 + 权限）· Lot 扩展见 `migrate_lot_pkg.sql` · 新库同步 `schema.sql`  
> 更新：2026-10-09 · **TD-1 ✅**

---

## 1. `mes_bin_def`（字典）

| 字段 | 说明 |
|------|------|
| bin_scope | GLOBAL / PRODUCT / PROGRAM / PROGRAM_VERSION |
| product_code / program_name / program_version | 不适用填 **空串**（非 NULL） |
| bin_type / bin_code / bin_name | HARD/SOFT + 号 + 名 |
| is_shippable / status / version | 可出货 · 启停 · 乐观锁 |
| deleted | TINYINT 软删；**UK 不含 deleted** |

UK：`(product_code, program_name, program_version, bin_type, bin_code)`。演示种子 GLOBAL HARD 1–4。

---

## 2. `mes_test_record`（头）

| 字段 | 说明 |
|------|------|
| record_no | `TR-yyyyMMdd-###`，UK |
| lot_id / lot_no | 所属批 + 快照 |
| test_stage / program_* / test_time / total_qty | 业务键字段 |
| eqp_id / eqp_code | 可空 |
| source_type / source_ref | FILE/API/MANUAL |
| deleted | 作废 = 软删头 |

索引：`lot_id` · `(lot_id, test_time)`。**无**业务唯一键（允许重测）。

---

## 3. `mes_test_bin_summary`（汇总，无软删）

挂 `record_id`；`bin_name` / `is_shippable` 为登记快照。UK `(record_id, bin_type, bin_code)`。实体不继承 `BaseEntity`。

查询前提：调用方只传未作废头 id（应用层保证；`loadBins` 不 JOIN 头）。

---

## 4. `mes_test_submit_guard`（判重，无软删）

UK：`(lot_id, eqp_key, program_name, program_version, test_time, total_qty, window_bucket)`。  
`eqp_key`：有设备写 `eqp_id`，无设备写 **0**。  
`window_bucket = FLOOR(UNIX_TIMESTAMP(NOW())/600)`。作废**不删**守卫行。

---

## 5. `mes_test_record_no_seq`

| 字段 | 说明 |
|------|------|
| seq_day | yyyyMMdd PK |
| next_no | 当日已分配流水 |

与 insert 同事务同连接取 `LAST_INSERT_ID()`。

---

## 6. Lot 扩展（同切片）

见 `MES-Lot数据库设计.md`：`mes_lot_strip` · `mes_lot_customer_map`。`mes_lot` **结构不变**，本切片**不写** `customer_lot` 列。

---

## 7. 权限种子

| id | 码 |
|----|-----|
| 340 | `test:view`（菜单 `/app/test`，icon `clipboard`） |
| 341 | `test:create` |
| 342 | `test:void` |
| 343 | `test:edit-bin` |
