# MES 看板（Dashboard）— 一期功能清单

> 前提：WIP / Hold / Equipment / Alarm / Track 履历已齐；`/app/dashboard` 现 mock  
> 对齐：`MES-Dashboard架构设计.md` · `docs/UI/MES-UI设计.md` §3.1 · 实施进度  
> 更新：2026-09-08  
> 状态：**Dash-1～4 一期已完成**

---

## 0. 目标

把生产看板从 mock 换成**可信指挥屏**：班次内能看见在制、锁批、报警、设备 Down，以及近 7 日 TrackOut 趋势。

原则：

- 只读聚合；状态真相在源模块
- 单一 `GET /dashboard/overview`；前端不扇出拼屏
- 顶卡禁止假「稼动 %」
- 不挡 Track、不写业务表、不做 Report

---

## 1. 范围总览

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | `DashboardFacade` + `GET /dashboard/overview` | ✅ |
| P0 | KPI：在制 / 活跃 Hold / 未清 Alarm / Down 台数 | ✅ |
| P0 | 设备状态矩阵（真 Eqp） | ✅ Dash-2 |
| P0 | 报警流（真 Alarm） | ✅ Dash-2 |
| P0 | 近 7 日 TrackOut 趋势 | ✅ Dash-3 |
| P0 | 前端换 mock；轮询 + 暂停 | ✅ Dash-4 |
| P1 | KPI 深链筛选；按站拥堵；投屏 Shell；短 TTL 缓存 | 后置 |
| P2 | 真 OEE；全量 WS；Yield 顶卡 | 后置 |

---

## 2. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| Dash-1 | Facade + HTTP overview；KPI 四卡真数；趋势可空数组 | ✅ |
| Dash-2 | equipment[] + alarms[] 真数据；口径锁死 | ✅ |
| Dash-3 | outputTrend 按日 TRACK_OUT；缺日补 0 | ✅ |
| Dash-4 | Admin 换 mock；轮询/暂停；去掉稼动文案 | ✅ |

顺序：Dash-1 → 2 → 3 → 4。  
**禁止** Dash-4 先于 Dash-1。  
**禁止** 任一切片引入 OEE 字段。

### Dash-1（聚合骨架 + KPI）✅

- 包 `com.mes.dashboard`：`DashboardFacade` / Controller `/dashboard/overview`
- 权限：`dashboard:view`
- KPI：`WipService.count()` / `HoldService.countActive()` / Alarm 未清数 / Eqp Down；`generatedAt`；域失败 → `partial`
- 本切片允许矩阵/报警/趋势先空（实现上已与 2/3 同接口一并交付）
- 无前端换 mock

### Dash-2（矩阵 + 报警流）✅

> 架构意图：把「哪台坏了 / 哪条还在响」从 KPI 数字落到**可扫一眼的现场态**；仍只读，不把看板做成第二套设备台或告警台。

#### 2.1 设备矩阵 `equipment[]`

| 项 | 锁死 |
|----|------|
| 真相源 | `MesEqpService`（启用设备）；状态枚举与 Eqp 一致：`idle/running/down/pm/eng/offline`（小写） |
| 行字段 | `id` · `eqpCode` · `name` · `status` · `currentLotNo?` |
| 当前批 | **不在** `mes_eqp`；用 WIP 投影 `currentEqpId → lotNo` 反查；一机多批 `putIfAbsent` 留一条即可 |
| KPI 联动 | 同一次扫描算 `eqpDownCount` / `eqpRunningCount` / `eqpTotal`，禁止矩阵与顶卡两套过滤 |
| 范围 | 一期拉启用设备（实现侧分页上限）；不做区域树、不做 Chamber 展开 |

#### 2.2 报警流 `alarms[]` + 顶卡 `alarmOpenCount`

| 项 | 锁死 |
|----|------|
| 未清口径 | **OPEN + ACK**（CLEARED 不算）；`countUncleared` 与 `listUncleared` **同一口径** |
| 流字段 | `id` · `level` · `source` · `message` · `raisedAt` · `status` |
| 排序 | `lastRaiseAt` 倒序（无则 `firstRaiseAt`）；新响的在上 |
| 条数 | `alarmLimit`（默认 20，钳 1～100）；只裁剪流，**不**裁剪 KPI 总数 |
| source | 有实体：`code@TYPE:id`；否则告警码；不在看板解析 Lot/Eqp 显示名 |
| 处置 | **禁止** overview 提供 ACK/CLEAR；人去 `/app/alarm` |

#### 2.3 依赖与失败

```
fillEquipment ──► MesEqpService.page(enabled=1) + WipService.page（反查 lot）
fillAlarms    ──► AlarmFacade.countUncleared / listUncleared
```

- Eqp 挂 → 矩阵空 + `partial`；Alarm 仍可出  
- Alarm 挂 → 流空、报警 KPI 可 0 + `partial`；矩阵仍可出  
- WIP 反查挂 → 矩阵仍出，仅 `currentLotNo` 可能空 + `partial`（`wip-lot-on-eqp`）

#### 2.4 本切片不做

- 看板内改设备态 / ACK 报警  
- GEM 实时态、真 OEE、维护工单 ETA  
- 前端色板映射（留给 Dash-4；后端保持域内原样）  
- 独立 `GET /dashboard/equipment` 扇出口（一期坚持单 overview）

#### 2.5 验收（Dash-2）

- [x] 改设备为 `down` → 矩阵该行 status 变且 `eqpDownCount` +1  
- [x] 机台有 WIP 占用 → `currentLotNo` 有批号；无占用可为 null  
- [x] 新 OPEN 告警 → `alarmOpenCount` +1，流中可见；CLEAR 后计数与流去掉  
- [x] ACK 未 CLEAR → 仍计入未清、仍可出现在流中  
- [x] overview 无 POST ACK；Dashboard 包无写 Alarm/Eqp

落地：`DashboardFacadeImpl#fillEquipment` / `#fillAlarms`；`AlarmFacade#countUncleared` / `#listUncleared`

### Dash-3（TrackOut 趋势）✅

- 默认 `trendDays=7`；按日本地时区
- 数据源：History `countDailyByTxType(TRACK_OUT)`；缺日补 `trackOutCount=0`
- 失败 → `partial` + 全 0 序列

### Dash-4（前端）✅

- `DashboardPage` 接 `GET /dashboard/overview`；删除 mock KPI/设备/报警/趋势
- 顶卡文案：在制 / 锁批 / 报警 / 故障（无稼动）
- Admin Light（与侧栏顶栏统一；投屏 Dark 后置全屏壳）；轮询 20s；暂停；LiveDot；`generatedAt` / `partial`
- 设备色板：后端小写 status → 色块字母；报警流可进告警台
- 落地：`web/src/api/dashboard.ts` · `web/src/pages/DashboardPage.tsx`

---

## 3. 验收

- [x] 无权限用户 403；有 `dashboard:view` 200
- [x] Hold 一笔 → holdActiveCount +1（刷新后）
- [x] 设备改 Down → 矩阵色变且 eqpDownCount 变
- [x] 新 OPEN Alarm → 流首或计数变
- [x] TrackOut → 当日 trend +1（跨日边界按约定时区）
- [x] 顶卡无「稼动」字样（Dash-4 换文案）
- [x] Dashboard 无业务写接口

---

## 4. 后置

见架构 §8。Report 单独立项，不在本清单扩 scope。
