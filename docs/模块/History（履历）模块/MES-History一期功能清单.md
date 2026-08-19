# MES 履历追溯（History）— 一期功能清单

> 前提：Track 已写 `mes_tx_log`；Genealogy P0 已闭环  
> 对齐：`MES-History功能文档.md` · `MES-History接口设计.md` · `MES-History已完成功能.md`  
> 更新：2026-08-19  
> 状态：**H-1～H-5 已闭环**

---

## 0. 目标

先交付「查得真、圈得住、异常看得出」；片级 / 客诉包 / 独立库后置。

原则：

- 写仍只在 Track；History 只读
- 业务只调 `HistoryFacade`
- `/app/history` 接真数据；现场侧栏不改

现状：写齐；Facade + 调查查询 + 设备反查 + Admin 调查台（按日密排日志）已落地。

---

## 1. 范围总览

| 优先级 | 能力 | 状态 |
| --- | --- | --- |
| P0 | `HistoryFacade`；Lot 履历查询从 TrackService 委托 | ✅ |
| P0 | 管理端分页 + 类型/时间过滤 | ✅ |
| P0 | 设备反查 + `idx_tx_eqp_time` | ✅ |
| P0 | `/app/history` 调查台（异常加重、行详情、禁 mock） | ✅ |
| P0 | SPLIT/MERGE 跳已有谱系 | ✅ |
| P1 | `EDC_COLLECT` 履历；Recipe 展示增强 | 后置 |
| P2 | 客诉包、片级、分表/独立只读库 | 后置 |

---

## 2. 切片

| 切片 | 交付 | 状态 |
| --- | --- | --- |
| H-1 | `HistoryFacade` + `GET /lots/{id}/history` 委托；无新表 | ✅ |
| H-2 | `GET /history` 分页过滤（lotId / eqpId / txType / 时间） | ✅ |
| H-3 | DDL `idx_tx_eqp_time`；设备反查走 Facade | ✅ |
| H-4 | Admin `/app/history` 真数据：Lot 时间线 + 设备模式 + 行详情 | ✅ |
| H-5 | SPLIT/MERGE 跳 GenealogyTree（Lots 已有能力，本页入口） | ✅ |

**禁止**跳过 Facade 在页面直查 `mes_tx_log`。  
**禁止**改 Track 侧栏当「履历模块」。

已有库上线前执行 `server/src/main/resources/db/migrate_history.sql`。

---

## 3. 验收（总）

见 `MES-History功能文档.md` §8。查验：`MES-History已完成功能.md`。

---

## 4. 关联

- `MES-History功能文档.md`
- `MES-History接口设计.md`
- `MES-History已完成功能.md`
- `MES-LotGenealogy接口设计.md`
- `MES-Track数据库设计.md` §2
