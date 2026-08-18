# MES 在制（WIP）— 功能查验清单

> 更新：2026-07-27  
> 状态：**一期前后端已落地**（列表 + 按站汇总；WS 后置）  
> 需求依据：`MES-WIP功能文档.md` · `MES-WIP数据库设计.md`

图例：✅ 已完成 · ⏳ 未做 · — 不适用

---

## 0. 完成度总览

| 能力 | 后端 | 前端 | 权限码 |
|------|------|------|--------|
| 在制列表 `GET /wip` | ✅ | ✅ WipPage | `wip:list` |
| 按站汇总 `GET /wip/summary/by-step` | ✅ | ✅ 站条筛选 | `wip:list` |
| 默认仅 wait/processing/held | ✅ | ✅ | — |
| 读 mes_wip_lot（Track 同步） | ✅ Track/Lot 同事务 sync | — | — |
| 独立投影表 mes_wip_lot | ✅ `migrate_wip.sql` / `schema.sql` | — | — |
| WebSocket 实时 | — 二期 | — | — |
| 过账深链带 lotNo | — | ⏳ 见功能文档 §9.1 | — |
| 设备可读名 | ⏳ | ⏳ | 依赖 Equipment |
| 拥堵高亮 | — | ⏳ §9.2 | — |
| 菜单种子 | ✅ `wip:list` | ✅ `/app/wip` | `wip:list` |

---

## 1. 验收要点（一期）

- [x] 默认不含 created / completed  
- [x] Track 过站后刷新与现场台一致  
- [x] 按站汇总与列表一致  
- [x] 无写接口；无 `wip:list` 403  
- [x] 空态文案与跳转  

---

## 2. 后续完善（勾选用）

详见 `MES-WIP功能文档.md` §9。

- [ ] P0 过账深链 `/track?lotNo=`  
- [ ] P0 设备编码/名称展示  
- [ ] P1 最堵站高亮  
- [ ] P1 与 Lot 页话术区分  
- [ ] P2 WebSocket / 区线体 / Dispatch / 排队时长  

---

## 3. 关联

- 功能：`MES-WIP功能文档.md`（含 §9 产品债）  
- 库表：`MES-WIP数据库设计.md`  
- Track / Lot 模块文档  
- 进度：`docs/架构/MES-实施进度与下一步.md`  
