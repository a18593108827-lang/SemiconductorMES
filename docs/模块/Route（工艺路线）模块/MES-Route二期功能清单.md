---
type: 功能清单
module: Route
status: done
slices: []
aligns: []
updated: 2026-07-30
---

# MES 工艺路线（Route）— 二期功能清单

> 前提：一期线性主路径 + 版本快照已落地  
> 对齐：`MES-半导体业务清单.md` §2 · Track 二期 Skip/Rework  
> 更新：2026-07-30

---

## 0. 目标

在**不拆掉版本快照契约**的前提下，把线性 Route 升级为半导可执行的 Process Flow：回流、分支、受控跳站、站属性约束。

原则：
- Route 仍只负责「定义」；Track 负责「执行事务」
- 配置进版本快照；在途 Lot 仍只认放行时的 `route_version_id`
- Recipe / Reticle 不进 Route body（运行时解析）

---

## 1. 范围总览

| 优先级 | 能力 | Route 侧 | Track 侧依赖 |
|--------|------|----------|--------------|
| P0 | Rework 回流 | 回流边 + 次数上限 | `track:rework` 事务 |
| P0 | 条件/可选分支 | 多 `next` + 条件码 | TrackOut 选边 / 量测结果驱动 |
| P0 | Step 站属性 | `eqp_type` / 可量测标记 | Dispatch / TrackIn 校验 |
| P1 | Skip 规则 | 允许跳至目标站配置 | `track:skip` 强权限 |
| P1 | Temporary Off-Flow | 离线边 + 回主路径锚点 | Off-Flow / Resume 事务 |
| P1 | Future Hold 挂点 | Step 可挂 future hold 模板（后置） | Hold 模块消费；**Lot 预约 P0 ✅** |
| P1 | Queue Time | 站间时间窗（max；min 可选） | Track 计时；超时 Hold/告警；**不做** Pre-Gate/QMS |
| P2 | 层级子流程 | RouteStep 引用子 Route | Track 展开/折叠 |
| P2 | 并行站 | AND/OR 汇合 | 多站同时 wait |
| P2 | 图形编辑器 | 拖拽画布 | — |
| 后置 | Product 主数据 | `product_code` → ProductId | Product 模块 |
| 不做（二期） | Recipe body 进快照 | — | 继续 Facade 运行时解析 |

---

## 2. P0 功能明细

### 2.1 Rework 回流

| 项 | 说明 |
|----|------|
| 配置 | RouteStep 上配置：`rework_to_sort_no`、`max_rework_count`（按站或按路线） |
| 计数 | Lot 运行态记各站/全线返工次数（Track 写，Route 只定义上限） |
| 发布校验 | 回流目标必须存在于同版本；禁止死环无出口（至少能回到主路径） |
| 权限 | 配置属 `route:edit`；执行属 `track:rework` |
| 验收 | 超次拒绝；在途快照上限不随后续升版变化 |

### 2.2 分支（多下一站）

| 项 | 说明 |
|----|------|
| 模型 | 从单 `next_sort_no` 扩展为边表：`from_sort → to_sort + edge_type + condition_code` |
| edge_type | `normal` / `branch` / `rework` / `skip_allow`（P1） |
| condition | 一期可人工选边；二期对接量测结果码（如 PASS/FAIL/REWORK） |
| 默认边 | 每站必须有且仅有一条 `default` 边（兼容现线性行为） |
| Track | TrackOut 无条件时走 default；有结果码时匹配边，匹配失败拒绝 |
| UI | Route「+ 分支」；**不是** Lot 分批（Split 在 Track，见 `MES-LotSplit接口设计.md`） |
| 设计 | `MES-Branch接口设计.md` |

### 2.3 Step 站属性增强

| 字段 | 用途 |
|------|------|
| `eqp_type` | Dispatch / TrackIn 设备类型校验（一期可放宽 → 二期可开关强制） |
| `step_type` | 已有；分支条件可依赖量测站 |
| `allow_skip` | 是否允许被 Skip 目标/源（配合 P1） |
| `max_queue_min` | Queue Time 上限（分钟；可先落库，P1 再启用执行；优先落边，见 §3.4） |

---

## 3. P1 功能明细

### 3.1 Skip 规则

| 项 | 说明 |
|----|------|
| 配置 | 版本内白名单：`(from_sort, to_sort)` 或「同路线任意前向」策略二选一（建议白名单） |
| 执行 | `track:skip` + 二次确认；写履历 |
| 禁止 | 无配置、跳入已归档路径、跨版本 |

### 3.2 Temporary Off-Flow

| 项 | 说明 |
|----|------|
| 配置 | 从主路径某站可进入 Off-Flow 子序列，结束站必须指回主路径锚点 |
| 执行 | Track：Enter Off-Flow / Resume；Lot 标记 `off_flow=true` |
| 约束 | Off-Flow 内仍防跳站；Resume 只回锚点 |

### 3.3 Future Hold 挂点

| 项 | 说明 |
|----|------|
| 主交付 | **Hold 模块**：Lot 级预约锁批 ✅ P0 已闭环 |
| 配置（后置） | RouteStep 关联 Hold 原因模板 / timing；Release 时实例化为 pending — **本期不做（FH-4）** |
| 执行 | Track 到站调 Hold.tryActivate → 生成 active Hold ✅ |
| 设计 | `docs/模块/Hold（锁批）模块/MES-FutureHold接口设计.md` |
| 另后置 | 通知 / Matrix / SPC 自动挂 — **本期不做（FH-5）** |

### 3.4 Queue Time

> 产品定位：**P1 半导标配**（超时拦截闭环）。**不对齐**大厂 L1 Pre-Gate / QMS / RL（属 P2，等 Dispatch 成熟 + 真实 fab 客户驱动）。

| 项 | 说明 |
|----|------|
| 业务含义 | trigger 站完成后 → target 站开工前的最大等待；超限风险：氧化/腐蚀/沾污 → 良率损失 |
| 配置 | 优先落 **边**：`from_sort → to_sort` + `max_queue_min`（`min_queue_min` 可选后置）；站字段 `max_queue_min` 可作兼容缓存 |
| 计时 | Track：`from` TrackOut 起算 → `to` TrackIn 止；**中间站保留开窗**（context 全程可展示剩余时间） |
| 执行 | 到期 → `@Scheduled` / TrackIn → Hold 和/或 Alarm；HOLD 清窗；解锁填备注后放行；在途 Lot 认放行时快照上限 |
| 本期不做 | Pre-Gate / Stopping 防进、独立 QMS、RL 放行、跨多站复杂 Time Link 排程 |
| 验收 | 超限自动 Hold、WIP=held；解锁填备注后可 TrackIn；中间站倒计时可见；升版不影响在途快照 |
| 设计 | `docs/模块/Route（工艺路线）模块/MES-QueueTime接口设计.md` |

---

## 4. P2（可后再开）

| 能力 | 说明 |
|------|------|
| 层级子流程 | RouteStep 类型=`sub_route`，引用另一 Route 的 active/指定版本；发布时展开或运行时展开（二选一，建议运行时展开+快照子 versionId） |
| 并行站 | 多站同时可 TrackIn；汇合规则 AND |
| 图形编辑器 | 节点/边拖拽；底层仍写边表 |
| Process Time | 加工时长上下限（偏 Track） |

---

## 5. 数据模型增量（草案）

```
mes_route_step          -- 保留；next_sort_no 降级为 default 边缓存（或废弃只读兼容）
mes_route_edge          -- 新增：version_id, from_sort, to_sort, edge_type, condition_code, max_rework?, max_queue_min?, sort_no
mes_step                -- 增：eqp_type, allow_skip, max_queue_min（可空；兼容/缓存，执行以边为准优先）
```

Lot / Track 运行态（Track 模块表，不进 Route）：
- `rework_count`（JSON 按站或整型全线）
- `off_flow` / `off_flow_anchor_sort`

发布规则增量：
- 每站 ≥1 条出边（终点站除外）
- 每站恰好 1 条 default
- rework/skip/off-flow 边目标合法

---

## 6. 接口增量（草案）

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/routes/versions/{id}/steps` | body 增 `edges[]`；兼容旧单 `nextSortNo` |
| GET | `/routes/versions/{id}` | 返回 steps + edges |
| — | Track API | `rework` / `skip` / `offFlow` / `resume`（见 Track 二期） |

权限：
- 配置沿用 `route:list/add/edit`
- 可选拆 `route:publish`（二期可做）
- 执行：`track:rework` / `track:skip`

---

## 7. 前端增量

| 页 | 改动 |
|----|------|
| RoutePage 草稿 | 步骤表 + **边/回流**编辑（目标站、条件、返工上限） |
| 版本只读 | 展示主路径 + 回流/分支标注 |
| 工序库 | 编辑 `eqp_type` 等属性 |
| 现场台 | Rework/Skip 入口（Track 页，强权限） |
| P2 | 图形画布 |

---

## 8. 实施切片（建议顺序）

| 切片 | 交付 | 估时参考 |
|------|------|----------|
| R2-1 | 边表 + 兼容线性 default + API/UI 读写 | 先打底 |
| R2-2 | Rework 配置 + Track rework 事务 + 次数 | P0 闭环 |
| R2-3 | 条件分支（人工选边 → 结果码） | P0 |
| R2-4 | Step `eqp_type` 强制开关 | P0 |
| R2-5 | Skip 白名单 + 事务 | P1 |
| R2-6 | Off-Flow + Queue Time（时间窗+超时 Hold/Alarm）/ Future Hold 挂点 | P1；QMS/Pre-Gate 不进本切片 |
| R2-7 | 子流程 / 并行 / 画布 | P2 |

---

## 9. 验收要点

1. 旧线性版本升版后可无边配置发布，行为与一期一致  
2. Rework 超次拒绝；履历可追  
3. 分支无匹配边时 TrackOut 失败  
4. 升版发布不影响在途 Lot 的边/上限  
5. 无 `track:skip` / `track:rework` 不能执行对应事务  
6. Recipe 仍不进 Route 快照  

---

## 10. 关联

- `MES-Route功能文档.md` §5  
- `MES-Route已完成功能.md` §7  
- `MES-QueueTime接口设计.md`  
- `docs/架构/MES-SpringScheduled使用.md`  
- `MES-Track功能文档.md`（Skip/Rework）  
- `MES-半导体业务清单.md` §2  
- `半导MES架构设计.md` §5.3  
