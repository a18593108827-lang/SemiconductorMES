---
type: 功能文档
module: Track
status: done
slices: []
aligns: []
updated: 2026-08-10
---

# MES 执行引擎（Track）功能文档

> 定位：工艺「执行」中枢——一切改变 Lot **当前位置 / 加工态** 的动作均为 Transaction  
> 对齐：`docs/架构/半导MES架构设计.md` §4.3、§5.4；业界 Lot Tracking（AMAT 等）  
> 一期状态：**一期已落地**；**Split / Merge / Scrap / Bonus 已闭环**（Rework/Skip/Off-Flow 亦已落地）  
> 更新：2026-08-10  
> 查验：`MES-Track已完成功能.md`

---

## 1. 目标（全套方向）

- Track = **唯一执行引擎**；Lot/WIP **不得**各自维护一套「当前站」真相
- 统一事务管道：锁 Lot → 校验 → 改状态 → 写履历 → 发事件
- 按 Lot 上 `route_version_id` 快照推进，防跳站；在途不受 Route 新发布影响
- 事务字典一次定好（可分期实现），避免后期硬编码三个接口难扩展

---

## 2. 边界

```
Lot     = 批次主数据 + 快照指针（route_version_id）+ 被 Track 写入的运行态字段
Route   = 工艺定义（版本步骤）
Track   = 事务执行（Release / TrackIn / TrackOut / Abort / Split / Merge / Scrap / Bonus / Hold…）
WIP     = 在制只读投影（来自 Track/Lot）
Hold    = 拦截规则（Track 事务前强制校验）
History = 事务履历只读调查（写在 Track；谱系归 Genealogy）
```

**禁止**：

- WIP 与 Track 双写当前站
- 业务接口绕过 Track 直接改 Lot 加工态 / qty
- 跳站 / 返工 / 分合批无规则、无权限、无履历

---

## 3. 角色与权限（草案）

| 权限码 | 用途 |
|--------|------|
| `track:view` | 现场台 / 查询当前 Lot 执行态 |
| `track:release` | 放行（事务 Release） |
| `track:move` | 独立移站 |
| `track:track-in` | 开工 |
| `track:track-out` | 完工 |
| `track:hold` | 发起锁批（可与 Hold 模块码合并，落地时定） |
| `track:skip` | 跳站（强权限） |
| `track:rework` | 返工 |
| `track:off-flow` | Temporary Off-Flow / 回主路径 |
| `track:split` | 分批 |
| `track:merge` | 合批 |
| `track:scrap` | 报废 |
| `track:bonus` | 数量调整 |

现场菜单已有 `track:view` / `track:track-in` / `track:track-out` 等（见权限种子）。

---

## 4. 事务字典（全套预留，分期实现）

| 事务 | 一期 | 说明 |
|------|------|------|
| **Release** | ✅ 必做 | 绑当时 active 的 `route_version_id`；Lot → 可执行（如 wait 首站） |
| Move | ✅ P1 | `POST /track/move`；wait→下一站 wait；见 `MES-TrackMove接口设计.md` |
| TrackIn | ✅ 必做 | 开工；记录 eqp（一期可空）、人、时间 |
| TrackOut | ✅ 必做 | 完工；自动推进下一站 wait 或 completed |
| **Hold** | ✅ 见 Hold 模块 | 锁批；TrackIn/Out/Move 前 `assertNoActive` |
| **ReleaseHold** | ✅ 见 Hold 模块 | 解锁 |
| Reserve / Dispatch | 后置 | 选机预约（依赖 Equipment / Dispatch） |
| Skip | ✅ | 跳站，规则+权限 |
| Rework | ✅ | 回流，次数上限 |
| Off-Flow | ✅ | 临时旁路 / 回锚点 |
| Split | ✅ | `POST /track/split`；见 `MES-LotSplit接口设计.md` |
| Merge | ✅ | `POST /track/merge`；同站同快照；见 `MES-LotMerge接口设计.md` |
| Scrap | ✅ | `POST /track/scrap`；全批/部分；见 `MES-LotScrap接口设计.md` |
| Bonus | ✅ | `POST /track/bonus`；±delta；见 `MES-LotBonus接口设计.md` |
| **Abort** | ✅ | `POST /track/abort`；processing→同站 wait；**不 Hold**；见 `MES-TrackAbort接口设计.md` |

每个事务统一生命周期：

```
1. 分布式锁 / 乐观锁（lotId + Lot.version）
2. 加载 Lot + RouteVersionSteps + Hold(+ Eqp)
3. 校验：状态机允许？Hold？下一站合法？
4. DB 事务更新 Lot 运行态 / WIP 投影
5. 写 mes_tx_log（履历）
6. 提交后 Event / MQ；可选 WebSocket
```

---

## 5. 一期功能清单（薄实现，厚模型）

| 功能 | 说明 |
|------|------|
| Release | 入口建议 `POST /track/release`；内部复用/收敛现有 Lot 放行逻辑 |
| Move | ✅ `POST /track/move`；校验目标 = 快照 next；`wait`→下一站 `wait`（不经加工） |
| TrackIn | 当前站 wait→processing；记 eqpId? |
| TrackOut | processing→下一站 wait（内含移站），或末站 completed；加工站主路径仍用 Out |
| 履历查询 | 按 Lot 查事务流水 |
| 现场台 | 选 Lot → 看当前站 → In/Out/Move/Abort 等 |

### 5.1 Release（收归 Track）

- **语义**：Track 事务字典成员（与大厂一致）
- **数据**：写 `route_version_id`、状态 `released`（或首站 wait，落地时与状态机对齐）
- **迁移**：现有 `POST /lots/{id}/release` 可保留为兼容包装，或逐步切到 `/track/release`；**新代码以 Track 为准**
- 权限：优先 `track:release`；过渡期可同时认 `lot:release`

### 5.2 防跳站（硬约束）

- 只认 `Lot.route_version_id` 下的 `mes_route_step`
- TrackOut / Move 目标必须是 `next_sort_no` 指向站，或结束
- Skip 未开放前禁止任意选站

### 5.3 状态机（草案，落地前可微调命名）

```
created ──Release──► wait(首站)
wait ──TrackIn──► processing
wait ──Move─────► wait(下一站)           # 不经加工；仅 next
processing ──TrackOut──► wait(下一站) | completed
processing ──Abort──► wait(同站)          # 腾机、清计时；不进 Hold
wait ──Scrap(部分)──► wait（qty↓）
wait ──Scrap(全批)──► scrapped
任意可执行态 ──Hold──► held ──ReleaseHold──► 原态
```

说明：`created` 仅 Lot 创建；**进入生产后的变迁只由 Track 事务触发**。`scrapped` / `merged` / `completed` 不可再 Track。Abort ≠ Hold ≠ Scrap。Move ≠ TrackOut（未加工只搬家）。

---

## 6. 一期不做（预留扩展点）

| 项 | 说明 |
|----|------|
| 真实设备联机 / EAP | Adapter 后置；TrackIn eqp 可手选或空 |
| Dispatch 自动选机 | 先手填 eqpId |
| Skip / Rework / 并行站 | 表与事务码预留 |
| Queue Time | ✅ 已落地（站间；到期自动 Hold + 清窗；`@Scheduled`） |
| Process Time | ✅ 已落地（站内；见 `MES-ProcessTime接口设计.md`） |
| 片级 Send-ahead | 厂型决策后 |
| RocketMQ / 复杂 WS | 一期可先同步写履历 + 可选 Spring Event |

---

## 7. 接口草案

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/track/release` | `track:release` | `{ lotId }` → 绑 active 版本 |
| POST | `/track/move` | `track:move` | `{ lotId, toSortNo? }`；wait→下一站 wait；见 Move 设计 |
| POST | `/track/track-in` | `track:track-in` | `{ lotId, eqpId? }` |
| POST | `/track/track-out` | `track:track-out` | `{ lotId }` → 自动下一站 |
| POST | `/track/abort` | `track:abort` | 加工中止；见 Abort 设计 |
| GET | `/track/lots/{lotId}/context` | `track:view` | 当前/下一站 + `canAbort`/`canMove`/`canSplit`… |
| POST | `/track/split` | `track:split` | 分批 |
| POST | `/track/merge` | `track:merge` | 合批 |
| GET | `/track/merge/candidates` | `track:merge` / `lot:list` | 合批候选 |
| POST | `/track/scrap` | `track:scrap` | 报废 |
| GET | `/track/scrap/reason-codes` | `track:scrap` / `lot:list` | 报废原因码 |
| POST | `/track/bonus` | `track:bonus` | 数量调整 |
| GET | `/track/bonus/reason-codes` | `track:bonus` / `lot:list` | 调整原因码 |
| GET | `/lots/{id}/history` | `history:list` 或 `track:view` | 本 Lot 履历；委托 `HistoryFacade.listByLot` |
| GET | `/history` | `history:list` | 调查分页；见 History 接口设计 |
| GET | `/history/{txId}` | `history:list` | 单行详情 |

统一响应：`{ code, msg, data }`；Token：Bearer。

兼容（过渡）：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/lots/{id}/release` | 已实现；文档标注为兼容入口，逻辑应收敛到 Track |

---

## 8. 与已落地模块关系

| 模块 | 关系 |
|------|------|
| Route | Track 只读版本步骤；不改编工艺 |
| Lot | 持有快照与运行态字段；创建/改属性仍在 Lot |
| Auth | 现场权限码已部分存在 |
| Hold / Eqp / Dispatch | Track 校验钩子预留，模块后补 |
| History | 写 `mes_tx_log`；调查读走 `HistoryFacade` |

---

## 9. 验收要点（一期）

1. Release 无 active 版本失败；成功后 `route_version_id` 锁定  
2. TrackIn 非当前 wait 站失败；Hold 中（二期）失败  
3. TrackOut 只能到快照下一站；末站 → completed  
4. 同 Lot 并发事务乐观锁 / 行锁不丢更新  
5. 每笔事务有履历（谁/何时/何事务/何站/何机）  
6. Route 升版发布不影响已放行 Lot  

---

## 10. 相关文档

- `MES-Track二期功能清单.md`
- `MES-Track数据库设计.md`
- `MES-Track已完成功能.md`
- `docs/模块/Lot（批次）模块/MES-Lot功能文档.md`
- `docs/模块/Route（工艺路线）模块/MES-Route功能文档.md` §4.3
- `docs/模块/Lot（批次）模块/MES-LotScrap接口设计.md`
- `docs/模块/Lot（批次）模块/MES-LotBonus接口设计.md`
- `docs/架构/MES-实施进度与下一步.md`
