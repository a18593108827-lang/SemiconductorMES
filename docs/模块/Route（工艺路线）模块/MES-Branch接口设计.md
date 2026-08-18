# 条件/可选分支 — 接口设计（架构）

> 范围：Route 配置 branch 边 + TrackOut 按结果码选边  
> 原则：定义进版本快照；**走 TrackOut，不新开事务**（与 Rework 分离）  
> 前提：`mes_route_edge` 已落地；本能力扩 `branch` + TrackOut 入参  
> 更新：2026-08-07  
> **易混：** 分支 ≠ Lot 分批（Split）；分批见 `MES-LotSplit接口设计.md`

---

## 0. 产品一句话（防混淆）

**分支：** 同一批 Lot 完工时，按结果码选「下一站走哪条边」——改的是**路径**，不改数量、不建新 Lot。

**不是：**
- 不是把一批拆成多批（那是 **Split 分批**，在现场台 Track）
- 不是返工回前序站（那是 **Rework**，独立事务）
- 不是白名单跳站（那是 **Skip**）

**操作入口**

| 角色 | 在哪 | 做什么 |
|------|------|--------|
| 工艺工程师 | Route 维护 · 「边」区 ·「+ 分支」 | 配 `from → to + condition_code` |
| 现场操作员 | Track 完工 | 选分支结果码（或默认）后 TrackOut |

---

## 1. 边界

```
Route  = 配 normal（默认）/ branch（条件）边进 route_version 快照
Track  = POST /track/track-out 带 resultCode → 匹配边 → 进站
Rework = 仍只走 POST /track/rework；TrackOut 永不匹配 rework 边
Hold   = TrackOut 前 assertNoActive（不变）
Split  = 无关；不建子 Lot、不改 qty
```

**禁止**
- TrackOut 用 `toSortNo` 任意跳站（那是 Skip）
- TrackOut 走进 `rework` 边
- 无 default 边的站发布成功
- 改 active 版本边；在途只认放行时 `route_version_id`
- 用「+ 分支」表达分批（UI/文档禁止把 Branch 叫成 Split）

**与邻近能力对照**

| | 分支 Branch | 回流 Rework | 分批 Split |
|--|--|--|--|
| 改什么 | 下一站 | 当前站（回前序） | Lot 数量与家族 |
| Lot 个数 | 仍 1 | 仍 1 | 1→N |
| 事务 | TrackOut | `/track/rework` | `/track/split` |
| 配置 | Route `branch` 边 | Route `rework` 边 | 无 Route 配置 |
| 执行入口 | Track「完工」选码 | Track「返工」 | Track「分批」 |
| 权限 | `track:track-out` | `track:rework` | `track:split` |

**状态机**（相对一期无新状态）

```
processing ──TrackOut(resultCode?)──► wait(匹配边目标) | completed
```

---

## 2. 数据契约（快照内）

### 2.1 边扩展

沿用 `mes_route_edge`，增量字段：

| 字段 | 说明 |
|------|------|
| edge_type | `normal`（默认出边）/ `branch` / `rework`（已有） |
| condition_code | **branch 专用**：匹配码，如 `PASS` / `FAIL` / `REWORK_CANDIDATE`；`normal`/`rework` 为空 |
| reason_codes | 仍仅 rework 用（已有） |
| max_rework_count | 仍仅 rework 用 |

DDL 增量（已有库 ALTER）：

```sql
ALTER TABLE mes_route_edge
  ADD COLUMN condition_code VARCHAR(64) NULL COMMENT 'branch 条件码' AFTER reason_codes;
```

唯一约束建议：
- `(version_id, from_sort_no, to_sort_no, edge_type)` 保持  
- 另：同站 `branch` 的 `condition_code` 不可重复（应用层校验）  
- 同站 **恰好 1 条** `normal`（default）

### 2.2 与 `next_sort_no` 兼容

- 步骤上的 `next_sort_no` = **default 下一站缓存**（读路径、旧客户端）  
- 保存草稿时：若写了 `nextSortNo`，服务端确保存在/同步一条 `edge_type=normal`  
- TrackOut **优先读边表**：有 `normal`/`branch` 用边；无边表时回退 `next_sort_no`（兼容未升版旧快照）

### 2.3 Lot 运行态

分支**不增** Lot 字段。  
履历用已有 `ext_json`：

```json
{
  "resultCode": "PASS",
  "edgeType": "branch",
  "edgeId": "...",
  "toSortNo": 40
}
```

无 resultCode 走 default 时：`"edgeType":"normal"`。

---

## 3. Route 配置接口

`PUT /routes/versions/{versionId}/steps`（`route:edit`，仅 draft）

```json
{
  "steps": [
    { "stepId": "1001", "sortNo": 10, "nextSortNo": 20 },
    { "stepId": "1002", "sortNo": 20, "nextSortNo": 30 },
    { "stepId": "1003", "sortNo": 30, "nextSortNo": 40 },
    { "stepId": "1004", "sortNo": 40, "nextSortNo": null }
  ],
  "edges": [
    {
      "fromSortNo": 30,
      "toSortNo": 40,
      "edgeType": "normal"
    },
    {
      "fromSortNo": 30,
      "toSortNo": 40,
      "edgeType": "branch",
      "conditionCode": "PASS"
    },
    {
      "fromSortNo": 30,
      "toSortNo": 10,
      "edgeType": "branch",
      "conditionCode": "FAIL"
    },
    {
      "fromSortNo": 30,
      "toSortNo": 10,
      "edgeType": "rework",
      "maxReworkCount": 2,
      "reasonCodes": "CD_FAIL"
    }
  ]
}
```

说明：
- `PASS` 与 default 可指向同一站（常见）  
- `FAIL` 可指向旁路站；若需「返工+计数」，应配 **rework 边**并由现场调 `/track/rework`，不要用 branch 冒充  
- 可选：`FAIL` branch 仅作旁路量测，与 rework 并存

**发布校验增量**

| 规则 | 说明 |
|------|------|
| 非终点站 | 必须恰好 1 条 `normal` 出边；`to` 合法 |
| 终点站 | 无 `normal` 出边；`next_sort_no=null` |
| branch | `condition_code` 非空；同站 condition 唯一；`to` 合法且 ≠ from |
| normal | `condition_code` 必须空 |
| 与 steps | `normal.to` 应与该站 `next_sort_no` 一致（或保存时自动对齐） |
| rework | 沿用既有回流校验（含可达终点） |

`GET /routes/versions/{id}` 继续返回 `steps + edges`（含 `conditionCode`）。

---

## 4. Track 执行接口

### 4.1 TrackOut 扩参（主接口）

`POST /track/track-out`  
权限：`track:track-out`（不变）

**Request**

```json
{
  "lotId": "77001",
  "resultCode": "PASS"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| lotId | Y | |
| resultCode | N | 空 → 走 `normal`；有值 → 匹配同站 `branch.condition_code` |

**选边算法**

```
1. 加载当前站 from = current_sort_no
2. 查快照边：from + type ∈ {normal, branch}
3. if resultCode 空白:
     取唯一 normal → 目标
     无 normal → 回退 next_sort_no；再无则末站 completed（与现逻辑一致）
4. if resultCode 有值:
     匹配 branch where condition_code = resultCode（忽略大小写，建议存大写）
     命中 → 目标
     未命中 → 拒绝（不静默走 default，避免误放行）
5. 禁止匹配到 rework
6. 目标站存在 → wait；无出边且为末站语义 → completed
```

**刻意不静默降级**：量测员选了 `FAIL` 但未配边 → 失败提示配边，而不是当成 PASS。

**Response**：沿用 `TrackTxnResultVO`；可选增 `resultCode` / `edgeType`（非必须，履历已有）。

**ext_json 示例**

```json
{ "resultCode": "FAIL", "edgeType": "branch", "edgeId": "5310", "toSortNo": 10 }
```

default：

```json
{ "edgeType": "normal", "edgeId": "5309", "toSortNo": 40 }
```

### 4.2 上下文扩展

`GET /track/lots/{lotId}/context`

```json
{
  "canTrackOut": true,
  "nextStep": { "...default 站..." },
  "branchOptions": [
    {
      "conditionCode": "PASS",
      "toSortNo": 40,
      "toStepCode": "ETCH-01",
      "toStepName": "刻蚀"
    },
    {
      "conditionCode": "FAIL",
      "toSortNo": 10,
      "toStepCode": "PHOTO-01",
      "toStepName": "光刻"
    }
  ]
}
```

- `nextStep` = normal 目标（兼容现 UI）  
- `branchOptions` = 当前站全部 branch；空则 TrackOut 可不传 resultCode  
- 前端：有 options 时完工须选结果码（或显式「走默认」不传）

---

## 5. 权限

不新增权限码。配置 `route:edit`；执行 `track:track-out`。

---

## 6. 前端增量（最小）

| 页 | 改动 |
|----|------|
| RoutePage | 草稿边表支持 `branch` + `conditionCode`（与 rework 分区或同表 type 切换） |
| TrackPage | 有 `branchOptions` 时 TrackOut 前选择 resultCode |

---

## 7. 实施切片

| 切片 | 交付 |
|------|------|
| B1 | DDL `condition_code`；保存/详情/发布校验（含每站 1 条 normal） |
| B2 | TrackOut 选边 + ext_json；旧快照无边回退 next |
| B3 | context.branchOptions + 现场台选码 |
| B4 | Route UI 维护 branch |

---

## 8. 验收

1. 无 branch 的线性路线：不传 resultCode，行为与现网一致  
2. 传未配置的 resultCode → TrackOut 失败，站位不变  
3. PASS/FAIL 分别进配置目标站；履历 `ext_json` 可追  
4. TrackOut 不能走 rework 边  
5. 升版改边不影响在途 Lot  
6. 发布：缺 default / 重复 condition → 拒绝  

---

## 9. 关联

- `MES-Rework接口设计.md`（事务边界）  
- `MES-LotSplit接口设计.md`（分批；勿与分支混淆）  
- `MES-Route二期功能清单.md` §2.2  
- `MES-Track功能文档.md` TrackOut  
