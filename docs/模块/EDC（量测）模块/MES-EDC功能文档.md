# MES 量测采集（EDC）功能文档

> 定位：站级**量测真相** + **Spec 单笔判定** + 对 Track/SPC 的 **Facade**  
> 产品一句话：指定站必须采到**合格**数据，否则不许 TrackOut（防假过站）  
> 对齐：`docs/架构/半导MES架构设计.md`；`docs/业务清单/MES-半导体业务清单.md` §9；业界 Data Collection（CM）/ OOS 门禁（Camstar）/ Crawl→Walk（Applied）  
> 一期状态：**主数据 + 手录判定 + 现场录入已落地；`EdcFacade` / TrackOut 门禁未接（FAIL 单仍可完工）**  
> 更新：2026-08-18  
> 查验（落地后）：`MES-EDC已完成功能.md`  
> 范围边界：`MES-EDC与SPC范围说明.md`  
> Track 钩子：`docs/模块/Track（执行引擎）模块/MES-Track二期功能清单.md` §3.2 / T2-7  
> 表结构：`MES-EDC数据库设计.md`  
> 切片：`MES-EDC一期功能清单.md`

---

## 1. 目标（最小集）

- 维护量测特性（Param）与规格（Spec：USL/LSL/目标）
- 维护站级采集计划（Plan）：哪些 Step **要求** EDC、采哪些 Param
- 现场/管理端**手录**采集；单笔对照 Spec → PASS / OOS
- 对外只暴露 `EdcFacade`；TrackOut 调 `assertClearToTrackOut`
- 未配 Plan 的站：TrackOut **行为不变**

**不做（一期）：** SPC 图 / CPK / OOC 规则、FDC、SECS 自动回传、片/槽级采数、强行放行旁路码。

---

## 2. 边界

```
EDC     = 采集真相 + Spec 单笔判定 + Facade（本期 MES 内嵌）
Track   = TrackOut 钩子只问「能不能出」；不存点、不判规格
Route   = 提供 step / route_version / sort_no；不内嵌 Spec body
Hold    = 一期不自动挂；二期可由策略「拒出后 Hold」或 SPC 调 HoldService
SPC     = 只读 EDC 历史点（后置）；禁止另造点表
Adapter = 自动回传入口后置；仍写同一 collection 表
Lot/WIP = 不双写量测结果；qty/status 仍只由 Track 事务改
```

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Track / Dispatch / Lot 直查 `mes_edc*` | 双真相；难拆库 |
| P2 | Track 内自建假量测表冒充门禁 | 迁不动、和 SPC 分裂 |
| P3 | 用 `step_type=量测` 隐式等同「要求 EDC」 | 必须显式 Plan；避免误伤 |
| P4 | 一期把 OOC / 控制限塞进门禁 | OOC 属 SPC；门禁只认 OOS / 无数据 |
| P5 | 采集不绑「本趟访问」只认历史任意 PASS | 返工/重进站可偷用旧合格单 |

---

## 3. 架构决策（已拍板）

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 模块形态 | MES 同进程包 `com.mes.edc`；表前缀 `mes_edc*` | 对齐 Recipe；可后拆 |
| D2 | 对外契约 | 唯一 `EdcFacade` | Track/SPC 零 Mapper |
| D3 | 「要求 EDC」来源 | **`mes_edc_plan`（按 step_id）**；运行时解析 | 不拷 Spec 进 Route；在途认当前 Plan |
| D4 | 与 step_type | `step_type=2` **不**自动 require | 显式绑定，防误卡 |
| D5 | 判定范围 | 仅 **OOS**（相对 USL/LSL）；缺数 = 不合格 | 门禁最小语义 |
| D6 | 采集粒度 | **Lot 级**（一期） | Wafer/Unit 后置 |
| D7 | 访问绑定 | 采集头绑 `track_in_tx_id`（本趟 TrackIn 履历 id） | 重进站须重采 |
| D8 | Spec 追溯 | 落点时固化 `spec_id`（及上下限快照列可选） | 规格事后改不影响历史 |
| D9 | 门禁默认 | `required && !clear` → **拒 TrackOut** | 防假过站；Hold 策略二期 |
| D10 | 未配站 | `required=false` → clear | 验收：行为不变 |
| D11 | 数据源 | 一期 `source=MANUAL`；预留 `AUTO` | Adapter 不改表模型 |
| D12 | 配置开关 | `mes.edc.gate-enabled`（默认 true） | 紧急时可关钩子，不删 Plan |

---

## 4. 领域模型

### 4.1 对象

| 对象 | 说明 |
|------|------|
| EdcParam | 量测特性：编码、名称、单位、值类型（NUMBER 一期） |
| EdcSpec | 某 Param 的规格版本：USL / LSL / target；可选 `product_code` |
| EdcPlan | 站采集计划：`step_id` + `required` + 启停 |
| EdcPlanItem | Plan 内要采的 Param 列表（及选用哪条 Spec） |
| EdcCollection | 一次采集头：Lot + 站上下文 + 本趟 `track_in_tx_id` + 总结果 |
| EdcCollectionItem | 点值：param、数值、相对 Spec 的 PASS/OOS |

### 4.2 Spec 状态

| 状态 | 说明 |
|------|------|
| `draft` | 可改 |
| `active` | PlanItem / 解析只认 active（同 param±product 至多一条 active） |
| `obsolete` | 升版后；历史 collection 仍持旧 `spec_id` |

### 4.3 Collection 总结果

| result | 规则 |
|--------|------|
| `PASS` | Plan 内全部必采项均有值且均 PASS |
| `FAIL` | 任一项 OOS 或必采项缺失（录入提交时也可直接 FAIL） |

门禁认：**本趟**存在 `result=PASS` 的 collection（见 §5）。

### 4.4 上下文解析（Context Resolution）

输入：`lotId` + `routeVersionId` + `sortNo` + `stepId` +（门禁时）当前 Lot 最近一次本站 `TRACK_IN` 的 `tx_id`

```
1. plan = findEnabledPlan(stepId)
2. if plan == null || !plan.required → required=false, clear=true
3. items = planItems(plan)
4. col = findLatestCollection(lotId, trackInTxId)  // 必须同 visit
5. if col == null || col.result != PASS → clear=false, reason=NO_DATA | OOS
6. else clear=true
```

**重采：** 同 `track_in_tx_id` 允许多次提交；门禁取**最新一条** PASS（或约定：最新一条必须 PASS，失败覆盖）。一期建议：**最新一条**决定 clear（无论 PASS/FAIL），避免旧 PASS 掩盖新 FAIL。

---

## 5. 门面契约

包：`com.mes.edc.facade.EdcFacade`（实现 `EdcFacadeImpl`）。

| 方法 | 消费方 | 说明 |
|------|--------|------|
| `assertClearToTrackOut(lotId, routeVersionId, sortNo, stepId)` | TrackOut | `required && !clear` 抛业务错 |
| `evaluateGate(...)` | Track context / 现场 | 返回 `EdcGateResult`，不抛 |
| `getActivePlan(stepId)` | Admin / 现场 | 计划摘要 |
| `getLatestCollection(lotId, trackInTxId)` | 现场 / 追溯 | 只读 |

```
EdcGateResult {
  boolean required;
  boolean clear;
  String reasonCode;   // NONE / NO_DATA / OOS / GATE_DISABLED
  String message;
  Long collectionId;   // 命中时
}
```

建议错误码：`EDC_BLOCK_TRACK_OUT`（与 T2-7 一致）。

**禁止：** Track / Lot / Hold / Dispatch 注入 EDC Mapper。

---

## 6. 与 Track 集成

顺序（落地 T2-7 时）：

```
TrackOut 既有校验（processing / Hold / ProcessTime…）
  → edcFacade.assertClearToTrackOut(...)
  → 通过则推进并写 TRACK_OUT
```

- 失败：整笔回滚；**无** TRACK_OUT 履历  
- context 透出：`edcRequired` / `edcClear` / `edcBlockReason`  
- 采集动作本身：**不是**新 Track 事务码；可写 `mes_tx_log` 扩展类型 `EDC_COLLECT`（可选，一期建议写，便于审计）

TrackIn 成功后：现场可知 `track_in_tx_id`，录入 API 必带该 id（或服务端按 Lot 当前 processing 自解析最近 TRACK_IN）。

---

## 7. 角色与权限

| 权限码 | 用途 |
|--------|------|
| `edc:view` | 特性/规格/计划/采集查询；菜单 |
| `edc:edit` | Param / Plan / PlanItem / Spec 草稿 |
| `edc:publish` | Spec 发布 |
| `edc:collect` | 提交采集（现场台） |

角色建议：admin / process_eng 全量；supervisor `view`；operator `view` + `collect`。  
权限 id：`253–256`（`migrate_edc.sql`）。

---

## 8. 接口（一期草案）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET/POST/PUT | `/edc/params`… | view/edit | 特性 |
| GET/POST/PUT | `/edc/specs`… | view/edit | 规格草稿 |
| POST | `/edc/specs/{id}/publish` | publish | 发布 |
| GET/POST/PUT | `/edc/plans`… | view/edit | 站计划 |
| PUT | `/edc/plans/{id}/items` | edit | 计划项 |
| POST | `/edc/collections` | collect | 提交采集（含 items）；同 visit 可重采 |
| GET | `/edc/collections` | view | 按 lot / step / trackInTxId / result 分页 |
| GET | `/edc/collections/latest` | view | 同 visit 最新一条（含点值）；无则 `data=null` |
| GET | `/edc/collections/{id}` | view | 详情 |
| GET | `/edc/gate?lotId=&stepId=` | view 或 track:view | 门禁预检（未建） |

统一 `{ code, msg, data }`；雪花 ID 前端禁止 `Number(id)`。

---

## 9. 页面

| 入口 | 说明 |
|------|------|
| Admin `/app/edc` | 特性 / 规格 / 站计划 三 Tab ✅ |
| 现场 TrackPage | 加工中且本站有启用计划：量测条 + 采集抽屉 ✅；拒出提示 ⏳ |
| Track context | 综合 `canTrackOut`（ProcessTime ∧ EDC）— EDC 段未接 |

---

## 10. 验收要点（最小集）

1. 无 Plan / `required=false`：TrackOut 与现网一致 ⏳（钩子未接，现网本就不挡）  
2. `required=true` 且无本趟采集：拒 Out，无 TRACK_OUT ⏳  
3. 本趟最新采集 FAIL（OOS）：拒 Out ⏳ **当前可完工**  
4. 本趟最新 PASS：可 Out ⏳  
5. 改 Spec 后，历史 collection 仍按落点时 `spec_id` 可追溯 ✅（落点有 `spec_id` + USL/LSL 快照）  
6. 业务模块零直表；只走 Facade ⏳（录入已走 EDC API；TrackOut 尚未走 Facade）  
7. Track 库无量测明细表 ✅  

---

## 11. 演进

| 阶段 | 能力 |
|------|------|
| 一期 | 手录 + Spec OOS + Facade +（紧随）T2-7 |
| 二期 | 拒出后可选 Auto-Hold；product 维 Spec；`EDC_COLLECT` 履历强化 |
| 三期 | Adapter `source=AUTO`；Wafer/Slot 采数 |
| 后置 | SPC 读点；独立 EDC 服务（契约不变） |

拆库条件：点量暴涨、团队拆分、或 SPC/FDC 独立部署——保持 Facade 与表前缀即可迁。

---

## 12. 关联

- `MES-EDC数据库设计.md`
- `MES-EDC一期功能清单.md`
- `MES-EDC与SPC范围说明.md`
- `MES-Track二期功能清单.md` §3.2 / T2-7
- `docs/架构/MES-实施进度与下一步.md`
- `docs/业务清单/MES-半导体业务清单.md` §9
