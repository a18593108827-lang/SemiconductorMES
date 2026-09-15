# MES 派工（Dispatch）— Critical 禁派功能清单

> 前提：Dispatch 最小集已齐；Alarm-1～7（含 `HOLD_LOT`）已齐；Hold 最小集已齐  
> 对齐：`MES-Alarm架构设计.md` §8/§9 P1 · `MES-Dispatch功能文档.md` §4 · 业务清单 §4「禁跑规则」· Alarm-P1 后置项  
> 产品口径：严重未关告警禁止推荐/预约；**不**替代 Hold；**不**因 Alarm 挡 TrackIn/Out；**不**做升级通知/GEM  
> 更新：2026-09-15  
> 状态：**Disp-C1 ✅ · Disp-C2 ✅**

---

## 0. 目标

交付 **Critical 禁派钩子**：Lot / 目标机存在 **未关闭 CRITICAL** 告警时，候选不推荐、Reserve 拒绝；ACK/CLEAR 后恢复可派。

原则：

- **感知在 Alarm，拦派在 Dispatch，拦货（过站）在 Hold**——三层正交，禁止混职责
- 告警是否「挡派」的判定真相只在 Alarm 门面查询；Dispatch **只调断言/查询接口**，零 `mes_alarm` Mapper
- 与顶栏口径一致：未关闭 = `OPEN` ∪ `ACK`；级别 = `CRITICAL`（WARNING/INFO **不**禁派）
- `HOLD_LOT` 已 held → 仍先走现网 Hold 闸；本钩子补「未锁批但仍严重未关」与「机台严重未关」漏网
- Track **零改**门禁；禁宣称 AMS / OCAP / 设备联机禁跑

闭环一句话：

```
raise CRITICAL →（可选 HOLD_LOT→held）→ 派工禁推荐/禁预约
     → 人 ACK/CLEAR（及必要时 Hold Release）→ 再可派 → TrackIn
```

---

## 1. 架构约束（必须遵守）

| # | 约束 | 说明 |
|---|------|------|
| A1 | 高内聚 | 「何谓挡派 CRITICAL」规则、按 Lot/Eqp 查询 **全部**在 Alarm 侧（建议 `AlarmFacade` 或 `AlarmDispatchGate`）；Dispatch 只消费结果 |
| A2 | 低耦合 | `com.mes.dispatch` **零** Alarm/Hold Mapper；只依赖 `HoldService`（已有）+ Alarm **只读门面方法**；Alarm **零** Dispatch 依赖 |
| A3 | 门禁分层 | Track **不**因 Alarm 拒站（维持 Alarm 架构 D）；Dispatch **仅**本清单约定路径可因 CRITICAL 拒派；过站硬拦仍只认 Hold / EDC 等 |
| A4 | 双闸顺序 | `listCandidates` / `reserve`：**先** `HoldService`（held 则直接空候选/拒约），**再** Critical 闸；禁止颠倒导致文案混乱 |
| A5 | 读时判定 | 每次候选/预约 **现场查库**；禁止跨请求缓存「是否可派」；禁止前端只藏按钮 |
| A6 | 实体范围 | Lot 闸：`entity_type=LOT` 且 `entity_id=lotId`；Eqp 闸：`entity_type=EQP` 且 `entity_id=eqpId`；CHART/NONE **不**挡派 |
| A7 | 级别/状态 | 仅 `level=CRITICAL` 且 `status∈{OPEN,ACK}`；与 `listActiveCritical` 同语义 |
| A8 | 幂等文案 | 已 held 优先返回锁批文案；未 held 但 Lot CRITICAL → 明确「严重未关闭告警，禁止派工」；目标机 CRITICAL → 「设备存在严重未关闭告警」 |
| A9 | 灰度 | `mes.dispatch.block-critical-alarm-enabled` 默认 **true**；false 时与现网零行为差（仍保留 Hold 闸） |
| A10 | 并发 | 见 §5；写路径 Reserve 必须再检；禁止只信候选页快照 |

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Dispatch 包内直接查 `mes_alarm` / 拼 SQL | 规则双通道；与 A1/A2 冲突 |
| P2 | Alarm 包依赖 Dispatch / 改 Reserve | 感知层侵入编排层 |
| P3 | TrackIn/Out / Move 因 CRITICAL 拒绝 | 与 Alarm「不挡 Track」契约冲突；过站用 Hold |
| P4 | WARNING/INFO 禁派 | 噪音过大；顶栏也不按此挡 |
| P5 | 仅 ACK 后才放行、ACK 仍挡却要求「未 ACK 才禁」双口径 | 与顶栏「未关闭」不一致；本清单统一 **未 CLEAR 即挡** |
| P6 | CLEAR 时自动 Release Hold | 解锁 SSOT 在 Hold；闭环靠人/策略各走各的 |
| P7 | 本波做未 ACK 升级、邮件、企微、GEM | 另切片 |
| P8 | 因本钩子改码表默认 level / 默认 HOLD_LOT | 运营配置；开发不改种子语义 |

**相对 Alarm-P1 A4 的修订说明**

Alarm-P1 A4「Dispatch 不因 Alarm 实例拒站」在本清单生效后收窄为：**Dispatch 不因非 CRITICAL / 已 CLEAR 的实例拒派**；**CRITICAL 未关闭**由本钩子显式允许。Track 侧 A4 不变。

---

## 2. 范围总览

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | Alarm：按 Lot / Eqp 查询是否存在挡派 CRITICAL（只读） | ✅ |
| P0 | `listCandidates`：Lot 挡 → 空候选 + 文案；Eqp 挡 → 剔除该机 | ✅ |
| P0 | `reserve`：Lot 或目标 Eqp 挡 → 业务拒绝 | ✅ |
| P0 | 配置总阀 `block-critical-alarm-enabled` | ✅ |
| P1 | Admin/现场：候选空态展示告警摘要（码/id，只读） | ✅ |
| P2 | 未 ACK 升级通知 | 不做（本清单） |
| P2 | Track 因 Alarm 拒站 | 不做 |

---

## 3. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| Disp-C1 | 后端闭环：Alarm 查询 API + Dispatch 候选/预约双闸 + 配置 | ✅ |
| Disp-C2 | Admin/现场空态与拒绝文案（可读告警码，不深链改 Alarm） | ✅ |

顺序：**Disp-C1 → C2**。  
**禁止** C2 先于 C1。  
**禁止** C1 改 Track。  
**禁止** C1 在 Alarm 内写 Reserve/Hold。

---

### Disp-C1（后端闭环）✅

#### 1.1 Alarm 侧交付（高内聚）

在 `AlarmFacade`（或独立 `AlarmDispatchGate`，仍属 alarm 包）新增只读方法，建议：

| 方法 | 语义 |
|------|------|
| `boolean hasBlockingCriticalForLot(Long lotId)` | 存在 `LOT/{lotId}` + CRITICAL + OPEN/ACK |
| `boolean hasBlockingCriticalForEqp(Long eqpId)` | 存在 `EQP/{eqpId}` + CRITICAL + OPEN/ACK |
| `Set<Long> listEqpIdsWithBlockingCritical(Collection<Long> eqpIds)` | 批量：候选过滤用，避免 N+1 |
| （可选）`AlarmVO findFirstBlockingCriticalForLot(Long lotId)` | 供文案带 code/id；无则 null |

实现约束：

- 仅 `MesAlarmMapper`（或既有查询）条件：`level=CRITICAL`、`status∈(OPEN,ACK)`、实体匹配
- **不**改状态、**不**调 Hold、**不**发 WS
- 查询不加 `FOR UPDATE`（只读闸；并发见 §5）
- 单测/验收与 `listActiveCritical` 过滤条件一致（可抽私有 `blockingCriticalQw` 防漂移）

#### 1.2 Dispatch 侧交付

`DispatchServiceImpl`：

1. `listCandidates(lotId)`  
   - 现有 held 判定不变  
   - 总阀 true 且 `hasBlockingCriticalForLot` → `candidates=[]`，`recommendedEqpId=null`，`message` 含严重未关（可选带 alarm code）  
   - 组装候选时：`listEqpIdsWithBlockingCritical` 剔除机台；若滤后为空且非 Lot 挡，message 区分「设备均有严重未关告警」
2. `reserve(dto)`  
   - `assertNoActive` Hold 之后  
   - `hasBlockingCriticalForLot` → 抛业务错  
   - `hasBlockingCriticalForEqp(eqpId)` → 抛业务错  
   - **必须在写预约前再查**，不得只信候选接口结果

落地：`AlarmFacade`（+Impl）· `DispatchServiceImpl` · `application.yml` · 错误码/文案与现网 `BusinessException` 风格一致

#### 1.3 配置

| 键 | 默认 | 说明 |
|----|------|------|
| `mes.dispatch.block-critical-alarm-enabled` | true | false：跳过 Critical 闸；Hold 闸仍生效 |

#### 1.4 本切片不做

- 前端大改；升级定时；Track 门禁；自动 CLEAR/自动 Release

---

### Disp-C2（可见性）✅

#### 2.1 交付

- Admin `/app/dispatch` 与现场选机：空候选时展示 C1 返回的 `message`（及可选 alarmCode）
- Reserve 失败 toast 用后端消息，不前端猜

#### 2.2 本切片不做

- 在派工页一键 ACK/CLEAR（处置仍在告警台）
- 改顶栏逻辑（已与未关闭 CRITICAL 对齐则不动）

---

## 4. 业务闭环（必须可讲清）

| 步骤 | 系统行为 | 用户动作 |
|------|----------|----------|
| 1 严重发生 | `raise` → OPEN CRITICAL；若码表 HOLD_LOT → 异步/独立事务锁批 | 看告警台/顶栏 |
| 2 派工 | held → 锁批文案；未 held 但 Lot CRITICAL → 禁候选/禁约；机台 CRITICAL → 该机不可选/不可约 | 不能把严重批派出去 |
| 3 处置告警 | ACK（认领）仍 **禁派**；CLEAR → 该实例不再挡 | 告警台关闭 |
| 4 处置锁批 | 若曾 HOLD_LOT：Hold 台 Release | 与 CLEAR 顺序不强制，两闸独立 |
| 5 再派 | 无挡派 CRITICAL 且无 active Hold → 候选恢复 | Reserve → TrackIn |
| 6 过站 | Track **只**认 Hold/EDC/预约等现网闸 | 不因历史 CRITICAL 拒 In |

**不完整闭环（本清单明确不靠它）**：只 ACK 不 CLEAR 就期望能派 → **不允许**（与 A7/P5）。

---

## 5. 并发与一致性

| 场景 | 要求 |
|------|------|
| 候选已返回可派，Reserve 前新 OPEN CRITICAL | Reserve **再查**并拒绝；接受候选人机短暂不一致 |
| Reserve 事务中 CLEAR 并发提交 | 以 Reserve 读到的快照为准；读偏允许，不要求锁定告警行 |
| 同 Lot 同时 Reserve 与 raise | 现网预约行锁 + Critical 再检；最坏：先约后 OPEN → **本波不**自动释约（避免 Alarm 写 Dispatch）；下一次候选/续约会挡。若需「OPEN 后踢预约」另立切片 |
| Hold 与 Critical 同时为真 | 只表现 Hold 文案（A4/A8） |
| 批量候选滤机台 | `listEqpIdsWithBlockingCritical` 一次 IN 查询；禁止循环单条 |
| 与 `listActiveCritical` 条件漂移 | 共享常量/私有拼装；验收 V8 |

锁序：Dispatch 写路径保持现网 **Lot/Reserve 行锁**；**禁止**为 Critical 闸去锁 `mes_alarm` 再锁 Lot（无必要，易死锁）。

---

## 6. 与 HOLD_LOT / 顶栏关系

| 能力 | 挡什么 | 解除 |
|------|--------|------|
| Hold / HOLD_LOT | Track + 派工（held） | Hold Release |
| Critical 禁派 | **仅**候选推荐 + Reserve | Alarm CLEAR（ACK 不够） |
| 顶栏 critical | 可见性 | ACK/CLEAR 后列表变化；挡派仍看 CLEAR |

已 `HOLD_LOT` 的严重码：通常先 held，Critical 闸为双保险（Hold 失败仍 OPEN 时生效）。

---

## 7. 错误与文案

| 场景 | 行为 |
|------|------|
| Lot 挡 · 候选 | 空列表 + message（中文）：如「存在未关闭的严重告警，禁止派工」 |
| Lot 挡 · 预约 | `BusinessException` 同上义 |
| Eqp 挡 · 候选 | 剔除该机；可无全局空（有其它机则仍推荐） |
| Eqp 挡 · 预约 | `BusinessException`：如「目标设备存在未关闭的严重告警」 |
| 总阀 false | 与改前一致 |

不新增对外错码枚举也可；若现网有 code 字段则跟现网风格。

---

## 8. 验收

| # | 场景 | 期望 |
|---|------|------|
| V1 | Lot 上 OPEN CRITICAL，未 Hold | 候选空；Reserve 拒 |
| V2 | 同告警 ACK 未 CLEAR | 仍禁派 |
| V3 | CLEAR 后无其它 CRITICAL | 候选恢复；可 Reserve |
| V4 | 仅 WARNING OPEN | 不禁派 |
| V5 | EQP CRITICAL，Lot 无 | 该机从候选剔除；约该机拒；约其它机可 |
| V6 | held + CRITICAL | 文案走锁批；行为仍不可派 |
| V7 | 总阀 false | CRITICAL 仍可派（Hold 除外） |
| V8 | 挡派条件与 `listActiveCritical` 一致 | 同实例顶栏可见 ⟺ Lot/Eqp 匹配时挡派 |
| V9 | 包依赖 | dispatch 无 alarm mapper；alarm 无 dispatch 引用；Track 无新增 Alarm 门禁 |
| V10 | 候选通过后、预约前插入 CRITICAL | 预约失败 |

---

## 9. 明确不做（本清单）

- 未 ACK 升级、邮件/企微、OCAP、GEM  
- Track 因 Alarm 拒站；OPEN 后自动释放 Reserve  
- PM 禁派、规则表可视化、APS  
- 修改 Alarm 种子码默认 level / HOLD_LOT  

---

## 10. 关联

| 文档 | 路径 |
|------|------|
| Dispatch 功能 | `MES-Dispatch功能文档.md` |
| Alarm 架构 | `docs/模块/Alarm（告警）模块/MES-Alarm架构设计.md` |
| Alarm P1 | `docs/模块/Alarm（告警）模块/MES-Alarm-P1功能清单.md` |
| Hold | `docs/模块/Hold（锁批）模块/MES-Hold功能文档.md` |
| 业务清单 | `docs/业务清单/MES-半导体业务清单.md` §4 / §8 |
| 进度 | `docs/架构/MES-实施进度与下一步.md` |
