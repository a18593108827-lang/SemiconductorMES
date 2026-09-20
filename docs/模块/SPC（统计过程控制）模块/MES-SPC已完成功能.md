---
type: 已完成功能
module: SPC
status: done
slices: []
aligns: []
updated: 2026-09-02
---

# MES SPC — 已完成功能

> 对齐：`MES-SPC一期功能清单.md` · `MES-SPC架构设计.md`  
> 更新：2026-09-02

---

## 已落地

| 切片 | 说明 |
|------|------|
| SPC-1 | `EdcFacade.listSeries` / `getCollection`；`EdcCollectedEvent`；索引 `idx_edc_col_step_time` |
| SPC-2 | `mes_spc_chart` / `mes_spc_eval`；权限 257/258；`mes.spc.enabled` |
| SPC-3 | `SpcFacade` + `SpcCollectedListener`；I-MR / WE1 / RUN；LEARNING 写限；OOC → eval + `SPC_OOC` |
| SPC-4 | HTTP `/spc`；查 `spc:view`，写 `spc:edit` |
| SPC-5 | Admin `/app/spc`：左列表 + 右 I/MR/点表；Drawer 维护 |

## 关键路径

| 项 | 路径 |
|----|------|
| 读点 / 事件 | `com.mes.edc.facade.EdcFacade` · `com.mes.edc.event.EdcCollectedEvent` |
| Facade | `com.mes.spc.facade.SpcFacade` |
| 监听 | `com.mes.spc.listener.SpcCollectedListener` |
| I-MR | `com.mes.spc.support.SpcImr` |
| HTTP | `com.mes.spc.controller.MesSpcController` |
| Admin | `web/src/pages/SpcPage.tsx` · `web/src/api/spc.ts` |
| DDL | `server/src/main/resources/db/migrate_spc.sql` |
| 配置 | `mes.spc.enabled`（`application.yml`） |

## HTTP（SPC-4）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/spc/charts` | `spc:view` |
| GET | `/spc/charts/{id}` | `spc:view` |
| GET | `/spc/charts/{id}/series` | `spc:view` |
| POST | `/spc/charts` | `spc:edit` |
| PUT | `/spc/charts/{id}/limits` | `spc:edit` |
| PUT | `/spc/charts/{id}/enabled` | `spc:edit` |
