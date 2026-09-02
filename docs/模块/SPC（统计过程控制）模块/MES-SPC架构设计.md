# MES 量测趋势预警（SPC）— 架构设计

> 定位：只读观察 EDC 点 → 控制限 / 判异 → Alarm；**不**改 Lot 状态、**不**挡 TrackOut  
> 范式：**采集真相在 EDC，SPC 是只读观察者 + 提交后副作用**（同构 History 对 `mes_tx_log`）  
> 产品口径：给工艺看某站某特性最近是否在飘；不是现场 SPC 系统，不是 Camstar/SPACE 全集  
> 对齐：`docs/架构/半导MES架构设计.md` §3.2 / §5.8；`MES-EDC与SPC范围说明.md`；`MES-EdcFacade接口设计.md`  
> 前提：EDC 一期 P0 已齐（点在 `mes_edc_collection*`；门禁只认 OOS）  
> 状态：**架构已定 · SPC-1～4 已落地**；工艺页待 SPC-5  
> 更新：2026-09-02

---

## 1. 边界

```
SPC     = 图定义 + 控制限 + 判异 + 序列查询门面（本期同进程）
EDC     = 点真相 + Spec（LSL/USL）+ TrackOut 门禁；不判 OOC
Track   = 只调 EdcFacade 做 Out；不调 SPC、不存点、不画图
Hold    = 本期不因 OOC 挂；OOS 自动 Hold 仍只在 EDC
Alarm   = OOC 唯一闭环；raise 失败不得回滚采集
History = 不因 SPC 写 mes_tx_log（采集履历仍是 EDC_COLLECT）
FDC/APC = 另一条线；禁止进本包
```

**本切片做什么**

- 包 `com.mes.spc`；唯一对外 `SpcFacade`
- 图主数据：`param + step`，可选 `eqp`
- 图类型一期只 **I-MR**（Lot 级一点一值）
- 采集提交 **AFTER_COMMIT** 判异；OOC → `AlarmService.raise`
- Admin `/app/spc` 趋势页（工艺）；现场完工 **无新入口**
- `EdcFacade.listSeries`：SPC 只经此读点

**本切片不做什么**

- TrackOut 挂钩、改 `canTrackOut`、改完工按钮
- OOC 自动 Hold / 锁机 / OCAP
- 片级 subgroup、X̄-R / EWMA / CUSUM、全套 Western Electric
- 另造点表、把规格限当控制限
- 独立 SPC 服务（SPACE / Applied E3 形态）

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Track / Dispatch / Lot 注入 `com.mes.spc.*` 或 SPC Mapper | 过站与趋势分裂 |
| P2 | SPC 注入 `com.mes.edc.mapper.*` | 拆库时迁不动；点算法双份 |
| P3 | EDC 包编译依赖 `com.mes.spc` | 无 SPC 时采集必须仍能提交 |
| P4 | `mes_spc_*` 复制 `value_num` 当第二真相 | 与 EDC 点分裂 |
| P5 | 用 USL/LSL 填 UCL/LCL | 规格≠控制限；假失控 / 漏漂移 |
| P6 | 门禁或 `evaluateGate` 认 OOC | 合格批被过程预警卡住 |
| P7 | 判异失败回滚采集事务 | 副作用不得破坏采得进 |
| P8 | 样本不足仍当 Cpk 对外承诺 | 手录点稀疏；数字不成立 |
| P9 | 现场 TrackPage 新开 SPC 按钮 | 买家是工艺，不是操作员 |
| P10 | SPC 写 `mes_tx_log` / 改 `mes_lot` | 状态与履历仍只由 Track/EDC 采集路径写 |

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 形态 | 同进程 `SpcFacade`，对齐 Recipe / Edc / History | 一期不拆服务 |
| D2 | 点所有权 | 只读 EDC；SPC 只存图+限+判异记录 | 两点表必漂 |
| D3 | 两套限 | Spec 在 `mes_edc_spec`；Control 在 `mes_spc_chart` | 业界 VoC / VoP |
| D4 | 图上下文 | `param_id + step_id`；`eqp_id` 空=该站全部机 | 手录常无量测机；有机再按机拆图 |
| D5 | 图类型 | 仅 I-MR | 一期无片级 subgroup |
| D6 | 入图点 | 该上下文全部数值点（含 OOS），点上标注 `item_result` | 只画 PASS 会美化过程 |
| D7 | 触发 | EDC 采集事务提交后发 `EdcCollectedEvent`；SPC `@TransactionalEventListener(AFTER_COMMIT)` | 架构已定 Spring Events；无 SPC 则无人听 |
| D8 | 过站 | TrackOut **不**问 SPC | 门禁只认 OOS / 缺数 |
| D9 | OOC 动作 | 只 `AlarmService.raise("SPC_OOC", …)` | 合格批不可被趋势锁死 |
| D10 | 控制限 | `MANUAL` 或 `LEARNING`（满 `learning_n` 后算 ±3σ 并冻结到下次手工重算） | 点少时禁止自动 Hold 式乱限 |
| D11 | 规则 | **WE1 出界必做**；可选连跑 N 点同侧（默认 7，0=关） | 首刀可解释；不做 WE 全集 |
| D12 | Cpk | `n < 25` 返回 `null` + `sampleCount`；不展示假指数 | 手录达不到大厂采样密度 |
| D13 | 判异持久化 | `mes_spc_eval` 快照当时 UCL/CL/LCL + 规则；不存点值 | 告警可回放「按哪版限判的」 |
| D14 | 权限 | HTTP `spc:view` / `spc:edit`；Facade **不**鉴权 | 同 EdcFacade |
| D15 | 应急阀 | `mes.spc.enabled` 默认 true；false 时监听空操作、查询仍可读历史图 | 关掉判异，不删图 |

**刻度**：Lot 级点。`source=AUTO` 与 MANUAL 同一序列，图不区分来源。

---

## 3. 依赖与事件

```
TrackOut ──► EdcFacade.assertClearToTrackOut     （已有，不改语义）
采集提交 ──► mes_edc_collection* 提交
         ──► EdcCollectedEvent（AFTER_COMMIT）
                └── SpcFacade.onCollected
                       ├── EdcFacade.listSeries
                       ├── 写 mes_spc_eval（若已有限且命中规则）
                       └── AlarmService.raise(SPC_OOC)

Admin ──► SpcFacade.getChart / getSeries
SPC  ──x  edc.mapper / track / lot 写路径
EDC  ──x  com.mes.spc
```

`EdcCollectedEvent` 载荷（无 SPC 类型）：`collectionId, lotId, lotNo, stepId, stepCode, eqpId, eqpCode, collectedAt`。SPC 再经 Facade 拉点。发布方在 EDC 采集成功路径；**不得** `import com.mes.spc`。

`AlarmService.raise` 今日只打日志。SPC 仍调它：闭环口子先占住，完整 Alarm 模块后置不改 Facade。

---

## 4. 门面契约

包：`com.mes.spc.facade.SpcFacade`  
实现：`com.mes.spc.facade.impl.SpcFacadeImpl`

```
SpcFacade
  onCollected(collectionId)                    // 监听调用；内部吞异常，只打日志
  getChart(chartId) → SpcChartVO
  listCharts(stepId?, paramId?) → 列表
  getSeries(chartId, from?, to?, limit) → SpcSeriesVO
  saveChart(cmd) / setLimits(chartId, ucl, cl, lcl) / enable(chartId, enabled)
```

`SpcSeriesVO`：

| 字段 | 说明 |
|------|------|
| chart | 上下文、图类型、limitMode、UCL/CL/LCL、learningN、当前 n |
| specUsl / specLsl | **展示用**，来自点上快照或当前 Spec；**不参与** OOC |
| cpk / cp | n≥25 才有值，否则 null |
| points[] | time、value、lotId/lotNo、itemResult、collectionId、evalOoc? |
| lastEval | 最近一次 OOC 摘要；无则 null |

`EdcFacade` 增补（SPC 唯一读点口，实现仍在 EDC）：

```
  listSeries(paramId, stepId, eqpId, from, to, limit) → List<EdcSeriesPoint>
  getCollection(collectionId) → 采集头+点 | null   // SPC-1 监听用
```

| 参数 | 规则 |
|------|------|
| eqpId | null=不按机过滤（含采集 `eqp_id` 空）；非空只该机 |
| limit | 默认 100，max 500 |
| 排序 | `collected_at ASC, item.id ASC` |
| 软删 | 头、项 `deleted=0` |

点字段：`itemId, collectionId, lotId, lotNo, eqpId, collectedAt, valueNum, itemResult, uslSnap, lslSnap`。

EDC 补索引：`idx_edc_col_step_time (step_id, collected_at)`（已有库 `migrate_spc.sql` 一并执行）。

---

## 5. 表（种子，落地时出 DDL）

SPC **不**建点表。只建：

### 5.1 `mes_spc_chart`

| 字段 | 说明 |
|------|------|
| id | 雪花 |
| param_id / step_id | 必填 |
| eqp_id | 可空 |
| chart_type | 一期固定 `IMR` |
| limit_mode | `MANUAL` / `LEARNING` |
| learning_n | 默认 25 |
| ucl / cl / lcl | LEARNING 未满则为空；空则不判 WE1 |
| run_n | 连跑同侧；0=关；默认 7 |
| enabled | |
| 审计 / version / deleted | 同其它主数据 |

唯一：`uk_spc_chart_ctx (param_id, step_id, eqp_id)`（`eqp_id` 空用 `0` 占位）。

### 5.2 `mes_spc_eval`

| 字段 | 说明 |
|------|------|
| id | |
| chart_id | |
| collection_item_id | 逻辑引用，不硬 FK |
| ooc | 0/1 |
| rule_code | `WE1` / `RUN` |
| ucl_snap / cl_snap / lcl_snap | |
| create_time | |

索引：`idx_spc_eval_chart_time (chart_id, create_time)`、`uk_spc_eval_item (chart_id, collection_item_id)`（同点不重复判；重采新 item 新行）。

---

## 6. 判异（实现必须遵守）

1. 无启用图、或 `mes.spc.enabled=false` → 返回。  
2. 按 item.param + 头.step（+ 头.eqp 匹配 D4）找图；站级图与机台图可同时命中，各判一次。  
3. `ucl/lcl` 皆空 → 只累计 LEARNING；满 `learning_n` 后用这 n 点算 CL=均值、UCL/LCL=CL±3σ（σ 用移动极差估计，I-MR）；写入图上，mode 可仍为 LEARNING 但限已冻结，直到 `setLimits` 或显式重算。  
4. 有限：最新点 `value > ucl 或 < lcl` → WE1。  
5. `run_n>0`：含本点在内连续 run_n 点相对 CL 同侧 → RUN。  
6. 命中 → insert eval + `raise`。payload 必含 `chartId, collectionItemId, lotId, paramId, stepId, ruleCode, value, ucl, lcl`。  
7. 本方法任何异常 catch 后打错误日志，**不**抛回事件线程。

σ 样本不足（n<2）不算限、不判。

---

## 7. HTTP / 权限 / 配置

| 项 | 选择 |
|----|------|
| 前缀 | `/spc` |
| 查图/序列 | `spc:view` |
| 改图/改限 | `spc:edit` |
| 菜单 | `/app/spc`（工艺）；**不**挂现场台 |
| 配置 | `mes.spc.enabled`（默认 true） |

现场 context **不**增加 `spc` 段。

---

## 8. 与兄弟能力

| | EdcFacade 门禁 | SpcFacade 趋势 | Hold EDC_OOS |
|--|--|--|--|
| 问什么 | 本趟能不能出 | 这站这特性稳不稳 | 这批超规要不要锁 |
| 限 | LSL/USL | UCL/LCL | 原因码 |
| 何时 | TrackOut | 采集提交后 | 采集事务内（已有） |
| 失败 | 拒 Out | Alarm | 锁批 |

---

## 9. 延期（明确不在本期拍板实现）

| 项 | 再开条件 |
|----|----------|
| OOC → Hold / 锁机 | 点密度稳定 + 误报可接受 + 单独原因码 `SPC_OOC` |
| X̄-R / 片级 subgroup | `mes_lot_wafer` 与多点采集已落地 |
| WE 全集 / EWMA | 误报治理之后 |
| OCAP 工作流 | Alarm 模块有确认/关闭之后 |
| 独立 SPC 服务 | 图量、多厂、或要对齐 SPACE |
| FDC / R2R | Adapter 时序点，不进本包 |
| RocketMQ 替换 Spring Event | 已有总线且采集与 SPC 拆进程 |

---

## 10. 验收（架构）

1. Track 模块无 `com.mes.spc` 依赖。  
2. `com.mes.edc` 无 `com.mes.spc` 依赖；有 `EdcCollectedEvent`。  
3. SPC 无 `com.mes.edc.mapper` 注入。  
4. 无 `mes_spc_*` 点值列。  
5. `assertClearToTrackOut` 语义与现网一致（OOC 不影响）。  
6. 判异异常不导致采集接口失败。  
7. n<25 时 API 的 cpk 为 null。

切片：`MES-SPC一期功能清单.md`（SPC-1～5）。DDL 随 SPC-2：`migrate_spc.sql`。

---

## 11. 关联

- `MES-SPC一期功能清单.md`
- `docs/模块/EDC（量测）模块/MES-EDC与SPC范围说明.md`
- `docs/模块/EDC（量测）模块/MES-EdcFacade接口设计.md`
- `docs/模块/EDC（量测）模块/MES-EDC功能文档.md` P4
- `docs/架构/半导MES架构设计.md`
- `server/src/main/java/com/mes/alarm/service/AlarmService.java`
