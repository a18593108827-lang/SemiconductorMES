# Abort — 接口设计（架构）

> 范围：加工中整批中止（`processing→wait` 同站）+ 原因码 + 履历 + 清 Process Time + 腾机 + context/`canAbort` + 现场台  
> 原则：**新开 Track 事务**；状态仍只由 Track 写；**不推进站**、**不改 qty**、**不当 Skip**  
> 产品口径：P0 **异常安全阀**（合法退站腾机）；**不是** CancelTrackIn 旁路改库；**不是** 片级 Mid-Process 处置；**不是** 设备 E40 Abort  
> 对齐：`MES-Track二期功能清单.md` §2.2 / T2-2；`docs/业务清单/MES-半导体业务清单.md` §3  
> 前提：TrackIn/Out、Hold.assertNoActive、Process Time、Dispatch Reserve 消费、`mes_tx_log` 已落地  
> 状态：**T2-2 已落地（后端 + 现场台 Abort）**  
> 更新：2026-08-12  
>
> **产品速记**  
> - 落点：`processing → wait`（**同站**），**不进 Hold**，默认可再 TrackIn  
> - 原因码：P0 **代码白名单**（非 Admin 配置表）；改码改 `TrackServiceImpl.ABORT_REASON_CODES`  
> - 权限：仅 `track:abort`（刷种子后须重新登录）  

---

## 1. 边界

```
Track     = 唯一写路径：Abort 事务改 status / 清 eqp / 清计时 / 写履历
Hold      = active Hold → 禁 Abort（与 In/Out/Rework 一致）
Dispatch  = TrackIn 已 consume；Abort 腾机靠清 eqp；残留 active → 防御释约
ProcessT  = Abort 必须 clearPersisted（再 In 重新开表）
QueueT    = 不开关窗（仍同站 wait；未 Out）
Adapter   = 不调设备 Abort（EAP/E40 属 P2+）
Lot/片    = 不改 qty；不做片级分类 Split/Out/Hold
```

**本切片做什么**

- `POST /track/abort`：`processing` → 本站 `wait`；清 `current_eqp_id`；清 `process_started_at`
- 原因码白名单必填；可选 remark
- `mes_tx_log.tx_type = ABORT`；ext 可追溯
- context：`canAbort`；现场台 Abort 入口
- 权限 `track:abort`；原因码查询接口
- 腾机：清 Lot 占机；若 Lot 仍有 **active** Reserve → 翻 `released`（防御）

**本切片不做什么**

- 片级 Mid-Process Abort（Processed / Not Processed / Issue → Split+Out / Abort+Hold）
- Abort 后按原因自动 Hold（可后置配置）
- 设备侧 SEMI E40 PRAbort / E94 CJAbort 联机
- Abort 当 Skip / 改站号 / 改 Route / 改 qty
- 重建或「保留」同机 Reserve（P0 不重建预约）
- CancelTrackIn 语义别名（对外统一叫 Abort）

**禁止**

- 非 Track 路径把 `processing` 改回 `wait` 冒充退站
- Abort 推进 `current_sort_no` / 写下一站
- Abort 改 `qty` / 写 Scrap genealogy
- Hold 中 Abort
- `scrapped` / `merged` / `completed` Abort
- 无原因码 Abort

**与兄弟能力分工**

| | TrackOut | Abort | Rework | Scrap | Skip |
|--|--|--|--|--|--|
| 问什么 | 本站加工**完成**否 | 本站加工**中止**否 | 是否回流前序 | 是否报废减量 | 是否跳站 |
| 前置 | processing | processing | wait/processing | wait | wait |
| 站号 | 推进或完工 | **不变** | 跳回 to_sort | 不变 | 跳过 |
| qty | 不变 | **不变** | 不变 | 减 | 不变 |
| 计时 | 清（成功） | **清** | 清 | 通常无 | 通常无 |
| 履历 | TRACK_OUT | **ABORT** | REWORK | SCRAP | SKIP |

**状态机**：无新 `Lot.status`。仍 `wait` / `processing` / `held`。

```
wait ──TrackIn──► processing
                     │
                     ├──TrackOut──► wait(下一站) | completed
                     ├──Rework────► wait(回流站)
                     └──Abort─────► wait(同站)   ← 本切片
                          └ 清 current_eqp_id
                          └ 清 process_started_at
                          └ 不推进 sort_no / step_id
                          └ 不改 qty / route_version
```

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 事务形态 | **独立** `POST /track/abort` + `tx_type=ABORT` | 与 Out/Rework 权限、审计隔离；非 Out 分支 |
| D2 | 粒度 | P0 **整批** Top-Lot | 对齐 CM Material.Abort；片级属后置增强 |
| D3 | 落点 | 同站 `wait`；站号/步骤不变 | Abort≠Skip；未完成不可冒充过站 |
| D4 | 腾机 | 清 `current_eqp_id`；WIP sync | 加工作废须释放机台占用 |
| D5 | Reserve | **不重建**；残留 active → `released` | In 已 `consumed`；腾机靠 D4；「保留约」会假占产能 |
| D6 | Process Time | **必须** `clearPersisted` | 与 PT 设计 §5.3 一致；再 In 重新开表 |
| D7 | Queue Time | **不动** 开窗字段 | 未 TrackOut；站间窗语义不变 |
| D8 | Hold | `assertNoActive`；Abort **绝不**自动 Hold | P0 回 wait 可再 In；**不是** scrap/锁批。自动 Hold 属后置 |
| D9 | 原因码 | **代码常量白名单** `ABORT_REASON_CODES` | 同 Scrap/Bonus；无表、无 Admin 页；改码改常量并发布 |
| D10 | Off-Flow | processing 允许 Abort → 旁路**当前站** wait | 清 eqp 即释锚点占台；禁改锚点元数据 |
| D11 | 设备 | 不调 Adapter | MES 账先合法；EAP 对账后置 |
| D12 | fail 策略 | 校验失败整笔回滚；不写残履历 | 与现 Track 事务一致 |
| D13 | 落库 | `LambdaUpdateWrapper` 显式 SET null | `updateById` 跳过 null，否则腾机/清秒表不生效 |
| D14 | WIP | sync 用 Wrapper 写 `current_eqp_id`（含 null） | 投影与 Lot 一致腾机 |

**命名**：对外 Abort / `ABORT`；禁止实现别名接口 `cancel-track-in`（避免与「撤销履历」混淆）。

---

## 3. 数据契约

### 3.1 Lot 运行态（仅 Track 写）

| 字段 | Abort 行为 |
|------|------------|
| status | `processing` → `wait` |
| current_sort_no | **不变** |
| current_step_id | **不变** |
| current_eqp_id | → `null` |
| process_started_at | → `null`（`ProcessTimeSupport.clearPersisted`） |
| qty / route_version_id / product… | **不变** |
| off_flow / anchor_* | **不变**（仅清 eqp） |

乐观锁：`version` 条件写在 Wrapper；`status` / `current_eqp_id=null` / `process_started_at=null` **同笔**更新。成功后 `selectById` 校验已是 `wait` 再 sync WIP。

### 3.2 事务履历 `mes_tx_log`

| 项 | 约定 |
|----|------|
| tx_type | `ABORT` |
| from_status / to_status | `processing` → `wait` |
| from_sort_no / to_sort_no | **相同**（本站） |
| step_id | 当前站 step |
| eqp_id | Abort 前 `current_eqp_id`（腾机前快照） |
| remark | 可选；可拼原因文案 |
| ext_json | 见下 |

```json
{
  "reasonCode": "EQP_ABORT",
  "remark": "腔体报警",
  "processStartedAt": "2026-08-11T10:00:00.000",
  "processElapsedMin": 12,
  "clearedProcessTime": true,
  "releasedReserveId": null
}
```

- `processStartedAt` / `processElapsedMin`：若 Abort 前有开表则记；便于审计「中止前已加工多久」
- `releasedReserveId`：仅防御释约命中时填写

### 3.3 原因码（P0 白名单）

**配置位置（真相源）**

| 项 | 说明 |
|----|------|
| 落点 | `TrackServiceImpl` 常量 `ABORT_REASON_CODES` |
| 读取 | `GET /track/abort/reason-codes` → `abortReasonCodes()` 原样返回 |
| 校验 | `isAbortReasonAllowed`；非法拒 Abort |
| Admin | **无**；不进 `mes_hold_reason` / 独立原因表 |
| 改法 | 改常量列表 → 编译发布；前端下拉随接口刷新 |
| 后置 | 迁配置表 + Admin 维护（见 §10） |

| code | 说明 |
|------|------|
| EQP_ABORT | 设备中止/报警 |
| EQP_DOWN | 设备宕机/不可用 |
| RECIPE_ERROR | 配方/参数错误 |
| OPERATOR | 人为误操作/主动中止 |
| PROCESS_ISSUE | 工艺异常（未到报废） |
| OTHER | 其他（须填 remark） |

校验：`reasonCode` 必填且 ∈ 白名单；`OTHER` 时 `remark` 必填。

**与 Hold 原因码无关**：Abort 不用 `mes_hold_reason`；Abort **不会**写 Hold 记录、不会把 Lot 置 `held`。

### 3.4 Dispatch Reserve

| 场景 | 行为 |
|------|------|
| TrackIn 已 `consumed`（常态） | 无 active；Abort **不**改历史 consumed 行 |
| 异常残留 `active`（同 Lot） | `leaveActive(..., released, "ABORT", abortTxId)` |
| 「Abort 后保留同机预约」 | **P0 不做** |

Dispatch 增量钩子（内部，非 HTTP）：

```
void releaseActiveOnAbort(Long lotId, Long abortTxId);
```

### 3.5 配置项（可选，P0 可硬编码默认）

```yaml
mes:
  abort:
    enabled: true
    # P1：逗号分隔原因码 → Abort 成功后自动 Hold
    # auto-hold-reason-codes: EQP_DOWN,PROCESS_ISSUE
```

P0 不读 auto-hold；预留键名。

---

## 4. 执行流程

### 4.1 Abort 主路径

同事务、顺序固定：

```
1. requireExecutableLot(lotId)
2. holdService.assertNoActive(lotId)
3. status == processing，否则拒
4. current_sort_no / route_version_id 非空
5. 校验 reasonCode（白名单；OTHER→remark 必填）
6. 快照 fromStatus / fromSortNo / fromEqpId / process_started_at
7. LambdaUpdateWrapper（带 version）：
     SET status=wait, current_eqp_id=NULL, process_started_at=NULL, version+1
8. selectById 断言已 wait；刷新内存 Lot
9. wipProjectionService.syncFromLot（Wrapper 写含 null 的 eqp）
10. dispatchService.releaseActiveOnAbort(lotId, null)  # 防御释约；常态无 active
11. writeTxLog(ABORT, …, ext≤512)
12. return toTxnVo(lot, ABORT, false)
```

**不做**：改站、开/结 Q-Time、Future Hold POST、Recipe 校验、Reserve consume、**Hold.create**。

### 4.2 错误码

| code | 场景 |
|------|------|
| （沿用 Assert/Business 文案或统一码） | 非 processing |
| `ABORT_REASON_REQUIRED` | 缺原因码 |
| `ABORT_REASON_INVALID` | 不在白名单 |
| `ABORT_REMARK_REQUIRED` | OTHER 缺 remark |
| （Hold 既有） | active Hold |

建议前缀 `ABORT_`，与 `PROCESS_TIME_*` 并列。

### 4.3 context 增量

`GET /track/lots/{lotId}/context`

```json
{
  "canAbort": true
}
```

`canAbort` =  
`status==processing`  
∧ 无 active Hold  
∧ 非 Off-Flow 禁令外（**允许** Off-Flow processing）  
∧ `StpUtil.hasPermission("track:abort")`  
∧ Lot 可执行（非 scrapped/merged/completed）

前端：仅 `canAbort===true` 显示 Abort；点开强制选原因码。

---

## 5. 接口一览

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/track/abort` | `track:abort` | 加工中止 |
| GET | `/track/abort/reason-codes` | `track:abort` **或** `track:view` | 原因白名单 |
| GET | `/track/lots/{lotId}/context` | `track:view` | 增 `canAbort` |

### 5.1 请求

`POST /track/abort`

```json
{
  "lotId": 1001,
  "reasonCode": "EQP_ABORT",
  "remark": "腔体压力异常"
}
```

| 字段 | 约束 |
|------|------|
| lotId | 必填 |
| reasonCode | 必填；白名单 |
| remark | 可选；`OTHER` 必填 |

### 5.2 响应

复用 `TrackTxnResultVO`（`txType=ABORT`，`completed=false`）。

### 5.3 原因码响应

```json
[
  { "code": "EQP_ABORT", "label": "设备中止/报警" }
]
```

风格对齐 `TrackScrapReasonVO`。

### 5.4 权限种子

| 码 | 说明 |
|----|------|
| `track:abort` | 加工中止 |

DDL：`server/src/main/resources/db/migrate_track_abort.sql`（权限 id=302；角色 1/2/3/4）。  
已有库须执行该脚本；**改权限后重新登录**，否则 `canAbort` / 接口仍按旧会话权限。

---

## 6. 前端

| 页 | 改动 |
|----|------|
| TrackPage | 琥珀色「中止」按钮（`canAbort`）；`AbortPanel` 选原因 + remark |
| AbortPanel | `web/src/components/track/AbortPanel.tsx`；GSAP 入场 150–250ms |
| 履历侧栏 | `ABORT` + Ban 图标 + reasonCode + 已加工分钟 |
| Banner | 加工中可 Abort；成功后 status≠processing → Process Time Banner 消失 |

文案：**加工中止（回本站等待）**；禁止写「取消过站 / 退站到上一站 / 锁批」。

---

## 7. 实施切片

| 切片 | 交付 | 状态 |
|------|------|------|
| AB-1 | 常量 `TX_ABORT`；原因白名单；DTO/VO；权限种子 | ✅ |
| AB-2 | `TrackService.abort` 主路径 + PT clear + WIP + tx_log | ✅ |
| AB-3 | `DispatchService.releaseActiveOnAbort` | ✅ |
| AB-4 | context `canAbort`；Controller 两接口 | ✅ |
| AB-5 | TrackPage Abort UI + 履历展示 | ✅ |

挂 **T2-2**。PT 文档 PT-4（Abort 清计时）随本切片关闭。

---

## 8. 验收

1. `processing` Abort → 同站 `wait`；`current_eqp_id` 空；`process_started_at` 空  
2. `current_sort_no` / `current_step_id` / `qty` 不变  
3. 履历 `ABORT`；ext 含 `reasonCode`；可再 TrackIn  
4. Hold 中 Abort 失败  
5. `wait` / `completed` / `scrapped` Abort 失败  
6. 无原因码 / 非法码失败；`OTHER` 无 remark 失败  
7. 有 Process Time 约束站：Abort 后再 In 重新开表  
8. 常态（Reserve 已 consumed）：Abort 成功且机台可被他批预约/开工  
9. 人为造 active Reserve 残留：Abort 后该行 `released`  
10. Off-Flow processing Abort → 仍 off_flow，当前旁路站 wait，锚点 eqp 占台解除  
11. 无非 Track 旁路可写同等状态变更  
12. Abort 成功后 Lot.`status` **不是** `held`；无新增 active Hold  
13. 原因码列表与 `ABORT_REASON_CODES` 常量一致（非 Hold 原因表）

---

## 9. 代码落点

| 层 | 路径 |
|----|------|
| API | `TrackController`：`/abort`、`/abort/reason-codes` |
| 服务 | `TrackServiceImpl.abort`；常量 `ABORT_REASON_CODES` / `TX_ABORT` |
| Dispatch | `DispatchService.releaseActiveOnAbort` |
| WIP | `WipProjectionServiceImpl.syncFromLot`（Wrapper 写 null eqp） |
| DTO/VO | `TrackAbortDTO`；`TrackAbortReasonVO`；`TrackContextVO.canAbort` |
| 权限 | `migrate_track_abort.sql`；`schema.sql` id=302 |
| 前端 | `AbortPanel.tsx`；`TrackPage.tsx`；`api/track.ts` |

人话摘要：

- 加工中出事 → 合法回本站等待，机台让出来  
- **不会锁批**；站别、数量不动；秒表清掉；记下为什么中止  
- 原因码改 Java 常量，不进配置后台  

---

## 10. 后置（非本切片）

| 项 | 说明 |
|----|------|
| Abort→自动 Hold | 按原因码配置（如 EQP_DOWN）；质量兜底；**当前未做** |
| 片级 Mid-Process | 分类 → Split + Out / Abort+Hold / Protocol |
| EAP 联机 | 先设备 Abort 事件，再调 MES Abort |
| 原因码配置化 | 迁表 / Admin 维护（替代 `ABORT_REASON_CODES`） |
| 统计 | Abort 频次 by eqp / step / reason |

---

## 11. FAQ

| 问 | 答 |
|----|----|
| 中止后会进 Hold 吗？ | **不会**。只回本站 `wait`。 |
| 原因码在哪配？ | `TrackServiceImpl.ABORT_REASON_CODES`；接口只读这份列表。 |
| 和报废/跳站什么关系？ | Abort≠Scrap≠Skip；不改 qty、不改站号。 |
| 按钮灰了？ | 需 `processing` + 无 Hold + 权限 `track:abort`（刷权限后重登）。 |
| 点了像没腾机？ | 须用 Wrapper 清 `current_eqp_id`（已按 D13 落地）；勿只用 `updateById`。 |

---

## 12. 关联

- `MES-Track二期功能清单.md` §2.2 / T2-2 / §9.3  
- `MES-ProcessTime接口设计.md` §5.3 / PT-4 / D9  
- `MES-Track功能文档.md`  
- `MES-Track已完成功能.md`  
- `MES-Track数据库设计.md`  
- `docs/模块/Dispatch（派工）模块/MES-Dispatch功能文档.md`  
- `docs/业务清单/MES-半导体业务清单.md` §3  
- 业界对照：CM `Material.Abort`（整批）；片级见 *Handle Abort Mid Process*（后置）  
