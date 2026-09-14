# MES 告警（Alarm）— 一期功能清单

> 前提：`AlarmService.raise` 已被 QTime / ProcessTime / SPC 调用；Hold 最小集已齐  
> 对齐：`MES-Alarm架构设计.md` · `docs/架构/半导MES架构设计.md` §5.8  
> 更新：2026-09-04  
> 状态：**一期 P0（Alarm-1～4）已完成**；P1 见 `MES-Alarm-P1功能清单.md`

---

## 0. 目标

先交付「该响的能看见、能认领、能关闭」；不挡过站、不因 Alarm 锁批、不上 GEM / OCAP。

原则：

- 各域只调 `AlarmService.raise`；生命周期只在 Alarm
- 锁批只经 `HoldService`；一期 `on_raise` 全 `NONE`
- TrackOut / 现场完工 **零改动**
- `raise` 失败不得回滚采集或 Track

---

## 1. 范围总览

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | 码表 `mes_alarm_code` + 实例 `mes_alarm`；种子码 | ✅ |
| P0 | `raise` 落库 + 去重 + `mes.alarm.enabled` | ✅ |
| P0 | ACK / CLEAR；权限 `alarm:view` / `ack` / `clear` | ✅ |
| P0 | HTTP `/alarm` | ✅ |
| P0 | Admin `/app/alarm` 真列表 + CRITICAL 顶栏 | ✅ |
| P0 | WS `alarm.active`（后端推 + 前端订） | ✅ |
| P1 | `on_raise=HOLD_LOT`；未 ACK 升级；禁派钩子 | 清单：`MES-Alarm-P1功能清单.md`（Hold 策略先做；升级/禁派后置） |
| P2 | GEM 进仓、Pareto、OCAP、独立 AMS | 后置 |

---

## 2. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| Alarm-1 | DDL 码表/实例；权限种子；`mes.alarm.enabled`；种子码 | ✅ |
| Alarm-2 | `raise` 落库去重 + 实体解析；吞异常；**调用方零改签名** | ✅ |
| Alarm-3 | 查询门面 + HTTP `/alarm`（list/get/ack/clear/顶栏） | ✅ |
| Alarm-4 | Admin 换 mock；顶栏严重条；WS `alarm.active` | ✅ |

建议顺序：Alarm-1 → 2 → 3 → 4。  
**禁止** Alarm-2 先于 Alarm-1（无表必空转或再造日志）。  
**禁止** Alarm-4 直查 `mes_spc_*` / `mes_edc_*`。  
**禁止** 一期实现 `HOLD_LOT`（属 P1 / Alarm-5）。

已有库上线前执行 `server/src/main/resources/db/migrate_alarm.sql`（表 + 权限 + 种子码）；并入 `schema.sql`。

### Alarm-1（表 / 权限 / 配置）✅

- 表结构锁死架构 §5：`mes_alarm_code`、`mes_alarm`  
- 去重：应用层 OPEN 同 `dedupe_key` 只一行（或部分唯一，落地二选一写进 migrate 注释）  
- 权限：`270 alarm:view`（原 `alarm:list`）；`271 alarm:ack`；`272 alarm:clear`；`273 alarm:edit`  
- `mes.alarm.enabled` 默认 true  
- 种子码：`SPC_OOC` / `QTIME_EXCEED` / `QTIME_OPEN_FAIL` / `PROCESS_TIME_VIOLATION`；级别 WARNING；`on_raise=NONE`  
- 本切片 **无** raise 改行为（由 Alarm-2 换真）、无前端换 mock  

落地：`migrate_alarm.sql` · `schema.sql` · `MesAlarmCode` / `MesAlarm` · Mapper · `application.yml`
### Alarm-2（raise 成真）✅

- 替换 `AlarmServiceImpl` 日志 stub → 落库  
- 行为锁死架构 §6：enabled 阀、未知码仍落、OPEN bump、ACK 后再 raise 新开 OPEN  
- 从 payload 解析 `entity_type` / `entity_id`（有 `lotId`→LOT；有 `eqpId`→EQP；有 `chartId`→CHART；否则 NONE/0）  
- **不**调 Hold；**不**改 QTime / ProcessTime / SPC 调用点签名  
- `REQUIRES_NEW` + 吞异常；采集 / TrackOut 不因 raise 失败  
- 本切片无 HTTP  

落地：`AlarmServiceImpl`

### Alarm-3（HTTP）✅

- 前缀 `/alarm`；list/get/critical 要 `alarm:view`；ack 要 `alarm:ack`；clear 要 `alarm:clear`  
- 读路径走 `AlarmFacade`；写 ACK/CLEAR 鉴权在 Controller  
- `GET /alarm/critical`：status∈(OPEN,ACK) 且 level=CRITICAL（一期种子无 CRITICAL 时可空）  
- 无旁路 SQL；无前端  

落地：`AlarmFacade` · `AlarmFacadeImpl` · `MesAlarmController` · `AlarmVO` / `AlarmQuery`

### Alarm-4（Admin + WS）✅

- `/app/alarm`：列表筛选 / 详情 Drawer / ACK·CLEAR；顶栏铃铛接 `/alarm/critical` + STOMP  
- STOMP `/ws` → `/topic/alarm.active`；CONNECT 校验 Sa-Token  

落地：`api/alarm.ts` · `lib/alarmWs.ts` · `AlarmPage` · `AdminShell`；后端 `WebSocketConfig` · `AlarmWsPublisher`

---

## 3. 配置

| 键 | 默认 | 说明 |
|----|------|------|
| `mes.alarm.enabled` | true | false：`raise` 空操作；查询仍可读历史 |

---

## 4. 验收（总）

见 `MES-Alarm架构设计.md` §10。

必验：

1. SPC OOC / QTime 超时后库中有对应 `mes_alarm`（或 bump `raise_count`）  
2. 同 lot 连续同码 raise：OPEN 仅一行，`raise_count≥2`  
3. ACK 后再 raise：新 OPEN 行  
4. ACK/CLEAR 权限拒绝无码账号  
5. `raise` 抛错模拟时采集 / TrackOut 仍成功  
6. Track 无「因 Alarm 拒站」；Lot 不因 `SPC_OOC` 自动 Hold  
7. `/app/alarm` 非 mock；`mes.alarm.enabled=false` 时新 raise 不落库  

---

## 5. 关联

- `MES-Alarm架构设计.md`
- `MES-Alarm-P1功能清单.md`
- `docs/架构/半导MES架构设计.md` §5.8
- `docs/架构/MES-实施进度与下一步.md`
- `docs/模块/SPC（统计过程控制）模块/MES-SPC架构设计.md` D9
- `docs/模块/Hold（锁批）模块/MES-Hold功能文档.md`
- `docs/UI/MES-UI设计.md`
