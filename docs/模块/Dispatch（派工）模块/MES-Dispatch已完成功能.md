# MES 派工（Dispatch）— 功能查验清单

> 更新：2026-07-29  
> 状态：**一期最小集已完成（含并发加固 + 前端）**  
> 需求依据：`MES-Dispatch功能文档.md` · `MES-Dispatch数据库设计.md`

图例：✅ 已完成 · ⏳ 未做 · — 不适用  

---

## 0. 完成度总览

| 能力 | 后端 | 前端 | 权限码 |
|------|------|------|--------|
| 候选 / 推荐 | ✅ `GET /dispatch/candidates` | ✅ Track + Admin | `dispatch:view` / `track:view` |
| Reserve | ✅ create / release / list + TrackIn 钩子 | ✅ 预约/释约/倒计时 | `dispatch:reserve` |
| 并发 | ✅ slot UNIQUE + FOR UPDATE + version | — | — |
| 表 / 迁移 | ✅ migrate_dispatch(+slot/+version) | — | — |
| 配置 TTL | ✅ `mes.dispatch.reserve-ttl-minutes` | — | — |
| 规则表 / APS / Recipe | — 后置 | — | — |

---

## 1. 接口速查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dispatch/candidates?lotId=` | 候选 + recommendedEqpId |
| POST | `/dispatch/reserve` | 预约 |
| POST | `/dispatch/reserves/{id}/release` | 释约 |
| GET | `/dispatch/reserves` | 列表（默认 active） |

TrackIn 内部：`assertReserveMatch` → 成功后 `consumeOnTrackIn`。

---

## 2. 前端入口

| 路径 | 说明 |
|------|------|
| `/track` | 候选下拉、推荐、预约/释约 |
| `/app/dispatch` | Admin 派工页 |
| `web/src/api/dispatch.ts` | 前端 API |

---

## 3. 验收要点

- [ ] 不可用设备不进候选  
- [ ] eqp_type 过滤生效（有类型时）  
- [ ] 返回 recommendedEqpId；Track 默认选中  
- [ ] TrackIn 仍 assertUsable  
- [ ] 预约占机；他批不可再约；In 须同机  
- [ ] 同机并发双约失败（唯一约束）  
- [ ] 超时后可再约（slot 已清）  

---

## 4. 后续扩展

- [ ] 规则表权重  
- [ ] Recipe 资格  
- [ ] What-Next  
- [ ] APS 计划窗软约束  

---

## 5. 关联

- `MES-Dispatch功能文档.md`  
- `MES-Dispatch数据库设计.md`  
- Equipment / Track / Lot / Hold  
- `docs/方案/MES-APS高级计划与排程方案.md`  
- `docs/架构/MES-实施进度与下一步.md`
