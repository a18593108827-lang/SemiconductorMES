---
type: 接口设计
module: Route
status: done
slices: []
aligns: []
updated: 2026-07-31
---

# Step 站属性（eqp_type 强校验）— 接口设计（架构）

> 范围：Step / RouteStep 站属性定义 + Dispatch / TrackIn 设备类型校验  
> 原则：**定义进版本快照**；执行不新开事务；与 Rework / Branch 无关  
> 现状：`mes_step.eqp_type` 已有；Dispatch 已「有值才滤」；TrackIn **未**校验；快照未固化  
> 更新：2026-07-31

---

## 1. 边界

```
Step 主数据     = 工序模板（可改 eqp_type）
Route 草稿/发布 = 把站属性固化进 mes_route_step（随 version 只读）
Dispatch        = 候选机过滤 + 预约时类型匹配（读快照）
TrackIn         = 开工前强制类型匹配（读快照；与 Dispatch 同源）
```

**本切片做什么**
- 固化 `eqp_type`（必做）
- TrackIn 与 Dispatch **同一套**校验
- 发布可强制「加工站必须有类型」（开关）
- 预留字段落库：`allow_skip` / `max_queue_min`（本切片不执行）

**本切片不做什么**
- 不新开 Track 事务
- 不改边表 / 回流 / 分支
- 不启用 Skip 执行；Queue Time 执行见 `MES-QueueTime接口设计.md`
- 不做设备类型字典表（字符串对齐即可，与现 `mes_eqp.eqp_type` 约定一致）

**禁止**
- 运行时读 **直播** `mes_step.eqp_type` 做进站校验（改主数据会污染在途 Lot）
- TrackIn 跳过类型校验、只靠 Dispatch（无预约直开也会漏）
- 改 active 版本的站属性；在途只认放行时 `route_version_id`

**与相关模块**

| | Step 站属性 | Recipe 资格 | Dispatch 预约 |
|--|--|--|--|
| 问什么 | 机台 **类型** 是否匹配站要求 | 该机是否有绑定配方 | 是否约到这台机 |
| 时机 | 候选 / 预约 / TrackIn | TrackIn | 预约 + TrackIn 消费 |
| 数据源 | **RouteStep 快照** | RecipeBinding | mes_dispatch_reserve |

**状态机**：无新状态。仍是 `wait ──TrackIn(eqp)──► processing`。

---

## 2. 数据契约

### 2.1 主数据 `mes_step`（已有 + 预留）

| 字段 | 说明 |
|------|------|
| eqp_type | 设备类型；空=不限类型（见校验模式） |
| step_type | 已有：1加工 2量测 3其它 |
| allow_skip | **新增可空**：是否允许作 Skip 源/目标（P1 用） |
| max_queue_min | **新增可空**：入站前最大等待分钟（P1 Queue Time） |

DDL 增量：

```sql
ALTER TABLE mes_step
  ADD COLUMN allow_skip TINYINT NULL COMMENT '1允许Skip 0禁止 空=跟随全局' AFTER eqp_type,
  ADD COLUMN max_queue_min INT NULL COMMENT '站间最大等待分钟 预留' AFTER allow_skip;
```

主数据 CRUD 沿用现接口；改 `eqp_type` **只影响之后新保存的草稿/新发布版本**。

### 2.2 快照 `mes_route_step`（本切片关键）

| 字段 | 说明 |
|------|------|
| eqp_type | **发布时固化**的设备类型；运行时真相 |
| step_type | 固化（量测/加工判断、后续分支/量测联动） |
| allow_skip | 固化预留 |
| max_queue_min | 固化预留 |

DDL 增量：

```sql
ALTER TABLE mes_route_step
  ADD COLUMN eqp_type VARCHAR(64) NULL COMMENT '快照设备类型' AFTER next_sort_no,
  ADD COLUMN step_type TINYINT NULL COMMENT '快照工序类型' AFTER eqp_type,
  ADD COLUMN allow_skip TINYINT NULL COMMENT '快照Skip许可' AFTER step_type,
  ADD COLUMN max_queue_min INT NULL COMMENT '快照QueueTime' AFTER allow_skip;
```

**写入规则**
1. `PUT .../steps` 保存草稿：每步按 `stepId` 读当前 `mes_step`，把上述字段 **拷贝** 进 `mes_route_step`
2. `publish`：再刷一遍拷贝（防草稿期间主数据已变且未重存），然后归档只读
3. `upgrade`：拷源版本快照字段到新草稿（不回读直播主数据；工程师可再改步骤触发重拷）

**兼容旧快照**  
`mes_route_step.eqp_type` 为空时：运行时 **回退** 读 `mes_step`（与现 Dispatch 行为一致），并打日志；新发布版本不得依赖此回退。

### 2.3 Lot 运行态

不增字段。履历 `ext_json`（TrackIn 可选）：

```json
{
  "eqpTypeRequired": "ETCHER",
  "eqpTypeActual": "ETCHER",
  "eqpId": "..."
}
```

---

## 3. 校验模式（配置）

`application.yml`：

```yaml
mes:
  step:
    eqp-type-mode: soft   # off | soft | force
```

| 模式 | 发布 | Dispatch 候选/预约 | TrackIn |
|------|------|-------------------|---------|
| `off` | 不查类型 | 不滤类型 | 不校验 |
| `soft`（默认，贴近现状） | 不强制填 | **有**快照类型才过滤/匹配 | **有**快照类型才匹配 |
| `force` | `step_type=加工(1)` 必须非空 `eqp_type`；量测/其它可空 | 同 soft，但加工站必有类型 | 同左；无类型配置视为数据错误拒绝 |

比较规则：`trim` 后 **全等**（大小写敏感，与现 Dispatch 一致）。  
设备 `eqp_type` 空且站要求非空 → 不匹配。

统一入口（建议组件）：

```
StepEqpTypeGuard.requireMatch(versionId, sortNo|stepSnapshot, eqpId)
StepEqpTypeGuard.resolveRequired(lot) → String|null
```

Dispatch / TrackIn **只调 Guard**，禁止各写一套。

---

## 4. Route / Step 配置接口

### 4.1 工序主数据（已有，补字段）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST/PUT | `/steps` · `/steps/{id}` | `step:add` / `step:edit` | body 增 `allowSkip` / `maxQueueMin` |
| GET | `/steps` · `/steps/{id}` | `step:list` | VO 带回 |

```json
{
  "stepCode": "ETCH-01",
  "stepName": "刻蚀",
  "stepType": 1,
  "eqpType": "ETCHER",
  "allowSkip": 0,
  "maxQueueMin": 120,
  "status": 1
}
```

### 4.2 保存草稿步骤（行为变更，URL 不变）

`PUT /routes/versions/{versionId}/steps`（`route:edit`，仅 draft）

请求体 **不必** 传 `eqpType`（以主数据为准自动拷贝）。若前端误传，**忽略**，避免与主数据双源。

服务端伪逻辑：

```
for each stepItem:
  step = load MesStep(stepId)
  routeStep.eqpType = step.eqpType
  routeStep.stepType = step.stepType
  routeStep.allowSkip = step.allowSkip
  routeStep.maxQueueMin = step.maxQueueMin
```

### 4.3 版本详情（读）

`GET /routes/versions/{versionId}` 步骤项增：

```json
{
  "stepId": "1002",
  "stepCode": "ETCH-01",
  "stepName": "刻蚀",
  "sortNo": 20,
  "nextSortNo": 30,
  "eqpType": "ETCHER",
  "stepType": 1,
  "allowSkip": 0,
  "maxQueueMin": 120
}
```

生效版只读；展示快照值，不二次读主数据覆盖。

### 4.4 发布校验增量

`POST /routes/versions/{versionId}/publish`

在现有边/步骤校验之后：

1. 每个 `route_step` 再从主数据刷快照字段（见 §2.2）
2. `eqp-type-mode=force` 时：凡 `step_type=1` 且 `eqp_type` 空 → 拒绝：`加工站未配置设备类型: sortNo=xx`
3. 不校验厂内是否真有该类型设备（避免环境耦合；Dispatch 空候选即暴露）

---

## 5. 执行接口（行为变更，URL 不变）

### 5.1 Dispatch 候选

`GET /dispatch/candidates?lotId=`

- `eqpType` 取自 **当前站 RouteStep 快照**（空则旧快照回退主数据）
- `soft/force` 且类型非空：只返回 `mes_eqp.eqp_type` 相等且可用设备

### 5.2 Dispatch 预约

`POST /dispatch/reserves`

类型不匹配 → `设备类型与当前站不匹配`（文案可带 required/actual）。

### 5.3 TrackIn

`POST /track/track-in`

```json
{ "lotId": "...", "eqpId": "..." }
```

顺序（插入类型校验）：

```
1. lot 可执行 + Hold
2. 设备可用
3. StepEqpTypeGuard.requireMatch（本切片新增）
4. 预约匹配（若有）
5. Recipe 资格
6. 改状态 + 履历 + 消费预约
```

失败：站位不变、不写履历。

### 5.4 Track context 增量

`GET /track/context?lotId=` 的 `currentStep`：

```json
{
  "stepId": "...",
  "stepCode": "ETCH-01",
  "stepName": "刻蚀",
  "sortNo": 20,
  "eqpType": "ETCHER",
  "stepType": 1
}
```

前端可提示「本站要求设备类型 ETCHER」；无新按钮。

---

## 6. 权限

不新增权限码。

| 动作 | 权限 |
|------|------|
| 改 Step 属性 | `step:edit` |
| 保存/发布路线（固化快照） | `route:edit` / `route:publish` |
| 预约 | 现有 dispatch 权限 |
| TrackIn | `track:track-in` |

---

## 7. 前端增量（最小）

| 页 | 改动 |
|----|------|
| Step 工序库 | 编辑/展示 `eqpType`；预留 `allowSkip`/`maxQueueMin` 可先隐藏或只读空 |
| Route 版本详情 | 步骤列展示快照 `eqpType` |
| Track / Dispatch | 展示当前站要求类型；校验失败用后端错误文案 |

---

## 8. 实施切片

| 切片 | 交付 |
|------|------|
| S1 | DDL：`mes_route_step` 快照字段；`mes_step` 预留字段 |
| S2 | 保存草稿 / 发布 / 升版拷贝快照；版本详情回传 |
| S3 | `StepEqpTypeGuard`；Dispatch 改读快照；TrackIn 接入 |
| S4 | `eqp-type-mode=force` 发布校验；context / 履历 ext_json |
| S5 | 工序库 + Route/Track 最小展示 |

---

## 9. 验收

1. `soft` + 站无类型：行为与现网一致（任意可用类型可进）  
2. `soft/force` + 站有类型：错类型设备预约/TrackIn 均失败  
3. 发布后改 Step 主数据 `eqp_type`：**在途 Lot 仍按旧快照**  
4. 升版并重新发布后，**新放行** Lot 用新类型  
5. `force`：加工站空类型无法发布  
6. 无预约直接 TrackIn：类型仍校验（不依赖 Dispatch）  
7. Recipe 资格失败与类型失败错误可区分  

---

## 10. 关联

- `MES-Route二期功能清单.md` §2.3  
- `MES-Dispatch功能文档.md` 候选/预约  
- `MES-Equipment功能文档.md` `eqp_type` 约定  
- `MES-Recipe功能文档.md`（资格 ≠ 类型，串联于 TrackIn）  
- `MES-Branch接口设计.md` / `MES-Rework接口设计.md`（事务边界参考，本能力无新事务）
