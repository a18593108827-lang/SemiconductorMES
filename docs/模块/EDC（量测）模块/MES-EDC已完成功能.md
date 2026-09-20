---
type: 已完成功能
module: EDC
status: done
slices: []
aligns: []
updated: 2026-08-19
---

# MES 量测采集（EDC）— 已完成功能（查验清单）

> 对齐：`MES-EDC功能文档.md`
> 现状：主数据 + 手录判定 + 现场录入 + Facade + TrackOut 钩子 + 现场拒出提示已落地
> 更新：2026-08-19

---

## 1. 主数据

| 项 | 状态 |
|----|------|
| Param CRUD / 启停 | ✅ API `/edc/params` · UI `/app/edc` |
| Spec 草稿 / 发布 / active 唯一 | ✅ API `/edc/specs` · UI `/app/edc` 规格 Tab |
| Spec 产品维 | ✅ `product_code` 空=全产品默认 |
| Plan 按 step + required | ✅ API `/edc/plans` · UI `/app/edc` 站计划 Tab |
| PlanItem 绑定 Param/Spec | ✅ `PUT /edc/plans/{id}/items` · 详情抽屉整表替换 |

## 2. 采集

| 项 | 状态 |
|----|------|
| 手录提交；绑 track_in_tx_id | ✅ POST `/edc/collections` |
| OOS 判定；头 result PASS/FAIL | ✅ `value < LSL` 或 `> USL` → 项 OOS；缺必采或超限 → 头 FAIL |
| 同 visit 最新条 | ✅ GET `/edc/collections/latest?lotId=&trackInTxId=` |
| 分页查询 | ✅ GET `/edc/collections` |
| 粒度 | ✅ 一期每特性一个数值；Wafer/多点后置 |
| 现场录入 | ✅ TrackPage：`processing` + 本站启用计划 → 量测条 + 抽屉 |

## 3. Facade / 门禁

| 项 | 状态 |
|----|------|
| `EdcFacade.evaluateGate` | ✅ 只读判定；不抛 |
| `EdcFacade.assertClearToTrackOut` | ✅ `TrackServiceImpl.trackOut` 时长校验之后、改状态之前 |
| `GET /edc/gate` | ✅ `edc:view` 或 `track:view`；无单独前端页 |
| `mes.edc.gate-enabled` | ✅ `application.yml` 默认 true |
| 未配站不挡 | ✅ 无 Plan / `required=0` 不拦 |
| T2-7a TrackOut 钩子 | ✅ 失败无 TRACK_OUT；错误码 `EDC_BLOCK_TRACK_OUT` |
| context `edc` | ✅ `required` / `clear` / `reasonCode` / `message`；`!clear` 时 `canTrackOut=false` |
| TrackPage 拒出提示 | ✅ 仍是原「完工」按钮：灭 + 旁注原因；采合格后刷新再亮。无新入口 |

## 4. 工程约束

| 项 | 状态 |
|----|------|
| Track 服务不直查 `mes_edc*` | ✅ 录入走 `/edc/*` HTTP；过站只调 `EdcFacade` |
| Track 库无量测明细表 | ✅ 点在 `mes_edc_collection*` |
| 权限 edc:view/edit/publish/collect | ✅ migrate_edc.sql 253–256 |

---

## 关联

- `MES-EDC一期功能清单.md`
- `MES-EDC功能文档.md`
- `MES-EdcFacade接口设计.md`
