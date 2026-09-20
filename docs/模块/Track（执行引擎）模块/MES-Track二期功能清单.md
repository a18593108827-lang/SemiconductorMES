---
type: 功能清单
module: Track
status: done
slices: []
aligns: []
updated: 2026-08-12
---

# MES 执行引擎（Track）— 二期功能清单

> 前提：一期 Release / TrackIn / TrackOut / Hold / Rework / Skip / Off-Flow + Split / Merge / Scrap / Bonus 已落地；Dispatch Reserve 钩子、Queue Time 开窗结算已落地  
> 对齐：`MES-半导体业务清单.md` §3 · 大厂 Lot Tracking（SiView / Camstar·Opcenter / AMAT / CM）  
> 更新：2026-08-12

---

## 0. 目标

在**不拆掉「状态只由 Track 写」契约**的前提下，把 Track 从「站级过站引擎」升级为半导量产标配执行中枢：加工时长卡控、中止、片/Carrier 联动，并为 EDC / 切工艺 / 联机留口。

原则：

- Track = 唯一执行引擎；Lot / WIP / Dispatch **不得**另写当前站 / 加工态
- 新能力优先挂**已有事务钩子**或**新事务码**；禁止旁路改 `status` / `qty` / `current_`*
- 定义类约束进 Route 版本快照；在途只认 `route_version_id`
- 设备联机 / FDC 属 Adapter，Track 只记 eqp / 人 / 时 / recipe 引用

---



## 1. 范围总览


| 优先级    | 能力                            | Track 侧                                 | 依赖模块                |
| ------ | ----------------------------- | --------------------------------------- | ------------------- |
| P0     | Process Time ✅               | TrackIn 开计时；&lt;min 拒 Out；&gt;max Out+Hold | Route 快照字段          |
| P0     | Abort                         | processing→wait（同站）；清 eqp；写履历           | —                   |
| P0     | 片级 Split / Scrap ⏸            | 事务入参扩 wafer 列表；qty 与片数一致（**依赖未齐，暂缓**） | Lot `mes_lot_wafer` |
| P0     | Carrier TrackIn 校验 ⏸         | 可选强制已绑 FOUP；记 carrier_id（**依赖未齐，暂缓**） | Lot / Carrier       |
| P1     | Move 独立接口 ✅                   | wait 站间合法移站（不经加工）                       | Route next          |
| P1     | TrackOut EDC 门禁 ✅             | 钩子拒 Out；完工旁提示（无新按钮）                 | EDC                 |
| P1     | 并行站 / Batch ⏸                 | 同站多 Lot 共机；Batch 开/关（**依赖未齐，暂缓**）     | Eqp Chamber/Batch · Dispatch |
| P1     | context 增强 ✅ / Carrier ⏸     | 按钮与计时已交付；`carrier*` 随 T2-3（**不单开收口轮**） | 现场台 / Carrier      |
| P2     | 中途切 Route 版本 ⏸               | 切换点+新快照（**设计/审批未齐，暂缓**）              | Lot · 工艺变更审批        |
| P2     | Change Product ⏸               | 改产品身份专用事务（**设计/切版耦合未齐，暂缓**）         | Lot · 切 Route        |
| P2     | EAP / Adapter 联机 ⏸            | TrackIn 下发 / Out 上报（**Adapter 未建，暂缓**） | Equipment Adapter   |
| P2     | Store / Retrieve ⏸             | 入/出库位事务（**Carrier/Stocker 未齐，暂缓**）       | Carrier / Stocker   |
| 后置     | Ship / Receive / Send-ahead   | 出货、收料、先遣片                               | 厂型                  |
| 不做（二期） | 绕过 Track 改状态；Track 内做 FDC 实时流 | —                                       | 硬禁止                 |


**已落地、不纳入二期交付（前提能力）**


| 能力                                 | 状态  |
| ---------------------------------- | --- |
| Release / In / Out / Hold          | ✅   |
| Split / Merge / Scrap / Bonus      | ✅   |
| Rework / Skip / Off-Flow           | ✅   |
| Dispatch Reserve → TrackIn 校验消费    | ✅   |
| Queue Time 开窗 / 到期自动 Hold·Alarm / 清窗后解锁放行 | ✅   |


---



## 2. P0 功能明细



### 2.1 Process Time


| 项        | 说明                                                       |
| -------- | -------------------------------------------------------- |
| 定义       | Route 版本步骤：`min_process_min` / `max_process_min`（可空=不控）  |
| 开计时      | TrackIn 写 `process_started_at`                           |
| 结算       | TrackOut：elapsed 对照上下限；过短/过长按策略拒 Out 或 Alarm+允许          |
| 建议策略     | P0：低于 `min` **拒绝 Out**；超 `max` **允许 Out + 自动 Hold**（PROCESS_TIME_EXCEED）；Alarm 默认开 |
| 清窗       | Abort / Rework / 全批 Scrap 清计时                            |
| 与 Q-Time | Queue=站间等待；Process=站内加工；互不替代                             |
| 权限       | 沿用 `track:track-out`；强行放行属 P2 专码                         |
| 验收       | 快照约束；在途不随后续升版变；履历可查起止                                    |
| 设计       | `MES-ProcessTime接口设计.md`                                   |




### 2.2 Abort


| 项   | 说明                                                       |
| --- | -------------------------------------------------------- |
| 场景  | 加工中异常中止：未正常 TrackOut，回到本站 `wait`（**不进 Hold**）        |
| 前置  | `status=processing`；非 Hold（Hold 中禁 Abort）                      |
| 规则  | → `wait`；清 eqp；**不推进**下一站；清 Process Time                     |
| 原因  | 必填；P0 代码白名单 `TrackServiceImpl.ABORT_REASON_CODES`（非配置表）      |
| 禁止  | Abort 当 Skip；Abort 改 qty；Abort 跨站；Abort 当 Scrap/自动 Hold       |
| 权限  | `track:abort`（`migrate_track_abort.sql`；刷后重登）                 |
| 验收  | 履历 `ABORT`；可再 TrackIn；Reserve 防御释约；status≠held                 |
| 设计  | ✅ `MES-TrackAbort接口设计.md`                                         |
| 切片  | T2-2 ✅                                                               |




### 2.3 片级 Split / Scrap

> **状态：⏸ 暂缓** — 等 Lot `mes_lot_wafer` 落地后再做 T2-4。


| 项     | 说明                                                               |
| ----- | ---------------------------------------------------------------- |
| 前置    | Lot P1 `mes_lot_wafer` 已有                                        |
| Split | `POST /track/split` 增可选 `waferIds[]`；子 Lot 继承片成员；qty=片数          |
| Scrap | `POST /track/scrap` 增可选片列表；片 `scrap_flag`；Lot.qty / scrap_qty 同步 |
| 守恒    | Lot.qty == 未报废片数                                                 |
| 权限    | 仍 `track:split` / `track:scrap`                                  |
| 验收    | 无片表时行为与一期 Lot 级一致；有片时禁只改 qty 不改片                                 |
| 设计    | 扩展既有 Lot Split/Scrap 设计；Track 只扩 DTO/校验                          |




### 2.4 Carrier TrackIn 校验

> **状态：⏸ 暂缓** — 等 Carrier 台账 + Lot 绑定（Lot L2-7）落地后再做 T2-3。无 `carrier_id` 源时开闸无意义。

| 项   | 说明                                              |
| --- | ----------------------------------------------- |
| 前置  | Carrier 模块台账已有；Lot 已挂 `carrier_id` / 绑解 API（L2-7） |
| 模型  | 二期先 **一 Lot 一 Carrier**（SiView 一盒多 Lot 属后置）     |
| 本期范围 | **L1**：配置开关强制 TrackIn 时 `carrier_id` 非空；履历记 carrier_id |
| 不做（本期） | L2 现场读码比对、L3 SlotMap 一致性、L4 一盒多 Lot / E87 设备侧 |
| 校验  | 开关开：未绑 → 拒绝 In；开关关 = 与现行为一致；SlotMap 仅占位，有片表后再严 |
| 履历  | TrackIn `mes_tx_log` 记 carrier_id               |
| context | 同切片暴露 `carrierId` / `carrierRequired`（T2-5b）   |
| 解绑  | 绑/解在 Lot/Carrier 模块；**Track 只读校验，不写绑定**        |
| 权限  | 沿用 `track:track-in`                             |
| 验收  | 开关关=与现行为一致；开关开且未绑→拒绝 In                         |
| 设计  | 随 Carrier 模块；Track 钩子本节约束                       |
| 产品口径 | 对外称「TrackIn 载具闸 / prerequisite」；未到 L2–L3 前不宣称 Carrier Management / 对标 E87 |
| 业界对齐 | 大厂分层：物理 RFID(E99) → 设备 E87(ID+SlotMap) → MES 绑定 → MCS；本期只做 MES L1 |


---



## 3. P1 功能明细



### 3.1 Move 独立接口

> 设计：`MES-TrackMove接口设计.md`（T2-6）


| 项     | 说明                                                |
| ----- | ------------------------------------------------- |
| 语义    | `wait` → 合法下一站 `wait`（物流/虚拟站）；**不经** processing   |
| 校验    | 目标=`next_sort_no`（Special Move 属 P2）               |
| 与 Out | TrackOut 仍内含移站；Move 供「未加工只搬家」                     |
| 权限    | `track:move`（id=294；`migrate_track_move.sql`）    |
| 接口    | `POST /track/move` `{ lotId, toSortNo? }`         |
| 设计    | ✅ `MES-TrackMove接口设计.md`                          |
| 切片    | T2-6 ✅（后端 + MovePanel + 刷新交互）                     |




### 3.2 TrackOut EDC 门禁

> **状态：✅** — EDC-6 钩子 + EDC-7 现场提示。  
> 契约：`docs/模块/EDC（量测）模块/MES-EdcFacade接口设计.md`；`MES-EDC功能文档.md` §5–6  
> 禁止：Track 内自建假量测表冒充门禁。  
> 现场：仍是原「完工」按钮；`!edc.clear` 时灭 + 旁注原因；采合格后刷新再亮。

| 项 | 说明 |
| ---- | ---- |
| 形态 | **钩子**挂 TrackOut，不新开 tx_type；未过站则无 TRACK_OUT |
| 语义 | Plan.`required` 且本趟无 PASS 采集 / OOS → **拒 Out**（默认）；可选事后 Auto-Hold 二期 |
| Track | 只调 `edcFacade.assertClearToTrackOut(...)`；错误码 `EDC_BLOCK_TRACK_OUT` |
| 未配站 | `required=false` → 行为与现网一致 |
| context | 嵌套 `edc`（required / clear / reasonCode / message）；现场台完工旁提示 |
| 依赖 | EDC 主数据/录入/Spec；**不依赖 SPC** |
| 权限 | 沿用 `track:track-out`；强行放行属 P2 |
| 验收 | 未配站不变；不合格无 TRACK_OUT；Track 库无量测明细 |
| 开工顺序 | ① EDC 最小集 + Facade ✅ → ② 本钩子 ✅ → ③ 现场台录入 / 拒出提示 ✅；SPC 再后置 |




### 3.3 并行站 / Batch

> **状态：⏸ 暂缓** — 等 Equipment **Chamber / Batch 能力标签**（及可选 Dispatch 批约束）落地后再做 T2-8。  
> 禁止：无 Eqp 能力源时在 Track 内自建假 Batch 表冒充共机。  
> 关联：`MES-Equipment功能文档.md`（Chamber/Port 后置）· `MES-Equipment已完成功能.md`（Chamber 未勾）


| 项     | 说明                                           |
| ----- | -------------------------------------------- |
| 并行    | 同 step 多 eqp 可选；已有 Dispatch 候选，本项补「多腔/批处理」状态 |
| Batch | 多 Lot 同时 TrackIn 同机；开批/关批；Out 须整批或按策略拆     |
| Track | 开批绑成员 Lot；In/Out 校验同 Batch；**不写**设备能力主数据     |
| 不做（本期前） | 无能力标签的硬编码共机；Cascade 炉管专用策略；Chamber 级配方下载     |
| 权限    | 沿用 In/Out；开/关批可加 `track:batch`               |
| 依赖    | Eqp Chamber/Batch 能力标签；Dispatch 批约束可后置      |
| 验收    | 无标签标签站=与现网一致；有标签站：未开批禁多 Lot 同机 In；整批 Out 策略可测 |
| 设计    | 开做时再立 `MES-TrackBatch接口设计.md`                |
| 开工顺序  | ① Eqp Chamber/Batch 标签 → ② Track 开批/共机钩子 → ③ 现场台 Batch 入口；Dispatch 批约束可后置 |




### 3.4 context 增强

> **状态：主路径 ✅；Carrier 字段 ⏸** — `canAbort` / `processTime` / `canMove` 已随 T2-1、T2-2、T2-6 落地；`carrierId` / `carrierRequired` 等 T2-3（Lot L2-7）。  
> **产品口径**：context 是现场台控制面（显隐/提示），**不是**新事务；不单开「展示收口」切片，无明确现场吐槽不空转。  
> 禁止：无 Carrier 绑定时伪造 `carrierRequired` 假字段。


| 项     | 说明                                                               |
| ----- | ---------------------------------------------------------------- |
| 语义    | `GET /track/lots/{lotId}/context` 驱动按钮与提示；状态仍只由 Track 事务写       |
| 已交付 ✅ | `canAbort`；`canMove` / next*；`processTime`（已耗/上下限/可否 Out）        |
| UI ✅  | Abort 入口；Move 入口；ProcessTimeBanner；In/Out/Rework/Skip/分合报废等跟 can* |
| 待补 ⏸  | `carrierId` / `carrierRequired`（随 T2-3 TrackIn 载具闸同发）           |
| 不做（本期） | 单独 T2-5 大改版；无吐槽清单的纯文案/样式空转                                      |
| 权限    | 沿用 `track:view`；各动作仍各事务码                                         |
| 验收    | 无 Carrier 时行为与现网一致；有绑定时按钮/提示跟 `carrierRequired`                 |
| 切片    | 主路径已并入 T2-1/2/6；**T2-5a 关闭为已交付**；**T2-5b Carrier context → 随 T2-3** |


---



## 4. P2（可后再开）

### 4.1 中途切 Route 版本

> **状态：⏸ 暂缓** — P2；无接口设计；无**工艺变更审批**（现网仅有权限申请审批，不可冒充）。  
> 对齐 Lot L2-8；`route_version_id` 仍仅 Release 可写（见 `MES-Lot已放行属性约束设计.md`）。  
> 禁止：`PUT /lots` 改 `route_version_id`；无切换点规则时硬切快照。


| 项     | 说明                                                                 |
| ----- | ------------------------------------------------------------------ |
| 语义    | 在途 Lot 不重放行，从当前快照切到另一已发布 `route_version_id`；之后只认新快照                 |
| 切换点   | 指定从哪一站起走新路径；须定义新旧站对齐规则（开做时立设计）                                     |
| Track | 专用事务 `POST /track/change-route`；写新版本 + 履历；**禁**旁路改字段                 |
| 审批    | 须工艺变更审批最小集；**不等于** `perm:approve` 权限申请                              |
| 不做（本期） | 无审批硬切；跨 Route（非同 route 升版）未定义前不做；与 Special Move 混用                   |
| 权限    | `track:change-route`                                               |
| 依赖    | Lot 契约（禁 PUT 写版本）；工艺变更审批；切换点/站映射设计                                 |
| 验收    | 未开放时行为与现网一致；开放后：履历可追旧→新版本；在途自切换点起只认新快照；升版仍不影响未切换批                 |
| 设计    | 开做时再立 `MES-TrackChangeRoute接口设计.md`                                |
| 开工顺序  | ① 切换点规则设计 → ② 工艺变更审批最小集 → ③ Track 事务 + 履历 → ④ 现场台/Lot 入口；与 Lot L2-8 同发 |


### 4.2 Change Product

> **状态：⏸ 暂缓** — P2；无接口设计；常与 ChangeProcess（中途切 Route）耦合，切版本身亦暂缓。  
> 对齐：`MES-Lot已放行属性约束设计.md`（已放行禁 PUT 改 `product_code` 已落地）。  
> 禁止：已放行用 `PUT /lots` 改产品；无工艺约束时只改字符串冒充换型。


| 项     | 说明                                                                 |
| ----- | ------------------------------------------------------------------ |
| 语义    | 在途 Lot 改产品身份（`product_code`）；不重放行；须专用事务写履历                         |
| Track | 拟 `POST /track/change-product`（或等价事务码）；**禁**旁路 / PUT                 |
| 与切版   | 大厂 ChangeProduct / ChangeProcess 同级；换产品常须同步切 Route/版本（规则开做时定）         |
| 不做（本期） | 无设计硬改码；无 Product 主数据时假装完整换型；与 Hot/白名单 PUT 混用                         |
| 权限    | 开做时定（建议独立码，勿复用 `lot:edit`）                                          |
| 依赖    | Lot 已放行约束；切 Route 规则（若换型改路径）；Product 主数据可后置（现仍字符串）                   |
| 验收    | 未开放：已放行 PUT 改产品仍拒；`created` 仍可改。开放后：履历可追旧→新产品；相关合批等同产品约束仍成立           |
| 设计    | 开做时再立 `MES-TrackChangeProduct接口设计.md`                              |
| 开工顺序  | ① 换型是否强制连带切 Route 定规 → ②（若要）切版就绪 → ③ Change Product 事务 → ④ 入口；Product CRUD 可后置 |


### 4.3 EAP / Adapter 联机

> **状态：⏸ 暂缓** — P2；Equipment Adapter / SECS·GEM 未建；Track **禁止**内嵌设备协议。  
> 对齐：`半导MES架构设计.md`（协议外置）· `MES-Equipment已完成功能.md`（Adapter 后置）。  
> 禁止：在 Track/业务服务内直连 SECS；用假下发冒充联机；Track 内做 FDC 实时流。


| 项     | 说明                                                                  |
| ----- | ------------------------------------------------------------------- |
| 语义    | MES↔设备自动化：In 触发下发（如 recipe download）；Out/事件由 Adapter 上报后再记账          |
| Track | 只调 Adapter Facade / 收已校验事件；写 eqp·人·时·recipe **引用**；**不写**协议细节         |
| Adapter | SECS/GEM 桥、状态回写、报警；独立进程/服务（架构阶段二）                                    |
| 不做（本期） | 无 Adapter 硬联机；业务库塞连接串当「已对接」；Abort/Move 先调设备再改账（设备侧后置）                |
| 依赖    | Equipment Adapter 最小集；Recipe Download 能力可同发或后置                       |
| 验收    | 未开放：手选 eqp TrackIn 与现网一致。开放后：无 Adapter 可达则拒下发；事件乱序不可旁路改 status         |
| 设计    | 开做时再立 Adapter 接口 + Track 钩子说明（可挂 `MES-TrackEapHook接口设计.md`）           |
| 开工顺序  | ① Adapter 最小集（连通/事件）→ ② TrackIn 下发钩子 → ③ TrackOut/完工事件对账 → ④ 现场台联机态提示 |


### 4.4 Store / Retrieve

> **状态：⏸ 暂缓** — P2；无 Carrier 台账 / Stocker（及 MCS）位置源时开闸无意义。  
> 对齐：`MES-TrackMove接口设计.md`（物理 Stocker/Port **不进** Move）。  
> 禁止：用 Move 冒充入出库；无位置主数据时假写库位；本事务推进 `current_sort_no`。


| 项     | 说明                                                              |
| ----- | --------------------------------------------------------------- |
| 语义    | Carrier 相对 Stocker：**Store** 入位、**Retrieve** 取回；记物理位置，**不**推进工艺站 |
| Track | 专用事务写履历/位置引用；Lot 站位与 processing 态不变（除非厂规另定，开做时写死）                  |
| 与 Move | Move = wait 站间搬家；Store/Retrieve = 库存位；正交，禁止混用                    |
| 不做（本期） | 无 Carrier/Stocker 硬做；AMHS 调度本体；一盒多 Lot 入出库                       |
| 依赖    | Carrier 台账 + 绑定；Stocker/Port 位置；MCS 可后置                          |
| 验收    | 未开放：行为与现网一致。开放后：Store/Retrieve 不改工艺站号；位置可追；未绑 Carrier 拒事务         |
| 设计    | 开做时再立 `MES-TrackStoreRetrieve接口设计.md`                            |
| 开工顺序  | ① Carrier + Stocker 位置 → ② Store/Retrieve 事务 → ③（可选）MCS 事件驱动；与 T2-3 可同阶段但本项更后 |


### 4.5 其余 P2


| 能力                      | 说明                                                  |
| ----------------------- | --------------------------------------------------- |
| Special Move-Next       | 非默认下一站的授权移站                                         |
| Ship / Receive          | 出货/来料事务                                             |
| Unscrap / 数量回滚          | 强审批；与 Lot P2 对齐                                     |
| Send-ahead / Experiment | 先遣片、工程分批独立路径                                        |
| 一盒多 Lot                 | Carrier 内多 Lot；TrackIn 校验扩容                         |


---



## 5. 数据模型增量（草案）

```
mes_step / mes_route_step
  -- ✅ min_process_min / max_process_min

mes_lot（运行态，仍仅 Track 写）
  -- ✅ process_started_at
  -- 已有：carrier_id?（Lot P1）；qtime_*（已落地）

mes_hold_reason
  -- ✅ 8008 PROCESS_TIME_EXCEED

mes_tx_log
  -- tx_type 增：ABORT（T2-2）✅；MOVE（T2-6）✅
  -- TRACK_IN/OUT ext：processStartedAt / processElapsedMin / processTimeExceededMax
  -- MOVE ext：moveKind=NEXT / fromSortNo / toSortNo / fromStepId / toStepId
```

约束：

- Process / Abort / 片级变更 **禁止** 非 Track 路径写入
- `scrapped` / `merged` / `completed` 不可 Abort / In / Move

---



## 6. 接口增量（草案）


| 方法   | 路径                            | 权限                           | 说明                                 |
| ---- | ----------------------------- | ---------------------------- | ---------------------------------- |
| POST | `/track/abort`                | `track:abort`                | 加工中止                               |
| GET  | `/track/abort/reason-codes`   | `track:abort` / `track:view` | 中止原因                               |
| POST | `/track/move`                 | `track:move`                 | P1 独立移站                            |
| POST | `/track/change-route`         | `track:change-route`         | P2 ⏸ 暂缓（设计/审批未齐）                  |
| POST | `/track/split`                | `track:split`                | 扩 waferIds?                        |
| POST | `/track/scrap`                | `track:scrap`                | 扩 waferIds?                        |
| GET  | `/track/lots/{lotId}/context` | `track:view`                 | ✅ canAbort/processTime/canMove；carrier* ⏸ 随 T2-3 |


权限种子增量：


| 码                    | 说明          |
| -------------------- | ----------- |
| `track:abort`        | 加工中止        |
| `track:move`         | 独立移站（P1 启用） |
| `track:change-route` | 中途切版本（P2 ⏸） |
| `track:batch`        | 可选；批处理开/关   |


---



## 7. 前端增量


| 页          | 改动                                               |
| ---------- | ------------------------------------------------ |
| TrackPage  | Process Time Banner ✅；Abort ✅；Move ✅；Carrier 待 T2-3 |
| TrackPage  | 片级 Split/Scrap 选片（Lot 片列表就绪后）                    |
| TrackPage  | EDC 录入 ✅；拒出提示 ✅（原完工按钮）；Batch 开/关批 ⏸（随 T2-8） |
| TrackPage  | 中途切 Route ⏸（随 T2-9 / 设计+审批就绪）                  |
| TrackPage  | Change Product ⏸（随设计 + 切版规则；禁 PUT 已落地）           |
| TrackPage  | EAP 联机态提示 ⏸（随 Adapter 最小集）                        |
| TrackPage  | Store / Retrieve ⏸（随 Carrier/Stocker）               |
| context 驱动 | ✅ 跟 `canAbort` / `canMove` / processTime 等；`carrierRequired` ⏸ 随 T2-3 |


---



## 8. 实施切片（建议顺序）


| 切片   | 交付                                          | 估时参考 |
| ---- | ------------------------------------------- | ---- |
| T2-1 | Process Time 字段 + In 开计时 + Out 校验 + context | ✅    |
| T2-2 | Abort 事务 + 原因码 + 释约 + 现场台                   | ✅    |
| T2-3 | Carrier TrackIn 校验开关 + context carrier*（依赖 Carrier/Lot 绑 L2-7） | ⏸ 暂缓 |
| T2-4 | 片级 Split/Scrap 扩参（依赖 `mes_lot_wafer`）       | ⏸ 暂缓 |
| T2-5 | context：主路径（Abort/计时/Move）已并入 T2-1/2/6；Carrier 字段随 T2-3 | ✅ 主路径 / ⏸ Carrier |
| T2-6 | Move 独立接口                                   | ✅ P1 |
| T2-7 | TrackOut EDC 门禁                             | ✅ 钩子 + 现场提示 |
| T2-8 | 并行 / Batch（依赖 Eqp Chamber/Batch 能力标签）       | ⏸ 暂缓 |
| T2-9 | 切 Route / Change Product / EAP / Store（均 ⏸）…     | ⏳ P2（多项暂缓） |


---



## 9. 验收要点

1. 任意站位 / 加工态 / qty 变更仍可在 `mes_tx_log`（及 genealogy）追溯
2. Process Time 只认放行快照；升版不影响在途
3. Abort 后同站可再 In；未推进站号；Reserve 已释
4. 片级事务后 `qty == 未报废片数`
5. Carrier：L2-7 就绪后，开关开时未绑不可 TrackIn；本期不做现场 ID / SlotMap；context 同时暴露 `carrierId` / `carrierRequired`
6. Hold 中默认不可 Abort / Move / 片级 Split·Scrap（与现事务一致）
7. 未开放能力无旁路接口可改状态
8. EDC：模块就绪后，要求 EDC 的站不合格不可 TrackOut；未配站行为不变
9. Batch：Eqp 能力标签就绪后，有标签站须开批方可多 Lot 同机 In；无标签站行为不变
10. context：现场台按钮/计时已跟 canAbort/canMove/processTime；无 Carrier 源时不暴露假 carrier 字段
11. 切 Route：未开放时 `route_version_id` 仅 Release 可写；开放后须履历可追且自切换点起只认新快照
12. Change Product：未开放时已放行禁 PUT 改产品；开放后须专用事务+履历，禁止只改字符串无约束
13. EAP：未开放时手选机台过站不变；开放后协议只经 Adapter，Track 不直连设备、不做 FDC 流
14. Store/Retrieve：未开放无库位事务；开放后不推进工艺站号，且不与 Move 混用

---



## 10. 关联

- `MES-Track功能文档.md`
- `MES-Track已完成功能.md`
- `MES-Track数据库设计.md`
- `MES-ProcessTime接口设计.md`
- `MES-TrackAbort接口设计.md`
- `MES-TrackMove接口设计.md`（P1 独立移站；T2-6）
- `docs/模块/EDC（量测）模块/MES-EdcFacade接口设计.md`（T2-7 契约；EDC-4/6/7 ✅）
- `docs/模块/EDC（量测）模块/MES-EDC功能文档.md`（T2-7 背景 §5–6）
- `docs/模块/EDC（量测）模块/MES-EDC一期功能清单.md`（EDC-6/7）
- `docs/模块/EDC（量测）模块/MES-EDC与SPC范围说明.md`（SPC 可后置，不挡门禁）
- `MES-QueueTime接口设计.md`（站间等待；与 Process Time 正交）
- `docs/模块/Dispatch（派工）模块/MES-Dispatch功能文档.md`（Reserve 钩子）
- `docs/模块/Equipment（设备）模块/MES-Equipment功能文档.md`（Chamber/Port；T2-8 依赖）
- `docs/模块/Equipment（设备）模块/MES-Equipment已完成功能.md`（Chamber 未做）
- `docs/模块/Lot（批次）模块/MES-Lot二期功能清单.md`（片 / Carrier / 切版本）
- `docs/业务清单/MES-半导体业务清单.md` §3、§9
- `docs/架构/半导MES架构设计.md` §4.3、§5.4
- SPC 详细接口设计：开做时再立 `MES-SPC接口设计.md`
- 待建：`MES-TrackBatch接口设计.md`（T2-8；⏸ 依赖 Eqp 能力标签）
- 待建：`MES-TrackChangeRoute接口设计.md`（T2-9 切版；⏸ 设计/工艺变更审批未齐）
- 待建：`MES-TrackChangeProduct接口设计.md`（T2-9 换型；⏸ 设计/与切版耦合未齐）
- 待建：Equipment Adapter + `MES-TrackEapHook接口设计.md`（T2-9 联机；⏸ Adapter 未建）
- 待建：`MES-TrackStoreRetrieve接口设计.md`（T2-9；⏸ 依赖 Carrier/Stocker）
- `MES-TrackMove接口设计.md`（物理 Stocker/Port 不进 Move）
- `docs/模块/Lot（批次）模块/MES-Lot已放行属性约束设计.md`（`route_version_id` / `product_code` 禁 PUT）
- `docs/模块/Equipment（设备）模块/MES-Equipment已完成功能.md`（Adapter / SECS 后置）
- `docs/架构/半导MES架构设计.md`（设备协议外置）