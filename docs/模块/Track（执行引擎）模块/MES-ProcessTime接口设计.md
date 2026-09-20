---
type: 接口设计
module: Track
status: done
slices: []
aligns: []
updated: 2026-08-11
---

# Process Time — 接口设计（架构）

> 范围：Route 步骤加工时长上下限 + TrackIn 开计时 + TrackOut 校验 + context 展示  
> 原则：定义进版本快照；**不新开 Track 事务**；**&lt;min 拒 Out**、**&gt;max 允许 Out + 自动 Hold**；状态仍只由 Track 写  
> 产品口径：P0 **站内加工门禁**（防假过站 / 超时失控）；**不是** Queue Time，**不是** RPT/调度  
> 对齐：`MES-Track二期功能清单.md` §2.1；`docs/业务清单/MES-半导体业务清单.md` §3 Process Time  
> 前提：TrackIn/Out、Route 版本步骤快照、Queue Time 已落地；Abort 清计时随 Abort 切片收口  
> 状态：**T2-1 已落地**（后端 + 工序库配置 + 现场台 Banner）  
> 更新：2026-08-11

---

## 1. 边界

```
Route     = 步骤配 min_process_min / max_process_min 进 route_version 快照
Track     = TrackIn 开计时；TrackOut 结算校验；context 暴露已耗/上下限
Hold      = 超 max 且 Out 成功后 `HoldService.create(PROCESS_TIME_EXCEED)`（落 wait）
Alarm     = 超 max 默认告警；末站 completed 无法 Hold 时强制 Alarm
Dispatch  = 不消费；禁止当 RPT / Critical Ratio 用
Adapter   = 不参与计时；设备真实跑程属 EAP/FDC（P2+）
```

**本切片做什么**

- 版本步骤：`min_process_min` / `max_process_min`（可空=该界不控）
- Lot 运行态：`process_started_at`（TrackIn 成功写入）
- TrackOut：`elapsed < min` → **拒绝**；`elapsed > max` → **允许 Out + 自动 Hold**
- Abort / Rework / 全批 Scrap → 清计时
- 在途只认放行时 `route_version_id` 步骤快照；升版不影响在途
- context / 现场台：加工中展示已耗、距 min、距 max；超 max 提示「可完工将锁批」

**本切片不做什么**

- Remaining Process Time（RPT）/ Critical Ratio / 派工加权
- 与设备上报加工时长对账
- 强行放行跳过 Hold（P2）
- 超限自动 Scrap / 自动 Rework
- Pre-Gate、QMS、跨站加工窗
- 用 Process Time 替代 Queue Time

**禁止**

- 运行时读直播 `mes_step` 做卡控（须读 Lot 所属版本 `mes_route_step` 快照）
- 非 Track 路径写 `process_started_at` / 清计时
- 旁路改 status 冒充「加工完成」以逃避时长校验
- 把 Q-Time 开窗字段与 Process Time 混用同一套 Lot 字段
- 超 max 后仍卡在 processing 无出口（须 Out+Hold 或 Abort）

**与兄弟能力分工**

| | Queue Time | Process Time | Abort | EDC 门禁 |
|--|--|--|--|--|
| 问什么 | 站间**等待**是否超窗 | 站内**加工**是否在窗内 | 加工中能否中止回 wait | 量测是否合格 |
| 定义 | Route 边/time_link | Route **步骤** min/max | — | 站/EDC 配置 |
| 计时 | Out(from)→In(to) | In→Out **同站** | 清 Process 计时 | — |
| 违规 | Hold/Alarm（可拒 In） | **&lt;min 拒 Out；&gt;max 允许 Out + 自动 Hold** | — | 拒 Out |
| 状态机 | 无新状态 | 无新状态 | processing→wait | 无新状态 |

**状态机**：无新 `Lot.status`。仍 `wait` / `processing` / `held`。

```
wait ──TrackIn──► processing
  └ 本站快照存在 min 或 max：写 process_started_at = now
  └ 均空：process_started_at = null（本站不控）

processing ──TrackOut──►
  └ 读快照本站 min/max
       ├ 均空：清 process_started_at，正常 Out
       ├ elapsed < min：拒 Out；计时保留
       ├ elapsed > max：允许 Out，清计时；落下一站 wait 后 Hold(PROCESS_TIME_EXCEED)；可选 Alarm
       └ 窗内：清 process_started_at，正常 Out

processing ──Abort──► wait（同站）
  └ 清 process_started_at；不推进站

Rework / 全批 Scrap：清 process_started_at
```

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 约束落点 | **步骤**：`mes_route_step.min/max_process_min` | 站内窗，非 from→to；与 Q-Time 边模型正交 |
| D2 | 计时点 | TrackIn **成功提交时刻** → TrackOut **请求时刻** | 对齐事务边界；不依赖设备时钟 |
| D3 | 运行态 | Lot 单字段 `process_started_at` | 一 Lot 同时仅一站 processing；履历靠 tx_log |
| D4 | min 违规 | **硬拒 TrackOut** | 防假过站；等够再出 |
| D5 | max 违规 | **允许 Out + 自动 Hold**（原因码 PROCESS_TIME_EXCEED） | 腾机台；超时变质量处置，避免卡死 processing |
| D6 | 末站完工超 max | 无法 Hold completed → **强制 Alarm** | Hold 仅 wait/processing |
| D7 | Alarm | `mes.process-time.alarm-on-max-exceed` 默认 true | 超 max 可感知 |
| D8 | 强行放行跳过 Hold | **不做**（P2 专码） | 权限/审计未就绪 |
| D9 | 与 Abort | Abort **必须**清计时 | 中止后同站再 In 重新开表 |
| D10 | 与设备 | 不计 Adapter 时长 | MES 认 Track 事务时；对账后置 |
| D11 | fail 策略 | 仅 &lt;min / 缺钟 **回滚本笔 Out** | &gt;max 不拒 Out |

**刻度**：分钟整数；`elapsedMin = ceil((now - process_started_at) / 60s)` 或 floor——**拍板：floor 到分钟，不足 1 分钟按 0**（与短工序 min≥1 配合；若需秒级后置配置）。

**空值语义**

| min | max | 行为 |
|-----|-----|------|
| null | null | 本站不控；不写/不校验 `process_started_at`（In 仍可写 null） |
| 有 | null | 只校验下限 |
| null | 有 | 只校验上限 |
| 有 | 有 | 须 `1 ≤ min ≤ max`（发布校验） |

---

## 3. 数据契约

### 3.1 步骤快照 `mes_route_step`

| 字段 | 说明 |
|------|------|
| min_process_min | INT NULL；空=不控下限 |
| max_process_min | INT NULL；空=不控上限 |

```sql
ALTER TABLE mes_route_step
  ADD COLUMN min_process_min INT NULL COMMENT '站内加工下限分钟' AFTER /* 现有末列 */,
  ADD COLUMN max_process_min INT NULL COMMENT '站内加工上限分钟' AFTER min_process_min;
```

发布校验：

- 非空则各自 `≥ 1`
- 两者皆非空则 `min_process_min ≤ max_process_min`
- 运行时只读 Lot.`route_version_id` 下对应 `current_sort_no` 步骤行

直播 `mes_step`：**不**作为执行真相；工序库可配 `min/max_process_min`，**发布进版本**写入 `mes_route_step`。运行时只读快照。

DDL：`server/src/main/resources/db/migrate_process_time.sql`（幂等 ALTER + Hold 原因）；已合入 `schema.sql`。

### 3.2 Lot 运行态（仅 Track 写）

| 字段 | 类型 | 说明 |
|------|------|------|
| process_started_at | DATETIME(3) NULL | TrackIn 成功且本站有 min 或 max 时写入；否则保持 null |

与 `qtime_*` 并存、互不覆盖。

**落库注意**：MyBatis-Plus `updateById` 默认跳过 null，清计时须 `LambdaUpdateWrapper.set(process_started_at, null)`（见 `ProcessTimeSupport.clearPersisted`），并刷新乐观锁 `version`。

### 3.3 事务履历 `mes_tx_log`

| 项 | 约定 |
|----|------|
| TRACK_IN | `ext_json` 可含 `processStartedAt`（若开计时） |
| TRACK_OUT 成功 | `ext_json` 含 `processElapsedMin`、`minProcessMin`、`maxProcessMin`；超 max 另带 `processTimeExceededMax: true` |
| TRACK_OUT 失败（&lt;min / 缺钟） | 不改 Lot；**不插** tx_log；API 抛 `BusinessException`（错误码前缀见下） |
| REWORK | 清计时（已落地） |
| ABORT / SCRAP(全批) | 设计须清；Abort 未落地前预留同一 `clearPersisted`；Scrap 仅 `wait` 可做，通常已无计时 |

错误码：

| code | 场景 |
|------|------|
| `PROCESS_TIME_TOO_SHORT` | elapsed < min（拒 Out） |
| `PROCESS_TIME_CLOCK_MISSING` | 本站应控但 `process_started_at` 空（拒 Out） |

超 max 不抛业务错误码拒 Out；履历 `processTimeExceededMax=true` + Hold 原因码 **`PROCESS_TIME_EXCEED`（id=8008）**。

Alarm 码：`PROCESS_TIME_VIOLATION`（payload 含 lotId / elapsed / max 等）。

### 3.4 配置项

```yaml
mes:
  process-time:
    enabled: true
    alarm-on-max-exceed: true   # 超 max：Hold 同时 Alarm；末站无法 Hold 时强制 Alarm
```

---

## 4. Route 配置接口

**配置入口（已落地）**：Route → **工序库** 创建/编辑步骤字段 `minProcessMin` / `maxProcessMin`；发布版本时写入 `mes_route_step` 快照。  
权限 `route:edit`；校验 `ProcessTimeBounds.validate`（≥1 且 min≤max）。

```json
{
  "stepCode": "ETCH-01",
  "minProcessMin": 30,
  "maxProcessMin": 120
}
```

`GET` 工序 / 版本步骤：原样回传。  
升版：拷贝 `min_process_min` / `max_process_min`（与其它步骤属性同策略）。

---

## 5. Track 执行

### 5.1 TrackIn 开计时

在现有 TrackIn **成功提交后**（已 → processing）：

```
1. 读 Lot.route_version_id + current_sort_no → 快照步骤
2. 若 min、max 皆空 → process_started_at = null；结束
3. 否则 process_started_at = now（覆盖任何残留）
4. tx_log TRACK_IN ext 带 processStartedAt
```

开计时写失败：打错误日志 +（可选）Alarm；**默认 fail-closed 倾向**：若本站应控而写入失败，应回滚 TrackIn（与 D10 一致，避免「已 processing 无时钟」）。实现须同事务写 Lot。

### 5.2 TrackOut 结算

在现有 TrackOut **业务校验序列中**，`Hold.assertNoActive` 之后、改站/改 status **之前**：

```
1. assertOnTrackOut：读快照 min/max
2. 若皆空 → SettleResult.skipped；继续 Out
3. 若 process_started_at 空 → 拒 Out（PROCESS_TIME_CLOCK_MISSING）
4. elapsedMin = floor 分钟差
5. 若 min 非空且 elapsedMin < min → 拒 Out（TOO_SHORT）；计时保留
6. 若 max 非空且 elapsedMin > max → 标记 exceededMax，**不拒** Out
7. 清 process_started_at（内存 + clearPersisted）；推进站；履历 putExt
8. queueTimeSupport.openAfterTrackOut（开站间窗）
9. disposeAfterTrackOut：若 exceededMax
   a. wait/processing → Hold(PROCESS_TIME_EXCEED)；刷新 Lot status/version
   b. alarm-on-max-exceed 或无法 Hold（如末站 completed）→ Alarm
10. Future Hold POST：仅当 Lot 尚未 held（已被 PT Hold 则跳过）
```

&lt;min 失败时不改站。超 max 先出站腾机，再锁批处置。

旁路末站 Out→Resume：先清计时，Resume 后再 `disposeAfterTrackOut`（超时仍可 Hold）。

### 5.3 清计时矩阵

| 事务 | 行为 |
|------|------|
| TrackOut 成功 | 清 |
| TrackOut 失败（时长） | **保留** |
| Abort | **清**（T2-2；钩子复用 `clearPersisted`） |
| Rework | **清**（已落地） |
| 全批 Scrap | 设计须清；现 Scrap 仅 wait，通常已无计时 |
| 部分 Scrap / Split / Merge | **保留**（若不再 processing 则应清） |
| Skip | 不经 processing 则无计时；若异常残留则清 |
| Hold | **保留**（计时继续走；Hold 拦住 Out） |
| Off-Flow Resume | 旁路末站路径会清；Enter 前若 Abort 则跟 Abort |

### 5.4 context 增量

`GET /track/lots/{lotId}/context`  
权限：`track:view`

仅 `status=processing` 且本站快照有 min 或 max 时返回：

```json
{
  "processTime": {
    "startedAt": "2026-08-10T10:00:00.000",
    "minProcessMin": 30,
    "maxProcessMin": 120,
    "elapsedMin": 12,
    "remainToMinMin": 18,
    "remainToMaxMin": 108,
    "canTrackOutByTime": false,
    "exceededMax": false,
    "willHoldOnOut": false
  }
}
```

无约束或非 processing：`processTime: null`。  
`canTrackOutByTime`：仅看是否已过 min（超 max 仍为 true）。  
`willHoldOnOut`：已超 max，提示出站后锁批。

前端：未到 min 禁用完工；超 max 可点完工，琥珀提示「出站后自动锁批」。

### 5.5 与 Queue Time 同笔事务顺序

TrackOut 成功路径上二者无冲突（PT 在提交前校验；QT 在成功后开窗）。  
TrackIn：先 PT 无关；QT 在 In 前结算——**先 Q-Time 结算，再 In，再 PT 开计时**。

---

## 6. 接口一览

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| （扩） | Route 步骤写入/读取 | `route:edit` / `route:view` | min/maxProcessMin |
| POST | `/track/track-in` | `track:track-in` | 钩子开计时 |
| POST | `/track/track-out` | `track:track-out` | 钩子校验 |
| GET | `/track/lots/{lotId}/context` | `track:view` | `processTime` |

无新 HTTP 事务码。P2 强行放行另议 `track:process-time-override`。

---

## 7. 前端（已落地）

| 页 | 改动 |
|----|------|
| Route → 工序库 | 最小/最大加工(分)；保存走 `ProcessTimeBounds` |
| TrackPage | `ProcessTimeBanner`：太短禁用完工；超时琥珀「可完工，出站后自动锁批」 |
| API | `TrackProcessTime`：`canTrackOutByTime` / `exceededMax` / `willHoldOnOut` |

文案与 Q-Time Banner 区分：**站内加工** vs **站间等待**。  
Out 失败仅展示 `PROCESS_TIME_TOO_SHORT` / `PROCESS_TIME_CLOCK_MISSING`（超时不失败）。

---

## 8. 实施切片

| 切片 | 交付 | 状态 |
|------|------|------|
| PT-1 | DDL：`mes_step`/`mes_route_step` 两列 + `lot.process_started_at` + Hold 8008；发布校验 | ✅ |
| PT-2 | `ProcessTimeSupport`：In 开计时 + Out 校验/处置 + 履历 ext | ✅ |
| PT-3 | context + `ProcessTimeBanner`；工序库配置 | ✅ |
| PT-4 | Abort 清计时收口；全批 Scrap 防御清（可选） | ⏳ 随 T2-2 |
| PT-5（后置） | 强行放行跳过 Hold；设备时长对账 | ⏳ P2 |

挂 **T2-1**（已完成）。`alarm-on-max-exceed` 已随 PT-2 落地。

---

## 9. 验收

1. 步骤只配 max，超时 TrackOut **成功**且产生 active Hold（PROCESS_TIME_EXCEED）  
2. 步骤只配 min，未到时 TrackOut 失败；到达后可 Out  
3. 皆空：行为与现网一致，context.processTime=null  
4. 升版改大 max，在途 Lot 仍按**放行版本**步骤约束  
5. Rework（及后续 Abort）后 `process_started_at` 空；再 In 重新计时  
6. 与 Q-Time 同 Lot 可并存，字段不互相覆盖  
7. 无 RPT/派工权重变化  
8. context：超 max 时 `canTrackOutByTime=true` 且 `willHoldOnOut=true`  
9. 末站完工超 max：Out 成功、Lot=`completed`、有 Alarm、无 Hold  

---

## 10. 代码落点（实现索引）

| 层 | 路径 |
|----|------|
| 核心 | `com.mes.track.support.ProcessTimeSupport` |
| 钩子 | `TrackServiceImpl`：TrackIn / TrackOut / Rework / context |
| 校验 | `com.mes.route.support.ProcessTimeBounds`；`MesStepServiceImpl` 保存 |
| VO | `TrackProcessTimeVO` ← `TrackContextVO.processTime` |
| Lot | `MesLot.processStartedAt` |
| 快照 | `MesRouteStep` / `MesStep`：`minProcessMin` / `maxProcessMin` |
| DDL | `db/migrate_process_time.sql`；`schema.sql` |
| 配置 | `application.yml` → `mes.process-time.*` |
| 前端 | `web/src/components/track/ProcessTimeBanner.tsx`；`TrackPage.tsx`；工序库 `RoutePage` |

人话摘要：

- 开工按下秒表（本站配了 min/max 才写）  
- 太短不让出站；太长先出站腾机台，再自动锁批  
- 末站已经完工锁不了批 → 至少告警一声  

---

## 11. 关联

- `MES-Track二期功能清单.md` §2.1 / T2-1  
- `docs/模块/Route（工艺路线）模块/MES-QueueTime接口设计.md`（站间；正交）  
- `MES-Track数据库设计.md`（Lot 运行态扩展）  
- `MES-TrackAbort接口设计.md`（T2-2；清计时契约以本文 §5.3 为准）  
- `docs/业务清单/MES-半导体业务清单.md` §3  
- 大厂对照：Camstar Min/Max Time Windows；非 Opcenter RPT  
