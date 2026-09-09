# 半导体制造执行系统（MES）架构设计参考

> 版本：v0.1（参考稿）  
> 技术栈：React / Java 21 / MyBatis-Plus / MySQL / Redis / RocketMQ / Spring `@Scheduled`（本期）/ XXL-JOB（后置）/ MinIO / Spring Events / Sa-Token  
> 通信：REST API + WebSocket

---

## 1. 系统目标

面向晶圆厂 / 封测厂的 **制造执行系统（MES）**，覆盖：

- Lot 全生命周期管理
- 工艺路径定义与生产执行（Track）
- 设备派工与配方下发
- Hold / Alarm / 质量监控
- 全流程追溯与报表

**设计原则**

1. **状态唯一真相**：Lot/WIP 当前状态由 Track 执行引擎维护，禁止多模块双写冲突
2. **主路径强一致，副作用异步化**：Move/TrackIn/TrackOut 走 DB 事务；履历、通知、统计走 MQ
3. **设备协议外置**：SECS/GEM 放独立 Adapter，业务服务不直连设备协议
4. **模块可分期交付**：先核心链路，再增强能力
5. **先业务 MES，再数采 / AI / RAG**

---

## 2. 技术架构

### 2.1 总体分层

```
┌─────────────────────────────────────────────────────────┐
│  前端 (React)                                            │
│  业务页面 / 看板 / 现场操作台                              │
└───────────────┬───────────────────────┬─────────────────┘
                │ REST                  │ WebSocket
┌───────────────▼───────────────────────▼─────────────────┐
│  API Gateway / BFF（可选）                                │
└───────────────┬─────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────┐
│  MES 业务服务层 (Spring Boot / Java 21)  ← 起步单体模块化 │
│  Lot | WIP | Track | Route | Dispatch | Recipe          │
│  Equipment | Alarm | Hold | History | Report | Auth     │
└───┬─────────┬─────────┬─────────┬─────────┬─────────────┘
    │         │         │         │         │
 MySQL     Redis    RocketMQ   Scheduled/XXL-JOB    MinIO
    │
┌───▼─────────────────────────────────────────────────────┐
│  后期独立：Equipment Adapter · 数采 · AI/RAG              │
└─────────────────────────────────────────────────────────┘
```

### 2.2 技术选型职责

| 组件 | 职责 |
|------|------|
| React | 管理端、现场操作、看板 |
| Java 21 + Spring Boot | 业务服务（起步单体，不上 Spring Cloud） |
| MyBatis-Plus | ORM / CRUD |
| MySQL | 主业务库（Lot、Route、设备主数据、当前 WIP） |
| Redis | 设备在线态、派工热数据、分布式锁、Sa-Token 会话 |
| RocketMQ | 状态变更事件、Hold/Alarm 通知、履历异步、跨模块解耦 |
| Spring Events | **同进程内**领域事件（勿当跨服务总线） |
| Spring `@Scheduled` | **本期**超时检测（Queue Time 到期扫批）；单实例 `fixedDelay`；见 `MES-SpringScheduled使用.md` |
| XXL-JOB | **后置**：多实例、控制台、分片、日结/报表预聚合；不替代业务 Handler |
| MinIO | 配方文件、量测图、审计附件 |
| Sa-Token | 登录鉴权、Session、RBAC、权限注解、踢人/顶号 |
| REST | 命令与查询 |
| WebSocket | 设备状态、Lot 动态、Alarm 实时推送 |

### 2.3 部署形态

**阶段一（单体模块化）**：一个 Spring Boot，包按模块划分。  
**阶段二（按需拆分）**：Equipment Adapter、数采、AI/RAG、History/Report 只读服务。  
**阶段三**：确有多服务治理需求再上 Gateway / 注册中心（不必先绑全套 Spring Cloud）。

---

## 3. 模块划分

### 3.1 模块总览

| 模块 | 英文 | 职责 | 优先级 |
|------|------|------|--------|
| 批次管理 | Lot Management | Lot 创建、属性、Split/Merge、报废 | P0 |
| 在制品管理 | WIP Management | 在制视图、库存态、查询聚合 | P0 |
| 轨道/执行 | Track Management | 生产执行状态机，MES 流程中枢 | P0 |
| 工艺路线 | Route Management | Route/Step/Operation 定义 | P0 |
| 派工引擎 | Dispatch Engine | 决定 Lot 下一台设备 | P1 |
| 配方管理 | Recipe Management | 工艺配方及版本 | P1 |
| 设备管理 | Equipment Management | 设备主数据、状态、可用性 | P0 |
| 报警管理 | Alarm Management | 质量报警、统计（含 SPC 接口） | P1 |
| 锁批管理 | Hold Management | Hold/Release、原因码 | P0 |
| 履历追溯 | History Management | 全流程审计与追溯 | P0 |
| 报表中心 | Report Center | 报表、导出、数据服务 | P2 |
| 用户权限 | User & Permission | 用户、角色、权限、Sa-Token | P0 |
| 载具管理 | Carrier Management | FOUP/Carrier/Slot Map | P1（半导建议补） |
| 量测采集 | EDC | 量测数据录入与规格门禁 | P1 |
| 量测趋势 | SPC | 控制限 / 判异 / Alarm；不挡过站 | P1 |
| 设备适配 | Equipment Adapter | SECS/GEM 协议桥 | P1 |

### 3.2 模块职责边界（关键）

```
Route     = 工艺「定义」（能走哪些 Step）
Track     = 工艺「执行」（当前走到哪、允许什么 Transaction）
Dispatch  = 「选机」（下一步去哪台 Eqp）
WIP       = 「在制视图」（查询/聚合，不抢 Track 状态真相）
Lot       = 「批次实体」主数据与生命周期事件
Hold      = 「拦截」叠加在 Track 事务前校验
Equipment = 「设备能力与状态」供 Dispatch / Track 使用
Recipe    = 「参数版本」与 Step/Eqp 绑定
History   = 「只追加」事件与履历
EDC       = 「点真相 + Spec 门禁」
SPC       = 「只读趋势 + OOC Alarm」；不问 TrackOut
```

**禁止**：WIP 与 Track 各自维护一套「当前 Step / 状态」。

---

## 4. 领域模型（核心）

### 4.1 关键实体

| 实体 | 说明 |
|------|------|
| Lot | 批次，含产品、数量、优先级、当前状态 |
| Wafer / Unit | 片级（按厂需要；很多流程先 Lot 级后扩展） |
| Route | 工艺路线版本 |
| RouteStep | 路线中的工序节点 |
| Operation / Step | 工序定义（可多路由复用） |
| Equipment | 设备 |
| EquipmentGroup / Chamber | 设备组 / Chamber（按需） |
| Recipe | 配方及版本 |
| Carrier / FOUP | 载具 |
| Hold | 锁批记录 |
| Alarm | 报警记录 |
| TransactionLog | TrackIn/Out/Move 等事务日志 |
| EventHistory | 领域事件履历 |

### 4.2 Lot 状态（示例）

```
Created → Released → Wait → Reserved → Processing → Completed
                ↘ Held ↗
                ↘ Scrapped
                ↘ Terminated
```

实际以厂内 Transaction 字典为准，状态变更 **只能由 Track 事务触发**。

### 4.3 核心 Transaction（Track）

| Transaction | 说明 |
|-------------|------|
| Release | 放行进入生产 |
| Move / Arrive | 移入站点 / 到达 |
| Reserve / Dispatch | 预约设备 |
| TrackIn | 开工 |
| TrackOut | 完工 |
| Hold / ReleaseHold | 锁批 / 解锁 |
| Skip / Rework | 跳站 / 返工（规则严格控制） |
| Split / Merge | 分批 / 合批 |
| Scrap / Bonus | 报废 / 增量 |

每个 Transaction：**校验 → 改状态（事务） → 写履历 → 发 MQ 事件**。

---

## 5. 模块详细设计

### 5.1 Lot Management

- 创建 Lot（工单下发 / 手动）
- 属性维护：产品、数量、优先级、客户 Lot、备注
- Split / Merge / Scrap / Bonus
- 与 Carrier 绑定（有载具模块时）

接口示例：`POST /api/lots`、`POST /api/lots/{lotId}/split`、`GET /api/lots/{lotId}`

### 5.2 WIP Management

- 按站点 / 设备 / 产品 / 状态查询在制
- 现场看板数据源
- **只读聚合为主**，状态取自 Track/Lot 投影表

详细需求与表结构：
- `docs/模块/WIP（在制）模块/MES-WIP功能文档.md`
- `docs/模块/WIP（在制）模块/MES-WIP数据库设计.md`
- `docs/模块/WIP（在制）模块/MES-WIP已完成功能.md`（查验清单）

### 5.2.1 Dashboard（生产看板）

- **定位**：班次执行指挥屏；只读聚合 WIP / Hold / Alarm / Eqp + TrackOut 日趋势
- **不做**：真 OEE、Yield 顶卡、Report 明细、写业务状态
- **形态**：`GET /dashboard/overview` 单接口；前端轮询；可复用 `alarm.active`
- 详设：`docs/模块/Dashboard（看板）模块/MES-Dashboard架构设计.md`  
  清单：`docs/模块/Dashboard（看板）模块/MES-Dashboard一期功能清单.md`

### 5.3 Route Management

- Route / RouteStep 定义与版本
- Step 属性：设备类型、是否必过量测、可跳站、返工入口
- Route 发布 / 生效；在途 Lot 绑定 Route 版本快照

详细需求与表结构：
- `docs/模块/Route（工艺路线）模块/MES-Route功能文档.md`
- `docs/模块/Route（工艺路线）模块/MES-Route数据库设计.md`
- `docs/模块/Route（工艺路线）模块/MES-Route已完成功能.md`（查验清单）

### 5.4 Track Management（流程中枢）

事务处理伪流程：

```
1. 加分布式锁 (lotId)
2. 加载 Lot + RouteStep + Hold + Eqp
3. 校验是否允许该 Transaction
4. DB 事务更新 Lot/WIP 投影
5. 写 TransactionLog
6. 提交后发 RocketMQ / Spring Event
7. WebSocket 推送现场刷新
```

### 5.5 Dispatch Engine

- 候选设备过滤：类型匹配、状态 UP、配方可用、Chamber 能力
- 排序：FIFO / 优先级 / 负载均衡 / 交期
- 起步：规则表 + 优先级权重

### 5.6 Recipe Management

- 一期：**已落地**——主数据 / 版本 / Step×Eqp 绑定 / `RecipeFacade`；Dispatch 资格过滤；TrackIn 履历记 versionId
- Admin：`/app/recipe`；配置 `mes.recipe.require-binding`
- 边界：业务只调 Facade；表前缀 `mes_recipe*`，便于二期拆独立 RMS
- 详设：
  - `docs/模块/Recipe（配方）模块/MES-Recipe功能文档.md`
  - `docs/模块/Recipe（配方）模块/MES-Recipe数据库设计.md`
  - `docs/模块/Recipe（配方）模块/MES-Recipe已完成功能.md`

### 5.7 Equipment Management

- 设备主数据、业务状态（idle/running/down/pm/eng/offline）
- TrackIn 前 `assertUsable`；一期人工改态，Adapter 后置
- 接收 Adapter 上报 / WebSocket 推送 ← 二期+

详细需求与表结构：
- `docs/模块/Equipment（设备）模块/MES-Equipment功能文档.md`
- `docs/模块/Equipment（设备）模块/MES-Equipment数据库设计.md`
- `docs/模块/Equipment（设备）模块/MES-Equipment已完成功能.md`

### 5.8 Alarm Management

- 业务报警：超时、违规事务、设备 DOWN（GEM/Adapter 后置）
- 质量报警：对接 EDC/SPC；确认 / 关闭 / 升级通知
- **一期 P0 已落地**：`docs/模块/Alarm（告警）模块/MES-Alarm架构设计.md` · `MES-Alarm一期功能清单.md`  
  - 统一 `raise` 落库 + OPEN/ACK/CLEAR + 去重 + Admin/STOMP；不挡 TrackOut；锁批只调 Hold  
  - 不做独立 AMS / OCAP / GEM 进仓（P1/P2）

### 5.9 Hold Management

- 按 Lot 设置 Hold；原因码、权限、强制备注（最小集）
- Track 事务前强制校验
- Queue Time 到期自动 Hold（`QTIME_EXCEED`）；解锁必填备注后放行

详细需求与表结构：
- `docs/模块/Hold（锁批）模块/MES-Hold功能文档.md`
- `docs/模块/Hold（锁批）模块/MES-Hold数据库设计.md`
- `docs/模块/Hold（锁批）模块/MES-Hold已完成功能.md`
- 定时任务：`docs/架构/MES-SpringScheduled使用.md`

### 5.10 History Management

- 事务履历、状态变更、操作人、前后值；**只追加**
- **写**：Track 同事务插入 `mes_tx_log`（已落地）。本期**不**改异步写
- **读**：`HistoryFacade` 只读（一期 P0 已落地）；管理端调查台 + 设备反查；现场侧栏兼容 `GET /lots/{id}/history`
- 与 Genealogy 正交（图归谱系，线归履历）；片级 / 分表 / 独立只读库后置

详设：`docs/模块/History（履历）模块/`  
查验：`docs/模块/History（履历）模块/MES-History已完成功能.md`

### 5.11 Report Center

- **定位**：事后复盘（Move 过站 / Hold 分布）；只读聚合；**不是** Dashboard，**不是** YMS
- **一期**：`ReportFacade` + `GET /report/move` · `GET /report/hold`；Admin `/app/report`；权限 `report:view`
- **入口**：侧栏独立分组 **「复盘 → 报表」**；**禁止**挂进「生产执行」；看板/履历链入为辅
- **口径**：过站 = `TRACK_OUT`（与 Dashboard 趋势同源）；Hold 按 `hold_time` 窗 + `reason_code`
- **不做（一期）**：Yield 顶卡、真 OEE、Excel、预聚合日表、Hold 按站
- 详设：`docs/模块/Report（报表）模块/MES-Report架构设计.md`  
  清单：`docs/模块/Report（报表）模块/MES-Report一期功能清单.md`

### 5.12 User & Permission（Sa-Token）

- 登录 / 登出 / Token 续期；会话存 Redis
- `@SaCheckLogin` / `@SaCheckPermission` / `@SaCheckRole`
- 敏感事务二次校验（Scrap、Bonus、Hold Release、Skip）
- 顶号、踢人下线；操作审计关联 History
- Token 头：`Authorization`；WebSocket 连接时校验 Token
- 权限码示例：`track:track-in`、`hold:release`、`lot:scrap`

### 5.13 补充模块（半导建议）

- Carrier / FOUP、Equipment Adapter（SECS/GEM）
- **EDC（量测）**：设计已定，见 `docs/模块/EDC（量测）模块/`  
  - 一期：Param / Spec / Plan / 手录 / `EdcFacade` / TrackOut 钩子 / 现场拒出提示 ✅  
  - Facade 契约：`MES-EdcFacade接口设计.md`  
  - SPC 趋势预警架构已定：`docs/模块/SPC（统计过程控制）模块/MES-SPC架构设计.md`；Track 不存点、不问 SPC；OOC 只 Alarm

---

## 6. 关键业务流程

### 6.1 Lot 放行到完工（主路径）

```
创建 Lot → Release → Dispatch 选机 → Reserve / Move
  → TrackIn → [设备加工] → TrackOut → 下一站 → Completed
```

### 6.2 Hold 拦截

```
任意 Track 事务 → Hold 校验失败则拒绝 → 仅允许 ReleaseHold 后继续
```

---

## 7. 通信与事件

### 7.1 REST

按模块 `/api/{module}/...`，写操作建议 `Idempotency-Key`。

### 7.2 WebSocket

- `eqp.status.{eqpId}` / `lot.update.{lotId}` / `wip.site.{siteId}` / `alarm.active`

### 7.3 RocketMQ Topic（示例）

| Topic | 说明 |
|-------|------|
| `mes.lot.changed` | Lot/Track 变更 |
| `mes.hold.changed` | Hold 变更 |
| `mes.alarm.raised` | 报警 |
| `mes.eqp.status` | 设备状态 |

---

## 8. 数据设计要点

| 类别 | 示例 | 说明 |
|------|------|------|
| 主数据 | product, route, eqp, recipe | 变更少 |
| 运行态 | lot, wip_lot, hold | 高频读写 |
| 履历 | tx_log, event_history | 只追加 |
| 配置 | dispatch_rule, reason_code | 规则 |

- Track 事务：单 Lot 串行（锁 lotId）+ 乐观锁 Lot.version
- 履历按时间分表；报表走只读或预聚合

---

## 9. 非功能要求

| 项 | 建议目标（起步） |
|----|------------------|
| Track 事务 RT | P99 < 500ms（不含设备协议） |
| 安全 | Sa-Token + RBAC（Redis 会话）；敏感事务鉴权 |
| 审计 | 谁、何时、对哪个 Lot、做了什么、前后状态 |
| 可观测 | 日志 traceId、MQ 消费监控、慢 SQL |

---

## 10. 分期实施路线

| 阶段 | 内容 |
|------|------|
| Phase 0 | 工程结构、Sa-Token、字典、Lot/Route/Equipment 主数据 |
| Phase 1 MVP | Track（Release/Move/TrackIn/TrackOut）、Hold、History、WIP、WebSocket |
| Phase 2 | Dispatch、Recipe、Carrier |
| Phase 3 | Adapter、EDC、Alarm/SPC |
| Phase 4 | Report、Split/Merge/Rework、性能 |

**当前落地（2026-07-27）**：Auth ✅、Route ✅、Lot ✅、Track ✅（一期）；下一步 **WIP**。  
进度：`docs/架构/MES-实施进度与下一步.md`。  
Track：`docs/模块/Track（执行引擎）模块/`（Release / In / Out / Move / Abort 等已落地；见二期清单）。

---

## 11. 包结构建议（单体模块化）

```
com.mes
  ├── lot / wip / track / route / dispatch / recipe
  ├── equipment / alarm / hold / history / report / auth
  ├── carrier / edc（补充；edc 一期内嵌）
  └── common
```

每模块：`api / application / domain / infrastructure`

---

## 12. 风险与约束

1. Track 与 WIP 双写 → 以 Track 为准，WIP 为投影  
2. Spring Events 当跨服务总线 → 跨进程用 RocketMQ  
3. 设备协议塞进业务服务 → 独立 Adapter  
4. Dispatch 过早复杂化 → 先规则表  
5. 一上来 Spring Cloud → 不必要，先单体  

---

## 13. 待决策清单

| 项 | 选项 | 影响 |
|----|------|------|
| 厂型 | 前道 / 后道 / 封测 | Wafer/Slot/Reticle |
| 片级管理 | Lot 级 vs Wafer 级 | 模型复杂度 |
| 设备通讯 | 一期是否接真实机台 | Adapter 优先级 |
| 多工厂 | 单厂 vs 多 Site | 数据隔离 |

---

## 附录 A. 模块依赖

```
Auth ──────► 全部模块
Route / Equipment / Recipe / Hold / Dispatch ──► Track
Lot ───────► Track, WIP, Hold
Track ─────► History, WIP(投影), Alarm(可选)
Report ◄──── History, WIP, Equipment（只读）
```

## 附录 B. MVP 接口清单（最小）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 注册 |
| POST | /api/auth/login | 登录 |
| POST | /api/lots | 创建 Lot |
| POST | /api/lots/{id}/release | 放行 |
| GET | /api/routes/{id} | 路线详情 |
| POST | /api/track/move | 移站 |
| POST | /api/track/track-in | 开工 |
| POST | /api/track/track-out | 完工 |
| POST | /api/holds | 创建 Hold |
| POST | /api/holds/{id}/release | 解锁 |
| GET | /api/wip | 在制查询 |
| GET | /api/lots/{id}/history | 履历（现场兼容，委托 HistoryFacade） |
| GET | /api/history | 调查分页（lotId 或 eqpId） |
| GET | /api/history/{txId} | 履历单行 |
| GET | /api/equipments | 设备列表 |
