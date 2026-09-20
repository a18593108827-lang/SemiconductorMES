---
type: 功能清单
module: Alarm
status: done
slices: []
aligns: []
updated: 2026-09-14
---

# MES 告警（Alarm）— P1 功能清单（Alarm → Hold）

> 前提：Alarm-1～4（P0）已齐；Hold 最小集已齐；`raise` 已被 QTime / ProcessTime / SPC 调用  
> 对齐：`MES-Alarm架构设计.md` §2 D3/D9 · §6 · §9 P1 · 业务清单 §8  
> 产品口径：码表策略「告警 + 锁批」；**不**替代 Hold，**不**因 Alarm 挡 TrackOut，**不**做 GEM/OCAP  
> 更新：2026-09-14  
> 状态：**Alarm-5 ✅ · Alarm-6 ✅ · Alarm-7 ✅**

---

## 0. 目标

交付 `on_raise=HOLD_LOT`：新开 OPEN 且实体为 Lot 时，经 `HoldService` 自动锁批；告警仍可见、可 ACK/CLEAR。

原则：

感知在 Alarm，拦截只在 Hold；Track **零改**门禁

- 策略真相在 `mes_alarm_code`；调用方签名不变，仍只 `raise(code, message, payload)`
- `raise` 落库成功优先；Hold 失败 **只打日志**，不得回滚告警、不得拖垮调用方
- 已自挂 Hold 的码（QTime / ProcessTime）与 SPC_OOC **默认保持** `NONE`，防双重锁批
- 禁宣称 AMS / OCAP / 设备告警联动

---



## 1. 架构约束（必须遵守）


| #   | 约束      | 说明                                                                                         |
| --- | ------- | ------------------------------------------------------------------------------------------ |
| A1  | 高内聚     | 读码表策略、解析 Lot、调 Hold、记失败日志 **全部**在 `AlarmService`（`doRaise` 策略段）；禁止散落到 SPC/QTime/Controller |
| A2  | 低耦合     | Alarm **只**依赖 `HoldService` 接口；**零** `mes_hold` / `mes_lot` Mapper；Hold **零** Alarm Mapper |
| A3  | 锁批 SSOT | 改 Lot/`held` / WIP / `tx_log(HOLD)` **只**经 `HoldService.create`；Alarm 禁直接改 Lot             |
| A4  | 门禁正交    | Track / Dispatch **不**因 Alarm 实例拒站；拦货只认 active Hold（及既有 EDC OOS 等）                         |
| A5  | 事务隔离    | `raise` 保持 `REQUIRES_NEW` + 吞异常；Hold 失败不得导致 `mes_alarm` 回滚                                 |
| A6  | 策略触发点   | **仅新 insert OPEN** 执行 HOLD_LOT；同键 OPEN **bump 不再挂锁**                                       |
| A7  | 实体门槛    | 仅 `entity_type=LOT` 且 `entity_id>0`；EQP/CHART/NONE 有策略也跳过并 warn                            |
| A8  | 幂等      | 目标 Lot 已有 active Hold → **跳过** create，info 日志；不抛、不重试刷屏                                     |
| A9  | 自挂码禁策略  | `AlarmSelfHoldCodes`（如 ProcessTime/QTime）禁配、禁跑 HOLD_LOT；新自挂码须加入集合                         |
| A10 | 灰度      | 种子码默认仍 `NONE`；靠码表改 `HOLD_LOT` 启用；可选总阀（见 §4）                                                |
| A11 | 并发      | 见 §6；禁止无锁双开 OPEN、禁止无保护双挂 Hold                                                              |


**禁止**


| #   | 禁止                                                       | 理由                      |
| --- | -------------------------------------------------------- | ----------------------- |
| P1  | Alarm 包 `update mes_lot` / 自写 `mes_hold`                 | 锁批语义分裂                  |
| P2  | Track/SPC/EDC 旁路读 `on_raise` 自己 Hold                     | 策略双通道；与 A1 冲突           |
| P3  | Hold 失败回滚已 insert 的 OPEN                                 | 丢告警信号；违背 D11            |
| P4  | bump 同 OPEN 每次再 `create` Hold                            | 已 held 批刷失败日志 / 语义噪音    |
| P5  | 默认把 `SPC_OOC` / `QTIME_*` / `PROCESS_TIME_*` 改成 HOLD_LOT | 双挂或与 SPC D9 冲突          |
| P6  | `raise` 鉴权 / 要求登录才 Hold                                  | 系统路径（定时 QTime 等）无 Token |
| P7  | 本波做未 ACK 升级通知通道、邮件、GEM、OCAP                              | 属架构 P1 余量 / P2；另切片      |
| P8  | Alarm 页直查 Hold 表拼「是否已锁」当主逻辑                              | 列表可展关联提示，SSOT 仍在 Hold   |


---



## 2. 范围总览


| 优先级 | 能力                                                             | 状态      |
| --- | -------------------------------------------------------------- | ------- |
| P0  | `doRaise` 消费 `on_raise` / `hold_reason_code`；新 OPEN → 条件调 Hold | ✅      |
| P0  | Hold 失败 / 非 Lot / 原因码缺失：告警保留 + 日志                              | ✅      |
| P0  | 已 active → 跳过；与现网自挂 Hold 码默认 `NONE`                            | ✅      |
| P0  | 专用 Hold 原因码种子（供 HOLD_LOT 引用）                                   | ✅      |
| P1  | 码表维护 HTTP：改 `on_raise` / `hold_reason_code`（`alarm:edit`）      | ✅      |
| P1  | Admin 码表或告警详情展示策略 / 关联 Hold 提示                                 | ✅      |
| P2  | 未 ACK 升级（定时抬级或班次）                                              | 后置      |
| P2  | Dispatch critical 禁派钩子                                         | 另册：`MES-Dispatch-Critical禁派功能清单.md` |
| P2  | GEM / 邮件 / OCAP                                                | 不做（本清单） |


---



## 3. 切片


| 切片      | 交付                                | 状态  |
| ------- | --------------------------------- | --- |
| Alarm-5 | 策略内核：`HOLD_LOT` + 事务/并发契约 + 原因码种子 | ✅  |
| Alarm-6 | 码表维护 API（启用策略的运营入口）               | ✅  |
| Alarm-7 | Admin：策略可见 + 可选关联 Hold 展示         | ✅  |


顺序：**Alarm-5 → 6 → 7**。  
**禁止** Alarm-6/7 先于 5（无内核改码表无意义）。  
**禁止** Alarm-5 把现网四种子码默认改成 `HOLD_LOT`。  
**禁止** Alarm-5 引入 Dispatch/升级定时任务。

---



### Alarm-5（策略内核）✅



#### 5.1 交付

- `AlarmServiceImpl.doRaise` 在 **insert 新 OPEN 成功后**：
  1. 读码表 `on_raise`（未知码 / 停用码按现逻辑落库，策略视为 `NONE`）
  2. 仅当 `on_raise=HOLD_LOT` 继续
  3. 仅当 `entity_type=LOT` 且 `entity_id>0`
  4. `hold_reason_code` 非空且原因码启用；否则 warn 跳过 Hold
  5. 若 `HoldService.hasActive(lotId)` → info 跳过
  6. 否则 `HoldService.create(lotId, reasonCode, remark)`
    - `remark` 建议含 `alarmId` / `alarmCode`（便于调查台）
  7. `create` 任何异常：**catch + error 日志**；**不**抛出 `doRaise`；**不**删已写 OPEN
- **bump** 路径：只更新次数/消息/WS；**不**调 Hold
- ACK 后再 raise → 新 OPEN：允许再次走 HOLD_LOT（若其间已 Release，应能再挂；若仍 active，走 A8 跳过）
- 调用方（SPC / QTime / ProcessTime）**零改签名**；不删其现有自挂 Hold
- 种子：
  - 现有四码：`on_raise` **保持** `NONE`（migrate 注释写明禁止默认改）
  - 新增 Hold 原因码：如 `ALARM_POLICY`（或 `A_ALARM_HOLD`），供码表 `hold_reason_code` 引用
  - **不**强制新增演示告警码；验收用测试库临时改一码为 `HOLD_LOT`，或后置 Alarm-6 改码
- 可选配置：`mes.alarm.hold-on-raise-enabled` 默认 **true**（总阀；false 时无视码表 HOLD_LOT，便于风暴止血）。与 `mes.alarm.enabled` 正交：总关则连 raise 都不落

落地：`AlarmServiceImpl` · `AlarmHoldOnRaiseExecutor` · `HoldService`（仅注入接口）· `migrate_alarm_p1.sql` · `schema.sql` · `application.yml`（`hold-on-raise-enabled`）

#### 5.2 事务与失败语义（锁死）


| 场景                              | 期望                                                 |
| ------------------------------- | -------------------------------------------------- |
| raise 外层                        | 仍 `REQUIRES_NEW`；调用方事务不因 Alarm 失败回滚                |
| OPEN 已 insert，Hold 抛业务错（状态不可锁等） | OPEN 保留；日志；WS 仍可推 OPEN                             |
| Hold 与 raise 同本地事务且未 catch      | **禁止**——必须 catch，或 Hold 走独立 `REQUIRES_NEW` 且失败不向上抛 |
| `mes.alarm.enabled=false`       | 不落库、不 Hold                                         |
| 总阀 hold-on-raise false          | 落库/bump 正常；跳过 Hold                                 |


推荐实现：**Hold 调用包在 try/catch**；若 `create` 自身事务因异常回滚，只要不污染 Alarm 事务即可（同库同连接时优先 catch + 独立事务传播，避免 OPEN 被连带回滚）。

#### 5.3 本切片不做

- HTTP 改码表；Admin 策略编辑
- 未 ACK 升级；Dispatch 禁派
- 改 Track / SPC D9；OOC 默认 Hold
- 把 QTime/ProcessTime 自挂 Hold 删掉改纯策略（收敛属后续专项，本波不动）

---



### Alarm-6（码表维护 API）✅



#### 6.1 交付

- `alarm:edit`：更新码表 `name` / `level` / `on_raise` / `hold_reason_code` / `enabled` / `remark`
- 校验：`on_raise∈{NONE,HOLD_LOT}`；`HOLD_LOT` 时 `hold_reason_code` 必填且原因码启用
- **禁止** 删码、改 PK `code`（避免历史实例悬空）；停用走 `enabled=0`
- 列表只读可并入现有 Facade 或轻量 `GET /alarm/codes`

落地：`AlarmFacade` 扩展 · `MesAlarmController` · DTO/VO · 权限已有 273

#### 6.2 本切片不做

- 复杂审批流；按线别覆盖策略
- 前端大改（可放到 Alarm-7）

---



### Alarm-7（Admin 可见性）✅



#### 7.1 交付

- 告警码维护 UI（或抽屉）：改 `on_raise` / 原因码
- 告警详情：展示码表策略；若 `entity=LOT`，只读提示「是否存在 active Hold」（调 `HoldService.hasActive` / 既有 Lot Hold API，**不** Join 写 SQL 到 Alarm 包乱连表）
- 文案：策略锁批 ≠ 人工 Hold；解锁仍走 Hold 台

落地：Admin `AlarmPage` · `api/alarm.ts`；详情策略/`lotHoldActive` 由 `AlarmFacade.get` 填（HoldService.hasActive）

#### 7.2 本切片不做

- 在 Alarm 页内一键 Release Hold（权限与审计应在 Hold）
- 升级铃铛 / 邮件

---



## 4. 配置


| 键                                 | 默认   | 说明                                  |
| --------------------------------- | ---- | ----------------------------------- |
| `mes.alarm.enabled`               | true | false：`raise` 空操作（P0 已有）            |
| `mes.alarm.hold-on-raise-enabled` | true | false：忽略所有 `HOLD_LOT`（P1 新增，可选但建议有） |


---



## 5. 并发与一致性


| 场景                    | 要求                                                                                             |
| --------------------- | ---------------------------------------------------------------------------------------------- |
| 两请求同时新开同 `dedupe_key` | 保持 P0：`SELECT … FOR UPDATE` 找 OPEN；仅一行 OPEN。无部分唯一索引时禁止裸 insert 双开                              |
| 两请求同时 HOLD 同 Lot      | `HoldService.create` 侧 `hasActive` + Lot 更新乐观/约束；第二笔失败 → Alarm catch 或 A8；**不得**两行 active Hold |
| bump 与新 OPEN 交错       | 以 DB 锁住的 OPEN 行为准；bump 不 Hold                                                                  |
| raise 与人工 Hold 并发     | 先人工持锁 → 策略 A8 跳过；先策略挂上 → 人工 create 走现网「已存在生效中的锁批」                                              |
| raise 与 TrackIn 并发    | Track 只认 Hold；Hold 提交后 In 必拒；Hold 未提交前 In 成功属可接受竞态窗口（与现网人工 Hold 同级，不为本波加分布式锁）                  |


锁序建议（策略路径）：**先完成 Alarm OPEN 写库 → 再调 Hold**（Hold 内自锁 Lot）。禁止 Alarm 先锁 Lot 再写告警造成与 Hold 包锁序逆转死锁；若未来 Hold 与 Alarm 同事务加锁，统一 **Lot → Alarm** 需专项评审——本波用 catch + 事务分离规避。

---



## 6. 与现网自挂 Hold 的关系


| 来源                | 今日行为                           | P1 码表                             |
| ----------------- | ------------------------------ | --------------------------------- |
| QTime 超时          | 自 `HoldService.create` + raise | `QTIME_EXCEED` = `NONE`           |
| ProcessTime 超 max | 自 Hold + raise                 | `PROCESS_TIME_VIOLATION` = `NONE` |
| EDC OOS Auto-Hold | 不经 Alarm                       | —                                 |
| SPC OOC           | 仅 raise                        | `SPC_OOC` = `NONE`（架构 D9 / 禁止 P6） |


P1 启用方式：运营把**需要「只告警不够、必须拦」且当前无自挂**的码改为 `HOLD_LOT`。  
不在本波删除 QTime/ProcessTime 自挂（避免行为回退）；「谁负责 Hold」收敛单独立项。

---



## 7. 错误与日志（不新增对外业务错码给调用方）


| 内部情况                    | 日志级别                      | 调用方感知  |
| ----------------------- | ------------------------- | ------ |
| 策略 HOLD 但非 Lot          | warn                      | 无；告警已落 |
| `hold_reason_code` 空/停用 | warn                      | 无      |
| 已 active                | info                      | 无      |
| `create` 失败             | error（含 lotId/alarmId/原因） | 无      |
| raise 总失败               | error（P0）                 | 无      |


HTTP 码表校验失败（Alarm-6）：返回现网业务错，与策略运行时日志分离。

---



## 8. 验收


| #   | 场景                                      | 期望                                                   |
| --- | --------------------------------------- | ---------------------------------------------------- |
| V1  | 测试码 `HOLD_LOT` + payload.lotId；Lot=wait | 新 OPEN + active Hold + tx_log HOLD；Lot=held          |
| V2  | 同键再 raise                               | bump；**不**第二笔 Hold                                   |
| V3  | 已 held 再新 OPEN（先 CLEAR 告警或换码）           | OPEN 成功；Hold 跳过或业务失败被吞                               |
| V4  | `HOLD_LOT` 但 entity=EQP                 | 仅告警；无 Hold                                           |
| V5  | Hold.create 强制失败                        | OPEN 仍在；error 日志；调用方（如 SPC 采集）成功                     |
| V6  | `SPC_OOC` 默认                            | 仍不自动 Hold                                            |
| V7  | `mes.alarm.enabled=false`               | 无 OPEN、无 Hold                                        |
| V8  | 总阀 hold-on-raise false                  | 有 OPEN、无策略 Hold                                      |
| V9  | 包依赖                                     | `com.mes.alarm` 无 hold/lot Mapper；Track 无新增 Alarm 门禁 |
| V10 | Alarm-6                                 | `HOLD_LOT` 缺原因码拒保存；`NONE` 可清空原因                      |


---



## 9. 明确不做（本清单）

- Stocker / MCS / GEM S5F1
- 未 ACK 升级、邮件/企微、OCAP、Pareto
- Dispatch 禁派钩子 → `docs/模块/Dispatch（派工）模块/MES-Dispatch-Critical禁派功能清单.md`
- 删除 QTime/ProcessTime 自挂 Hold
- Alarm 内 Release；因 Alarm 改 `canTrackOut`

---



## 10. 关联


| 文档     | 路径                                     |
| ------ | -------------------------------------- |
| 架构     | `MES-Alarm架构设计.md`                     |
| P0 清单  | `MES-Alarm一期功能清单.md`                   |
| Hold   | `docs/模块/Hold（锁批）模块/MES-Hold功能文档.md`   |
| SPC D9 | `docs/模块/SPC（统计过程控制）模块/MES-SPC架构设计.md` |
| 进度     | `docs/架构/MES-实施进度与下一步.md`              |
| 业务     | `docs/业务清单/MES-半导体业务清单.md` §8          |


