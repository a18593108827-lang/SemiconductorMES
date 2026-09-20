---
type: 接口设计
module: Route
status: done
slices: []
aligns: []
updated: 2026-07-30
---

# Rework 回流 — 接口设计（架构）

> 范围：Route 配置边 + Track 执行事务  
> 原则：定义进版本快照；执行走统一 Track 管道；独立事务，不塞进 TrackOut  
> 更新：2026-07-30

---

## 1. 边界

```
Route  = 配回流边（to_sort / max_count）进 route_version 快照
Track  = POST /track/rework 改 Lot 当前站 + 计数 + 履历
Hold   = rework 前 assertNoActive（同 TrackIn/Out）
```

**禁止**
- TrackOut 带 `toSortNo` 偷偷回流（绕开 `track:rework`）
- 改 active 版本边；在途只认放行时 `route_version_id`
- Lot/WIP 私自改 `current_sort_no`

**状态机增量**

```
wait | processing ──Rework──► wait(回流目标站)
  └ 清空 current_eqp_id；processing 直接回流等同 Abort+回站
```

---

## 2. 数据契约（快照内）

### 2.1 边（版本内）

`mes_route_edge`（随 version 发布只读）：

| 字段 | 说明 |
|------|------|
| version_id | 路线版本 |
| from_sort_no | 触发站 |
| to_sort_no | 回流目标站（须同版本存在） |
| edge_type | 固定 `rework` |
| max_rework_count | 该边次数上限，≥1 |
| reason_codes | 可选，逗号分隔；空=任意 reason |

同 `(version, from, to, rework)` 唯一。  
一站可多条 rework 边（回不同站）；执行时必须显式指定 `toSortNo`。

### 2.2 Lot 运行态

| 字段 | 说明 |
|------|------|
| rework_counts | JSON：`{"{fromSortNo}": n}`，按**触发站**累计 |
| （可选）rework_total | 全线累计，预留 |

计数真相在 Lot；Route 只提供上限。

---

## 3. Route 配置接口

沿用草稿保存，**扩 edges**；线性 `nextSortNo` 仍可写，服务端同步成 `edge_type=normal` 的 default 边。

### 3.1 保存草稿步骤+边

`PUT /routes/versions/{versionId}/steps`  
权限：`route:edit`  
仅 `draft`。

```json
{
  "steps": [
    { "stepId": "1001", "sortNo": 10, "nextSortNo": 20 },
    { "stepId": "1002", "sortNo": 20, "nextSortNo": 30 },
    { "stepId": "1003", "sortNo": 30, "nextSortNo": null }
  ],
  "edges": [
    {
      "fromSortNo": 30,
      "toSortNo": 10,
      "edgeType": "rework",
      "maxReworkCount": 2,
      "reasonCodes": "CD_FAIL,OVERLAY_FAIL"
    }
  ]
}
```

发布校验增量：
- rework 的 to 必须存在且 ≠ from
- `maxReworkCount` ≥ 1
- 不要求无环（回流必然成环）；从 to 沿主路径 `next` 必须能走到终点（`next=null`）；主路径成环/断链则拒绝发布

### 3.2 版本详情

`GET /routes/versions/{versionId}`  
`data` 增：

```json
{
  "steps": [ "...同现网..." ],
  "edges": [
    {
      "id": "...",
      "fromSortNo": 30,
      "toSortNo": 10,
      "edgeType": "rework",
      "maxReworkCount": 2,
      "reasonCodes": "CD_FAIL,OVERLAY_FAIL"
    }
  ]
}
```

---

## 4. Track 执行接口

### 4.1 返工事务（主接口）

`POST /track/rework`  
权限：`track:rework`  
锁：`lotId` + 乐观锁 `Lot.version`

**Request**

```json
{
  "lotId": "77001",
  "toSortNo": 10,
  "reasonCode": "CD_FAIL",
  "remark": "CD 超规"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| lotId | Y | |
| toSortNo | Y | 回流目标，必须命中当前站一条 rework 边 |
| reasonCode | N | 边配了 reasonCodes 则必填且命中 |
| remark | N | 写入履历 |

**校验序**

1. Lot 存在；状态 ∈ `{wait, processing}`；非 completed  
2. `assertNoActive` Hold  
3. 有 `route_version_id`、`current_sort_no`  
4. 快照存在边：`from=current_sort` ∧ `to=toSortNo` ∧ `type=rework`  
5. reasonCode 规则  
6. `rework_counts[from] + 1 ≤ max_rework_count`  
7. 目标站行存在  

**落库**

- `current_sort_no / current_step_id` → 目标站  
- `status` → `wait`；`current_eqp_id` → null  
- `rework_counts[from] += 1`  
- `mes_tx_log`：`tx_type=REWORK`  
- WIP 投影同步  

**Response `data`**（对齐现有 `TrackTxnResultVO`）

```json
{
  "lotId": "77001",
  "lotNo": "LOT-001",
  "txType": "REWORK",
  "status": "wait",
  "currentSortNo": 10,
  "currentStepId": "1001",
  "completed": false,
  "reworkCount": 1,
  "maxReworkCount": 2
}
```

**错误（业务码示例）**

| 场景 | msg |
|------|-----|
| 无权限 | 403 |
| 无匹配边 | 当前站未配置回流至目标站 |
| 超次 | 返工次数已达上限 |
| reason 不合法 | 返工原因不匹配 |
| Hold 中 | 存在有效锁批 |

### 4.2 上下文（现场台选目标）

`GET /track/lots/{lotId}/context` 扩展：

```json
{
  "canRework": true,
  "reworkCount": 1,
  "reworkOptions": [
    {
      "toSortNo": 10,
      "toStepCode": "PHOTO-01",
      "toStepName": "光刻",
      "maxReworkCount": 2,
      "remainCount": 1,
      "reasonCodes": ["CD_FAIL", "OVERLAY_FAIL"]
    }
  ]
}
```

`canRework` = 有权限且状态允许且存在 remainCount>0 的边（前端展示用；后端仍以事务校验为准）。

---

## 5. 履历

`mes_tx_log`：

| 字段 | 值 |
|------|-----|
| tx_type | `REWORK` |
| from_sort_no / to_sort_no | 触发站 → 回流站 |
| remark | 请求 remark |
| ext_json | `{ "reasonCode","reworkCount","maxReworkCount","edgeId" }` |

---

## 6. 与 TrackOut 关系

| 事务 | 走哪条边 |
|------|----------|
| TrackOut | 仅 `normal`/`branch`（default 或 resultCode 匹配）；**禁止**走 rework |
| Rework | 仅 `rework` 边 |

量测 FAIL：现场调 `POST /track/rework`，不调 TrackOut。

---

## 7. 权限种子

| 码 | 角色建议 |
|----|----------|
| `track:rework` | admin / process_eng / supervisor |

配置仍走 `route:edit`。

---

## 8. 验收

1. 无边 / 错 to / 超次 / 无权限 → 失败且站位不变  
2. 成功 → wait 目标站、计数+1、履历可追  
3. 升版改 max 不影响在途 Lot（认快照边）  
4. Hold 中不可 rework  
5. TrackOut 不能落到 rework 目标（除非恰好是 default next）  
