---
type: 接口设计
module: Route
status: done
slices: []
aligns: []
updated: 2026-08-13
---

# Queue Time — 接口设计（架构）

> 范围：Route 定义时间窗约束 + Track 计时/超时处置 + Hold/Alarm 消费  
> 原则：定义进版本快照；**不新开 Track 事务**；超时处置复用 Hold / Alarm，不另造锁批语义  
> 产品口径：P1 **半导标配（超时拦截闭环）**；**不对齐**大厂 Pre-Gate / QMS / RL（P2）  
> 对齐：`MES-Route二期功能清单.md` §3.4；`MES-半导体业务清单.md` §3 Queue Time  
> 前提：边表、TrackIn/Out、Hold.assertNoActive / HoldService.create、Alarm 已落地  
> 更新：2026-08-13（到期自动 Hold + 清窗；`@Scheduled` 扫描；解锁备注=Continue）

---

## 1. 边界

```
Route     = 配时间窗（from→to + max_queue_min [+ on_violate]）进 route_version 快照
Track     = TrackOut 开窗；到期扫描 / TrackIn 结算；context 暴露剩余分钟
Hold      = 超时策略含 HOLD 时 `HoldService.create`（原因码 QTIME_EXCEED）；**独立事务**
Alarm     = 超时策略含 ALARM 时推告警（可与 Hold 并存）
Dispatch  = 本期只读剩余时间钩子；不改选机规则（L2 后置）
Job       = Spring `@Scheduled` 扫到期 Lot（见 `MES-SpringScheduled使用.md`）
```

**本切片做什么**

- 版本内配置：`from_sort → to_sort` 最大等待分钟（`min_queue_min` 字段预留、本期不校验）
- Lot 运行态开窗：`from` TrackOut 起算 → `to` TrackIn 止
- **跨站中间站**：开窗不因中间站 TrackOut 清除；context / 现场台全程可展示倒计时
- 到 target TrackIn 前 **以及扫描到期时**：超时按策略 Hold / Alarm / 两者
- HOLD / HOLD_ALARM 到期：**立即锁批并清窗**；解锁须填备注后允许开工（本期 Continue）
- 在途只认放行时 `route_version_id` 快照约束
- context / WIP 可展示剩余分钟（只读）

**本切片不做什么**

- Pre-Gate / Stopping / 入口闸门防进
- 独立 QMS、周期 release plan、RL
- Dispatch 按 remaining Q-time 插队（钩子可留，规则后置）
- Process Time（站内加工时长；**已落地**，见 `MES-ProcessTime接口设计.md`）
- 跨版本、跨 Route 时间窗
- 双人审批 / 超时自动 Scrap

**禁止**

- 运行时读直播 `mes_step.max_queue_min` 做卡控（须读 Lot 所属版本快照）
- Track / WIP 私自改状态冒充 Q-Time Hold（须走 `HoldService.create`）
- 用 Skip / Rework / Off-Flow 绕开未结算的时间窗而不留履历
- 改 active 版本约束；在途 Lot 约束不随后续升版变化

**与兄弟能力分工**

| | Queue Time | Future Hold | Process Time | Off-Flow |
|--|--|--|--|--|
| 问什么 | 站间**等待**是否超窗 | 到站是否该锁 | 站内**加工**是否超窗 | 是否在旁路 |
| 定义 | Route 时间窗 | Hold 预约（Lot） | Route 步骤 min/max（见 ProcessTime 设计） | Route off_flow 边 |
| 触发 | TrackOut 开窗 / 到期扫描·TrackIn 结算 | Track 到站 activate | TrackIn→Out | Enter/Resume |
| 超时结果 | Hold 和/或 Alarm | → active Hold | **&lt;min 拒 Out；&gt;max 允许 Out + Hold**（见 ProcessTime 设计） | — |

**状态机**：无新 Lot.status。仍 `wait` / `processing` / `held`。

```
TrackOut(trigger)
  └ 若快照存在 from→to 时间窗：Lot 写入开窗运行态（started_at / to_sort / max_min / policy）

中间站 wait|processing（current ≠ to_sort）
  └ 开窗保持；context.queueTime 仍返回；中间站 TrackOut 无新约束时不得清窗
  └ 到期（扫描或任意站 TrackIn）：HOLD 策略 → Hold + 清窗（停在当前站）

wait @ to_sort ──TrackIn──►
  └ 若存在指向本站的开窗：elapsed 与 max 比较
       ├ 未超：清开窗，正常 TrackIn
       └ 已超：HOLD → 独立事务 Hold + 清窗，本笔 TrackIn 失败
             ALARM → 告警；默认仍拒进站（见 alarm-only-block）

held ──ReleaseHold（QTIME_EXCEED 必填备注）──► prev_status，窗已清，可开工

路线完工 / Rework：清窗
```

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 约束落点 | **边优先**：`mes_route_edge.max_queue_min`；站字段仅兼容缓存 | 时间窗本质是 from→to；与清单 §3.4 一致 |
| D2 | 邻站 vs 跨站 | P1 支持任意同版本 `(from,to)`；`edge_type=time_link` 专载非导航约束 | 导航边（normal/branch/…）可附带 max；跨站不污染选边 |
| D3 | 计时点 | `from` **TrackOut 成功时刻** → `to` **TrackIn 请求时刻** | 对齐业界 trigger complete → target start |
| D4 | 运行态 | Lot 上开窗字段（非独立 qtime 事务表） | 一期一 Lot 一活跃窗够用；履历靠 tx_log |
| D5 | 超时处置 | 复用 Hold + Alarm；HOLD 到期 **锁批并清窗** | 不新造锁批；解锁=本期 Continue |
| D6 | 并发多窗 | P1 **单活跃窗**：新开窗覆盖未结算旧窗（旧窗写履历 `QTIME_SUPERSEDED`） | 降复杂度；多窗属 P2 |
| D7 | 大厂 L1/L2 | **明确不做** | 属 Dispatch/APS；等产能感知与客户驱动 |
| D8 | 中间站 | **保留开窗**：中间站 TrackOut 无新约束时不清窗；现场台全程倒计时 | 跨站 time_link 业界语义；曾误清导致中间站不可见 |
| D9 | 到期触发 | `@Scheduled` 扫描 + TrackIn 再拦；Hold 走 `REQUIRES_NEW` | 对齐 Future Hold 激活不回滚；不上 XXL-JOB |

**选边附带 vs time_link**

- 邻站：写在实际走过的 `normal`/`branch`/`default` 边上的 `max_queue_min`
- 跨站：`edge_type=time_link`，`from_sort`/`to_sort` 合法，**不参与** TrackOut 选下一站
- TrackOut 开窗规则：取「本站出发且 to 尚未到达」的约束集合中 **deadline 最早** 的一条开窗（P1 简化为：优先取刚走出的导航边若有 max；否则取 from=本站的 time_link 中 max 最小者）
- **无新约束**：若 Lot 已有未结算开窗 → **原样保留**（禁止 `clearPersisted`）

---

## 3. 数据契约

### 3.1 边快照 `mes_route_edge`

| 字段 | 说明 |
|------|------|
| max_queue_min | INT NULL；空=无 Queue Time |
| min_queue_min | INT NULL；**预留**，本期不校验 |
| on_violate | VARCHAR(16) NULL；`HOLD` / `ALARM` / `HOLD_ALARM`；空=读全局默认 |

```sql
ALTER TABLE mes_route_edge
  ADD COLUMN max_queue_min INT NULL COMMENT 'QueueTime上限分钟' AFTER max_rework_count,
  ADD COLUMN min_queue_min INT NULL COMMENT 'QueueTime下限预留' AFTER max_queue_min,
  ADD COLUMN on_violate VARCHAR(16) NULL COMMENT 'HOLD|ALARM|HOLD_ALARM' AFTER min_queue_min;
```

`edge_type` 增补：`time_link`（仅约束，不导航）。

发布校验：

- `max_queue_min` 若非空须 `≥ 1`
- `from_sort` / `to_sort` 须同版本存在；`from ≠ to`
- `time_link` 不得充当 default 导航边
- 导航边上带 `max_queue_min` 时，`to` 即该边目标站

### 3.2 站字段（兼容，非执行真相）

`mes_step.max_queue_min` / `mes_route_step.max_queue_min`：UI/旧数据兼容。

**发布时**：若步骤有 `max_queue_min` 且对应 default 出边无值，则 **回填** 到该 default 边（站→边归一）。  
**运行时**：只读边/time_link 快照，不读站字段。

### 3.3 Lot 运行态（Track 表，不进 Route）

| 字段 | 类型 | 说明 |
|------|------|------|
| qtime_from_sort | INT NULL | 开窗触发站 |
| qtime_to_sort | INT NULL | 目标站 |
| qtime_started_at | DATETIME(3) NULL | TrackOut 成功时刻 |
| qtime_max_min | INT NULL | 开窗时固化上限（防歧义；版本已固定仍固化便于履历） |
| qtime_on_violate | VARCHAR(16) NULL | 开窗时固化策略 |

Release / 完工 / 清批：清空上述字段。

### 3.4 Hold / Alarm

| 项 | 约定 |
|----|------|
| Hold 原因码 | 预置 `QTIME_EXCEED`（或配置项映射 reason_id） |
| Hold 创建 | **仅** `HoldService.create`；`REQUIRES_NEW`；备注带 from/to/elapsed/max；无登录写 SYSTEM |
| Alarm | code=`QTIME_EXCEED`；payload 含 lotId、sort、elapsedMin、maxMin |
| 清窗时机 | HOLD 到期锁批后立即清 `qtime_*`，避免解锁后再锁死 |
| 解锁 | `QTIME_EXCEED` **必填备注**（本期 Continue）；窗已清，TrackIn 放行 |
| 已有别的 active Hold | 只 Alarm + 清窗，不二次锁 |

### 3.5 配置项

```yaml
mes:
  qtime:
    enabled: true
    default-on-violate: HOLD   # HOLD | ALARM | HOLD_ALARM
    alarm-only-block: true     # ALARM 策略是否仍拒 TrackIn
    fail-open: true
    scan-ms: 30000             # @Scheduled fixedDelay
```

---

## 4. Route 配置接口

沿用 `PUT /routes/versions/{versionId}/steps` 的 `edges[]` 扩字段；权限 `route:edit`；仅 draft。

```json
{
  "fromSortNo": 10,
  "toSortNo": 20,
  "edgeType": "normal",
  "maxQueueMin": 120,
  "minQueueMin": null,
  "onViolate": "HOLD"
}
```

跨站示例：

```json
{
  "fromSortNo": 10,
  "toSortNo": 40,
  "edgeType": "time_link",
  "maxQueueMin": 180,
  "onViolate": "HOLD_ALARM"
}
```

`GET /routes/versions/{id}`：edges 原样回传；UI 标注时间窗。

升版：拷贝含 `max_queue_min` / `time_link` 的边（与 rework/skip 同策略）。

---

## 5. Track 执行

### 5.1 TrackOut 开窗

在现有 TrackOut **成功提交后**（站位已更新到下一站或完成）：

```
1. 读 Lot.route_version_id 快照
2. 解析本笔 TrackOut 的 from_sort（完工站）
3. 解析开窗约束（见 §2 选边规则）
4. 若有：写入 Lot qtime_* ；若原有未结算窗：先打 TX 履历 QTIME_SUPERSEDED 再覆盖
5. 若无新约束：**保留**未结算开窗（中间站倒计时不断）；勿清窗
6. 路线完工：清窗
```

不新开 HTTP 事务；失败开窗 **不回滚** TrackOut（开窗写失败打错误日志 + Alarm，避免卡死过站；可开关 `mes.qtime.fail-open`）。

### 5.2 TrackIn 结算

在现有 TrackIn **业务校验序列中**，`Hold.assertNoActive` **之后**、改 processing **之前**：

```
1. 若无开窗 → 跳过
2. 若已超时（不限是否目标站）：
   a. HOLD / HOLD_ALARM → QueueTimeExpireHandler.enforceIfExpired（REQUIRES_NEW：Alarm+Hold+清窗）
   b. ALARM → AlarmService.raise；不清窗、不锁批
   c. 需拦截则抛「Queue Time 已超时，禁止开工」（Hold 已提交，本笔 TrackIn 回滚）
3. 未超时且 current = qtime_to_sort → 清窗，继续 TrackIn
4. 未超时且非目标站 → 保留开窗
```

### 5.2.1 到期扫描

`QueueTimeExpireJob`：`fixedDelay` 扫 wait/processing 且开窗已到期的 Lot。  
`ALARM` 策略跳过（防刷告警）。`HOLD` / `HOLD_ALARM` 调同一 `enforceIfExpired`。  
扫描空档由 TrackIn 再拦。无登录上下文，Hold 操作人 `SYSTEM`。

详见 `docs/架构/MES-SpringScheduled使用.md`。

**Skip / Rework / Off-Flow**

| 事务 | 行为 |
|------|------|
| Skip | 若跳过了 `qtime_to_sort`：清空窗 + 履历 `QTIME_CLEARED`；若落在 to 上：按 TrackIn 同规则结算 |
| Rework | 清空窗（回流重新开）；目标站 TrackOut 再开 |
| Off-Flow Enter | 保留窗（旁路不自动取消）；Resume 回锚后仍按原 to 结算；若产品要旁路暂停计时可后置 |

### 5.3 context 增量

`GET /track/lots/{lotId}/context`  
权限：`track:view`

有未结算开窗时**无论当前是否目标站**均返回 `queueTime`（中间站可见倒计时）：

```json
{
  "queueTime": {
    "fromSortNo": 10,
    "toSortNo": 40,
    "startedAt": "2026-08-06T10:00:00.000",
    "maxQueueMin": 120,
    "elapsedMin": 95,
    "remainMin": 25,
    "onViolate": "HOLD",
    "violated": false
  }
}
```

无开窗则 `queueTime: null`。  
前端：`QueueTimeBanner` 按 `startedAt + maxQueueMin` 本地秒级倒计时；临近/超时变色。

### 5.4 Dispatch 钩子（本期不消费）

`DispatchCandidate` / 推荐接口可附带 `qtimeRemainMin`（null=无）。  
**禁止**本期改排序权重；文档占位供 M4。

---

## 6. 前端

| 页 | 改动 |
|----|------|
| Route 草稿边表 | 列：最大等待(分)、超时策略；支持加 `time_link` / 邻站 normal+qtime |
| 版本只读 | 主路径标注 Q-Time 窗 |
| 现场台 | 有 `queueTime` 即展示倒计时（**含中间站**）；超时提示禁止开工；QTIME 解锁须填备注 |
| Hold 原因 | 字典含 QTIME_EXCEED；解锁备注对 QTIME 必填 |

---

## 7. 实施切片

| 切片 | 交付 | 依赖 |
|------|------|------|
| QT-1 | DDL：edge 三列 + Lot 五字段；`time_link` 类型；发布校验 | 边表已有 |
| QT-2 | TrackOut 开窗 + TrackIn 结算 + 履历；中间站保留开窗；配置默认策略 | Hold/Alarm |
| QT-3 | context + 现场台倒计时（全程）+ Route UI 边字段 | QT-2 |
| QT-4（后置） | Dispatch 按 remainMin 加权；Pre-Gate | Dispatch 成熟 |

建议挂在二期 **R2-6**，与 Off-Flow 可并行（无事务冲突），但 QT-2 须在 Hold 原因码预置后。

---

## 8. 验收

1. 邻站边配 `max_queue_min`，超时后扫描或 TrackIn 产生 active Hold，WIP 为 held  
2. `QTIME_EXCEED` 解锁填备注后目标站可 TrackIn；不填备注失败  
3. `on_violate=ALARM` 仅告警；默认仍拒进站（`mes.qtime.alarm-only-block=false` 才放行）  
4. 升版改大上限，在途 Lot 仍按开窗固化的 `qtime_max_min`  
5. 无配置边不过站无开窗、无拦截  
6. 不出现 Pre-Gate / 自动停进料行为  
7. **跨站 time_link**：触发站 Out 后中间站倒计时可见；到期在当前站 Hold  
8. 中间站 TrackOut（无本站新约束）后开窗字段仍在，不得被清掉  
9. TrackIn 拒进时 Hold **已落库**（不随 TrackIn 回滚）

---

## 9. 关联

- `MES-Route二期功能清单.md` §3.4 / R2-6  
- `MES-Step站属性接口设计.md`（`max_queue_min` 预留 → 本设计收口执行）  
- `MES-FutureHold接口设计.md`（Hold 管道同构；Q-Time ≠ Future Hold）  
- `MES-OffFlow接口设计.md`（明确不做 Queue Time 的边界至此收口）  
- `MES-半导体业务清单.md` §3 / 里程碑 M4  
- 实现：`QueueTimeSupport` / `QueueTimeExpireHandler` / `QueueTimeExpireJob` / `QueueTimeBanner` / Route 边表 QTime 列  
- 定时：`docs/架构/MES-SpringScheduled使用.md`
