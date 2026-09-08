# MES 看板（Dashboard）— 架构设计

> 定位：班次级**执行指挥屏**——聚合 WIP / Hold / Alarm / Eqp 当前态 + TrackOut 粗趋势；**只读、不下写**  
> 范式：**Dashboard 不造状态**；只读各域投影 / Facade，前端可下钻到明细页  
> 产品口径：班组长/生产管控「现在哪里堵/坏/锁」；不是 BI、不是真 OEE、不是 Yield 调查台  
> 对齐：`docs/架构/半导MES架构设计.md` §5.2 / §5.8；业务清单 §11；`docs/UI/MES-UI设计.md` §3.1；实施进度「Dashboard 真数」  
> 前提：WIP / Hold / Equipment / Alarm / Track→`mes_tx_log` 一期已齐；前端 `/app/dashboard` 现为 mock  
> 状态：**待落地**（Dash-1～4）  
> 更新：2026-09-07

---

## 1. 边界

```
Dashboard = 只读聚合 API + Admin/投屏页；KPI / 设备矩阵 / 报警流 / TrackOut 趋势
WIP       = 在制投影真相；看板计 WIP 数、可选按站汇总
Hold      = 活跃锁批真相；看板计 active Hold 数
Alarm     = 告警实例真相；看板流 + 未清数；推送可复用 alarm.active
Eqp       = 设备态真相；矩阵色块 + Down/可用计数
Track/History = TrackOut 事件在 mes_tx_log；趋势按日 COUNT，不另建产出表
Report    = 事后复盘（Move 明细、Hold 分布、导出）；Dashboard 之后做，禁止并进本包
SPC/EDC   = 质量深挖；看板不顶卡 Yield / OOC 图表
Adapter   = SECS/GEM；真 OEE / 机台遥测后置
```

**本切片做什么**

- 包 `com.mes.dashboard`（或等价）；对外 **仅读** `DashboardFacade` + `GET /dashboard/overview`
- 替换 `web` mock：四卡 + 设备矩阵 + 报警流 + 近 7 日 TrackOut 趋势
- 权限沿用 `dashboard:view`（已有菜单码）
- 刷新：轮询 overview（默认 15～30s）+ 可选订 `alarm.active` 局部刷新报警区；保留「暂停」

**本切片不做什么**

- 真 OEE（Availability×Performance×Quality）及计划停机口径
- Yield / SPC 顶卡、3D 数字孪生、APS、预测维护
- 写接口、改 Lot/Hold/Eqp 状态、看板内 ACK Alarm（ACK 仍走 Alarm 页）
- 独立物化汇总表 / 预聚合任务（Report 阶段再评）
- 强制 WebSocket 全屏推送（可后置）

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Dashboard 直查各域表旁路 Facade/Service 约定 | 拆库与口径漂移；同 Alarm P8 |
| P2 | 顶卡展示「稼动 %」而无 GEM/计划停机定义 | 假数毁信任；进度已定不做真 OEE |
| P3 | Dashboard 包内写 `mes_lot` / Hold / Alarm | 指挥屏变执行器，职责分裂 |
| P4 | 把 Report 明细导出、Pareto 并进一期 | 范围膨胀；边界在 Report |
| P5 | 前端并行打 4～5 个域 API 拼屏且无聚合契约 | 扇出、权限不一致、半失败难处理 |
| P6 | 为趋势新建「产出日表」且与 tx_log 双写 | 真相分裂；一期 COUNT 履历即可 |
| P7 | 看板 Shell 复制一套业务状态机 | UI 只展示，状态只在源模块 |

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 形态 | 同进程只读 Facade；无独立微服务 | 与 Hold/History/Alarm 一致 |
| D2 | 职责 | **聚合编排**，不拥有业务表 | 源模块已是 SSOT |
| D3 | 对外 API | 单一 `GET /dashboard/overview` 返回整屏 DTO | 投屏一次拉齐；避免前端扇出 |
| D4 | 内部读法 | 调 WIP / Hold / Eqp / Alarm 已有 Service 或窄查询；趋势查 `mes_tx_log` | 禁止 Mapper 穿透外域表乱配 |
| D5 | 顶卡口径 | `wipCount` / `holdActiveCount` / `alarmOpenCount` / `eqpDownCount`（或可用台数） | 替换 mock「稼动 %」 |
| D6 | 趋势口径 | 近 N 日（默认 7）`tx_code=TRACK_OUT`（或项目既有出站码）按日 COUNT | 吞吐可见；非良率 |
| D7 | 设备矩阵 | id / name / status / currentLotNo（有则带） | 对齐现 UI；状态枚举与 Eqp 一致 |
| D8 | 报警流 | 最近 K 条 OPEN/ACK（或未 CLEARED），级别排序 | 复用 Alarm 查询语义 |
| D9 | 实时 | 一期 HTTP 轮询；Alarm 区可订已有 STOMP；全屏 WS 后置 | 降风险；暂停=停轮询 |
| D10 | 缓存 | 一期可无；若压测需要再加短 TTL（≤10s）本地缓存 | 勿先上 Redis 汇总 |
| D11 | 权限 | 仅 `dashboard:view`；聚合内读取**不**再要求调用方持有 `wip:list` 等（服务端编排） | 班组长有看板即可看数；明细页仍各自鉴权 |
| D12 | 下钻 | 前端 Link 到 `/app/wip` `/app/hold` `/app/alarm` `/app/equipment`；一期可不带深链查询 | 行动在源页 |
| D13 | 投屏 | 可沿用 Admin 页 + 深色；独立无侧栏 Shell 为 P1 | UI 规范已留「看板 Shell」 |
| D14 | 与 Report | Dashboard=当前态+粗趋势；Report=可筛选明细/导出 | 实施顺序：Dashboard → Report |

**刻度**：一期只消费 **MES 内已落地读模型**。设备 GEM 稼动、良率顶卡为后置。

---

## 3. 依赖与数据流

```
浏览器 / 投屏
    │  GET /dashboard/overview   (dashboard:view)
    │  可选 STOMP alarm.active
    ▼
DashboardFacade.overview()
    ├── WipService：count / 可选 summaryByStep（若屏上要拥堵条，P1）
    ├── HoldService：active 计数（+ 可选最近几条，P1）
    ├── EqpService：列表态 → 矩阵 + Down 计数
    ├── Alarm 查询：open 计数 + 最近流
    └── TxLog / History 只读：按日 TRACK_OUT 聚合
    ▼
DashboardOverviewVO → 换掉 web mock
```

失败策略：单域查询失败 → 该块 `null` + `partial=true` + 错误码列表；**不**整页 500（投屏可降级）。

---

## 4. 门面契约

```
DashboardFacade（只读）
  overview(trendDays = 7, alarmLimit = 20) → DashboardOverviewVO

DashboardOverviewVO
  generatedAt
  partial: boolean
  errors?: string[]          // 域失败说明
  kpi:
    wipCount
    holdActiveCount
    alarmOpenCount           // OPEN+ACK 或仅 OPEN：实现锁死一种并写进清单
    eqpDownCount             // status=Down；可选 eqpTotal / eqpRunning
  equipment: [{ id, eqpCode, name, status, currentLotNo? }]
  alarms: [{ id, level, source, message, raisedAt, status }]
  outputTrend: [{ day, trackOutCount }]   // 近 trendDays，缺日补 0
```

HTTP：

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/dashboard/overview` | `dashboard:view` | Query：`trendDays`（1–31，默认 7）、`alarmLimit`（默认 20） |

无 POST/PUT/DELETE。

---

## 5. 与现有模块对齐

| 模块 | 看板用法 | 不碰 |
|------|----------|------|
| WIP | 在制总数；P1 可复用 `/wip/summary/by-step` 思路 | 不改投影写路径 |
| Hold | active 计数 | 不在看板 Release |
| Equipment | 状态矩阵 | 不在看板改态 |
| Alarm | 计数 + 流；可订 `alarm.active` | ACK/CLEAR 仍 `/alarm` |
| History / tx_log | TrackOut 日聚合 | 不改 Track 写日志 |
| Dispatch / Recipe | 不进一期顶屏 | — |
| Report | 后置承接明细 | 禁止本期做导出 |

---

## 6. 前端

- 页：`/app/dashboard`（已有）；换 `mock` → `GET /dashboard/overview`
- 顶卡文案：**在制 / 锁批 / 报警 / Down（或可用）**——删除「稼动 %」
- Live 点 + 暂停：暂停则停轮询（及可选退订 STOMP）
- 色板与 Eqp/Alarm 现有约定一致（Running/Idle/Down/PM/Offline；critical/warning/info）

---

## 7. 切片（落地顺序）

| 切片 | 交付 |
|------|------|
| Dash-1 | Facade + `GET /dashboard/overview`；KPI 四卡真数（含趋势空或占位） |
| Dash-2 | 设备矩阵 + 报警流接真 |
| Dash-3 | TrackOut 近 7 日趋势；缺日补 0 |
| Dash-4 | 前端换 mock；轮询 + 暂停；可选订 alarm.active |

建议：1 → 2 → 3 → 4。  
**禁止** Dash-4 先于 1（再造前端假聚合）。  
**禁止** 一期引入 OEE 公式字段。

明细见 `MES-Dashboard一期功能清单.md`。

---

## 8. 后置（非本期）

| 项 | 说明 |
|----|------|
| P1 下钻深链 | KPI 点击带 filter 进 WIP/Hold/Alarm |
| P1 按站拥堵条 | 复用 WIP summary |
| P1 看板 Shell | 无侧栏投屏布局 |
| P1 短 TTL 缓存 | 多投屏打同一 overview |
| P2 真 OEE | 依赖 Adapter + 停机原因码治理 |
| P2 全量 WS | overview 推送或域事件合并 |
| Report | Move/Hold 分布与导出 |

---

## 9. 验收口径（架构）

- 无 mock 数字；顶卡无「稼动 %」
- 改一笔 Hold / 设备态 / 开一条 Alarm / 一次 TrackOut，在刷新周期内看板可见（趋势按日）
- Dashboard 包无写库业务表
- `dashboard:view` 可看 overview；无权限 403
- 单域挂掉时其余块仍可用（partial）

---

## 10. 文档入口

| 文档 | 路径 |
|------|------|
| 本设计 | `docs/模块/Dashboard（看板）模块/MES-Dashboard架构设计.md` |
| 一期清单 | `docs/模块/Dashboard（看板）模块/MES-Dashboard一期功能清单.md` |
| UI | `docs/UI/MES-UI设计.md` §3.1 |
| 进度 | `docs/架构/MES-实施进度与下一步.md` |
| 业务 | `docs/业务清单/MES-半导体业务清单.md` §11 |
