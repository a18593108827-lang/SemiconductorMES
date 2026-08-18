# MES 批次（Lot）功能文档

> 定位：批次主数据；放行绑 Route 版本快照（**语义属 Track 事务**，现接口暂挂 Lot）  
> 对齐：`docs/架构/半导MES架构设计.md` §5.1、`MES-Track功能文档.md`  
> 一期状态：**前后端已落地**；二期 **Split / Merge / Genealogy / Scrap / Bonus 已闭环**  
> 更新：2026-08-10  
> 查验：`MES-Lot已完成功能.md`

---

## 1. 目标

- 创建 / 查询 Lot，维护基础属性
- 创建 / 改属性；可选预填路线
- 放行绑 `route_version_id`（实现见 `POST /lots/{id}/release`；全套方向收归 `POST /track/release`）
- **进入生产后的状态变迁只由 Track 事务触发**
- 一期 **Lot 级**；片级后置；**分合批走 Track 事务**（见二期设计）

---

## 2. 角色与权限（草案）

| 权限码 | 用途 |
|--------|------|
| `lot:list` | 列表 / 详情 / 谱系 |
| `lot:add` | 创建 |
| `lot:edit` | 改属性（未放行可改路线等） |
| `lot:release` | 放行（可与 edit 合并，落地时定） |

分合批 / 报废 / 调量执行权限见 Track：`track:split` / `track:merge` / `track:scrap` / `track:bonus`。

---

## 3. 概念与边界

```
Lot     = 批次主数据 + 快照指针 + 被 Track 写入的运行态
Route   = 工艺定义（版本快照挂在 Lot 上）
Track   = 唯一执行引擎（Release / TrackIn/Out / Split / Merge / Scrap / Bonus / …）
WIP     = 在制投影（只读）
```

**禁止**：Lot 上维护可编辑工序顺序；禁止绕过 Track 改加工态 / qty。

---

## 4. 一期功能清单（MVP）

| 功能 | 说明 |
|------|------|
| 列表 / 详情 | 分页、关键字、状态筛选 |
| 创建 | 产品、数量、优先级（1–100，默认 50）、可选预填 `routeId`；`lotNo` 可选，空则自动 `LOT-yyyyMMdd-流水` |
| 改属性 | 未放行：可改产品/数量/目标路线；已放行：仅 `priority`/`hot_flag`/`customer_lot`/`remark`（见约束设计） |
| Release | 校验 Route 有 `active` 版本 → 写死 `route_version_id` → 进入可 Track 状态 |
| 查询绑定版本 | 详情带出版本号 / 步骤摘要（只读） |

### 4.1 Release 规则（硬约束）

1. 目标 Route 必须存在且有且仅认当前 `active` 版本  
2. 写入 `route_id` + `route_version_id`，Release 后 **不可改** `route_version_id`  
3. 无 active 版本 → 拒绝放行  
4. 在途 Lot 不受后续 Route 新发布影响（靠快照）

---

## 5. 二期已落地 / 后置

| 项 | 状态 | 说明 |
|----|------|------|
| Split 分批 | ✅ | `MES-LotSplit接口设计.md`；现场台面板 |
| Merge 合批 | ✅ | `MES-LotMerge接口设计.md`；同站同快照；源 → `merged` |
| Genealogy | ✅ | `MES-LotGenealogy接口设计.md`；API + 详情谱系简图（直系/影响面） |
| Scrap 报废 | ✅ | `MES-LotScrap接口设计.md`；全批/部分；`track:scrap` |
| Bonus | ✅ | `MES-LotBonus接口设计.md`；禁止代替 Scrap |
| 已放行属性约束 | ✅ | `MES-Lot已放行属性约束设计.md`；PUT 白名单 |
| Hot Lot | ✅ | `MES-LotHot接口设计.md` |
| Carrier 绑定 | ⏳ | Carrier 模块 |
| 片级 / Slot | ⏳ | P1 |
| 工单 / ERP 下发 | ⏳ | 先手动创建 |
| Hold | ✅ | Hold 模块；Release Hold ≠ Lot Release |
| 工艺变更中途切版本 | ⏳ | P2 |

---

## 6. 接口草案（MVP）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/lots` | `lot:list` | 分页列表 |
| POST | `/lots` | `lot:add` | 创建 |
| GET | `/lots/{id}` | `lot:list` | 详情（含 route 快照） |
| PUT | `/lots/{id}` | `lot:edit` | 改属性 |
| POST | `/lots/{id}/release` | `lot:release` | 放行绑版本 |
| GET | `/lots/{id}/genealogy` | `lot:list` | 分合批谱系树 |

统一响应：`{ code, msg, data }`；Token：`Authorization: Bearer {token}`。

分合批 / 报废 / 调量执行：`POST /track/split` · `POST /track/merge` · `POST /track/scrap` · `POST /track/bonus`（见 Track / 二期设计）。

---

## 7. 状态（与 Track 对齐）

流转：`created` → `wait` → `processing` → … → `completed` / `scrapped` / `merged`  
（`released` 为过渡兼容名；放行后一期直接写 `wait`）

| 英文 | 中文 | 说明 |
|------|------|------|
| `created` | 已创建 | 未放行；可改属性 / 目标路线 |
| `released` | 已放行 | 过渡兼容；推荐放行后直接 `wait` |
| `wait` | 待加工 | 已绑快照，在站待开工 |
| `processing` | 加工中 | 已 TrackIn |
| `held` | 锁批 | Hold 模块 |
| `completed` | 已完工 | TrackOut 末站 |
| `scrapped` | 已报废 | Scrap 全批终态；不可再 Track |
| `merged` | 已合批 | Merge 源批终态；不可再 Track |

- Release 成功：`created` → `wait`，并锁定 `route_version_id`  
- 进入生产后的状态 **只由 Track 事务推进**（除报废等专用接口）
- qty 变更禁止 `PUT`；走 Split / Merge / Scrap / Bonus

---

## 8. 与后续模块

| 模块 | 依赖本模块 |
|------|------------|
| Track | 执行中枢；Release / Split / Merge / Scrap 语义归 Track |
| WIP | 投影 Lot 当前站/状态；`merged` / `scrapped` 移出 WIP |
| Hold | 叠加在 Track 前校验 |
| History | `mes_tx_log`（含 SCRAP）+ `mes_lot_genealogy`（仅 Split/Merge） |

Track 文档：`docs/模块/Track（执行引擎）模块/`。

---

## 9. 业界对齐（摘要）

相符：Lot + 工艺版本快照 + Track 执行；WIP 不抢真相；Split/Merge 谱系。  
大厂更多、本期不做：片级 Send-ahead、Experiment、加工中合批、一盒多 Lot。  
详见：`docs/架构/MES-实施进度与下一步.md`。

---

## 10. 相关文档

- `MES-Lot数据库设计.md`
- `MES-Lot二期功能清单.md`
- `MES-LotSplit接口设计.md`
- `MES-LotMerge接口设计.md`
- `MES-LotGenealogy接口设计.md`
- `MES-LotScrap接口设计.md`
- `MES-LotBonus接口设计.md`
- `MES-Lot已完成功能.md`
- `docs/模块/Route（工艺路线）模块/MES-Route功能文档.md` §4.3
- `docs/模块/Track（执行引擎）模块/MES-Track功能文档.md`
