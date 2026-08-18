# MES 批次（Lot）— 二期功能清单

> 前提：一期 Lot 主数据 + Release 绑 Route 快照已落地  
> 对齐：`MES-半导体业务清单.md` §1 · 大厂 Lot Tracking（SiView / Camstar / AMAT）  
> 更新：2026-08-10

---

## 0. 目标

在**不拆掉「状态只由 Track 写」契约**的前提下，把 Lot 从「Lot 级主数据」升级为半导可追溯的批次实体：分合批谱系、报废、数量调整，并为片级 / Carrier 留口。

原则：
- Lot 仍只负责「实体 + 谱系 + 属性」；Track 负责「执行事务」
- Split / Merge / Scrap / Bonus **必须走事务**，禁止直接改 `qty` / `status`
- 子 Lot 继承父 Lot 的 `route_version_id`（同快照）；跨版本切工艺属 P2
- Carrier / SlotMap 物理容器与 Lot 解耦（Carrier 模块消费）

---

## 1. 范围总览

| 优先级 | 能力 | Lot 侧 | Track 侧依赖 |
|--------|------|--------|--------------|
| P0 | Split 分批 | 子 Lot 号、数量、父子谱系 | `track:split` 事务 |
| P0 | Merge 合批 | 源 Lot 合并、谱系闭合 | `track:merge` 事务 |
| P0 | Scrap 报废 | 全批 / 部分数量报废 | `track:scrap` 事务 |
| P0 | Bonus 数量调整 | 合法增/减 qty（非报废） | `track:bonus` 事务 |
| P0 | Genealogy 查询 | 父子树 / 反向追溯 API | 读 `mes_lot_genealogy` |
| P1 | Wafer / Unit | 片级主数据 + Lot 成员 | Split/Scrap 可落到片 |
| P1 | Carrier 绑定 | Lot↔FOUP；SlotMap 占位 | Carrier 模块；TrackIn 校验 |
| P1 | Hot Lot 标记 | `hot_flag` / 优先级策略说明 | Dispatch 加权消费 |
| P1 | 已放行属性约束 | 可改字段白名单落地 ✅ | — |
| P2 | 中途切 Route 版本 ⏸ | 切换点 + 新快照（**设计/审批未齐，暂缓**） | Track `change-route` |
| P2 | Change Product ⏸ | 已放行改产品专用事务（**暂缓**；禁 PUT 已落地） | Track 换型事务 |
| P2 | ERP / 工单下发 | 外部 Lot 创建入口 | 对接层 |
| 后置 | Experiment / Send-ahead | 工程分批、先遣片 | 大厂进阶 |
| 不做（二期） | 绕过 Track 改 qty/status | — | 硬禁止 |

---

## 2. P0 功能明细

### 2.1 Split 分批

| 项 | 说明 |
|----|------|
| 前置 | 父 Lot 非 `created`；非 `held`（或 Hold 允许分批策略二选一，建议**禁止 Hold 中 Split**）；非 `scrapped` / `completed` |
| 输入 | 父 `lotId`、子数量列表（或片列表 P1）、可选指定子 `lotNo` |
| 规则 | Σ子 qty = 父原 qty；父 qty 置 0 或父保留余量（**建议：父保留余量，子吃走指定量**） |
| 继承 | 子继承 `product_code` / `route_id` / `route_version_id` / 当前站运行态（由 Track 复制 WIP） |
| 谱系 | 写 `mes_lot_genealogy`：`parent_id → child_id`，`txn=split` |
| 权限 | `lot:split` 配置查询；执行 `track:split` |
| 验收 | 数量守恒；履历可追；子 Lot 可独立继续 Track |
| 设计 | `MES-LotSplit接口设计.md` |

### 2.2 Merge 合批

| 项 | 说明 |
|----|------|
| 前置 | 源 Lots ≥2；同 `product_code`；同 `route_version_id`；同当前站（`current_sort_no`）；均非 Hold |
| 输入 | 主 Lot（保留）+ 被合并 Lot 列表 |
| 规则 | 主 qty += Σ被合并 qty；被合并 Lot → `merged`（终态，不可再 Track） |
| 谱系 | 写 genealogy：`child/source → parent/target`，`txn=merge` |
| 禁止 | 跨产品、跨快照、跨站、加工中（`processing`）强制合批（一期建议禁） |
| 权限 | 执行 `track:merge` |
| 验收 | 数量守恒；被合并 Lot 不可 TrackIn；谱系可反查 |
| 设计 | `MES-LotMerge接口设计.md` |

### 2.3 Scrap 报废

| 项 | 说明 |
|----|------|
| 模式 | **全批 Scrap**：status→`scrapped`，qty→0，记入 scrap_qty；**部分 Scrap**：qty 减量，写 scrap 履历（P1 可落到片） |
| 原因 | 必填 reason_code（P0 白名单；后对接 Reason 主数据） |
| 状态 | 全批后不可再 Track；部分后可继续 |
| 权限 | `track:scrap` |
| 验收 | 不可复活无审批流（二期不做 Unscrap；P2 再议） |
| 落地 | ✅ 后端 + TrackPage + LotsPage scrap_qty；脚本 `migrate_lot_scrap.sql` |
| 设计 | `MES-LotScrap接口设计.md` |

### 2.4 Bonus 数量调整

| 项 | 说明 |
|----|------|
| 场景 | 盘点差异、计量修正；**非**报废、**非**分合批 |
| 规则 | 写明 delta（可正可负）；结果 qty≥0；写履历；**不改** scrap_qty/status |
| 禁止 | 用 Bonus 代替 Split/Merge/Scrap |
| 权限 | `track:bonus`（强权限） |
| 落地 | ✅ 后端 + TrackPage；脚本 `migrate_lot_bonus.sql` |
| 设计 | `MES-LotBonus接口设计.md` |

### 2.5 Genealogy 查询

| 项 | 说明 |
|----|------|
| API | 向上（祖先）、向下（子孙）；节点含边 qty / txId / reason |
| UI | Lot 详情谱系简图：当前居中、直系默认、展开深链、影响面汇总 |
| 文案 | 分出 = Split；并入 = Merge（见设计 §0.2） |
| 数据 | 只读；真相在 `mes_lot_genealogy` + `mes_tx_log` |
| 设计 | `MES-LotGenealogy接口设计.md` |
| 状态 | ✅ P0 闭环；片级 / 家族 Hold / 客诉包后置 |

---

## 3. P1 功能明细

### 3.1 Wafer / Unit 级

| 项 | 说明 |
|----|------|
| 表 | `mes_lot_wafer`：`lot_id, wafer_id/slot_no, status, scrap_flag` |
| 创建 | Release 时按 qty 生成片位（或外部导入 Wafer ID） |
| Split | 可按片列表拆；qty 与片数一致 |
| Scrap | 可按片报废；Lot.qty 同步 |
| 验收 | Lot.qty == 未报废片数 |

### 3.2 Carrier 绑定

| 项 | 说明 |
|----|------|
| 模型 | Lot↔Carrier 多对一（SiView 支持一盒多 Lot；**二期先做一 Lot 一 Carrier**） |
| SlotMap | 占位字段 / 表，执行校验放 Carrier 模块 |
| Track | TrackIn 可选校验 Carrier 已绑 |
| 设计 | 详见后续 Carrier 模块文档；Lot 只挂 `carrier_id` |

### 3.3 Hot Lot

| 项 | 说明 |
|----|------|
| 字段 | `priority` 主序 + 显式 `hot_flag`（双字段，见设计） |
| 消费 | **WIP/Lot 列表急度排序已落地**；Dispatch 多 Lot 争机（HT-4）⏸ 延后 |
| 权限 | `lot:edit` 可改（已放行也允许改优先级/Hot） |
| 设计 | `MES-LotHot接口设计.md`（§6.3.1 产品决策） |

### 3.4 已放行属性白名单

| 可改 | 不可改 |
|------|--------|
| `priority` / `hot_flag` / `customer_lot` / `remark` | `product_code` / `qty`（改 qty 走事务）/ `route_*` |

设计：`MES-Lot已放行属性约束设计.md`

---

## 4. P2（可后再开）

| 能力 | 说明 |
|------|------|
| 中途切 Route 版本 | ⏸ 暂缓：指定切换站；写新 `route_version_id`；须**工艺变更审批**（≠权限申请）；Track `change-route`；禁 PUT |
| Change Product | ⏸ 暂缓：已放行改 `product_code` 专用事务；禁 PUT；常与切 Route 同发；开做时立设计 |
| ERP / 工单创建 | 外部单号 → Lot；幂等 |
| Unscrap / 数量回滚 | 强审批 + 履历 |
| Experiment / Send-ahead | 工程分批、先遣片独立路径 |
| 一盒多 Lot | Carrier 内多 Lot 共存（对齐 SiView） |

---

## 5. 数据模型增量（草案）

```
mes_lot                 -- 增：parent_lot_id?, scrap_qty, hot_flag, carrier_id?, merged_to_lot_id?
                        -- status 增：merged
mes_lot_genealogy       -- 新增：id, txn_type(split|merge), parent_lot_id, child_lot_id,
                        --        qty, tx_id/ref, create_by, create_time
mes_lot_wafer           -- P1：lot_id, wafer_no, slot_no, status, scrap_flag
mes_lot_carrier         -- P1 或归 Carrier 模块：lot_id, carrier_id, bind_time
```

约束：
- Split/Merge/Scrap/Bonus **禁止** `PUT /lots/{id}` 改 qty/status
- `merged` / `scrapped` / `completed` 不可再 Track（除专用解禁 P2）

运行态仍归 Track/WIP：`current_sort_no` / `current_step_id` / `current_eqp_id`

---

## 6. 接口增量（草案）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/track/split` | `track:split` | 分批事务 |
| POST | `/track/merge` | `track:merge` | 合批事务 |
| POST | `/track/scrap` | `track:scrap` | 报废事务 |
| POST | `/track/bonus` | `track:bonus` | 数量调整 |
| GET | `/lots/{id}/genealogy` | `lot:list` | 谱系树 |
| GET | `/lots/{id}/wafers` | `lot:list` | P1 片列表 |
| POST | `/lots/{id}/carrier` | `lot:edit` | P1 绑/解绑 Carrier |

权限种子增量：

| 码 | 说明 |
|----|------|
| `lot:split` | 可选；若仅 Track 执行可不上 |
| `track:split` / `track:merge` / `track:scrap` / `track:bonus` | 执行事务 |

---

## 7. 前端增量

| 页 | 改动 |
|----|------|
| LotsPage 详情 | 谱系简图；Hot 标记；已放行字段禁用规则 |
| 现场台 / Track | Split / Merge / Scrap / Bonus 操作入口 + 二次确认 |
| P1 | 片列表、Carrier 绑定控件 |

---

## 8. 实施切片（建议顺序）

| 切片 | 交付 | 估时参考 |
|------|------|----------|
| L2-1 | `mes_lot_genealogy` + status=`merged` + 字段增量 | ✅ |
| L2-2 | Split 事务 + 子 Lot 继承快照/站 + 履历 | ✅ |
| L2-3 | Merge 事务（同站同快照）+ 谱系 + 现场台 | ✅ |
| L2-4 | Scrap（全批+部分）+ Bonus | ✅ Scrap；✅ Bonus |
| L2-5 | Genealogy API/UI（简图）+ 已放行白名单 + Hot Lot | ✅ |
| L2-6 | Wafer 表 + 按片 Split/Scrap | ⏳ P1 |
| L2-7 | Carrier 绑定（依赖 Carrier 模块） | ⏳ P1 |
| L2-8 | 中途切版本 / Change Product（均 ⏸）/ ERP / Unscrap | ⏳ P2（切版·换型暂缓） |

---

## 9. 验收要点

1. 任何 qty/status 变更均可在 `mes_tx_log`（及 genealogy）追溯  
2. Split/Merge 后数量守恒；跨产品/跨快照/跨站 Merge 拒绝  
3. 子 Lot 认父放行时的 `route_version_id`，不随后续 Route 升版变化  
4. `scrapped` / `merged` Lot 不可 TrackIn  
5. `PUT /lots/{id}` 改 qty 必须失败（引导走事务）  
6. Hold 中默认不可 Split/Merge/Scrap/Bonus（与 Hold 模块对齐）  
7. Bonus 不改 `scrap_qty`/status；负 Bonus ≠ Scrap  

---

## 10. 关联

- `MES-Lot功能文档.md` §5  
- `MES-Lot已完成功能.md` §2  
- `MES-Lot数据库设计.md` §3  
- `MES-LotSplit接口设计.md`  
- `MES-LotMerge接口设计.md`  
- `MES-LotGenealogy接口设计.md`  
- `MES-LotScrap接口设计.md`  
- `MES-LotBonus接口设计.md`  
- `MES-LotHot接口设计.md`  
- `MES-Lot已放行属性约束设计.md`  
- `MES-Track功能文档.md`  
- `MES-半导体业务清单.md` §1  
- `半导MES架构设计.md` §5.1  
