# Move — 接口设计（架构）

> 范围：`wait` 站间合法移站（不经加工）+ 履历 + context/`canMove` + 现场台入口  
> 原则：**新开 Track 事务**；状态仍只由 Track 写；**不经 processing**、**不占机**、**不改 qty**、**不当 Skip**  
> 产品口径：P1 **逻辑移站**（物流/虚拟站 / 未加工只搬家）；**不是** TrackOut 分支；**不是** Special 跳站；**不是** MCS 物理到达  
> 对齐：`MES-Track二期功能清单.md` §3.1 / T2-6；`docs/业务清单/MES-半导体业务清单.md` §3 Move/Arrive  
> 前提：Release / In / Out、Route 版本快照 next、Hold.assertNoActive、`mes_tx_log` 已落地  
> 状态：**T2-6 已落地（后端 + 现场台 MovePanel）**  
> 更新：2026-08-12  
>
> **产品速记**  
> - 落点：`wait(本站) → wait(下一站)`；**不进** processing；末站无 next → 拒 Move（完工仍走 TrackOut）  
> - 目标：P1 **仅**快照 `next_sort_no`；`toSortNo` 省略=默认 next；显式传入须等于 next  
> - 权限：`track:move`（`migrate_track_move.sql` / 权限 id=294；刷后须重新登录）  
> - 交互：成功后先刷新 context/履历/派工再提示；站卡换站闪一下（status 仍为 wait，靠站号变化感知）  

---

## 0. 业界落点（为何独立）

半导执行引擎普遍把「工艺位置推进」与「站内加工」拆开：

| 产品 | 对应能力 |
|------|----------|
| Siemens Opcenter EX Semi | WIP 主状态：`MoveIn → TrackIn → TrackOut → MoveOut`；另有 `MoveStd` / `MoveNonStd` |
| Critical Manufacturing | 事务并列：`Track-In/Out` + **`Move-Next`** + **`Special Move-Next`** |
| Applied SmartFactory | 部分 step 无设备；推进不必走加工 |
| IBM SiView 系 | 逻辑站位与 MCS Arrive/物理位分离 |

本切片对标 **Move-Next / MoveStd（默认下一站）**。  
**Special Move-Next / MoveNonStd** → P2（见 §10）。  
物理 Stocker/Port → Store/Retrieve 或 Carrier/MCS，**不进本接口**。

一期用 TrackOut「内含移站」覆盖加工站主路径；P1 补独立 Move，避免：

- 虚拟站 / 物流站 / Bank 站被迫假 In/Out
- AMHS/EAP「只到站未开工」无法写账
- 审计上无法区分「加工过站」与「只搬家」

---

## 1. 边界

```
Track     = 唯一写路径：Move 改 current_sort_no / step；写履历；WIP sync
Route     = 只认 Lot.route_version_id 快照的 next；禁读直播 mes_step 跳站
Hold      = active Hold → 禁 Move（与 In/Out/Abort 一致）
Dispatch  = 不消费、不释约（Lot 仍 wait，未占机）
ProcessT  = 不开关表（非 processing；无 process_started_at 变更）
QueueT    = 与 TrackOut 推进共用 `openAfterTrackOut`（结/开站间窗）
Adapter   = 不调设备；物理 Arrive 属 MCS/EAP（后置）
Lot/片    = 不改 qty；不做片级
FutureH   = 到新站后 `tryActivate(PRE)`（与 Skip 一致）
```

**本切片做什么**

- `POST /track/move`：`wait` → 合法下一站 `wait`
- 目标站 = `routeEdgeResolver.resolveTrackOut(..., null)` 的 next；可选 `toSortNo` 须等于 next
- `mes_tx_log.tx_type = MOVE`；ext：`moveKind=NEXT` / from·to
- context：`canMove` / `nextSortNo` / `nextStepName`
- 现场台 `MovePanel` + 履历 MOVE
- 权限 `track:move`（种子 294）

**本切片不做什么**

- Special Move（非默认下一站）
- MoveIn/MoveOut 四态拆分（Opcenter 细粒度；本系统合并为单次 Move 推进）
- TrackOut 内含移站的改写（Out 行为保持）
- Store / Retrieve / MCS 物理位
- Move 当 Skip / Rework / Change Route
- Move 进 `completed`（末站完工仍只允许 TrackOut）
- processing 上 Move（须先 Abort 或 Out）

**禁止**

- 非 Track 路径改 `current_sort_no` / `current_step_id` 冒充移站
- Move 写 `processing` / 写 `current_eqp_id` / 写 Process Time
- Hold 中 Move
- `scrapped` / `merged` / `completed` Move
- 目标 ≠ next（P1）
- Move 改 qty / product / route_version_id

**与兄弟能力分工**

| | TrackOut | Move | Skip | Rework | Abort |
|--|--|--|--|--|--|
| 问什么 | 本站加工**完成**否 | 未加工能否**只搬家** | 是否授权跳过 | 是否回流 | 加工**中止**否 |
| 前置 | processing | **wait** | wait | wait/processing | processing |
| 站号 | 推进或完工 | **推进 next** | 跳过 | 跳回 | **不变** |
| 经加工 | 是 | **否** | 否 | 视路径 | 中止 |
| qty | 不变 | **不变** | 不变 | 不变 | 不变 |
| 履历 | TRACK_OUT | **MOVE** | SKIP | REWORK | ABORT |

**状态机**：无新 `Lot.status`。仍 `wait` / `processing` / `held`。

```
wait ──TrackIn──► processing
wait ──Move─────► wait(下一站)     ← 本切片（仅 next）
                     └ 不经 processing
                     └ 不占 eqp
                     └ 不写 process_started_at

processing ──TrackOut──► wait(下一站) | completed   # 仍内含移站
processing ──Abort─────► wait(同站)

末站 wait：无 next → Move 拒绝；完工走 TrackOut（须先 In）或不支持「空站完工」
```

**与 TrackOut 内含移站的关系**

| 路径 | 何时用 |
|------|--------|
| TrackOut | 本站已加工完成；账上从 processing 离开并推进 |
| Move | 本站**不加工**或已在 wait 只需进下一站 |

二者推进规则同源（快照 next）；**权限与履历分离**，禁止用 Out 冒充未加工搬家，也禁止用 Move 冒充完工。

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 事务形态 | **独立** `POST /track/move` + `tx_type=MOVE` | 对齐 CM Move-Next；与 Out 权限/审计隔离 |
| D2 | 粒度 | 整批 Top-Lot | 与现 Track 事务一致；片级后置 |
| D3 | 前置状态 | 仅 `wait` | processing 须 Out/Abort；禁止半加工搬家 |
| D4 | 目标 | P1 **仅 next**；`toSortNo?` 校验相等 | 防跳站；Special → P2 |
| D5 | 完工 | Move **不**置 `completed` | 末站无加工语义时另议；P1 拒 Move |
| D6 | 设备/计时 | 不写 eqp；不动 Process Time | 未加工 |
| D7 | Hold | `assertNoActive` | 与现事务一致 |
| D8 | Queue Time | **与 TrackOut 推进共用结/开窗钩子**（若有） | 逻辑已换站；站间窗应跟站走；无钩子则本切片不新开 Q-Time 逻辑 |
| D9 | Dispatch | **无操作** | 未占机、未 consume |
| D10 | WIP | `syncFromLot` | 投影站号与 Lot 一致 |
| D11 | 乐观锁 | Wrapper + `version` | 与 Abort/Out 一致 |
| D12 | fail 策略 | 校验失败整笔回滚；不写残履历 | 与现 Track 一致 |
| D13 | 命名 | 对外 Move / `MOVE`；对标 Move-Next | 禁止别名 `arrive` 作本接口（Arrive 留给物理到达） |
| D14 | Out 内含移站 | **保留** | 加工站主路径不强迫先 Out 再 Move |

**不采用的方案**

| 方案 | 否决理由 |
|------|----------|
| 仅 TrackOut，虚拟站假 In/Out | 污染加工时长/EDC/设备账；履历失真 |
| Move = TrackOut 可选 flag | 权限与状态机缠死；processing 才能 Out |
| P1 直接做 Special Move | 跳站面过大；须审批/白名单，属 P2 |
| 引入 MoveIn/MoveOut 两事务 | 过度拆分；本系统 wait 已覆盖「已到站未开工」 |

---

## 3. 数据契约

### 3.1 Lot 运行态（仅 Track 写）

| 字段 | Move 行为 |
|------|-----------|
| status | 保持 `wait` |
| current_sort_no | → 目标站 `sort_no`（= next） |
| current_step_id | → 目标站 step_id |
| current_eqp_id | **必须已为 null**；Move 不写 |
| process_started_at | **不变**（应为 null） |
| qty / route_version_id / product… | **不变** |
| off_flow / anchor_* | P1：Off-Flow 中 **禁 Move**（与 Skip 策略对齐，避免旁路乱跳；后置可放宽） |

乐观锁：`version` 条件更新；`current_sort_no` / `current_step_id` 同笔写入。成功后 `selectById` 再 sync WIP。

### 3.2 下一站解析（真相源）

```
next = resolveNext(lot.route_version_id, lot.current_sort_no)
```

| 结果 | 行为 |
|------|------|
| 存在 next | 允许 Move；目标 = next |
| 无 next（末站） | 拒绝 `MOVE_NO_NEXT` |
| `toSortNo` 有值且 ≠ next.sort_no | 拒绝 `MOVE_TARGET_NOT_NEXT` |

在途只认放行快照；升版不影响已放行 Lot。

### 3.3 事务履历 `mes_tx_log`

| 项 | 约定 |
|----|------|
| tx_type | `MOVE` |
| from_status / to_status | `wait` → `wait` |
| from_sort_no / to_sort_no | 本站 → 目标站 |
| step_id | **目标站** step（或约定写 from；须在 ext 双侧可追溯——**采用 from=原站、ext 含 toStepId**） |
| eqp_id | null |
| remark | 可选 |
| ext_json | 见下 |

```json
{
  "fromSortNo": 100,
  "toSortNo": 110,
  "fromStepId": 501,
  "toStepId": 502,
  "moveKind": "NEXT"
}
```

`moveKind`：P1 固定 `NEXT`；P2 Special 用 `SPECIAL`。

### 3.4 配置项（可选）

```yaml
mes:
  move:
    enabled: true
    # P2：允许 Special；默认 false
    # allow-special: false
```

P1 不读 special；预留键名。

---

## 4. 执行流程

### 4.1 Move 主路径

同事务、顺序固定：

```
1. requireExecutableLot(lotId)
2. holdService.assertNoActive(lotId)
3. status == wait，否则拒
4. 非 Off-Flow（P1）
5. current_sort_no / route_version_id 非空
6. current_eqp_id 必须 null；process_started_at 必须 null（防御脏数据）
7. resolveNext；无 next → 拒
8. 若 toSortNo 非空：须 == next.sort_no，否则拒
9. 快照 fromSort / fromStep
10. LambdaUpdateWrapper（带 version）：
      SET current_sort_no=next, current_step_id=next.stepId, version+1
      （status 保持 wait）
11. selectById 断言站号已变；刷新内存 Lot
12. Q-Time 钩子（若 TrackOut 推进已有：同等调用结旧开新）
13. wipProjectionService.syncFromLot
14. writeTxLog(MOVE, …, ext≤512)
15. return toTxnVo(lot, MOVE, false)
```

**不做**：改 status 为 processing/completed、占机、开 Process Time、Reserve、Hold.create、Recipe 校验。

### 4.2 错误码

| code | 场景 |
|------|------|
| `MOVE_NOT_WAIT` | 非 wait |
| `MOVE_NO_NEXT` | 末站无下一站 |
| `MOVE_TARGET_NOT_NEXT` | toSortNo ≠ next |
| `MOVE_DIRTY_EQP` | current_eqp_id 非空 |
| `MOVE_DIRTY_PROCESS_TIME` | process_started_at 非空 |
| `MOVE_OFF_FLOW` | Off-Flow 中（P1） |
| （Hold 既有） | active Hold |

前缀 `MOVE_`，与 `ABORT_` / `PROCESS_TIME_*` 并列。

### 4.3 context 增量

`GET /track/lots/{lotId}/context`

```json
{
  "canMove": true,
  "nextSortNo": 110,
  "nextStepName": "CLEAN-01"
}
```

`canMove` =  
`status==wait`  
∧ 无 active Hold  
∧ 非 Off-Flow（P1）  
∧ 存在 next  
∧ `current_eqp_id==null`  
∧ `StpUtil.hasPermission("track:move")`  
∧ Lot 可执行（非 scrapped/merged/completed）

前端：仅 `canMove===true` 显示 Move；默认目标展示 `nextSortNo`，不提供任意站下拉（P1）。

---

## 5. 接口一览

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/track/move` | `track:move` | 独立移站 |
| GET | `/track/lots/{lotId}/context` | `track:view` | 增 `canMove` / next 提示 |

### 5.1 请求

`POST /track/move`

```json
{
  "lotId": 1001,
  "toSortNo": 110,
  "remark": "物流进清洗区"
}
```

| 字段 | 约束 |
|------|------|
| lotId | 必填 |
| toSortNo | 可选；有则必须 = next |
| remark | 可选 |

### 5.2 响应

复用 `TrackTxnResultVO`（`txType=MOVE`，`completed=false`）。

### 5.3 权限种子

| 码 | 说明 |
|----|------|
| `track:move` | 独立移站 |

DDL：`migrate_track_move.sql`（权限 id=**294**；角色 1/2/3/4）；`schema.sql` 已对齐。  
**改权限后重新登录**。

---

## 6. 前端

| 页 | 改动 | 状态 |
|----|------|------|
| TrackPage | accent「移站」按钮（`canMove`）；与 Abort 琥珀区分 | ✅ |
| MovePanel | 本站→下一站路径卡；可选 remark；GSAP 入场 | ✅ `web/src/components/track/MovePanel.tsx` |
| 履历侧栏 | `MOVE` + ArrowRightLeft；ext `Sfrom→Sto` / `NEXT` | ✅ |
| 刷新交互 | 成功后 `loadLotById(..., { keepFeedback: true })` 再提示；派工依赖 `currentSortNo`；站卡换站闪框 | ✅ |

文案：**移站 · 不经加工**；禁止写「完工 / 过站加工 / 跳站」。

**交互注意（已踩坑）**

- Move 后 status **仍是 wait**，不能靠状态 pill 感知；必须刷新站号 + 成功条  
- 派工 `useEffect` 须依赖 `currentSortNo`，否则机台候选停在旧站  
- 勿先 `flashFeedback` 再 `loadLotById`（后者默认清 feedback）

---

## 7. 实施切片

| 切片 | 交付 | 状态 |
|------|------|------|
| MV-1 | 常量 `TX_MOVE`；DTO；权限种子 | ✅ |
| MV-2 | `TrackService.move` + next 解析 + WIP + tx_log | ✅ |
| MV-3 | Q-Time `openAfterTrackOut`；Future Hold PRE | ✅ |
| MV-4 | context `canMove` / next*；Controller | ✅ |
| MV-5 | TrackPage MovePanel + 履历 + 刷新交互 | ✅ |

挂 **T2-6** ✅。

---

## 8. 验收

1. `wait` Move → 下一站 `wait`；`current_sort_no` / `current_step_id` 更新  
2. status 仍为 `wait`；`current_eqp_id` / `process_started_at` 仍空  
3. qty / route_version_id 不变  
4. 履历 `MOVE`；ext 含 from/to；`moveKind=NEXT`  
5. 可再 TrackIn（目标站）；派工候选随新站刷新  
6. Hold 中 Move 失败  
7. `processing` / `completed` / `scrapped` Move 失败  
8. 末站无 next → 失败  
9. `toSortNo` ≠ next → 失败  
10. TrackOut 行为不变（仍内含移站）  
11. 无非 Track 旁路可写同等站号变更  
12. 成功后现场台站卡/成功条可见；不因 reload 吞掉提示  

---

## 9. 代码落点

| 层 | 路径 |
|----|------|
| API | `TrackController`：`POST /move` |
| 服务 | `TrackServiceImpl.move`；常量 `TX_MOVE` / `ERR_MOVE_*` |
| Route | `routeEdgeResolver.resolveTrackOut(version, current, null)` |
| Q-Time | `queueTimeSupport.openAfterTrackOut` |
| FutureH | `futureHoldService.tryActivate(..., PRE)` |
| WIP | `WipProjectionServiceImpl.syncFromLot` |
| DTO/VO | `TrackMoveDTO`；`TrackContextVO.canMove` / `nextSortNo` / `nextStepName` |
| 权限 | `migrate_track_move.sql`；`schema.sql` id=294 |
| 前端 | `MovePanel.tsx`；`TrackPage.tsx`；`api/track.ts` `trackMoveApi` |

人话摘要：

- 人/物流把批挪到下一站，但本站没干活 → 记 MOVE，不要假完工  
- 只能去工艺规定的下一站；乱跳是 Special（以后再说）  
- 加工完离开仍用 TrackOut  
- 挪完还是 wait，看站号和成功条，别指望状态 pill 变色  

---

## 10. 后置（非本切片）

| 项 | 说明 |
|----|------|
| Special Move | 非 next；白名单 + 强权限 `track:move-special` + 审批；`moveKind=SPECIAL` |
| MoveIn/MoveOut 细拆 | 仅当要与 Opcenter 四态或外部系统逐态对账时再拆 |
| Arrive（物理） | Carrier/MCS 位置；与逻辑 Move 事件关联，不合并接口 |
| Off-Flow 上 Move | 明确旁路内允许的 next 规则后放宽 |
| EAP 触发 Move | 设备/搬送事件驱动调本接口 |
| 虚拟站自动 Move | 配置「免人工」站：入站后系统自动 Move（仍走本事务） |

---

## 11. FAQ

| 问 | 答 |
|----|----|
| 和 TrackOut 有何不同？ | Out=加工完成并推进；Move=未加工只推进。 |
| 为何末站不能 Move 完工？ | `completed` 属完工语义；P1 避免用搬家冒充结批。 |
| 能否指定任意站？ | P1 不能；P2 Special。 |
| 按钮灰了？ | 需 `wait` + 有 next + 无 Hold + 非 Off-Flow + `track:move`（刷权限后重登）。 |
| 加工中能否 Move？ | 不能；先 Abort 回 wait 或 Out。 |
| 点了成功但像没变？ | status 本来就还是 wait；看当前站号/下一站/成功条；派工应跟新站。 |

---

## 12. 关联

- `MES-Track二期功能清单.md` §3.1 / T2-6 / §9.6  
- `MES-Track功能文档.md`  
- `MES-Track已完成功能.md`  
- `MES-Track数据库设计.md`  
- `MES-TrackAbort接口设计.md`（状态机兄弟）  
- `MES-ProcessTime接口设计.md` / `MES-QueueTime接口设计.md`  
- `docs/业务清单/MES-半导体业务清单.md` §3  
- 业界：CM Move-Next / Special Move-Next；Opcenter MoveIn·MoveOut / MoveStd·MoveNonStd  
