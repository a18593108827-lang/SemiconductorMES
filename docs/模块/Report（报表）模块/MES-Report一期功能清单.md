# MES 报表（Report）— 一期功能清单

> 前提：History / Hold / Dashboard 一期已齐；尚无 `/app/report`  
> 对齐：`MES-Report架构设计.md` §0 · 业务清单 §12 · 实施进度 · 总册 §5.11  
> 更新：2026-09-09  
> 状态：**Rep-1～3 ✅ · Rep-4 未开工**

---

## 0. 目标

交付**可信复盘页**：选定日期窗内能看见过站量（按日 / 按站）与 Hold 原因分布，数字能与履历、锁批对上。

原则：

- 只读聚合；状态真相在 Track / Hold
- Move = `TRACK_OUT`；与 Dashboard 趋势同源
- 禁止假良率、假稼动
- 不挡 Track、不写业务表、不做 YMS / Excel
- **侧栏要有「报表」**，挂在独立 **「复盘」** 分组；**禁止**塞进「生产执行」
- 看板 / 履历链入仅作辅助下钻

---

## 1. 范围总览

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | `ReportFacade` + `GET /report/move` | ✅ |
| P0 | Move 按日 TRACK_OUT；缺日补 0 | ✅ |
| P0 | Move 按站；未归属桶 | ✅ |
| P0 | `GET /report/hold`；按 reasonCode | ✅ |
| P0 | Hold 计数 + 时长口径锁死 | ✅ |
| P0 | 权限 `report:view` | ✅ |
| P0 | Admin `/app/report` | ⬚ |
| P0 | 菜单：复盘 → 报表（不进生产执行） | ⬚ |
| P0 | 可选：看板 / 履历文字链 | ⬚ |
| P1 | Hold 按站；深链；导出；班次 | 后置 |
| P2 | Yield / OEE / 预聚合 / 自助 BI | 后置 |

---

## 2. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| Rep-1 | Facade + `/report/move` 按日；`report:view` 种子；byStep 可空 | ✅ |
| Rep-2 | Move `byStep` + step 显示名 | ✅ |
| Rep-3 | `/report/hold` 按原因 + 时长 | ✅ |
| Rep-4 | 前端页 + **复盘/报表**菜单 + 可选链入 | ⬚ |

顺序：Rep-1 → 2 → 3 → 4。  
**禁止** Rep-4 先于 Rep-1。  
**禁止** 任一切片引入 Yield / OEE 字段。  
**禁止** 菜单挂在「生产执行」或「系统管理」下。

---

### Rep-1（Move 按日骨架）✅

#### 1.1 交付

- 包 `com.mes.report`：`ReportFacade` / `ReportFacadeImpl` / `MesReportController` ✅
- `GET /report/move?from=&to=` ✅
- 权限：`report:view`（`migrate_report.sql` / schema）；菜单放 Rep-4 ✅
- `byDay[]`：窗内每日 `trackOutCount`；缺日补 0 ✅
- `totalTrackOut`；`generatedAt`；域失败 → `partial` ✅
- `byStep` = 空列表（Rep-2）✅

落地：`ReportFacadeImpl` · `ReportDateWindow` · `MesReportController` · `migrate_report.sql`

#### 1.2 口径锁死

| 项 | 锁死 |
|----|------|
| 事件 | `mes_tx_log.tx_type = TRACK_OUT`（与 Dashboard / `TrackServiceImpl.TX_TRACK_OUT` 同一常量） |
| 时间 | `create_time`；按**服务器本地**日历日切分（与 Dashboard 趋势一致） |
| 窗 | `from`/`to` 含两端；默认 to=今天、from=to-6；跨度 >31 → 400 |
| 空窗 | 合法但无数据 → 全日 0，非 404 |

#### 1.3 依赖

```
ReportFacade.moveSummary
  └── HistoryFacade 扩展聚合 或 HistoryTxLogMapper 按日 COUNT
      （推荐 Facade 方法，禁止 Controller 直调外域 Mapper）
```

可复用 / 扩展现有 `countDailyByTxType`；按站留给 Rep-2。

#### 1.4 本切片不做

- 前端页、按站、Hold、Excel
- MOVE / TRACK_IN 混算进「过站」

#### 1.5 验收（Rep-1）

- [x] 无 `report:view` → 403（`@SaCheckPermission`）
- [ ] 窗内人工 TrackOut N 次 → `totalTrackOut` 与 byDay 之和 = N（联调）
- [ ] 与同窗 Dashboard `outputTrend` 日合计一致（同 tz）（联调）
- [x] 跨度 32 日 → 400（`ReportDateWindow`）
- [x] Report 包无写接口

---

### Rep-2（Move 按站）✅

#### 2.1 交付

- `byStep[]`：`stepId` / `stepCode?` / `stepName?` / `trackOutCount` ✅
- 按 count 降序；`step_id` 为空 → `stepId=null`，名称「未归属」 ✅
- `byDay` 与 `byStep` 合计须相等（同一 TRACK_OUT 窗；联调勾）

落地：`HistoryFacade#countByStepAndTxType` · `ReportFacadeImpl#fillByStep`

#### 2.2 口径

| 项 | 锁死 |
|----|------|
| 分组键 | `mes_tx_log.step_id` |
| 显示名 | 只读 Step 主数据；找不到则仅 id /「未归属」 |
| 不按 | eqp、recipe、产品（后置） |

#### 2.3 验收（Rep-2）

- [ ] 两站各出站 → byStep 两行计数正确（联调）
- [ ] sum(byStep) == totalTrackOut == sum(byDay)（联调）
- [x] 无 step_id 的历史行进「未归属」，不丢数（实现口径）

---

### Rep-3（Hold 分布）✅

#### 3.1 交付

- `GET /report/hold?from=&to=` ✅
- `byReason[]`：`reasonCode` / `reasonName` / `holdCount` / `activeCount` / `avgDurationMinutes` ✅
- `totalHold`；时间窗规则同 Move ✅

#### 3.2 口径锁死

| 项 | 锁死 |
|----|------|
| 计入条件 | `mes_hold.hold_time` 的日期 ∈ [from, to]（按发生，非按仍在锁） |
| 分组 | `reason_code`；名从 `mes_hold_reason` |
| `holdCount` | 窗内发生笔数（含后续已释放） |
| `activeCount` | 上述中 `status=active` 的笔数 |
| 时长 | 每笔 duration = `(release_time ?? now) - hold_time`（分钟，向下取整）；`avgDurationMinutes` = 算术平均；无笔则为 null |
| 按站 | **一期不做**（hold 表无 step；见架构） |

#### 3.3 依赖

```
ReportFacade.holdSummary
  └── Hold 侧聚合（Service 新方法或 Mapper GROUP BY）
      + 原因名批量解析
```

#### 3.4 本切片不做

- 按站 Hold、Future Hold 单独主题、Release 操作
- 与 Alarm 交叉表

#### 3.5 验收（Rep-3）

- [ ] 窗内 Hold 原因 A 两笔 → 该行 holdCount=2
- [ ] 释放一笔 → activeCount 减、avg 仍含已释放时长
- [ ] 窗外 hold_time 不计入
- [ ] 无按站字段、无良率字段

---

### Rep-4（前端 + 菜单）

#### 4.1 交付

- `web/src/api/report.ts`
- `web/src/pages/ReportPage.tsx`；路由 `/app/report`
- 日期范围 + 查询；Move：按日图 + 按站表；Hold：原因表（可简单条形）
- 文案禁止「良率」「稼动」
- `partial` / 错误提示；链到 History、Hold
- **菜单种子**（新库 schema / 已有库 migrate）：
  - 目录：`复盘`（`perm_type=目录`，`perm_code` 空或 `review`）
  - 菜单：`报表` · `report:view` · path `/app/report` · 排序在「生产执行」与「系统管理」之间
- **禁止** parent = 生产执行
- 可选：看板趋势区 / 履历页「打开报表」文字链

#### 4.2 验收（Rep-4）

- [ ] 无 mock 数字
- [ ] 改 from/to 点查询 → 两块刷新
- [ ] 有 `report:view` → 侧栏出现「复盘 / 报表」并可进入
- [ ] 无权限 → 侧栏无该项；直链 403/无权限提示
- [ ] 「生产执行」分组项数与改前一致（无报表项）
- [ ] Admin Light，非 Dashboard 轮询壳

---

## 3. 总验收（一期）

- [ ] Rep-1～4 全部勾选
- [ ] Move 与 History TRACK_OUT 对账通过
- [ ] Hold 与 mes_hold 按 hold_time 对账通过
- [ ] Report 无业务写接口；无 Yield/OEE
- [ ] 侧栏「复盘 → 报表」可用；未污染生产执行
- [ ] 进度文档勾选 Report 一期完成

---

## 4. 后置

见架构 §8。Hold 按站 / Yield / OEE / 导出 / 预聚合不在本清单扩 scope。
