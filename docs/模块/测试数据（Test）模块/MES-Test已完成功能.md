---
type: 已完成功能
module: Test
status: done
slices: [TD-1]
aligns: [MES-Test功能文档.md, TD-1-plan.md]
updated: 2026-10-09
---

# MES 测试数据（Test）— 已完成功能（查验清单）

> 对齐：`MES-Test功能文档.md` · `TD-1-plan.md`  
> 切片：**TD-1 ✅**（2026-10-09，步骤 a–g）  
> 图例：✅ 已完成 · ⏳ 未做

---

## 0. 总览

| 能力 | 后端 | 前端 | 权限 |
|------|------|------|------|
| 测试记录登记 / 分页 / 详情 | ✅ `/test/records` | ✅ `/app/test` | `test:create` / `view` |
| 作废 | ✅ `PUT .../void` | ✅ 详情抽屉 | `test:void` |
| 按批摘要 | ✅ `/test/summary/by-lot/{id}` | ✅ Lots 详情「测试结果」 | `test:view` |
| Bin 字典 CRUD | ✅ `/test/bins` | ✅ Bin 字典 Tab | `test:view` / `edit-bin` |
| Strip 登记 / 列表 | ✅ `/lots/{id}/strips` | ✅ Lots「Mapping / 条级」 | `lot:edit` / `list` |
| 客户映射正反查 | ✅ customer-maps · by-external-lot | ✅ 同区 | `lot:edit` / `list` |
| 客诉三块 | ✅ Assembler + README 24 行口径 | —（包抽屉既有） | `complaint:*` |
| 守卫表判重 V6 | ✅ `mes_test_submit_guard` | 重入闸 + 用例 | — |
| 现场台 | — | ✅ **零改动** | — |

---

## 1. 工程约束

| 项 | 状态 |
|----|------|
| Track / Hold 零改状态、零写 tx_log | ✅ |
| `TestFacade` 只读；校验留 Service | ✅ |
| 客诉禁直连 `mes_test_*` | ✅ |
| 软删 TINYINT；UK 不含 deleted | ✅ |
| 单元：V1/V2/V5/V6/K7 + 反向探针 | ✅ `MesTestRecordServiceImplTest` |
| 前端登记重入闸 | ✅ `TestPage.test.tsx` |

---

## 2. 后置（非 TD-1）

- TD-2：不良 Bin → Hold/Rework **建议**
- TD-3：颗级 Die / 条级分档 / STDF·CSV 解析 / 设备选择器解析 `eqpId`

---

## 关联

- `MES-Test功能文档.md` · `MES-Test接口设计.md` · `MES-Test数据库设计.md` · `TD-1-plan.md`
- Lot：`MES-Lot已完成功能.md`（Strip / 映射）
- 客诉：`MES-客诉追溯包接口设计.md` §6.2 / §6.4
