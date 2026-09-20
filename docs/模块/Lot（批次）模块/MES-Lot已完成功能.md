---
type: 已完成功能
module: Lot
status: done
slices: []
aligns: []
updated: 2026-08-10
---

# MES 批次（Lot）— 功能查验清单

> 更新：2026-08-10  
> 状态：**一期已落地**；**二期 Split/Merge/谱系/Scrap/Bonus 已闭环**  
> 需求依据：`MES-Lot功能文档.md` · `MES-Lot数据库设计.md` · `MES-Lot二期功能清单.md`

图例：✅ 已完成 · ⏳ 未做 · — 不适用

---

## 0. 完成度总览

| 能力 | 后端 | 前端 | 权限码 |
|------|------|------|--------|
| Lot 列表 | ✅ `GET /lots` | ✅ LotsPage | `lot:list` |
| 创建 Lot（自动/手动 lot_no） | ✅ `POST /lots` + `mes_lot_no_seq` | ✅ 抽屉留空自动生成 | `lot:add` |
| Lot 详情 | ✅ `GET /lots/{id}` | ✅ 维护抽屉（含 scrap_qty） | `lot:list` |
| 改属性 | ✅ `PUT /lots/{id}` | ✅ 已放行白名单 | `lot:edit` |
| Release 绑 `route_version_id` | ✅ `POST /lots/{id}/release` | ✅ 确认后放行 | `lot:release` |
| Split 分批 | ✅ `POST /track/split` | ✅ TrackPage 分批面板 | `track:split` |
| Merge 合批 | ✅ `POST /track/merge` + candidates | ✅ TrackPage 合批面板 | `track:merge` |
| Scrap 报废 | ✅ `POST /track/scrap` + reason-codes | ✅ TrackPage 报废面板 | `track:scrap` |
| Bonus 数量调整 | ✅ `POST /track/bonus` + reason-codes | ✅ TrackPage 数量调整面板 | `track:bonus` |
| Genealogy 谱系 | ✅ `GET /lots/{id}/genealogy` | ✅ 详情谱系简图（直系/影响面） | `lot:list` |
| 表结构 mes_lot + genealogy | ✅ `migrate_lot_split.sql` | — | — |
| 权限码 + 菜单种子 | ✅ 220–223；298–301 | ✅ `/app/lots` · `/track` | 见上 |

---

## 1. 验收要点

- [x] 无 active Route 版本时 Release 失败  
- [x] Release 后 `route_version_id` 不可改  
- [x] Route 再升版发布不影响已放行 Lot（快照机制）  
- [x] 详情可读出版本步骤（只读）  
- [x] priority 超出 1–100 校验失败  
- [x] lot_no 唯一（手动）/ 自动 `LOT-yyyyMMdd-流水`  
- [x] Track 按快照过站（Track 模块一期）  
- [x] Split 数量守恒；子继承快照与站  
- [x] Merge 同产品/快照/站；源 → `merged`；谱系可反查  
- [x] Scrap 部分/全批；`scrap_qty` 累加；全批 → `scrapped`  
- [x] Hold 中拒绝 Scrap；processing 拒绝 Scrap  
- [x] Bonus ±delta；不改 `scrap_qty`/status；独立原因码  
- [x] Hold / processing / 终态拒绝 Bonus；`qty_after=0` 不升格 scrapped  
- [x] `PUT /lots/{id}` 改 qty 拒绝  
- [x] 已放行：可改 priority/hot/customer_lot/remark；禁改 product/qty/route  
- [x] 终态 Lot 不可 `PUT` 改属性  

---

## 2. 已知后置

- Unscrap / Unbonus / ERP 过账 / NC Disposition  
- 片级 Wafer、Carrier、ERP 工单下发  
- 加工中合批、Future Hold 合批继承  

设计：`MES-LotSplit接口设计.md` · `MES-LotMerge接口设计.md` · `MES-LotGenealogy接口设计.md` · `MES-LotScrap接口设计.md` · `MES-LotBonus接口设计.md`
