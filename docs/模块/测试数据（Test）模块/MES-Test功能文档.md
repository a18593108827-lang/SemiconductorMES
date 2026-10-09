---
type: 功能文档
module: Test
status: done
slices: [TD-1]
aligns: [MES-封测测试数据与Bin回流方案.md, INT-0001-封测颗级追溯与测试数据回流.md]
updated: 2026-10-09
---

# MES 测试数据（Test）功能文档

> 定位：封测**测试记录 + Bin 分档**真相；管理端手录；跨模块只读经 `TestFacade`  
> 产品一句话：一次测试结果与各档颗数进系统，和批次绑定，客诉包能带出分档  
> 对齐：`docs/方案/MES-封测测试数据与Bin回流方案.md`；`INT-0001`；架构铁律（Track SSOT）  
> 切片：**TD-1 ✅**（a–g）  
> 更新：2026-10-09  
> 查验：`MES-Test已完成功能.md` · 表：`MES-Test数据库设计.md` · 接口：`MES-Test接口设计.md` · plan：`TD-1-plan.md`

---

## 1. 目标（TD-1）

- 维护 Bin 字典（GLOBAL / PRODUCT / PROGRAM / PROGRAM_VERSION；不适用维填空串）
- 管理端登记测试记录：程序名 / 版本 / 总量 / 各档颗数；硬档合计必须等于总量
- 作废记录（原因必填；只软删头表；`record_no` 不复用）
- Lot 详情展示测试结果摘要；Strip / 客户映射在 Lot 模块（同切片）
- 客诉包带 `testSummaryByLot`（经 `TestFacade`）

**不做（TD-1）：** 颗级 Die、STDF/CSV 解析、条级分档、不良→Hold 自动建议、现场台录入、写 `mes_tx_log` / 改 Lot.status。

---

## 2. 边界

```
Test     = 测试记录头 + Bin 汇总快照 + 字典 + 提交守卫；只读 Facade
Lot      = Strip / 客户映射主数据；不代理 /test/summary
Track    = 状态真相；本模块零改状态
Hold     = 不联动（TD-2）
Complaint= 装配只经 TestFacade / MesLotService 只读，禁直连 mes_test_*
UI       = 管理端 /app/test + Lots 详情两区；现场台零改动（A5）
```

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | 客诉 / Report 直连 `mes_test_*` mapper | 跨模块须 Facade |
| P2 | Test 注入 `MesLotMapper` | D22；Lot 只经 Service |
| P3 | 先 SELECT 判重再 INSERT | 并发漏网；走守卫表 UK（V6） |
| P4 | 作废删汇总 / 删守卫 | 汇总随头消失；守卫保留防同桶重登 |
| P5 | PUT 冒充改 qty / 覆盖重测 | 重测新开记录；记录表无业务 UK |

---

## 3. 核心规则（摘要）

| 码 | 规则 |
|----|------|
| V1 | `Σ HARD.bin_qty == total_qty`，否则拒且零落库 |
| V2 | 字典四级回退：程序版本 → 程序 → 产品 → 全局 |
| V3 | `program_name` / `program_version` / `test_time` 必填 |
| V4 | `merged` / `scrapped` 拒登记 |
| V5 | 同请求 `(bin_type, bin_code)` 不重复 |
| V6 | 同桶守卫 UK → `TEST_RECORD_DUPLICATE` |
| K7 | 汇总行 `bin_name` / `is_shippable` 登记时快照 |
| A11 | 作废只软删头；守卫不删 |

---

## 4. 权限与菜单

| 码 | 用途 |
|----|------|
| `test:view` | 列表 / 详情 / 摘要 / Bin 查询；菜单「测试数据」 |
| `test:create` | 登记记录 |
| `test:void` | 作废 |
| `test:edit-bin` | Bin 字典增改 |

挂管理端生产执行目录；现场 operator 不授。

---

## 5. 关联

- 方案：`docs/方案/MES-封测测试数据与Bin回流方案.md`
- Lot Strip / 映射：`docs/模块/Lot（批次）模块/`
- 客诉三块：`MES-客诉追溯包接口设计.md` §6.2 / §6.4
