# MES 告警（Alarm）— 架构设计

> 定位：业务/质量事件的**统一告警台**——记、认、关、推；**不**替代 Hold，**不**挡 TrackOut，**不**收机台 GEM  
> 范式：**各域只 `raise`，Alarm 管生命周期**；锁批仍只走 `HoldService`  
> 产品口径：工艺/班组长看见该响的、认领严重项；不是 Applied AMS / 独立 fab Alarm Hub  
> 对齐：`docs/架构/半导MES架构设计.md` §5.8 / §7；SPC D9；Hold 边界；业务清单 §8  
> 前提：`AlarmService.raise` 已被 QTime / ProcessTime / SPC 调用（今日只打日志）  
> 状态：**架构已定 · Alarm-1～3 已落地**（表 + raise + HTTP）；前端/WS 未开  
> 更新：2026-09-04

---

## 1. 边界

```
Alarm   = 码表 + 实例落库 + OPEN/ACK/CLEAR + 去重 + 列表/顶栏 + 可选推送
Hold    = 唯一锁批；Alarm 策略触发时只调 HoldService，不自造锁
Track   = 不因 Alarm 拒 In/Out（门禁仍只认 OOS / active Hold 等已有规则）
SPC     = OOC → raise(SPC_OOC)；不关心 ACK
EDC     = OOS Auto-Hold 仍在 EDC；不经 Alarm 锁批
Eqp/EAP = GEM S5F1 / ALID 后置；本期无 Adapter 进仓
History = Alarm 自有审计字段；不因 raise 写 mes_tx_log（Hold 仍写 HOLD）
FDC/OCAP= 另一条线；禁止进本包
```

**本切片做什么**

- 包 `com.mes.alarm`；对外 `AlarmService`（写）+ 查询门面（读）
- 表：码表 + 告警实例；状态 `OPEN → ACK → CLEARED`
- `raise`：落库、同键去重、可选策略动作、推 `alarm.active`
- Admin `/app/alarm` 真列表；严重未 ACK 顶栏
- 权限 `alarm:view` / `alarm:ack` / `alarm:clear`（或等价）

**本切片不做什么**

- SECS/GEM 设备告警采集、ALID 目录同步
- 邮件/短信/企微通道（可留钩子，一期不做）
- OCAP 工作流、FDC、独立 AMS 服务
- 告警洪水 ML、Pareto 分析产品化
- 因 Alarm 改 `canTrackOut` / 现场完工按钮

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Alarm 包内直接改 `mes_lot` / 绕过 Hold 锁批 | 锁批语义分裂 |
| P2 | Track / Dispatch 注入 Alarm Mapper 做门禁 | 过站与告警耦合 |
| P3 | `raise` 失败回滚调用方事务（采集、TrackOut） | 副作用不得破坏主路径 |
| P4 | 无码表硬编码级别/策略散落各模块 | 无法统一治理、必刷屏 |
| P5 | 同 `(code, entity)` OPEN 未 CLEAR 再插新行刷屏 | 告警疲劳（业界主因） |
| P6 | OOC 默认自动 Hold | 与 SPC 架构 D9 冲突；合格批被趋势锁死 |
| P7 | 一期收 GEM / 假装已有 Adapter | 范围绑死 Adapter，Alarm 永远排不上 |
| P8 | Alarm 页直查 `mes_spc_*` / `mes_edc_*` | 旁路 Facade；拆库迁不动 |
| P9 | 把 Hold 列表并进 Alarm 当同一实体 | 用户分不清「告」与「锁」 |

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 形态 | 同进程 `com.mes.alarm`；对齐 Hold / History | 一期不拆 AMS |
| D2 | 职责切分 | 源模块只 `raise(code, message, payload)`；生命周期在 Alarm | 调用方零状态机 |
| D3 | 与 Hold | 正交；策略「告警+Hold」时调已有 `HoldService.create` | 不二造锁批 |
| D4 | 与 Track | Alarm **不**参与门禁 | 拦截只认 Hold / EDC OOS 等 |
| D5 | 状态机 | `OPEN` → `ACK` → `CLEARED`；CLEAR 可自 OPEN（关闭） | 业界最小；无复杂处置树 |
| D6 | 级别 | 码表：`CRITICAL` / `WARNING` / `INFO` | 顶栏只盯 CRITICAL 未 ACK |
| D7 | 去重键 | 默认 `(code, entity_type, entity_id)`；OPEN 存在则刷新 `last_raise_at` + 次数，不新开 | 防洪水 |
| D8 | 实体 | `entity_type`+`entity_id`：`LOT` / `EQP` / `CHART` / `NONE` | payload 可冗余编码字段 |
| D9 | 策略 | 码表 `on_raise`：`NONE` \| `HOLD_LOT`（P1）；默认 `NONE` | SPC_OOC 默认 NONE |
| D10 | 推送 | 落库后发 WS `alarm.active`（或域内事件再推）；MQ topic 后置 | 架构已留口 |
| D11 | 调用契约 | `raise` 吞异常、只打日志；返回 void 或 alarmId（不强制调用方处理） | 同 SPC 对 raise 的假设 |
| D12 | 权限 | HTTP 鉴权；`AlarmService.raise` **不**鉴权（系统调用） | 同 EdcFacade / Hold 系统挂 |
| D13 | 应急阀 | `mes.alarm.enabled` 默认 true；false 时 raise 空操作（仍可查历史） | 风暴时止血 |
| D14 | 前端 | 替换 mock；Admin 告警台；现场可只读严重条（可选） | UI 设计已列 P1 |

**刻度**：一期只消费 **MES 内业务/质量 raise**（QTime、ProcessTime、SPC_OOC…）。设备 GEM 为 P2。

---

## 3. 依赖与数据流

```
QTime / ProcessTime / SPC / （日后 Eqp Adapter）
        │
        ▼
 AlarmService.raise(code, message, payload)
        ├── 查码表（级别、on_raise、enabled）
        ├── 去重：OPEN 同键 → bump count / last_raise_at
        ├── 否则 insert OPEN
        ├── on_raise=HOLD_LOT → HoldService.create（失败只打日志，不回滚 raise 已提交语义见实现）
        └── 推送 alarm.active

人 ──► ACK / CLEAR（权限）──► 更新状态 + 审计人时
Admin / 顶栏 ──► 查询 Facade（只读实例 + 码表）
```

现有调用码（须进码表种子，级别可调）：

| code | 典型级别 | 建议 on_raise（一期） |
|------|----------|----------------------|
| `SPC_OOC` | WARNING | NONE |
| `QTIME_EXCEED` | WARNING | NONE（Hold 已由 QTime 自己挂） |
| `QTIME_OPEN_FAIL` | WARNING | NONE |
| `PROCESS_TIME_VIOLATION` | WARNING | NONE（超 max Hold 已在 ProcessTime） |

说明：今日部分路径 **已经 Hold 再 raise**。一期策略默认 `NONE`，避免双重挂锁；P1 再收敛「谁负责 Hold」。

---

## 4. 门面契约

```
AlarmService（写，系统）
  raise(code, message, payload)           // 落库/去重/策略；吞异常

AlarmFacade 或 AlarmQuery（读，HTTP）
  list(status?, level?, code?, from, to, limit) → 页
  get(id) → 详情（含 payload、次数、关联实体）
  ack(id, remark?)
  clear(id, remark?)
  listActiveCritical() → 顶栏
```

`payload`：`Map` → JSON 落库；约定常用键 `lotId, lotNo, eqpId, eqpCode, chartId, stepId, ruleCode`（有则写，不强校验）。

调用方 **禁止** 依赖返回值做事务分支；需要联动走码表策略或本域自管（如 QTime 已 Hold）。

---

## 5. 表（种子，落地时出 DDL）

### 5.1 `mes_alarm_code`

| 字段 | 说明 |
|------|------|
| code | PK，如 `SPC_OOC` |
| name | 展示名 |
| level | `CRITICAL` / `WARNING` / `INFO` |
| on_raise | `NONE` / `HOLD_LOT`（P1 启用） |
| hold_reason_code | `on_raise=HOLD_LOT` 时用的 Hold 原因码 |
| enabled | |
| remark | |
| 审计 | 同其它字典 |

### 5.2 `mes_alarm`

| 字段 | 说明 |
|------|------|
| id | 雪花 |
| code | 逻辑引用码表 |
| level | 触发时快照 |
| status | `OPEN` / `ACK` / `CLEARED` |
| message | |
| entity_type / entity_id | 去重与筛选；无实体则 `NONE` / `0` |
| dedupe_key | 冗余或生成列：`code|type|id` |
| payload_json | |
| raise_count | 去重累加，默认 1 |
| first_raise_at / last_raise_at | |
| ack_by / ack_at / ack_remark | |
| clear_by / clear_at / clear_remark | |
| 审计 / deleted | 软删慎用；关闭走 CLEARED |

索引：

- `uk_alarm_open_dedupe (dedupe_key)` **部分唯一**：仅 `status=OPEN`（MySQL 8 函数/生成列或应用层保证二选一，落地拍板）
- `idx_alarm_status_level_time (status, level, last_raise_at)`
- `idx_alarm_entity (entity_type, entity_id, last_raise_at)`

无 OPEN 部分唯一时：应用层 `SELECT OPEN FOR UPDATE` 再 bump，禁止并发双插。

---

## 6. 行为（实现必须遵守）

1. `mes.alarm.enabled=false` → raise 直接 return。  
2. 码不存在或 `enabled=0` → 打 warn，仍可按默认 WARNING + NONE 落一条（或拒写，落地二选一：**建议仍落**，避免丢信号）。  
3. 计算 `dedupe_key`；存在 OPEN → `raise_count++`，更新 `last_raise_at`、message/payload（以最新为准），**不**改 ACK 态（已 ACK 又 raise：见下）。  
4. **已 ACK 同键再 raise**：新开 OPEN（或回到 OPEN 清 ACK——**定：新开 OPEN**，保留 ACK 历史行已 CLEARED/保持 ACK 行不变）。推荐：ACK 后同键再发 = 新 OPEN 行。  
5. 新 OPEN：insert；按 `on_raise` 调 Hold（仅 `entity_type=LOT` 且能解析 lotId）。  
6. ACK：仅 `OPEN→ACK`；要权限；写人时。  
7. CLEAR：`OPEN` 或 `ACK` → `CLEARED`；要权限。  
8. 全程异常不抛回调用方。  
9. 列表默认不回已 CLEARED 超 N 天（配置），防表膨胀；物理归档后置。

---

## 7. HTTP / 权限 / 配置

| 项 | 选择 |
|----|------|
| 前缀 | `/alarm` |
| 列表/详情/顶栏 | `alarm:view` |
| 确认 | `alarm:ack` |
| 关闭 | `alarm:clear` |
| 码表维护 | `alarm:edit`（可后置，一期种子） |
| 菜单 | `/app/alarm` |
| 配置 | `mes.alarm.enabled`（默认 true） |

WebSocket：`alarm.active` 推送 OPEN 变更摘要（id、code、level、message、entity）。鉴权同现网 WS。

---

## 8. 与兄弟能力

| | Alarm | Hold | SPC | EDC OOS |
|--|--|--|--|--|
| 问什么 | 要不要人知道/认领 | 批能不能动 | 过程稳不稳 | 这趟数合不合格 |
| 改 Lot？ | 默认否 | 是（held） | 否 | 可选 Auto-Hold |
| 挡 Track？ | 否 | 是 | 否 | 拒 Out / Hold |
| 触发 | raise | create | 判异后 raise | 采集判定 |

```
感知 ── Alarm
拦截 ── Hold / EDC 门禁
趋势 ── SPC（结果进 Alarm）
```

---

## 9. 分期

| 期 | 交付 | 不做 |
|----|------|------|
| **P0** | 码表+实例表；raise 落库去重；ACK/CLEAR；真列表+顶栏；WS；种子码 | 策略 Hold、通知渠道、GEM |
| **P1** | `on_raise=HOLD_LOT`；未 ACK 升级（定时或班次）；Dispatch 可读 critical 禁派钩子 | OCAP、邮件网关 |
| **P2** | Adapter→raise 设备码；Pareto；与 OCAP/知识库挂接 | 独立 AMS 产品形态 |

切片锁定：`MES-Alarm一期功能清单.md`（Alarm-1～4 = 本期 P0；策略 Hold = P1）。

---

## 10. 验收（架构）

1. QTime / ProcessTime / SPC 仍只调 `AlarmService.raise`，无 Mapper 旁路。  
2. `raise` 异常不导致采集或 TrackOut 失败。  
3. 同键连续 raise，OPEN 仅一行（或文档 D7 语义可测）。  
4. Track 模块无「因 Alarm 拒站」逻辑。  
5. Hold 仍只经 `HoldService`；Alarm 无直接改 Lot 状态。  
6. `/app/alarm` 非 mock；CRITICAL 未 ACK 可在顶栏/页头可见。  
7. `com.mes.alarm` 无 `com.mes.edc.mapper` / `com.mes.spc.mapper`。

---

## 11. 关联

- `MES-Alarm一期功能清单.md`
- `docs/架构/半导MES架构设计.md` §5.8
- `docs/模块/SPC（统计过程控制）模块/MES-SPC架构设计.md` D9
- `docs/模块/Hold（锁批）模块/MES-Hold功能文档.md`
- `docs/模块/Route（工艺路线）模块/MES-QueueTime接口设计.md`
- `docs/模块/Track（执行引擎）模块/MES-ProcessTime接口设计.md`
- `docs/业务清单/MES-半导体业务清单.md` §8
- `docs/UI/MES-UI设计.md` Hold/Alarm
