# MES 设备（Equipment）— 功能查验清单

> 更新：2026-07-28  
> 状态：**最小集已验收通过**  
> 需求依据：`MES-Equipment功能文档.md` · `MES-Equipment数据库设计.md`

图例：✅ 已完成 · ⏳ 未做 · — 不适用  

---

## 0. 完成度总览


| 能力                      | 后端                                       | 前端            | 权限码                       |
| ----------------------- | ---------------------------------------- | ------------- | ------------------------- |
| 设备主数据 CRUD              | ✅ `/equipments`                          | ✅ Admin 列表/抽屉 | `eqp:list/add/edit`       |
| 业务状态改态                  | ✅ `PUT /equipments/{id}/status`          | ✅ 详情抽屉改态      | `eqp:status`              |
| TrackIn assertUsable    | ✅ TrackIn → `MesEqpService.assertUsable` | —             | —                         |
| 选机 options              | ✅ `GET /equipments/options`              | ✅ 现场台开工下拉     | `eqp:list` / `track:view` |
| 表结构 mes_eqp             | ✅ `migrate_eqp.sql`                      | —             | —                         |
| 权限种子                    | ✅                                        | ✅             | `eqp:*`                   |
| Adapter / E10 / Chamber | — 后置                                     | —             | —                         |


---

## 1. 验收要点

- [x] 停用或 down/pm/eng/offline 时 TrackIn 失败  
- [x] idle/running 且启用可通过校验  
- [x] Admin 可新建 / 编辑 / 改态  
- [x] 列表展示编码 Mono + 状态三重编码  
- [x] 业务服务无 SECS 依赖  

---

## 2. 后续扩展

- [ ] Chamber / Port  
- [ ] SECS/GEM Adapter 回写状态（Track EAP 联机钩子依赖此项；Track 清单标 ⏸）  
- [ ] E10 / OEE  
- [ ] Recipe 资格  
- [ ] Dispatch 选机  
- [ ] TrackIn/Out 自动改 running/idle  
- [ ] PM 工单  

详见功能文档 §9。

---

## 3. 关联

- `MES-Equipment功能文档.md`  
- `MES-Equipment数据库设计.md`  
- Track / Lot / WIP / Route  
- `docs/架构/MES-实施进度与下一步.md`

