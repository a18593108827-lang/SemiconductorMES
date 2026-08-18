# MES 量测采集（EDC）— 一期功能清单

> 前提：Track / Route / Lot / Recipe Facade 模式已落地
> 对齐：`MES-EDC功能文档.md` · `MES-Track二期功能清单.md` §3.2 / T2-7
> 更新：2026-08-18

---

## 0. 目标

先交付「采得进、判得了、卡得住」；SPC / 联机 / 片级后置。

原则：

- 状态仍只由 Track 写；EDC 只提供 Gate
- 业务只调 `EdcFacade`
- 采集必须绑 `track_in_tx_id`

现状：采得进、判得了、Facade 已齐；**卡得住（TrackOut 钩子）未接**。FAIL 单也能完工。

---

## 1. 范围总览

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | Param / Spec / Plan / PlanItem 主数据 | ✅ |
| P0 | 手录 Collection + OOS 判定 | ✅ API + TrackPage 抽屉 |
| P0 | `EdcFacade` + evaluateGate | ✅ Bean + `GET /edc/gate`；TrackOut 未挂 |
| P0 | Admin 最小页 / API | ✅ `/app/edc` 特性/规格/站计划 |
| P0 | T2-7 TrackOut 钩子 + 现场拒出 | ⏳（Facade 已齐；拒出未做） |
| P1 | `EDC_COLLECT` 履历；product 维 Spec | ⏳ 履历未写；Spec 已有 `product_code` |
| P1 | 拒出后可选 Auto-Hold | ⏳ |
| P2 | AUTO 回传；Wafer 点；SPC | 后置 |

---

## 2. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| EDC-1 | DDL + Param/Spec CRUD + Spec 发布 | ✅ |
| EDC-2 | Plan / PlanItem；step 显式 required | ✅ |
| EDC-3 | Collection 提交 + 判定；按 visit 查询 | ✅ |
| EDC-4 | `EdcFacade` + 权限种子 + 配置项 | ✅ `EdcFacadeImpl` · `GET /edc/gate` · `mes.edc.gate-enabled`；**无前端** |
| EDC-5 | Admin `/app/edc` 最小 UI | ✅ 三 Tab；采集不在管理端 |
| EDC-6 | T2-7a 钩子 + 错误码 | ⏳（见 Track 二期 §3.2） |
| EDC-7 | T2-7b context + TrackPage 录入/拒出 | ⏳ 录入 ✅（加工中量测条 + 抽屉）；拒出未做 |

建议顺序：EDC-1 → 2 → 3 → 4 → 5 → 6 → 7。
下一步：**EDC-6 TrackOut 挂钩**，再 EDC-7 现场拒出。
**禁止**跳过 Facade 直接在 Track 写点表。

---

## 3. 配置

| 键 | 默认 | 说明 |
|----|------|------|
| `mes.edc.gate-enabled` | true | false 时 Facade 对 required 站也 clear（应急）。`application.yml` 已配 |

---

## 4. 验收（总）

见 `MES-EDC功能文档.md` §10；Track 侧见 `MES-Track二期功能清单.md` §3.2。

已可验：主数据、手录、OOS、同 visit 最新条、现场录入、`GET /edc/gate`。
未可验：required 站拒 Out、FAIL 拒 Out（等 EDC-6/7）。

---

## 5. 关联

- `MES-EDC功能文档.md`
- `MES-EdcFacade接口设计.md`
- `MES-EDC已完成功能.md`
- `MES-EDC数据库设计.md`
- `MES-EDC与SPC范围说明.md`
- `MES-Track二期功能清单.md` §3.2 / T2-7
