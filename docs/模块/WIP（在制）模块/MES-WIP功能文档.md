---
type: 功能文档
module: WIP
status: done
slices: []
aligns: []
updated: 2026-07-27
---

# MES 在制（WIP）功能文档

> 定位：在制**只读**视图——回答「现在有哪些货、卡在哪站、什么状态」  
> 对齐：`docs/架构/半导MES架构设计.md` §5.2；业界 Lot Tracking 运行态视图（AMAT / ciMes 等）  
> 一期状态：**一期前后端已落地**（列表 + 按站汇总；WS 后置）  
> 更新：2026-07-27  
> 查验：`MES-WIP已完成功能.md`

---

## 1. 目标

- 班组长 / 工程师快速看清在制分布与单批当前位置  
- 数据只读，**不改** Lot 状态 / 当前站（真相在 Track）  
- 一期：列表筛选 + 按站汇总；不接设备看板、不接实时推送

---

## 2. 边界

```
Lot     = 主数据 + 快照 + Track 写入的运行态（真相源之一）
Track   = 唯一执行引擎（改状态）
WIP     = 在制查询 / 聚合（只读投影）
Hold    = 锁批（二期；WIP 仅展示 held）
Report  = 周期 / 产出分析（后置，不塞进 WIP）
```

**WIP 做：**

- 在制 Lot 列表（wait / processing / held）  
- 按工序站 / 产品 / 状态筛选  
- 按站汇总排队数  

**WIP 不做（一期）：**

- TrackIn / Out / Hold / Release（去现场台或 Lot）  
- 设备可用性矩阵、Alarm 墙（属 Eqp / Alarm）  
- Cycle Time / 瓶颈算法 / 产出日报（属 Report）  
- WebSocket 实时推送（可后置；一期手动刷新）  
- 片级 WIP、Carrier  

**与 Lot 列表差异：**

| | Lot 页 | WIP 页 |
|--|--------|--------|
| 范围 | 全生命周期（含 created / completed） | 默认仅在制 |
| 用途 | 建批 / 改属性 / 放行 | 看位置与排队 |
| 写操作 | 有 | **无** |

---

## 3. 角色与权限

| 权限码 | 用途 |
|--------|------|
| `wip:list` | 在制列表 / 按站汇总 / 详情跳转只读 |

菜单：`/app/wip`（种子已有 `wip:list`）。

---

## 4. 一期功能清单（MVP）

| 功能 | 说明 |
|------|------|
| 在制列表 | 分页；默认 `status ∈ {wait, processing, held}` |
| 筛选 | keyword（lotNo / productCode / customerLot）、status、currentSortNo、productCode |
| 列 | lotNo、status、productCode、qty、priority、当前站（sortNo+stepName）、设备、路线、更新时间 |
| 按站汇总 | 各 `current_sort_no`（或 step）下 wait/processing 数量 |
| 行操作 | 打开 Lot 详情（只读跳转）；可选「去现场台」深链带 lotNo |
| 刷新 | 手动刷新；不做自动推送 |

---

## 5. 数据来源（一期拍板）

**一期即用投影表 `mes_wip_lot`，由 Track 同事务维护；WIP API 只读投影。**

- `mes_lot`：运行态真相（全生命周期）  
- `mes_wip_lot`：在制读模型（仅 wait / processing / held；完工从投影删除）  

不采用「先扫 lot 再切表」：切表不难，难在漏同步；Track 写路径已收敛，一期双写更干净。

**禁止** WIP API / 页面对 Lot 或投影做业务写（投影仅 Track/约定同步点写入）。

---

## 6. 接口草案

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/wip` | `wip:list` | 在制分页列表 |
| GET | `/wip/summary/by-step` | `wip:list` | 按站汇总 |

### 6.1 `GET /wip`

Query：`keyword`、`status`、`productCode`、`currentSortNo`、`page`、`size`  
默认过滤：未传 status 时仅返回 wait / processing / held。

`data` 列表字段对齐 `MesLotVO` 精简版 + `currentStepName`（联查 `mes_step`）。

### 6.2 `GET /wip/summary/by-step`

返回：

```json
[
  { "sortNo": 10, "stepId": 5001, "stepName": "…", "waitCount": 3, "processingCount": 1, "heldCount": 0, "total": 4 }
]
```

仅统计在制状态。

统一响应：`{ code, msg, data }`；Token：Bearer。

---

## 7. 页面（Admin）

- 路径：`/app/wip`  
- 上：筛选条 + 刷新 + 现场台入口  
- 中：按站汇总条（简洁数字，非大色块 KPI 英雄区；点击筛站序）  
- 下：在制表格（mono 业务号，状态 pill）  
- 空态：「当前无在制批次」+ 链到 Lot 创建/放行  
- 行操作：链到现场台过账（**后续应带 lotNo 深链**，见 §9.1）

对齐：`DESIGN.md` Admin Light；`docs/UI/MES-UI设计.md` WIP 行。

---

## 8. 验收要点（一期）

1. 未放行 `created`、已完工 `completed` 默认不出现在 WIP  
2. TrackIn/Out 后刷新，站序与状态与 Lot/现场台一致  
3. 无 `wip:list` 不能进菜单 / 调接口  
4. WIP 无任何写状态接口  
5. 按站汇总人数与列表过滤结果一致  

---

## 9. 后续完善清单（产品债）

> 一期骨架可上线；下列按优先级补，**不必重做页面**。

### 9.1 P0 — 演示 / 班组长上手前建议做

| 项 | 现状 | 目标 | 验收 | 涉及 |
|----|------|------|------|------|
| 过账深链带批次 | 「过账」仅跳 `/track` | `/track?lotNo=xxx`，现场台自动载入 | 从 WIP 点过账后无需再搜批 | 前端 `WipPage` + `TrackPage` 读 query |
| 设备可读名 | 表列多为 `currentEqpId` | 显示设备编码/名称（无设备模块前可先冗余到投影） | 班组长不靠数字 ID 认机 | 后端 VO；可选 `mes_wip_lot` 冗余 `eqp_code`；依赖 Equipment |

### 9.2 P1 — 站多了再做

| 项 | 现状 | 目标 | 验收 | 涉及 |
|----|------|------|------|------|
| 拥堵高亮 | 各站卡片同等样式 | `total` 最大（或 wait 最多）站弱高亮 /「最堵」角标 | 3 秒内扫出瓶颈站 | 仅前端算 max，勿上大色块 KPI |
| 处理顺序提示 | 排序 priority+站序 | 筛到某站后副文案：「优先处理高优先级 wait」 | 操作员知道先干谁 | 文案；可选默认 status=wait |
| 与 Lot 页差异话术 | 副标题偏软 | 固定：「仅在制；建批/放行去批次管理」 | 新用户不问「和 Lot 啥区别」 | `WipPage` 文案 |

### 9.3 P2 / 二期能力

| 项 | 说明 | 前置 |
|----|------|------|
| WebSocket `wip.site.*` | 看板/本页自动刷新，可暂停 | 消息总线 |
| 区 / 线体维度 | 按厂区筛在制 | 工厂组织主数据 |
| 与 Dispatch 联动 | 站队列 → 派工建议 | Dispatch 模块 |
| 瓶颈 / 排队时长 | 进站等待时长、CT | Report 或履历派生 |
| 投屏 WIP 看板 | 大屏密度，非本页变体即可 | 看板 Shell |

### 9.4 明确不做（避免范围漂移）

- WIP 页写状态 / Hold / TrackIn  
- 玻璃拟态卡片墙、大色块英雄指标  
- 把设备矩阵、Alarm 墙塞进 WIP（属 Eqp / Alarm）

---

## 10. 关联文档

- `MES-WIP数据库设计.md`  
- `MES-WIP已完成功能.md`  
- Track：`docs/模块/Track（执行引擎）模块/`  
- Lot：`docs/模块/Lot（批次）模块/`  
- 进度：`docs/架构/MES-实施进度与下一步.md`  
