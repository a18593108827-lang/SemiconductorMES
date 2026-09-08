# MES 看板（Dashboard）— 一期功能清单

> 前提：WIP / Hold / Equipment / Alarm / Track 履历已齐；`/app/dashboard` 现 mock  
> 对齐：`MES-Dashboard架构设计.md` · `docs/UI/MES-UI设计.md` §3.1 · 实施进度  
> 更新：2026-09-07  
> 状态：**待做**（Dash-1～4）

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
| P0 | `DashboardFacade` + `GET /dashboard/overview` | 待做 |
| P0 | KPI：在制 / 活跃 Hold / 未清 Alarm / Down 台数 | 待做 |
| P0 | 设备状态矩阵（真 Eqp） | 待做 |
| P0 | 报警流（真 Alarm） | 待做 |
| P0 | 近 7 日 TrackOut 趋势 | 待做 |
| P0 | 前端换 mock；轮询 + 暂停 | 待做 |
| P1 | KPI 深链筛选；按站拥堵；投屏 Shell；短 TTL 缓存 | 后置 |
| P2 | 真 OEE；全量 WS；Yield 顶卡 | 后置 |

---

## 2. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| Dash-1 | Facade + HTTP overview；KPI 四卡真数；趋势可空数组 | 待做 |
| Dash-2 | equipment[] + alarms[] 真数据 | 待做 |
| Dash-3 | outputTrend 按日 TRACK_OUT；缺日补 0 | 待做 |
| Dash-4 | Admin 换 mock；轮询/暂停；去掉稼动文案 | 待做 |

顺序：Dash-1 → 2 → 3 → 4。  
**禁止** Dash-4 先于 Dash-1。  
**禁止** 任一切片引入 OEE 字段。

### Dash-1（聚合骨架 + KPI）

- 包 `com.mes.dashboard`：`DashboardFacade` / Controller `/dashboard/overview`
- 权限：`dashboard:view`（菜单已有则只接 API）
- KPI 口径锁死架构 §2 D5；`generatedAt`；域失败 → `partial`
- 本切片矩阵/报警/趋势可返回空列表
- 无前端换 mock（可 Swagger/HTTP 验）

### Dash-2（矩阵 + 报警流）

- 设备：与 Eqp 状态枚举一致；带 currentLot（有则）
- 报警：最近 N 条；计数与列表口径一致（OPEN+ACK 或仅 OPEN，实现时写死一处）
- 不在此切片做 ACK

### Dash-3（TrackOut 趋势）

- 默认 `trendDays=7`；按日本地时区
- 数据源：`mes_tx_log`（或 History 只读）出站事务码与 Track 写入一致
- 无日补 `trackOutCount=0`

### Dash-4（前端）

- `DashboardPage` 接 overview；删除 mock KPI/设备/报警/趋势
- 顶卡文案：在制 / 锁批 / 报警 / Down（或「故障」）
- 轮询 15～30s；暂停停轮询；LiveDot 保留
- 可选：订 `alarm.active` 仅刷新报警区

---

## 3. 验收

- [ ] 无权限用户 403；有 `dashboard:view` 200
- [ ] Hold 一笔 → holdActiveCount +1（刷新后）
- [ ] 设备改 Down → 矩阵色变且 eqpDownCount 变
- [ ] 新 OPEN Alarm → 流首或计数变
- [ ] TrackOut → 当日 trend +1（跨日边界按约定时区）
- [ ] 顶卡无「稼动」字样
- [ ] Dashboard 无业务写接口

---

## 4. 后置

见架构 §8。Report 单独立项，不在本清单扩 scope。
