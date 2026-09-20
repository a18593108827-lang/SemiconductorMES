---
type: 接口设计
module: Route
status: done
slices: []
aligns: []
updated: 2026-08-05
---

# Skip 跳站 — 接口设计（架构）

> 范围：Route 配置可跳路径 + Track 独立跳站事务  
> 原则：定义进版本快照；**独立事务，不塞进 TrackOut / Rework**  
> 对齐：大厂「站可跳标记 + 特权事务 + 白名单目标」；本项目采用 **前向白名单跳转**  
> 前提：`mes_route_edge`、`mes_route_step.allow_skip` 已落地  
> 更新：2026-08-05（Skip 仅 wait，对齐 CM Queued）

---

## 1. 边界

```
Route  = 配 allow_skip 站属性 + skip_allow 边（from→to）进 route_version 快照
Track  = POST /track/skip 改 Lot 当前站 → 目标站；写 SKIP 履历
Hold   = skip 前 assertNoActive（同 TrackIn/Out/Rework）
```

**本切片做什么**

- 前向跳站：从当前站一次跳到白名单目标站，中间站视为被跳过（不加工）
- 配置：`allow_skip` + `edge_type=skip_allow`
- 执行：独立事务 + `track:skip` + 原因/备注 + 履历

**本切片不做什么**

- 不回跳（回前序 = Rework）
- 不条件分流（= Branch / TrackOut resultCode）
- 不做 Critical Manufacturing 式「留在当前站仅标 Processed」（半导现场更需要显式落地目标站）
- 不做任意前向「同路线随便跳」（必须白名单边）
- 不做双人审批 / EBR 电子签名（可后置）

**禁止**

- TrackOut / Rework 带 `toSortNo` 冒充跳站
- TrackOut 匹配 `skip_allow` 边
- **processing 直接跳站**（须先 TrackOut，或后置 Abort→wait 后再跳）
- 无 `skip_allow` 边或路径上站 `allow_skip≠1` 仍跳成功
- 改 active 版本边/属性；在途只认放行时 `route_version_id`
- 跨版本、跳到不存在站、跳到当前站

**与兄弟能力分工**


|     | Skip          | Rework           | Branch                |
| --- | ------------- | ---------------- | --------------------- |
| 事务  | `/track/skip` | `/track/rework`  | TrackOut + resultCode |
| 边类型 | `skip_allow`  | `rework`         | `branch` / `normal`   |
| 方向  | **仅前向**       | 回前序              | 通常前向或旁路               |
| 站属性 | `allow_skip`  | —                | —                     |
| 次数  | 无（本切片）        | `maxReworkCount` | 无                     |
| 权限  | `track:skip`  | `track:rework`   | `track:track-out`     |
| 允许状态 | **仅 wait**（对齐 CM Queued） | wait \| processing | processing（TrackOut） |


**状态机**

```
wait ──Skip(toSortNo)──► wait(目标站)
  └ 未开工跳过本站及中间站（对齐 Critical Manufacturing SkipProcess）

processing ──✗──► 拒绝
  └ 须 TrackOut 进下一站 wait 后再跳；或（后置）Abort → wait 再跳
```

---



## 2. 数据契约（快照内）



### 2.1 站属性（已有）

```
mes_route_step.allow_skip（发布时从 mes_step 拷贝固化）：
```


| 值            | 含义                                       |
| ------------ | ---------------------------------------- |
| `1`          | 允许作为 Skip **源 / 被跳过中间站 / 目标**（本切片统一用此开关） |
| `0` / `null` | 不允许出现在任意 Skip 路径上                        |


主数据改 `allow_skip` 只影响之后新保存/新发布的版本。

### 2.2 边

沿用 `mes_route_edge`：


| 字段               | Skip 用法                |
| ---------------- | ---------------------- |
| edge_type        | 固定 `skip_allow`        |
| from_sort_no     | 触发站（当前站）               |
| to_sort_no       | 跳入目标站                  |
| reason_codes     | 可选白名单，逗号分隔；空=任意 reason |
| condition_code   | 空（不用）                  |
| max_rework_count | 空（不用）                  |


唯一约束沿用现有 `(version_id, from_sort_no, to_sort_no, edge_type, condition_code)`。  
一站可多条 `skip_allow`（跳不同目标）；执行必须显式传 `toSortNo`。

**前向定义**  
从 `from` 沿主路径 `normal`/`next_sort_no` 前进，必须能到达 `to`，且中间至少跨过 0 个站以上（`to ≠ from`）。禁止后向、禁止旁路到主路径不可达站。

被跳过的站 = 主路径上 `(from, to)` 开区间内的全部 `sort_no`。  
执行时这些站 + `from` + `to` 的快照 `allow_skip` 均须为 `1`。

### 2.3 Lot 运行态

本切片**不增** Lot 字段（不做跳站次数上限）。  
履历 `tx_type=SKIP`，结构化进 `ext_json`：

```json
{
  "edgeType": "skip_allow",
  "edgeId": "...",
  "fromSortNo": 20,
  "toSortNo": 50,
  "skippedSortNos": [30, 40],
  "reasonCode": "EQP_DOWN"
}
```

---



## 3. Route 配置接口



### 3.1 工序 / 草稿（行为增量）

- 工序库：编辑 `allowSkip`（已有字段，本切片要在 UI 露出）
- `PUT /routes/versions/{versionId}/steps`：`edges` 可含 `skip_allow`（与 rework/branch 同表）
- `normal` 仍由步骤 `next` 重建，前端勿传

```json
{
  "steps": [
    { "stepId": "1001", "sortNo": 10, "nextSortNo": 20 },
    { "stepId": "1002", "sortNo": 20, "nextSortNo": 30 },
    { "stepId": "1003", "sortNo": 30, "nextSortNo": 40 },
    { "stepId": "1004", "sortNo": 40, "nextSortNo": 50 },
    { "stepId": "1005", "sortNo": 50, "nextSortNo": null }
  ],
  "edges": [
    {
      "fromSortNo": 20,
      "toSortNo": 50,
      "edgeType": "skip_allow",
      "reasonCodes": "EQP_DOWN,PROCESS_WAIVE"
    }
  ]
}
```

含义：在站 20 可一键跳到 50，中间 30、40 被跳过（这些站 `allow_skip` 须为 1）。

### 3.2 发布校验增量

在现有边/步骤/`eqp_type` 校验之后：

1. `skip_allow`：`to` 存在、`to ≠ from`
2. 从 `from` 沿主路径 `next` **前向可达** `to`；否则拒绝
3. `from`、`to`、开区间内每一站的快照 `allow_skip == 1`；否则拒绝并指出 sortNo
4. 同站多条 `skip_allow` 目标不重复
5. `reasonCodes` 格式：trim、非空段、大写建议（与 rework 一致可只存原串）



### 3.3 版本详情

`GET /routes/versions/{versionId}` 的 `edges` 已含各类边；步骤项已回 `allowSkip`。  
前端边表 type 增加「跳站」。

### 3.4 升版

与 rework/branch 相同：拷非 `normal` 边（含 `skip_allow`）+ 重建 `normal`；步骤快照属性随步骤行拷贝。

---



## 4. Track 执行接口



### 4.1 Skip

`POST /track/skip`  
权限：`track:skip`

```json
{
  "lotId": "10001",
  "toSortNo": 50,
  "reasonCode": "EQP_DOWN",
  "remark": "刻蚀机台宕机，工程批准跳过中间站"
}
```


| 字段         | 必填  | 说明                       |
| ---------- | --- | ------------------------ |
| lotId      | Y   |                          |
| toSortNo   | Y   | 必须命中当前站一条 `skip_allow`   |
| reasonCode | 条件  | 边配了 `reasonCodes` 则必填且命中 |
| remark     | N   | 人工备注，进履历 remark          |


**执行顺序**

```
1. Lot 可执行 + 状态 **仅 wait**
2. Hold.assertNoActive
3. 快照边：from=current_sort ∧ to=toSortNo ∧ type=skip_allow
4. 校验 from/to/中间站 allow_skip=1（防旧快照脏数据）
5. reason 白名单
6. 定位目标 RouteStep
7. Lot：status=wait，current→目标，current_eqp_id=null
8. 写 TX_SKIP + ext_json；同步 WIP
```

失败：站位不变、不写履历。

**成功响应**（沿用 TrackTxnResultVO 形态）

```json
{
  "lotId": "10001",
  "lotNo": "LOT-...",
  "txType": "SKIP",
  "status": "wait",
  "currentSortNo": 50,
  "currentStepId": "...",
  "completed": false
}
```

**错误文案（建议）**


| 场景   | 文案                   |
| ---- | -------------------- |
| 无边   | 当前站未配置跳至目标站          |
| 站不可跳 | 跳站路径含不可跳站: sortNo=xx |
| 原因   | 跳站原因不匹配 / 不能为空       |
| Hold | 与现网 Hold 文案一致        |
| 状态   | 仅等待状态可跳站（加工中请先完工）         |




### 4.2 context 增量

`GET /track/context?lotId=`

```json
{
  "skipOptions": [
    {
      "toSortNo": 50,
      "toStepCode": "MET-01",
      "toStepName": "量测",
      "skippedSortNos": [30, 40],
      "reasonCodes": ["EQP_DOWN", "PROCESS_WAIVE"]
    }
  ],
  "canSkip": true
}
```


| 字段          | 规则                                                       |
| ----------- | -------------------------------------------------------- |
| skipOptions | 当前站全部 `skip_allow`；路径上任一站 `allow_skip≠1` 则该选项剔除          |
| canSkip     | `status=wait` ∧ options 非空 ∧ 有 `track:skip` |


前端：有 options 才显示「跳站」面板；选目标 + 原因后二次确认再调 API。

---



## 5. 权限 / DDL



### 5.1 权限

```sql
-- id 建议 296，挂现场台 290
INSERT INTO sys_permission (...) VALUES
(296, 290, 3, 'track:skip', '跳站', NULL, NULL, 6, 1, NOW(), NOW(), 0);
-- 角色：admin / process_eng / 现场主管（与 rework 同级或更严）
```

配置仍用 `route:edit` / `route:publish` / `step:edit`。

### 5.2 边类型常量

代码侧 `RouteEdgeTypes.SKIP_ALLOW = "skip_allow"`。  
无新表；`allow_skip` 已存在则本切片无强制 DDL（仅权限脚本 `migrate_skip.sql`）。

---



## 6. 前端增量（最小）


| 页        | 改动                                |
| -------- | --------------------------------- |
| 工序库      | 编辑/展示「允许跳站」`allowSkip`            |
| Route 边表 | type=`跳站(skip_allow)` + 目标站 + 原因码 |
| Track    | `skipOptions` → 跳站面板；二次确认         |


---



## 7. 实施切片


| 切片  | 交付                                                     |
| --- | ------------------------------------------------------ |
| K1  | `RouteEdgeTypes.SKIP_ALLOW`；保存/发布校验（前向可达 + allow_skip） |
| K2  | `POST /track/skip` + 履历 ext_json；权限 `track:skip`       |
| K3  | context.skipOptions / canSkip                          |
| K4  | Route / Step / Track 最小 UI                             |


建议复用：`RouteEdgeResolver` 增 listSkip / findSkip / computeSkippedSortNos；Impl 只改状态写履历。

---



## 8. 验收

1. 未配 `skip_allow`：context 无选项；调用 API 失败
2. 配 20→50：从 20 跳到 50，Lot 在 50/`wait`，履历含 `skippedSortNos=[30,40]`
3. 中间站 `allow_skip≠1`：发布失败或运行剔除选项/拒绝
4. TrackOut 不传 resultCode 仍走 normal，**不会**吃 skip 边
5. Hold 中不可跳
6. **processing 不可跳**（按钮灰、API 拒绝）；须先 TrackOut 再跳
7. 升版改跳站边不影响在途 Lot
8. 无权限账号 `canSkip=false` 且 API 403

---



## 9. 关联

- `MES-Route二期功能清单.md` §3.1  
- `MES-Rework接口设计.md` / `MES-Branch接口设计.md`（事务边界）  
- `MES-Step站属性接口设计.md`（`allow_skip` 快照）  
- 调研参考：Critical Manufacturing `Is Skippable` + `SkipProcess`（**仅 Queued/wait**）；Sepasoft Manual Mode + valid next steps（本设计取「站开关 + 白名单 + 独立事务 + 仅 wait」，不做全自由改 Active Step；Abort 后置）

