---
type: 接口设计
module: Test
status: done
slices: [TD-1]
aligns: [MES-封测测试数据与Bin回流方案.md, TD-1-plan.md]
updated: 2026-10-09
---

# MES 测试数据（Test）— 接口设计

> 对齐：`MES-Test功能文档.md` · `TD-1-plan.md` §2  
> 前缀：`/test`（管理端）；Strip / 映射见 Lot 接口  
> 更新：2026-10-09 · **TD-1 ✅**

业务错：`GlobalExceptionHandler` 恒 `code=500`，业务码在 `msg` 前缀；前端取 `msg`。

---

## 1. 测试记录

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/test/records` | `test:create` | 登记头 + 汇总 + 守卫（同事务） |
| GET | `/test/records` | `test:view` | 分页：`lotId` / `lotNo` / `stage` / `programName` / `from` / `to` / `page` / `size` |
| GET | `/test/records/{id}` | `test:view` | 详情含各档与占比 |
| PUT | `/test/records/{id}/void` | `test:void` | body `{ "reason" }`；只软删头 |
| GET | `/test/summary/by-lot/{lotId}` | `test:view` | 本批全部记录（Lot 详情）；**不经 Lot 代理** |

### 1.1 登记 Body（摘要）

`lotId` · `testStage`（CP/FT/OTHER）· `programName` · `programVersion` · `testTime` · `totalQty` · `sourceType`（FILE/API/MANUAL）· 可选 `eqpId`/`eqpCode`/`sourceRef`/`remark` · `bins[]`（`binType`/`binCode`/`binQty`）

### 1.2 错误码（msg 前缀）

| 前缀 | 场景 |
|------|------|
| `TEST_BIN_SUM_MISMATCH` | 硬档合计 ≠ 总量 |
| `TEST_BIN_DEF_NOT_FOUND` | 字典解析不到 |
| `TEST_RECORD_FIELD_REQUIRED` | 必填缺失 |
| `TEST_BIN_CODE_DUPLICATED` | 同请求档号重复 |
| `TEST_RECORD_DUPLICATE` | 守卫 / record_no UK |

---

## 2. Bin 字典

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/test/bins` | `test:view` | 可筛 product / program / version / binType |
| POST | `/test/bins` | `test:edit-bin` | 新增；不适用维传 `""` |
| PUT | `/test/bins/{id}` | `test:edit-bin` | 修改 / 停用；必带 `version`（乐观锁） |

额外前缀：`TEST_BIN_SCOPE_INCONSISTENT` · `TEST_BIN_DEF_CONFLICT`

---

## 3. Lot 侧（同切片，挂 `/lots`）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/lots/{id}/strips` | `lot:edit` | 批量登记；`LOT_STRIP_DUPLICATE` |
| GET | `/lots/{id}/strips` | `lot:list` | 本批条清单 |
| POST | `/lots/{id}/customer-maps` | `lot:edit` | INBOUND/OUTBOUND；`LOT_MAP_DUPLICATE`；**不写** `mes_lot.customer_lot` |
| GET | `/lots/{id}/customer-maps` | `lot:list` | 正查 |
| GET | `/lots/by-external-lot` | `lot:list` | 反查 |

`merged` / `scrapped` 拒写 Strip / 映射（与测试登记 V4 对齐）。

---

## 4. Facade（只读）

`TestFacade.listRecordsByLots(lotIds, capPerLot, binCap)` — 客诉装配唯一读口；校验不上门面。
