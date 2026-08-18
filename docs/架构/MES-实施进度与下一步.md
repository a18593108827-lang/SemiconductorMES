# MES 实施进度与下一步

> 更新：2026-08-18  
> 用途：记录当前落地进度、下一模块优先级、与业界预期对齐（骨架，后续可补）

---

## 1. 当前进度

| 模块 | 状态 | 说明 |
|------|------|------|
| 权限 / 用户 | ✅ 一期已完成 | 登录、RBAC、菜单、用户中心、申请审批 |
| Route | ✅ 一期已完成 | 工序/路线/版本、草稿编辑、发布/升版、单草稿 |
| Lot | ✅ 一期已完成 | 主数据 + 放行绑版本（兼容接口） |
| Track | ✅ 一期+二期部分 | Release/In/Out/Move/Abort、Q-Time（到期自动 Hold+清窗）/ProcessTime；T2-7 EDC 门禁 ⏸ 后续（先 EDC） |
| WIP | ✅ 一期已完成 | 投影表 + 列表/按站汇总 + Admin 页 |
| Hold | ✅ 最小集 + Future Hold P0 | 即时 Hold；预约锁批 FH-1～3；FH-4/FH-5 后置 |
| Equipment | ✅ 最小集前后端 | 主数据 CRUD / 改态 / TrackIn assertUsable / 现场选机；Adapter 后置 |
| Dispatch | ✅ 最小集前后端 | 候选/推荐、Reserve；已接 Recipe 资格过滤；Admin `/app/dispatch`；APS/规则表后置 |
| Recipe | ✅ 一期前后端 | 主数据/版本/绑定/Facade+钩子；权限 246–249；Admin `/app/recipe`；RMS 后置 |
| EDC | ⏳ 主数据/手录/Facade 已齐 · 拒出未接 | 见 `docs/模块/EDC（量测）模块/`；下一步 T2-7 |
| SPC | ⏸ 可后置 | 控制图/CPK/Alarm；**不挡** EDC 门禁；见 `MES-EDC与SPC范围说明.md` |

契约：Route 快照 ✅；Track 一期见 `docs/模块/Track（执行引擎）模块/`。  
Recipe 查验：`docs/模块/Recipe（配方）模块/MES-Recipe已完成功能.md`。

---

## 2. 建议落地顺序

1. ~~**Lot**~~ ✅  
2. ~~**Track（全套模型、分期实现）**~~ ✅ 一期；Move/Abort/ProcessTime 等二期项见 Track 清单  
3. ~~**WIP**~~ ✅  
4. ~~**Hold（最小集）**~~ ✅  
5. ~~**Equipment（最小集）**~~ ✅  
6. ~~**Dispatch（最小集）**~~ ✅  
7. ~~**Recipe（MES 内嵌最小集）**~~ ✅ →（可选）规则表 / What-Next；APS / 独立 RMS / Adapter 后置  
8. ~~**EDC 最小集**~~ ✅ Facade 已齐 → **TrackOut EDC 门禁 T2-7**（EDC-6/7）  
9. **SPC**（再后置，可选同期规划）：控制图 / CPK；不挡 ⑧  


---

## 3. 与业界预期（对照摘要）

| 能力 | 大厂常见 | 本项目预期 | 结论 |
|------|----------|------------|------|
| Route 版本 + 放行快照 | 标配 | 已定/部分落地 | 相符 |
| Track 事务驱动状态 | 中枢 | Phase1 | 相符 |
| WIP 只读投影 | 常见 | 架构已定 | 相符 |
| 线性主路径先做 | 起步常见 | 一期不做分支 | 相符 |
| Hold | MVP 常见 | 最小集 + Future Hold P0 已落地 | 相符 |
| History（履历） | MVP 常见 | Track 一期已有 `mes_tx_log` | 相符（管理端页可后补） |
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
| WIP | `docs/模块/WIP（在制）模块/` |
| 架构总册 | `docs/架构/半导MES架构设计.md` |
| 定时任务 | `docs/架构/MES-SpringScheduled使用.md` |
| 业务总单 | `docs/业务清单/MES-半导体业务清单.md` |

---

## 5. 待决策（从架构抄录，落地前拍板）

| 项 | 选项 | 影响 |
|----|------|------|
| 厂型 | 前道 / 后道 / 封测 | 是否要片级 |
| Lot 状态枚举 | 与 Track 对齐命名 | 接口与前端 |
| `lot:release` 是否并入 `lot:edit` | 权限粒度 | 种子数据 |
