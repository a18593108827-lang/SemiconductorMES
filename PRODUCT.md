# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

晶圆厂 / 封测厂三类角色：

- **现场操作员**：车间工位，手套/强光，触控为主，做 Track In/Out、Hold、扫码、分批等过账
- **工艺 / 设备工程师**：办公室，键盘鼠标，配 Route、Recipe、设备、排查 Alarm
- **班组长 / 管理人员**：看板与 WIP 总览，盯产出、Hold、设备可用性

主任务：Lot 全生命周期执行与追溯；任一屏应立刻回答「现在能不能动这批货」。

## Product Purpose

半导体制造执行系统（MES）。覆盖 Lot、WIP、Track、Route、Dispatch、Recipe、Equipment、Hold、Alarm、History、Report、权限。

成功标准：现场 3 步内完成 Track；管理端表格可扫可读；状态与报警一眼可判；无双写、无装饰干扰操作。

## Positioning

以 **Track 事务为唯一执行真相** 的半导 MES：Lot 主数据 + Route 版本快照 + Hold/Dispatch 叠加校验，状态不可被多模块改写。对标大厂 Lot Tracking（SiView / Camstar / AMAT）的「实体与事务分离」，而非通用离散制造 MES 的工单驱动模型。

## Operating Context

- **管理端（Admin Light）**：侧栏 + 顶栏 + 表格 + 抽屉；工程师配主数据、查谱系与履历
- **现场台（Field Dark）**：大触控过账；选 Lot → TrackIn/Out / Hold / Rework / Skip / Off-Flow / Split / Merge
- **业务对象**：Lot（含 Split 谱系）、Route 版本快照、WIP 投影、设备预约、Future Hold
- **约束环境**：车间可读性优先；不可逆事务（放行、分批、报废等）需确认；状态变更写 `mes_tx_log`

## Capabilities and Constraints

**已确认能力（节选）**

- Lot 创建 / 放行绑 Route active 版本快照；在途只认 `route_version_id`
- Track：Release / TrackIn / TrackOut / Rework / Skip / Off-Flow / Split / Merge
- Hold / Future Hold；Dispatch 预约；Recipe Facade；Queue Time（P1）
- Lot 谱系查询（Split 父子树）

**约束**

- 状态唯一真相在 Track；禁止 `PUT` 冒充分批改 qty
- Recipe / Reticle 不进 Route body（运行时解析）
- SECS/GEM 设备协议外置 Adapter（后置）
- 片级 Wafer / Carrier / Sorter 联动：未做（P1）

**未决（不阻塞当前）**

- 一盒多 Lot、Future Split、中途切 Route 版本、ERP 工单下发

## Brand Commitments

- 名称：MES（半导体制造执行）
- 人格：高效 / 硬核 / 现代
- 语气：短句、动词优先、状态名词固定；界面像工具，不像演示页
- 明确拒绝：紫色渐变 SaaS 营销风、奶油/米色温馨后台、玻璃拟态大卡片墙、emoji 当图标、入场编排动画与弹跳动效、每屏一套不同按钮/表单样式

## Evidence on Hand

- 产品与架构：`PRODUCT.md`、`DESIGN.md`、`docs/架构/半导MES架构设计.md`、`docs/业务清单/MES-半导体业务清单.md`
- 模块文档：`docs/模块/**`（Lot / Track / Route / Hold / Dispatch 等）
- 可运行实现：`server/`（Java）、`web/`（React）
- **不得虚构**：客户名、量产 fab 案例、SEMI 认证声明、外部标杆数字

## Product Principles

1. **状态优先**：先回答「能不能动这批货」；状态用色 + 文案 + 图标三重表达
2. **事务唯一真相**：过站与分批等只走 Track；主数据与投影不抢写
3. **双场景一体**：管理端与现场台同一组件体系，只换密度与主题
4. **密度服务任务**：表格与筛选默认；卡片仅用于可操作实体或 KPI
5. **熟悉感胜于猎奇**：侧栏 + 顶栏 + 表格 + 抽屉；动效只报状态（150–250ms）

## Accessibility & Inclusion

- 目标 WCAG 2.1 AA；正文对比 ≥4.5:1
- 支持 `prefers-reduced-motion`
- 色盲友好：状态不靠单色（辅以文字/图标/图案）
- 现场台触控热区 ≥44px；关键操作防误触（二次确认仅用于不可逆）
