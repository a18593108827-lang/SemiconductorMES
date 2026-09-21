---
type: 已完成功能
module: Hold
status: done
slices: []
aligns: []
updated: 2026-09-21
---

# MES 锁批（Hold）— 功能查验清单

> 更新：2026-09-21（登记 `assertReasonUsable` 与 skip 文案契约，供客诉包 CP-5）  
> 状态：**最小集 ✅**；**Future Hold P0（FH-1～3）✅**；**QTIME 到期自动 Hold ✅**；FH-4/FH-5 后置不做  
> 需求依据：`MES-Hold功能文档.md` · `MES-Hold数据库设计.md` · `MES-FutureHold接口设计.md`

图例：✅ 已完成 · ⏳ 未做 · — 不适用  
说明：总览表分「后端 / 前端」两列；同行右侧 ⏳ 仅表示 UI 未做，不影响左侧后端 ✅。

---

## 0. 完成度总览

| 能力 | 后端 | 前端 | 权限码 |
|------|------|------|--------|
| 原因码字典 | ✅ `GET/PUT /holds/reasons` | ⏳ 维护页未做（下拉已用启用码） | `hold:list` / `hold:create` |
| Hold 发起 | ✅ `POST /holds` | ✅ Admin 抽屉 + 现场台面板 | `hold:create` |
| `HoldService.assertReasonUsable` | ✅ 存在且启用；客诉 contain 整单前置 | — | — |
| skip 文案契约 `该批次已存在生效中的锁批` | ✅ `HoldService.MSG_ALREADY_HELD`；contain 映射 skipped（改文案须同步 CP-5） | — | — |
| `create` 断言顺序契约 | ✅ **先判已锁后判状态**——已 held 时状态断言会先挂，`MSG_ALREADY_HELD` 永远到不了；改顺序须同步 CP-5 | — | — |
| ReleaseHold | ✅ `POST /holds/{id}/release`；QTIME 必填备注 | ✅ Admin 抽屉 + 现场台面板 | `hold:release` |
| Track 拦截钩子 | ✅ In/Out → `assertNoActive` | — | — |
| Hold 列表/详情 | ✅ `/holds` `/holds/{id}` `/lots/{id}/holds` | ✅ `/app/hold` | `hold:list` |
| 表结构 mes_hold_reason / mes_hold | ✅ `migrate_hold.sql` | — | — |
| 权限菜单种子 | ✅ | ✅ | 见上 |
| Future Hold Set/Cancel/List | ✅ `/holds/future*` · `migrate_future_hold.sql` | ✅ Hold Tab + 现场台 | `hold:create` / `hold:list` |
| Future Hold 到站激活 | ✅ Track PRE/POST + 改站；`REQUIRES_NEW` | ✅ pending 展示 / 失败刷新 | — |
| Queue Time 到期自动 Hold | ✅ `@Scheduled` + `enforceIfExpired`；SYSTEM 操作人 | ✅ 超时提示 / QTIME 解锁备注 | — |
| 多层 / Mass / 自动 Hold | — 后置 | — | — |
| Route 挂点模板（FH-4） | — 后置本期不做 | — | — |
| 通知 / Matrix / SPC（FH-5） | — 后置本期不做 | — | — |

---

## 1. 验收要点（落地后勾）

### 1.1 即时 Hold

- [x] 无原因码不可 Hold  
- [x] active 时 TrackIn/Out 失败  
- [x] 解锁恢复 prev_status  
- [x] `QTIME_EXCEED` 解锁须填备注  
- [x] 写 HOLD / RELEASE_HOLD 履历  
- [x] WIP 投影同步 held  

### 1.2 Future Hold（P0）

- [x] Set 后 pending 不拦过站  
- [x] PRE 到站激活后 Lot held，开工失败；激活不因 TrackIn 回滚  
- [x] Cancel pending 后不再激活  
- [x] context / Hold 页 / 现场台可查可操作  
- [x] 履历 FUTURE_HOLD_SET / CANCEL / ACTIVATE  

---

## 2. 后续扩展

- [ ] 原因码独立维护页（CRUD + 启停 + `hold:reason:edit`）  
- [ ] 多层并发 Hold  
- [ ] Mass Hold/Release  
- [ ] 原因绑解锁角色 / release code  
- [ ] Disposition 工作流  
- [ ] **FH-4** RouteStep 挂点模板（本期不做）  
- [ ] **FH-5** 通知 / Matrix / SPC 自动挂（本期不做）  

详见功能文档 §5.1、§9；Future Hold：`MES-FutureHold接口设计.md`。

---

## 3. 关联

- `MES-Hold功能文档.md`  
- `MES-Hold数据库设计.md`  
- `MES-FutureHold接口设计.md`  
- Track / Lot / WIP  
- `docs/架构/MES-实施进度与下一步.md`  
- `docs/架构/MES-SpringScheduled使用.md`  
- `MES-QueueTime接口设计.md`  
