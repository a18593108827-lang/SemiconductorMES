# Temporary Off-Flow — 接口设计（架构）

> 范围：Route 配置旁路入口边 + Track Enter / Resume 事务  
> 原则：定义进版本快照；**独立事务，不塞进 TrackOut / Skip / Rework**  
> 对齐：Critical Manufacturing Temporary Off-Flow（进旁路 Flow → 结束回原站/原机台；Dispatched/Processed 逻辑占台）  
> 本切片：**同版本子序列**（不切换 `route_version_id`；跨 Route 旁路属 P2 子流程）  
> 前提：边表、主路径 `normal`、TrackIn/Out/Hold/Dispatch 已落地  
> 更新：2026-08-06（锚点占台 + Resume 降级 + 按站次数上限）

---

## 1. 边界

```
Route  = 配 off_flow 边（主站 → 旁路入口 + 次数上限）+ 旁路站链（normal）进 route_version 快照
Track  = POST /track/off-flow 进旁路；末站 TrackOut 自动 / 或 POST /track/off-flow/resume 回锚点
Lot    = off_flow 标记 + 锚点快照（站/机台/状态）+ 按触发站次数
Hold   = Enter/Resume 前 assertNoActive（同 Skip/Rework）
Dispatch = processing 进旁路后锚点机台逻辑占台（别批不可预约/开工）
```

**本切片做什么**

- 从主路径某站临时进入同版本 Off-Flow 子序列
- 旁路内正常 Dispatch / TrackIn / TrackOut（走旁路 `normal`）
- 旁路结束回到**进入时锚点**（站 + 可选机台/状态）
- **按触发站累计次数**，边配上限，超限拒绝
- processing 进入：锚点机台逻辑占台；Resume 机台不可用/被占 → 降级 wait
- Lot `off_flow=true`；履历可追

**本切片不做什么**

- 不切换到另一条 Route/版本（= P2 子流程 / CM Go To Flow 完整形态）
- 不做旁路内 Skip（禁止）
- 不做旁路内自由 Resume 到任意主路径站（只回锚点）
- 不做双人审批 / EBR
- 不做 Queue Time / Future Hold（Queue Time 见 `MES-QueueTime接口设计.md`）

**禁止**

- TrackOut / Skip / Rework 冒充进出 Off-Flow
- `off_flow=true` 时调用 Skip / Rework / 再次 Enter
- Resume 到非锚点、或 `off_flow≠true` 仍 Resume
- 超次数上限仍 Enter 成功
- 别批预约/开工已被 Off-Flow 锚点占用的机台（`anchor_status=processing`）
- 改 active 版本边；在途只认放行时 `route_version_id`
- 旁路站挂到主路径 default 链上冒充主站（发布校验拒绝）

**与兄弟能力分工**

|     | Off-Flow              | Skip        | Rework           | Branch                |
| --- | --------------------- | ----------- | ---------------- | --------------------- |
| 事务  | `/track/off-flow` + Resume | `/track/skip` | `/track/rework` | TrackOut + resultCode |
| 边类型 | `off_flow`            | `skip_allow` | `rework`        | `branch` / `normal`   |
| 方向  | 主 → 旁路 → **回锚点**   | 仅前向少做站     | 回前序重做           | 前向/旁路选边               |
| Lot  | `off_flow` + anchor + 次数 | 无        | rework 计数        | 无                     |
| 次数  | `max_rework_count`（复用字段） | 无（本切片） | `max_rework_count` | 无                  |
| 权限  | `track:off-flow`      | `track:skip` | `track:rework`  | `track:track-out`     |
| 允许状态 | **wait \| processing**（对齐 CM） | 仅 wait     | wait \| processing | processing（TrackOut） |

**状态机**

```
主路径 wait|processing
  ──Enter Off-Flow──► 旁路入口 wait（清空 current_eqp；若原 processing 则锚点记住机台+状态）
  └ Lot.off_flow=true；触发站次数+1；锚点机台逻辑占台（别批不可预约/开工该台）

旁路 wait ──Dispatch/TrackIn/TrackOut──► 沿旁路 normal 前进（off_flow 保持 true）

旁路末站
  ──TrackOut（无下一站）──► 自动 Resume
  ──Resume──► 锚点站 + 恢复 anchor 状态/机台（机台不可用或被占 → 降级 wait，ext.degaded=true）
  └ Lot.off_flow=false，清空 anchor（次数保留）
```

---

## 2. 数据契约

### 2.1 边（快照内）

沿用 `mes_route_edge`：

| 字段               | Off-Flow 用法                                      |
| ---------------- | ------------------------------------------------ |
| edge_type        | 固定 `off_flow`                                    |
| from_sort_no     | 主路径触发站（当前站）                                   |
| to_sort_no       | 旁路**入口**站                                       |
| reason_codes     | 可选白名单；空=任意 reason                               |
| condition_code   | 空（不用）                                           |
| max_rework_count | **必填 ≥1**：该触发站进入 Off-Flow 次数上限（复用列，语义= maxOffFlowCount） |

唯一约束沿用现有。一站可多条 `off_flow`（进不同旁路入口）；Enter 必须显式 `toSortNo`。  
次数按**触发站**累计（同站多目标共享同一计数）。旧边未配上限时运行时按 **1** 兜底。

**旁路子序列**

- 入口站及后续站通过 `normal`/`next_sort_no` 串联
- 这些站**不得**出现在从路线起点沿主路径 `normal` 可达集合中（纯旁路站）
- UI 约定：主路径 sort&lt;200；旁路 sort≥200（分段线性保存）
- 旁路末站：无 `normal` 下一站（`next_sort_no` 空）→ Resume 出口
- **不配** `off_flow_return` 边：回锚点由运行态决定（对齐 CM Return Path = 进入时站点）

### 2.2 Lot 运行态（Track 表，不进 Route）

`mes_lot` 增量：

| 字段                      | 类型           | 说明                                      |
| ----------------------- | ------------ | --------------------------------------- |
| off_flow                | TINYINT      | 0/1；是否在 Off-Flow 中                      |
| off_flow_anchor_sort    | INT          | 进入时主路径站 sort_no                         |
| off_flow_anchor_step_id | BIGINT       | 进入时 step_id（冗余，防快照歧义）                   |
| off_flow_anchor_eqp_id  | BIGINT NULL | 进入时机台；wait 进入多为 null；processing 进入保留     |
| off_flow_anchor_status  | VARCHAR(32)  | 进入时状态：`wait` / `processing`（Resume 恢复用） |
| off_flow_counts         | VARCHAR(512) | JSON：`{"{fromSortNo}": n}`，按**触发站**累计     |

Release 时清空 `off_flow` / anchor / `off_flow_counts`（与 rework_counts 同策略）。

### 2.3 锚点机台占台（对齐 CM）

当 `off_flow=1` 且 `off_flow_anchor_status=processing` 且 `off_flow_anchor_eqp_id` 非空：

- 该机台计入 Dispatch **负载**
- 候选列表剔除；**别批**不可 `reserve` / `TrackIn` 该台（`assertNotOffFlowAnchored`）
- 本批旁路内仍可开工**其他**机台

### 2.4 履历

| tx_type            | 时机        |
| ------------------ | --------- |
| `OFF_FLOW`         | Enter     |
| `OFF_FLOW_RESUME`  | Resume（含末站 TrackOut 自动触发） |

Enter `ext_json`：

```json
{
  "edgeType": "off_flow",
  "edgeId": "...",
  "fromSortNo": 30,
  "toSortNo": 210,
  "anchorSortNo": 30,
  "anchorStatus": "processing",
  "anchorEqpId": "1001",
  "reasonCode": "METROLOGY_EXTRA",
  "offFlowCount": 1,
  "maxOffFlowCount": 2
}
```

Resume：

```json
{
  "fromSortNo": 230,
  "anchorSortNo": 30,
  "restoredStatus": "wait",
  "restoredEqpId": null,
  "viaTrackOut": true,
  "degraded": true
}
```

`degraded=true`：本拟恢复 processing+机台，因机台不可用或已被别批占用而降级为 wait。

---

## 3. Route 配置接口

### 3.1 保存草稿

`PUT /routes/versions/{versionId}/steps`  
`edges` 可含 `off_flow`（与 rework/branch/skip_allow 同表）。  
`normal` 仍由步骤 `next` 重建。

```json
{
  "steps": [
    { "stepId": "1001", "sortNo": 10, "nextSortNo": 20 },
    { "stepId": "1002", "sortNo": 20, "nextSortNo": 30 },
    { "stepId": "1003", "sortNo": 30, "nextSortNo": 40 },
    { "stepId": "1004", "sortNo": 40, "nextSortNo": null },
    { "stepId": "2001", "sortNo": 210, "nextSortNo": 220 },
    { "stepId": "2002", "sortNo": 220, "nextSortNo": 230 },
    { "stepId": "2003", "sortNo": 230, "nextSortNo": null }
  ],
  "edges": [
    {
      "fromSortNo": 30,
      "toSortNo": 210,
      "edgeType": "off_flow",
      "maxReworkCount": 1,
      "reasonCodes": "METROLOGY_EXTRA,ENG_SAMPLE"
    }
  ]
}
```

含义：主路径 10→20→30→40；在 30 可进旁路 210→220→230（该站最多进 1 次），结束后回锚点 30。

### 3.2 发布校验增量

在现有边/步骤校验之后：

1. `off_flow`：`to` 存在、`to ≠ from`
2. `max_rework_count` 必填且 ≥1
3. `from` 必须在**主路径**集合内（从起点沿 `normal` 可达）
4. `to` 及沿其 `normal` 可达的全部站必须在**旁路集合**内（不与主路径相交）
5. 旁路子图从 `to` 出发无环，且恰好能走到「无下一站」的末站（本切片不做旁路内 branch）
6. 同站多条 `off_flow` 目标不重复
7. `reasonCodes` 规则同 rework/skip

### 3.3 版本详情 / 升版

- GET 详情 `edges` 含 `off_flow`（含次数上限）
- 升版：拷非 `normal` 边（含 `off_flow`）+ 重建 `normal`；旁路步骤行一并拷贝

### 3.4 前端边表

- 步骤：主路径「添加步骤」+「旁路步骤」（sort≥200）
- 边 type=Off-Flow；触发站 → 旁路入口；**条件/上限列填次数**；原因码可选

---

## 4. Track 执行接口

### 4.1 Enter Off-Flow

`POST /track/off-flow`  
权限：`track:off-flow`

```json
{
  "lotId": "10001",
  "toSortNo": 210,
  "reasonCode": "METROLOGY_EXTRA",
  "remark": "加测一次"
}
```

| 字段         | 必填  | 说明                          |
| ---------- | --- | --------------------------- |
| lotId      | Y   |                             |
| toSortNo   | Y   | 命中当前站一条 `off_flow`          |
| reasonCode | 条件  | 边配了 `reasonCodes` 则必填且命中    |
| remark     | N   | 进履历                         |

**执行顺序**

```
1. Lot 可执行；status ∈ {wait, processing}；off_flow 必须为 false
2. Hold.assertNoActive
3. 快照边：from=current_sort ∧ to=toSortNo ∧ type=off_flow
4. reason 白名单
5. 次数：used+1 ≤ max（旧边 max 空则按 1）；写入 off_flow_counts
6. 快照锚点：anchor_sort/step/eqp/status = 当前
7. Lot：off_flow=1；写 anchor 字段
8. Lot：current → 旁路入口；status=wait；current_eqp_id=null
9. 写 TX_OFF_FLOW + ext_json；同步 WIP
```

失败：站位、标记、次数均不变。

**成功响应**（TrackTxnResultVO）

```json
{
  "lotId": "10001",
  "txType": "OFF_FLOW",
  "status": "wait",
  "currentSortNo": 210,
  "offFlow": true,
  "offFlowCount": 1,
  "maxOffFlowCount": 1,
  "completed": false
}
```

### 4.2 Resume

`POST /track/off-flow/resume`  
权限：`track:off-flow`

```json
{
  "lotId": "10001",
  "remark": "旁路完成回主路径"
}
```

**执行顺序**

```
1. Lot 可执行；off_flow=true；status=wait（旁路加工中须先 TrackOut）
2. Hold.assertNoActive
3. 当前站必须是旁路末站（无 normal 下一站）；否则拒绝
4. 定位锚点 RouteStep（anchor_sort + version）
5. 拟恢复 status/eqp：
   - wait → eqp=null
   - processing → 恢复 anchor_eqp；assertUsable 失败或别批已在该台 processing → 降级 wait + eqp=null（degraded）
6. 清空 off_flow / anchor 字段（保留 off_flow_counts）
7. Lot：current → 锚点站；写状态/机台
8. 写 TX_OFF_FLOW_RESUME；同步 WIP
```

**末站 TrackOut 自动 Resume**

旁路末站 `TrackOut`：无 `normal`/`branch` 下一站且 `off_flow=true` 时，**不报「已完工」**，改为走 Resume 管道，履历写 `OFF_FLOW_RESUME`，ext 带 `viaTrackOut:true`。

### 4.3 旁路内既有事务约束

| 事务       | off_flow=true 时                         |
| -------- | -------------------------------------- |
| Dispatch / TrackIn / TrackOut | 允许；下一站只解析旁路 `normal`（本切片旁路无 branch）；TrackIn 仍受锚点占台约束（不可开别人的锚点机） |
| Skip     | **拒绝**（Off-Flow 中不可跳站）              |
| Rework   | **拒绝**                               |
| Enter 再次 | **拒绝**（不可嵌套）                           |
| Resume   | 仅末站 + wait                             |

主路径 `off_flow=false` 时 Resume 拒绝。

### 4.4 context 增量

`GET /track/lots/{lotId}/context`

```json
{
  "offFlow": false,
  "offFlowAnchorSortNo": null,
  "canEnterOffFlow": true,
  "canResumeOffFlow": false,
  "offFlowOptions": [
    {
      "toSortNo": 210,
      "toStepCode": "OF-MET-01",
      "toStepName": "加测入口",
      "maxOffFlowCount": 1,
      "remainCount": 1,
      "reasonCodes": ["METROLOGY_EXTRA", "ENG_SAMPLE"]
    }
  ]
}
```

| 字段                 | 规则                                                                 |
| ------------------ | ------------------------------------------------------------------ |
| offFlowOptions     | 当前站 `off_flow` 边；`remainCount>0` 才入列；`off_flow=false` 时计算          |
| canEnterOffFlow    | `!offFlow` ∧ status∈{wait,processing} ∧ options 非空 ∧ 有权限            |
| canResumeOffFlow   | `offFlow` ∧ status=wait ∧ 当前为旁路末站 ∧ 有权限                             |

前端：主路径出「Off-Flow」；旁路末站出「回主路径」；选项展示剩余次数。

**错误文案（建议）**

| 场景     | 文案                    |
| ------ | --------------------- |
| 无边     | 当前站未配置 Off-Flow       |
| 已在旁路   | 已在 Off-Flow 中         |
| 超次     | Off-Flow 次数已达上限       |
| 非末站 Resume | 仅旁路末站可回主路径         |
| 旁路 Skip | Off-Flow 中不可跳站        |
| 原因     | Off-Flow 原因不匹配 / 不能为空 |
| 状态     | 当前状态不可进入 Off-Flow     |
| 锚点占台   | 设备被 Off-Flow 批次占用：{lotNo} |

---

## 5. 权限 / DDL

### 5.1 权限

```sql
-- id 建议 297，挂现场台 290
INSERT INTO sys_permission (...) VALUES
(297, 290, 3, 'track:off-flow', '临时离线', NULL, NULL, 7, 1, NOW(), NOW(), 0);
```

配置仍用 `route:edit` / `route:publish`。

### 5.2 常量 / 脚本

- `RouteEdgeTypes.OFF_FLOW = "off_flow"`
- `migrate_off_flow.sql`：Lot 锚点字段 + 权限
- `migrate_off_flow_count.sql`：`off_flow_counts`
- `OffFlowCountStore`：读写按站次数
- `DispatchService.assertNotOffFlowAnchored`：锚点占台校验

---

## 6. 前端增量（最小）

| 页     | 改动                                                       |
| ----- | -------------------------------------------------------- |
| Route | 旁路步骤 + 边 Off-Flow + **次数上限**；原因码可选                        |
| Track | `offFlowOptions`（含剩余次数）/ Resume；二次确认；旁路中隐藏 Skip；占台由后端拦截 |

---

## 7. 实施切片

| 切片  | 交付                                                              | 状态 |
| --- | --------------------------------------------------------------- | -- |
| O1  | Lot 锚点 DDL；`OFF_FLOW` 边；保存/发布校验（主/旁路不相交）                        | 已完成 |
| O2  | `POST /track/off-flow` + 履历；权限                                    | 已完成 |
| O3  | Resume；末站 TrackOut 自动；禁 Skip/Rework/嵌套                           | 已完成 |
| O4  | context + Route/Track UI                                        | 已完成 |
| O5  | 锚点机台逻辑占台 + Resume 降级                                             | 已完成 |
| O6  | 按触发站次数上限（`off_flow_counts` + 边 max）                               | 已完成 |

---

## 8. 验收

1. 未配 `off_flow`：context 无选项；API 失败  
2. 主站 30 进 210：Lot `off_flow=1`，锚点=30，当前=210/`wait`，次数 1  
3. 旁路 TrackOut 210→220→230；230 TrackOut 自动回 30，`off_flow=0`  
4. 旁路中 Skip / 再次 Enter / 非末站 Resume：拒绝  
5. 同站上限=1：回锚点后再 Enter → 拒绝「次数已达上限」；context 无选项  
6. processing 进入：别批不可预约/开工锚点机；Resume 后恢复 `processing` + 原 `eqp_id`  
7. Resume 时锚点机不可用或被占：降级 wait，履历 `degraded=true`  
8. Hold 中不可 Enter/Resume  
9. 升版改旁路不影响在途 Lot  
10. 发布：旁路站与主路径相交 / 缺次数上限 → 失败  
11. 无权限 `canEnterOffFlow=false` 且 API 403  

---

## 9. 关联

- `MES-Route二期功能清单.md` §3.2  
- `MES-Skip接口设计.md` / `MES-Rework接口设计.md`（事务边界 / 次数模型）  
- 调研：Critical Manufacturing `Material.TemporaryOffFlow`（回原 Step+Resource 并保留 Dispatched/Processed；Dispatch=机台预约）  
- 后置：P2 子 Route 引用可升级为「Go To 另一 Route 版本」，锚点/占台/次数语义不变  
