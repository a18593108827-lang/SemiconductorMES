---
type: 进度
module: 
status: done
slices: []
aligns: []
updated: 2026-09-21
---

# MES 实施进度与下一步

> 更新：2026-09-21  
> 用途：记录当前落地进度、下一模块优先级、与业界预期对齐（骨架，后续可补）
---

## 1. 当前进度

| 模块 | 状态 | 说明 |
|------|------|------|
| 权限 / 用户 | ✅ 一期已完成 | 登录、RBAC、菜单、用户中心、申请审批 |
| Route | ✅ 一期已完成 | 工序/路线/版本、草稿编辑、发布/升版、单草稿 |
| Lot | ✅ 一期已完成 | 主数据 + 放行绑版本（兼容接口） |
| Track | ✅ 一期+二期部分 | Release/In/Out/Move/Abort、Q-Time / ProcessTime；T2-7 EDC 钩子 + 现场提示 ✅ |
| WIP | ✅ 一期已完成 | 投影表 + 列表/按站汇总 + Admin 页 |
| Hold | ✅ 最小集 + Future Hold P0 | 即时 Hold；预约锁批 FH-1～3；FH-4/FH-5 后置 |
| Equipment | ✅ 最小集前后端 | 主数据 CRUD / 改态 / TrackIn assertUsable / 现场选机；Adapter 后置 |
| Dispatch | ✅ 最小集前后端 | 候选/推荐、Reserve；已接 Recipe 资格过滤；Admin `/app/dispatch`；APS/规则表后置 |
| Recipe | ✅ 一期前后端 | 主数据/版本/绑定/Facade+钩子；权限 246–249；Admin `/app/recipe`；RMS 后置 |
| EDC | ✅ 一期 P0 | 采/判/Facade/拒 Out/现场提示；OOS Auto-Hold / `EDC_COLLECT` 已做；见 `docs/模块/EDC（量测）模块/` |
| History | ✅ 一期 P0 + 客诉包 CP-5 | 写在 Track；`HistoryFacade` + `/app/history`；客诉包 build/get/list/export/contain ✅；ZIP 后置 |
| SPC | ✅ SPC-1～5 已落地 | 读点/表/判异/HTTP/工艺页；见 `MES-SPC已完成功能.md` |
| Alarm | ✅ Alarm-1～4 | 落库/HTTP/Admin/STOMP；见一期清单 |
| Dashboard | ✅ 一期已完成 | overview 真数 + Admin 看板（铺满视口）；见 `docs/模块/Dashboard（看板）模块/` |
| Report | ✅ 一期已完成 | Move/Hold API + `/app/report` + 侧栏「复盘→报表」；联调通过；Yield·导出后置 |

契约：Route 快照 ✅；Track 一期见 `docs/模块/Track（执行引擎）模块/`。  
Recipe 查验：`docs/模块/Recipe（配方）模块/MES-Recipe已完成功能.md`。  
History 查验：`docs/模块/History（履历）模块/MES-History已完成功能.md`。  
Report 查验：`docs/模块/Report（报表）模块/MES-Report已完成功能.md`。

---

## 2. 建议落地顺序

1. ~~**Lot**~~ ✅  
2. ~~**Track（全套模型、分期实现）**~~ ✅ 一期；Move/Abort/ProcessTime 等二期项见 Track 清单  
3. ~~**WIP**~~ ✅  
4. ~~**Hold（最小集）**~~ ✅  
5. ~~**Equipment（最小集）**~~ ✅  
6. ~~**Dispatch（最小集）**~~ ✅  
7. ~~**Recipe（MES 内嵌最小集）**~~ ✅ →（可选）规则表 / What-Next；APS / 独立 RMS / Adapter 后置  
8. ~~**EDC 最小集**~~ ✅ 含现场拒出提示；P1 Auto-Hold / `EDC_COLLECT` ✅  
9. ~~**History（调查台）**~~ ✅ Facade + `/app/history` 真数据 + 设备反查；客诉包 CP-5 contain ✅；ZIP 后置  
10. ~~**SPC 趋势预警**~~ ✅ SPC-1～5；`/app/spc`；不挡 TrackOut  
11. ~~**Alarm 告警台**~~ ✅ Alarm-1～4；见 `MES-Alarm一期功能清单.md`；P1 Hold 策略 / P2 GEM 后置  
12. ~~**Dashboard 看板真数**~~ ✅ KPI + 设备矩阵 + 报警流 + TrackOut 趋势；见 Dashboard 清单  
13. ~~**Report 基础报表**~~ ✅ Rep-1～4（Move/Hold + Admin + 复盘/报表侧栏）  

Carrier / Adapter / Alarm→Hold / 片级等：**本轮不动**（Carrier 架构稿已立：`docs/模块/Carrier（载具）模块/MES-Carrier架构设计.md`）。
## 3. 与业界预期（对照摘要）

| 能力 | 大厂常见 | 本项目预期 | 结论 |
|------|----------|------------|------|
| Route 版本 + 放行快照 | 标配 | 已定/部分落地 | 相符 |
| Track 事务驱动状态 | 中枢 | Phase1 | 相符 |
| WIP 只读投影 | 常见 | 架构已定 | 相符 |
| 线性主路径先做 | 起步常见 | 一期不做分支 | 相符 |
| Hold | MVP 常见 | 最小集 + Future Hold P0 已落地 | 相符 |
| History（履历） | MVP 常见 | 写 + 调查台 + 设备反查已落地 | 相符；客诉包 contain ✅；片级 / ZIP 后置 |
| Eqp + Recipe + Dispatch | 量产必备 | Eqp/Dispatch/Recipe 最小集已落地 | 相符 |
| 片级 / Send-ahead / Experiment | 前道标配 | 非一期 | 刻意不做 |
| PCRB 级工艺变更板 | 大厂强 | 仅有权限申请 | 后补 |

**结论**：主路径模型与行业一致；深度按 MVP 收敛，不按 12 寸满配一次做完。

参考公开材料方向：Applied SmartFactory（Lot tracking = MES runtime）、Critical Manufacturing（主数据版本与对 WIP 影响可控）等。

---

## 4. 文档入口

| 文档 | 路径 |
|------|------|
| 权限 / 用户 | `docs/模块/权限、用户模块/` |
| Lot | `docs/模块/Lot（批次）模块/` |
| Route | `docs/模块/Route（工艺路线）模块/` |
| Track | `docs/模块/Track（执行引擎）模块/` |
| Hold | `docs/模块/Hold（锁批）模块/` |
| Equipment | `docs/模块/Equipment（设备）模块/` |
| Dispatch | `docs/模块/Dispatch（派工）模块/` |
| Recipe | `docs/模块/Recipe（配方）模块/` |
| EDC | `docs/模块/EDC（量测）模块/` |
| SPC | `docs/模块/SPC（统计过程控制）模块/`（清单 · 架构 · 已完成） |
| Alarm | `docs/模块/Alarm（告警）模块/MES-Alarm架构设计.md` |
| Dashboard | `docs/模块/Dashboard（看板）模块/`（架构 · 一期清单） |
| Report | `docs/模块/Report（报表）模块/`（架构 · 一期清单） |
| Carrier | `docs/模块/Carrier（载具）模块/`（架构 · 一期清单；**Car-1～5 ✅**） |
| History | `docs/模块/History（履历）模块/` |
| WIP | `docs/模块/WIP（在制）模块/` |
| 架构总册 | `docs/架构/半导MES架构设计.md` |
| 定时任务 | `docs/架构/MES-SpringScheduled使用.md` |
| 业务总单 | `docs/业务清单/MES-半导体业务清单.md` |
| 文档总账 | `docs/INDEX.md`（按 type / module / status 检索，自动生成） |
| 根级上下文 | `AGENTS.md`（AI 会话第一入口） |
| 意图目录 | `docs/intent/`（新需求唯一入口，INT 编号） |
| 模板 | `docs/_templates/`（INT / plan / EVAL / AGENTS 骨架） |

---

## 5. 文档体系重构（AI 原生 SDLC 对齐）

方案：`docs/架构/MES-AI原生SDLC文档体系重构方案.md`（2026-09-20 批准）

| 切片 | 交付 | 状态 |
|------|------|------|
| Doc-1 | 根级 `AGENTS.md` + `docs/_templates/` 四件模板 + `intent/`、`eval/` 目录占位 | ✅ 2026-09-20 |
| Doc-2 | 存量文档批量补 frontmatter（只加头不动正文）+ `docs/INDEX.md` 总账 | ✅ 2026-09-20 |
| Doc-3 | 全链试运行：Agent 数据暴露（Tool Facade）走 INT → 规格 → plan → 交付 | 待启动 |
| Doc-4 | （后置）`src/test` + `mvn test` 反馈回路 | 工程排期 |
| Doc-5 | （后置）eval 启用 + CI 挂 frontmatter 状态校验 | 依赖 Doc-4 |

---

## 6. 待决策（从架构抄录，落地前拍板）

| 项 | 选项 | 影响 |
|----|------|------|
| 厂型 | 前道 / 后道 / 封测 | 是否要片级 |
| Lot 状态枚举 | 与 Track 对齐命名 | 接口与前端 |
| `lot:release` 是否并入 `lot:edit` | 权限粒度 | 种子数据 |
