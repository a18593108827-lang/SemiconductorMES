# Lot 已放行属性约束 — 架构 / 接口设计

> 定位：Release 后字段可写性护栏；**不是**独立业务模块、**不新增**路径  
> 归属：Lot 主数据写路径（`PUT /lots/{id}`）；Track 事务不替代、不绕开  
> 对齐：`MES-Lot二期功能清单.md` §3.4；`MES-Lot功能文档.md` §4；`半导MES架构设计.md` §5.1  
> 关联：`MES-LotHot接口设计.md` §5（急度字段消费本白名单）  
> 业界：SiView / Camstar·Opcenter / FAB300 — 软属性可改、硬身份/数量/工艺走专用事务  
> 更新：2026-08-10  
> 状态：**P1 已闭环**（AC-1～AC-4）；AC-5 属性履历 / ChangeAttribute 后置

---

## 0. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| AC-1 | 字段矩阵定稿（A/B/C 三层） | ✅ |
| AC-2 | `MesLotService.update` 按状态硬拦 B/C | ✅ |
| AC-3 | LotsPage：已放行禁改 B/C；A 可改 + 引导文案 | ✅ |
| AC-4 | 错误文案与 Track 事务引导一致 | ✅ |
| AC-5 | 属性变更履历 / ChangeAttribute 事务 | ❌ 后置 |

---

## 1. 目标与边界

**一句话：** 已进入生产真相链的 Lot，属性写接口只能碰调度/标注字段；身份、数量、工艺、状态禁止旁路。

| 做 | 不做 |
|----|------|
| 状态门禁 + 字段白/黑名单 | 自定义 Attribute 引擎 |
| 服务端为唯一真相；UI 仅为防呆 | 用 UI 灰显代替后端校验 |
| qty/status 引导走 Track | 本期把 A 类升格为独立 Txn |
| 与 Hot 白名单字段共用同一 PUT | 升 Hot 审批 / 配额（见 Hot P2） |

**禁止**

- `PUT /lots/{id}` 改 `qty` / `status` / `scrap_qty` / `route_version_id`
- 已放行改 `product_code` / `route_id`（中途切版本 = P2 独立能力，不走本 PUT）
- Track 事务内「顺便」改无关主数据（事务只改契约内字段）

---

## 2. 业界对齐（架构依据）

| 大厂 | 本系统映射 |
|------|------------|
| ChangeAttribute（软属性） | P1：`PUT` + 白名单；P2 可选独立 Txn + 履历 |
| ChangeProduct / ChangeProcess | 禁 PUT；P2 中途切 Route |
| Split / Merge / Scrap / Bonus / AdjustQty | 已有 Track 事务；禁 PUT 改 qty |
| Audit Trail 强制走服务 | 写路径只经 `MesLotService` / Track；禁直改表 |

结论：分层与大厂一致；实现取**薄护栏**，不先上属性事务平台。

---

## 3. 模块边界

```
Client   = 按状态禁用字段；失败提示引导事务
Lot PUT  = 唯一属性写入口；状态 × 字段矩阵校验
Track    = qty/status/谱系/工艺切换的唯一执行引擎
WIP      = 投影同步 priority / hot_flag 等可调度字段
History  = 后置：属性变更履历（非 genealogy）
```

| 模块 | 职责 |
|------|------|
| Lot | 存属性；enforce 白名单 |
| Track | 守恒类 / 状态类变更 |
| Hot | A 类中的急度语义与 Dispatch 消费（不另开写入口） |
| Hold | 与可写性正交：`held` 仍允许改 A（调度标注）；禁止 Track 执行类事务另循 Hold 规则 |

---

## 4. 字段矩阵（核心契约）

### 4.1 分层

| 层 | 含义 | 字段 |
|----|------|------|
| A 调度/标注 | 不改变生产身份与数量守恒 | `priority` `hot_flag` `customer_lot` `remark` |
| B 身份/工艺 | 决定「造什么、按哪版流程」 | `product_code` `route_id` `route_version_id` |
| C 数量/状态 | 守恒与生命周期 | `qty` `status` `scrap_qty`（及 merged 指向等） |

### 4.2 按状态

| status | A | B | C |
|--------|---|---|---|
| `created` | ✅ | ✅（`route_version_id` 仍空，仅目标 `route_id`） | ✅ 可改 `qty`；`status` 仍只由 Release/Track 推进 |
| `wait` / `processing` / `held` / `released`（兼容） | ✅ | ❌ | ❌ |
| `completed` / `scrapped` / `merged` | ❌（建议整单只读） | ❌ | ❌ |

说明：

- 「已放行」= 非 `created` 且未终态；以是否已绑 `route_version_id` / 可 Track 为准，不单靠字面 `released`
- `created` 改 `qty` 允许：尚未进守恒履历；Release 后一律走事务
- `route_version_id`：**任何** `PUT` 不可写；仅 Release（及未来 P2 切版本事务）写入

### 4.3 请求语义

- Body 带 B/C 且与库中值**相同**：视为未改，通过（便于前端整单回传）
- Body 带 B/C 且与库中值**不同**：400，文案区分：
  - qty → `数量变更请走 Split/Merge/Scrap/Bonus 事务`
  - product/route → `已放行不可修改…`
- 禁止「部分字段省略 = 清空」：省略保持原值；显式 null 策略与现 DTO 一致（`customer_lot`/`remark` 可空）

---

## 5. 实现落点

### 5.1 服务端（强制）

唯一闸门：`MesLotServiceImpl.update`

```
load lot → assert editable status
if created:
    apply A + B(routeId/product) + qty
else if in-process:
    reject B/C deltas
    apply A only
else:
    reject all
sync WIP projection (priority/hot_flag)
```

- 不在 Controller 散落 if
- Hot 地板（`applyHotPriorityFloor`）仍在 A 写入前执行

### 5.2 前端（防呆，非安全边界）

| 状态 | UI |
|------|-----|
| `created` | 产品/数量/路线/急度/备注可编辑 |
| 在制 | 仅急度/客户 Lot/备注可编辑；产品/数量/路线只读 + 短说明 |
| 终态 | 全部只读 |

### 5.3 权限

| 操作 | 权限 |
|------|------|
| 改 A（含已放行） | `lot:edit` |
| Release | `lot:release` / `track:release` |
| 改 qty 等 | 对应 `track:*`，不经 `lot:edit` |

不新增 `lot:attr`。

---

## 6. API（复用，无新路径）

### 6.1 改属性

`PUT /lots/{id}`  
权限：`lot:edit`  
操作日志：`Lot` / `改批次属性`  
成功：`R<Void>`（`code=0`）

```json
{
  "productCode": "PROD-A",
  "qty": 25,
  "priority": 80,
  "hotFlag": 1,
  "customerLot": "CUST-001",
  "routeId": 12,
  "remark": "急单插队"
}
```

| 字段 | 类型 | created | 已放行在制 | 说明 |
|------|------|---------|------------|------|
| `productCode` | string? | ✅ 可改 | ❌ 变值拒绝 | 空→null |
| `qty` | int 必填 ≥0 | ✅ 可改 | ❌ 变值拒绝 | 引导 Track |
| `priority` | int 必填 1–100 | ✅ | ✅ | Hot=1 时服务端可抬到 ≥80 |
| `hotFlag` | 0/1? | ✅ | ✅ | null=保持原值 |
| `customerLot` | string? | ✅ | ✅ | 空→null |
| `routeId` | long? | ✅ 可改 | ❌ 变值拒绝 | 仅目标路线；不写 version |
| `remark` | string? | ✅ | ✅ | 空→null |

**Body 不含、且禁止经本接口写入：** `status` / `routeVersionId` / `scrapQty` / `parentLotId` / `mergedToLotId` / `current*`

**同值回传：** 已放行时 `productCode`/`qty`/`routeId` 与库中相等 → 通过（前端可整单提交）。

### 6.2 可编辑 status

| status | `PUT` |
|--------|-------|
| `created` | ✅ A+B+qty |
| `released` / `wait` / `processing` / `held` | ✅ 仅 A；B/C 变值 → 400 |
| `completed` / `scrapped` / `merged` | ❌ `当前状态不可编辑` |

### 6.3 错误（契约文案）

| 条件 | message（稳定，供前端展示） |
|------|------------------------------|
| 批次不存在 | `批次不存在` |
| 终态等不可编辑 | `当前状态不可编辑` |
| 已放行改 qty | `数量变更请走 Split/Merge/Scrap/Bonus 事务` |
| 已放行改 product | `已放行不可修改产品编码` |
| 已放行改 routeId | `已放行不可修改路线` |
| priority 越界 | `优先级范围为1-100` |
| hotFlag 非法 | `hotFlag 只能为 0 或 1` |
| created 且 route 不可用 | 沿用现 `assertRouteUsable` 文案 |
| 乐观锁冲突 | `数据已被他人修改，请刷新后重试` |
| 无 `lot:edit` | 401/403 |

### 6.4 示例

**已放行只改急度（合法）：**

```json
PUT /lots/100
{
  "productCode": "PROD-A",
  "qty": 25,
  "priority": 90,
  "hotFlag": 1,
  "customerLot": "CUST-001",
  "routeId": 12,
  "remark": null
}
```

→ 200；写 `priority`/`hot_flag`；WIP 同步；不写 tx_log / genealogy。

**已放行改数量（非法）：**

```json
{ "productCode": "PROD-A", "qty": 20, "priority": 50, "routeId": 12 }
```

→ 400，`数量变更请走 Split/Merge/Scrap/Bonus 事务`。

**created 改产品+数量（合法）：**

```json
{ "productCode": "PROD-B", "qty": 30, "priority": 50, "routeId": 15, "remark": "改投" }
```

→ 200。

### 6.5 相关接口（不在本约束内改写）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/lots/{id}/release` · `/track/release` | 写死 `route_version_id`；之后 B 锁定 |
| POST | `/track/split` `/merge` `/scrap` `/bonus` | 唯一改 qty（及事务内 status）入口 |
| GET | `/lots/{id}` | 回传当前属性；无「可写字段」元数据（P1 不做） |

### 6.6 后置（不做）

`POST /lots/{id}/attributes`（ChangeAttribute Txn）— 见 §8。

---

## 7. 数据与一致性

- **无新表**（P1）
- 改 A 后：`wipProjectionService.syncFromLot` 必达
- 改 A **不写** `mes_lot_genealogy`、**不写** Track tx
- 乐观锁 / `update_by`：沿用现更新行数校验

---

## 8. 扩展点（明确后置）

| 能力 | 触发条件 | 方向 |
|------|----------|------|
| 属性变更履历 | 审计要求 A 类可追 | `mes_lot_attr_log` 或通用 audit |
| ChangeAttribute Txn | 对齐大厂事务模型 | `POST /lots/{id}/attributes` |
| 中途切 Route | P2 | Track 事务写新 `route_version_id` |
| 自定义 Lot Attribute | 多客户字段 | 扩展表 + 可写标记 |

---

## 9. 验收

1. `created`：改 product/qty/route/priority 成功  
2. 已放行：改 priority/hot/customer_lot/remark 成功；WIP 急度同步  
3. 已放行：改 product/qty/route → 400；qty 文案见 §6.3  
4. `PUT` 无法改 `status` / `route_version_id` / `scrap_qty`  
5. `scrapped`/`merged`/`completed`：`PUT` 拒绝  
6. 安全边界在服务端（不依赖 UI 灰显）

---

## 10. 关联

- `MES-Lot二期功能清单.md` §3.4 / L2-5  
- `MES-LotHot接口设计.md` §5 / §6  
- `MES-Lot功能文档.md` §4 / §6  
- `MES-Lot数据库设计.md`  
- `MES-Track功能文档.md`  
