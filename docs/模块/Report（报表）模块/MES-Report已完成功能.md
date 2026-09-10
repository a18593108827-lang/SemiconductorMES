# MES 报表（Report）— 已完成功能（查验清单）

> 对齐：`MES-Report架构设计.md` · `MES-Report一期功能清单.md`  
> 现状：Move/Hold 只读聚合 + Admin 复盘页 + 侧栏「复盘→报表」已落地；联调通过  
> 更新：2026-09-10

---

## 1. Facade / HTTP

| 项 | 状态 |
|----|------|
| `ReportFacade` / `ReportFacadeImpl` | ✅ `com.mes.report` |
| `GET /report/move` | ✅ `report:view`；`from`/`to`；跨度 >31 → 400 |
| `GET /report/hold` | ✅ 同上 |
| Move：按日 TRACK_OUT；缺日补 0 | ✅ `HistoryFacade#countDailyByTxType` |
| Move：按站；无站「未归属」 | ✅ `HistoryFacade#countByStepAndTxType` |
| Hold：按 reasonCode；holdCount / activeCount / avgDurationMinutes | ✅ `HoldService#summarizeByReason` |
| 域失败 → `partial` + errors | ✅ 不向上冒泡成 500 |
| Report 包无写业务表 | ✅ |

## 2. 权限 / 菜单

| 项 | 状态 |
|----|------|
| `report:view` | ✅ id=311 |
| 目录「复盘」 | ✅ id=310；sort 在生产执行与系统管理之间 |
| 菜单「报表」→ `/app/report` | ✅ parent=310；**不在**生产执行下 |
| admin / process_eng / supervisor 绑定 | ✅ `migrate_report.sql` / schema |

## 3. 管理端

| 项 | 状态 |
|----|------|
| `ReportPage` · 路由 `/app/report` | ✅ |
| 日期窗 + 近7/30天 + 查询 | ✅ 非轮询 |
| Move 按日图 + 按站表 | ✅ Recharts |
| Hold 原因表 + 条形 | ✅ |
| 链履历 / 锁批；看板·履历「打开报表」 | ✅ |
| 无 `report:view` 提示 | ✅ |

## 4. 一期不做（仍后置）

Hold 按站 · Yield · OEE · Excel · 预聚合日表 · 班次

---

明细勾选见 `MES-Report一期功能清单.md` §3。
