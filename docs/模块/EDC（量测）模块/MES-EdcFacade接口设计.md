# EdcFacade — 接口设计（架构）

> 范围：EDC-4 = 对外门面 + 权限种子 + 应急配置；**不**改 Lot 状态、**不**写 TRACK_OUT  
> 原则：状态仍只由 Track 写；EDC 只回答「本站本趟能不能出」；业务零直表  
> 产品口径：指定站必须采到**合格**数据，否则不许 TrackOut（防假过站）  
> 对齐：`MES-EDC功能文档.md` §5–6；`MES-EDC一期功能清单.md` EDC-4；RecipeFacade 同构  
> 前提：Param / Spec / Plan / 手录判定已落地；T2-7（EDC-6/7）消费本契约后才拒出  
> 状态：**EDC-4 / EDC-6 / EDC-7 已落地**；SPC 读点口 + 事件（SPC-1）✅  
> 更新：2026-09-02

---

## 1. 边界

```
EDC     = 采集真相 + Spec 单笔判定 + 本门面（本期同进程）
Track   = TrackOut / context 只调 Facade；不存点、不判规格、不注入 Mapper
Route   = 提供 stepId / routeVersionId / sortNo；不内嵌 Spec / Plan
Hold    = 一期不因 EDC 自动挂；二期策略另立
SPC     = 后置只读 collection*；禁止另造点表；禁止进本门禁
Lot/WIP = 不双写量测结果
Move    = wait→wait，无 visit，**不走**本门禁
Abort   = 退回本站 wait；本趟采集对下次 In 作废（新 track_in_tx_id）
```

**本切片做什么**

- 包内唯一对外入口：`EdcFacade` / `EdcFacadeImpl`
- `evaluateGate`：只读判定，不抛
- `assertClearToTrackOut`：`required && !clear` 抛业务错（供 T2-7 挂钩）
- `getActivePlan` / `getLatestCollection`：只读
- HTTP 预检 `GET /edc/gate`（薄封装 `evaluateGate`）
- 配置 `mes.edc.gate-enabled`（默认 true）
- 权限种子已落地，本切片不改码表

**本切片不做什么**

- 在 `TrackServiceImpl.trackOut` 挂钩（EDC-6 / T2-7a）✅  
- Track context / TrackPage 拒出提示（EDC-7 / T2-7b）✅ 无新按钮
- 新表、新事务码、Auto-Hold、bypass 旁路码
- 重判 USL/LSL（认采集头 `result`，不在门面再算一遍）
- SPC / OOC / 控制限

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Track / Dispatch / Lot / Hold 注入 `com.mes.edc.mapper.*` | 双真相；拆库时迁不动 |
| P2 | Track 内自建量测表或缓存「最近 PASS」冒充门禁 | 与 SPC 分裂；返工可偷用旧单 |
| P3 | 用 `step_type=量测` 隐式 `required` | 误伤未配站 |
| P4 | 门禁认任意历史 PASS（不绑 `track_in_tx_id`） | 重进站假过 |
| P5 | `gate-enabled=false` 当日常放行 | 工艺形同虚设；旁路属 P2 特权+审计 |
| P6 | Facade 内改 `mes_lot` / 写 TRACK_OUT | 状态只由 Track 写 |
| P7 | Track 经 HTTP 调 `/edc/*` 做 Out 校验 | 同进程必须走 Spring Bean；事务边界在 TrackOut |

**与兄弟能力分工**

| | Process Time | RecipeFacade | EdcFacade |
|--|--|--|--|
| 问什么 | 站内加工时长是否在窗 | 这站这机有没有资格 | 本站本趟量测清不清 |
| 钩子 | TrackIn 开表 / TrackOut 结算 | TrackIn `assertQualified` | TrackOut `assertClearToTrackOut` |
| 未配 | 步骤 min/max 空 → 不控 | 无绑定默认不拦 | 无 Plan / `required=0` → clear |
| 应急阀 | `mes.process-time.enabled` | `mes.recipe.require-binding` | `mes.edc.gate-enabled` |
| 状态机 | 无新状态 | 无新状态 | 无新状态 |

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 形态 | 同进程 Facade，对齐 `RecipeFacade` | 一期不拆服务；契约稳定后可迁 |
| D2 | 消费方式 | Track **只注入接口**；HTTP 仅给人/管理端 | 过站与预检同一套算法 |
| D3 | 「要求 EDC」 | 仅 `mes_edc_plan.required=1` 且 `enabled=1` | 显式绑定；运行时解析，不进 Route 快照 |
| D4 | 判定 | 只认本趟最新 collection 头 `result=PASS` | 采集已做 OOS；门面不重算 |
| D5 | 最新条 | 同 `(lotId, track_in_tx_id)` 按 `collected_at` 降序第一条 | 新 FAIL 覆盖旧 PASS |
| D6 | visit | 当前 processing 最近一次本站 `TRACK_IN` 的 `tx_id` | 与录入绑定同一 id |
| D7 | 未配站 | `required=false, clear=true, reason=NONE` | 验收：行为与现网一致 |
| D8 | 应急 | `gate-enabled=false` → `clear=true, reason=GATE_DISABLED`；**`required` 仍按 Plan** | 现场能看出「本应卡、现应急」；不删 Plan |
| D9 | 权限 | HTTP 用 `edc:*`；Facade **不**鉴权 | 过站已有 `track:track-out`；避免双重门 |
| D10 | 失败 | 抛 `BusinessException`，文案前缀 `EDC_BLOCK_TRACK_OUT:` | 对齐 ProcessTime；整笔 Out 回滚、无履历 |
| D11 | 挂点（EDC-6） | ProcessTime `assertOnTrackOut` **之后**、改 Lot 状态 **之前** | 先时长后量测；失败不污染 WIP |

**刻度**：Lot 级；Wafer 后置。`source` 一期 MANUAL，AUTO 仍走同一 collection，门面不区分。

---

## 3. 门面契约

包：`com.mes.edc.facade.EdcFacade`  
实现：`com.mes.edc.facade.impl.EdcFacadeImpl`（`@Service`）

```
EdcFacade
  evaluateGate(lotId, routeVersionId, sortNo, stepId) → EdcGateResult
  assertClearToTrackOut(lotId, routeVersionId, sortNo, stepId)   // 抛或不抛
  getActivePlan(stepId) → 计划摘要 | null
  getLatestCollection(lotId, trackInTxId) → 采集头+点 | null
  listSeries(paramId, stepId, eqpId, from, to, limit) → 序列点   // SPC 只读；见 MES-SPC架构设计.md
  getCollection(collectionId) → 采集头+点 | null              // SPC-1 监听用
```

`assertClearToTrackOut` **必须**调用 `evaluateGate`，禁止第二套 if。

### 3.1 `EdcGateResult`

| 字段 | 类型 | 说明 |
|------|------|------|
| required | boolean | 本站启用 Plan 且 `required=1`（与应急开关无关） |
| clear | boolean | true=允许 TrackOut |
| reasonCode | String | `NONE` / `NO_DATA` / `OOS` / `GATE_DISABLED` |
| message | String | 给人看的一句 |
| collectionId | Long | 命中最新条时；否则 null |
| trackInTxId | Long | 解析到的本趟 In；无 visit 则 null |

语义矩阵：

| 条件 | required | clear | reasonCode |
|------|----------|-------|------------|
| 无启用 Plan 或 `required=0` | false | true | NONE |
| `gate-enabled=false` 且本应 required | true | true | GATE_DISABLED |
| required，无 visit / 无采集 | true | false | NO_DATA |
| required，最新条 `FAIL` | true | false | OOS |
| required，最新条 `PASS` | true | true | NONE |

`assertClearToTrackOut`：`required && !clear` →

```
throw new BusinessException("EDC_BLOCK_TRACK_OUT: " + result.getMessage());
```

### 3.2 判定（唯一算法）

输入：`lotId` + `routeVersionId` + `sortNo` + `stepId`  
（`routeVersionId` / `sortNo` 一期可只做校验上下文，不参与 Plan 查找；Plan 按 `stepId`。）

```
1. plan = 启用 Plan(stepId)
2. if plan == null || plan.required != 1
     → required=false, clear=true, NONE
3. required=true
4. if mes.edc.gate-enabled == false
     → clear=true, GATE_DISABLED
5. trackInTxId = 该 Lot 当前 processing 下、本 step 最近一次 TRACK_IN.id
6. if trackInTxId == null → clear=false, NO_DATA
7. col = 同 (lotId, trackInTxId) collected_at 最新一条
8. if col == null → clear=false, NO_DATA
9. if col.result != PASS → clear=false, OOS
10. else → clear=true, NONE, collectionId=col.id
```

**不**重跑 Spec。历史点已固化 `spec_id` + 上下限快照。

### 3.3 只读方法

| 方法 | 行为 |
|------|------|
| `getActivePlan(stepId)` | `enabled=1` 的 Plan + items 摘要；无则 null |
| `getLatestCollection(lotId, trackInTxId)` | 复用现有 latest 查询；无则 null |

实现上复用 `MesEdcPlanMapper` / `MesEdcCollectionService`（或同等查询），**禁止**在 Facade 复制一份 OOS 公式。

### 3.4 HTTP 预检（给人，不给 TrackOut）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/edc/gate?lotId=&stepId=` | `edc:view` 或 `track:view` | 调 `evaluateGate`；缺参 400 |

TrackOut **禁止**调此接口。

---

## 4. 依赖与包

```
TrackServiceImpl ──► EdcFacade
Dispatch / Lot / Hold ──✕──► edc.mapper

EdcFacadeImpl ──► Plan / Collection（只读）
              ──► mes_tx_log（只读，解析 TRACK_IN）
              ──► 不写 lot / wip / tx_log
```

```
com.mes.edc.facade.EdcFacade
com.mes.edc.facade.impl.EdcFacadeImpl
com.mes.edc.vo.EdcGateResult
```

现有 Controller / Service 不动职责；采集仍走 `/edc/collections`。

---

## 5. 配置项

```yaml
mes:
  edc:
    gate-enabled: true   # false：required 站也 clear（应急）；不删 Plan
```

| 键 | 默认 | 读点 | 语义 |
|----|------|------|------|
| `mes.edc.gate-enabled` | true | **仅 FacadeImpl** `@Value` | false 时算法第 4 步短路 |

正式卡控永远是站级 `plan.required`。关全局开关须运维/事故流程，不作产品旁路。

对齐：`mes.process-time.enabled`、`mes.recipe.require-binding`——模块开关住在**被调用方**，Track 不 if 配置。

---

## 6. 权限种子（已落地，本切片冻结）

| id | 码 | 用途 | HTTP |
|----|----|------|------|
| 253 | `edc:view` | 菜单 `/app/edc`；查询 | params/specs/plans/collections/gate |
| 254 | `edc:edit` | 草稿主数据 | Param / Plan / PlanItem / Spec 草稿 |
| 255 | `edc:publish` | Spec 发布 | `POST /edc/specs/{id}/publish` |
| 256 | `edc:collect` | 提交采集 | `POST /edc/collections` |

角色：admin / process_eng 全量；supervisor `view`；operator `view`+`collect`。  
DDL：`migrate_edc.sql`（已合入 `schema.sql`）。

过站权限仍是 `track:track-out`。强行放行（bypass）**不**在本期种子。

---

## 7. 与 Track 的衔接

EDC-6 挂点（已落地）：

```
hold.assertNoActive
  → status=processing
  → resolveTrackOut
  → processTimeSupport.assertOnTrackOut
  → edcFacade.assertClearToTrackOut(lotId, routeVersionId, sortNo, stepId)   ← EDC-6
  → 改 Lot / 写 TRACK_OUT / Q-Time / ProcessTime dispose
```

失败：事务回滚；**不插** `TRACK_OUT`；计时保留（与 `<min` 拒出一致）。

EDC-7 context（已落地，嵌套 `edc`，不是扁平 `edcRequired`）：

| context 字段 | 来源 |
|--------------|------|
| `edc.required` | `result.required` |
| `edc.clear` | `result.clear` |
| `edc.reasonCode` | `result.reasonCode` |
| `edc.message` | `result.message` |
| `canTrackOut` | processing ∧ ProcessTime 下限 ∧ **`edc.clear`** |

`GATE_DISABLED` 时 `edc.clear=true` 且 `edc.required=true`，现场旁注「量测应急放行，完工不卡」。

现场：仍用原「完工」按钮；`!clear` 灭按钮 + 完工旁人话（没采 / 超规）。采合格后刷新 context 再亮。

---

## 8. 演进

| 阶段 | 契约变化 |
|------|----------|
| EDC-4 | Facade + 配置 + `GET /edc/gate` |
| EDC-6 | `trackOut` 调用 `assertClearToTrackOut`；错误码不变 |
| EDC-7 | context `edc` + 现场完工旁提示；仍只调 Facade ✅ |
| 二期 | 拒出后可选 Hold；bypass 新权限+审计；**不**把 bypass 做成 `gate-enabled` |
| SPC | `listSeries` / `getCollection`；采集提交后发 `EdcCollectedEvent`（EDC **不**依赖 SPC 包）✅ |
| 拆库 | Facade → HTTP/gRPC 客户端；方法签名保持 |

拆库条件：点量、团队或 SPC 独立部署。表前缀 `mes_edc*` 与本接口不变即可迁。

---

## 9. 验收（EDC-4）

1. 存在 `EdcFacade` Bean；Track / 他模块无 EDC Mapper 注入 ✅  
2. 无 Plan / `required=0`：`evaluateGate` → `required=false, clear=true` ✅  
3. `required=1` 无本趟采集：`clear=false, NO_DATA`；`assert*` 抛 `EDC_BLOCK_TRACK_OUT:` ✅  
4. 本趟最新 FAIL：`OOS`；最新 PASS：`clear=true` ✅  
5. `mes.edc.gate-enabled=false`：required 站 `clear=true, GATE_DISABLED` ✅  
6. `GET /edc/gate` 与 `evaluateGate` 结果一致 ✅  
7. 权限码 253–256 行为不变 ✅  
8. **本切片结束后 TrackOut 行为仍与现网一致**（钩子在 EDC-6；现已挂）✅   
9. 本切片无前端页 ✅  

落地：

| 项 | 路径 |
|----|------|
| 接口 | `com.mes.edc.facade.EdcFacade` |
| 实现 | `com.mes.edc.facade.impl.EdcFacadeImpl` |
| 预检 HTTP | `GET /edc/gate` → `MesEdcGateController` |
| 配置 | `mes.edc.gate-enabled`（`application.yml`） |

---

## 10. 关联

- `MES-EDC功能文档.md` §5–6
- `MES-EDC一期功能清单.md` EDC-4
- `MES-EDC数据库设计.md`
- `MES-EDC与SPC范围说明.md`
- `docs/模块/SPC（统计过程控制）模块/MES-SPC架构设计.md`
- `docs/模块/SPC（统计过程控制）模块/MES-SPC一期功能清单.md`
- `MES-Track二期功能清单.md` §3.2 / T2-7
- `server/src/main/java/com/mes/recipe/facade/RecipeFacade.java`（同构）
