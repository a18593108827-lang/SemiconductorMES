# MES 量测趋势预警（SPC）— 一期功能清单

> 前提：EDC 一期 P0 已齐（点在 `mes_edc_collection*`；TrackOut 只认 OOS）；AlarmService.raise 已有  
> 对齐：`MES-SPC架构设计.md` · `MES-EDC与SPC范围说明.md` · `MES-EdcFacade接口设计.md`  
> 更新：2026-09-02  
> 状态：**SPC-1～5 已落地**

---

## 0. 目标

先交付「工艺能看见趋势、OOC 能响一声」；不挡过站、不锁批、不上独立 SPC。

原则：

- 点真相仍只在 EDC；SPC 只存图 / 控制限 / 判异记录
- 业务只调 `SpcFacade`；读点只经 `EdcFacade`
- EDC **不**依赖 `com.mes.spc`；采集提交后发事件，无人听也不影响采集
- TrackOut / 现场完工 **零改动**

---

## 1. 范围总览

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | `EdcFacade.listSeries` + 采集后事件（含业务编码） | ✅ |
| P0 | 图主数据 `mes_spc_chart`；I-MR；控制限 MANUAL/LEARNING | ✅ |
| P0 | `SpcFacade`；WE1；可选连跑；OOC → `AlarmService.raise(SPC_OOC)` | ✅ |
| P0 | 权限种子 `spc:view` / `spc:edit` | ✅ |
| P0 | HTTP `/spc` | ✅ |
| P0 | Admin `/app/spc` 图维护 + 趋势（工艺） | ✅ |
| P1 | n≥25 才返回 Cpk；规格限仅展示 | ✅（随 `getSeries`） |
| P2 | OOC Hold/锁机、X̄-R、WE 全集、OCAP、独立服务、FDC | 后置；见架构 §9 |

---

## 2. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| SPC-1 | EDC 读点口 + 事件：`listSeries`、`getCollection`；`idx_edc_col_step_time`；采集提交后 `EdcCollectedEvent`（AFTER_COMMIT） | ✅ |
| SPC-2 | DDL `mes_spc_chart` / `mes_spc_eval`；权限 257/258；`mes.spc.enabled` | ✅ |
| SPC-3 | `SpcFacade` + 监听 + I-MR 判异（WE1 / RUN）+ LEARNING 满 n 写限；OOC 写 eval + raise；吞异常 | ✅ |
| SPC-4 | HTTP `/spc` 薄封装 Facade；**无前端** | ✅ |
| SPC-5 | Admin `/app/spc`：图 CRUD / 改限 / 启停 / I 图+MR 图+点表；现场完工不改 | ✅ |

建议顺序：SPC-1 → 2 → 3 → 4 → 5。  
**禁止** SPC-3 先于 SPC-1（否则必注入 edc.mapper 或 EDC 依赖 SPC）。  
**禁止** SPC-5 直查 `mes_edc*` / `mes_spc_*`。

已有库上线前执行 `server/src/main/resources/db/migrate_spc.sql`（含 EDC 索引 + 权限种子 + 表）。

### SPC-1（EDC 侧，无 SPC 包）✅

- `EdcFacade.listSeries(paramId, stepId, eqpId, from, to, limit)`：规则见架构 §4  
- `EdcFacade.getCollection(collectionId)`：头+项；无则 null。供监听用 collectionId 拉点，禁止 SPC 注入 mapper  
- 事件字段（id + 编码，拆服务时不改模型）：`collectionId, lotId, lotNo, stepId, stepCode, eqpId, eqpCode, collectedAt`  
- 发布在采集事务内 `publishEvent`；监听方 `AFTER_COMMIT`；`com.mes.edc` 零 `import com.mes.spc`  
- 本切片 **无** 判异、无新表、无前端  
- 验收：无 SPC 时采集 API 行为与现网一致；`listSeries` 含 OOS 点  

落地：`EdcFacade` / `EdcSeriesPoint` / `EdcCollectedEvent` / `MesEdcCollectionMapper.listSeries`

### SPC-2（表 / 权限 / 配置）✅

- 表结构锁死架构 §5；**无** `value_num` 列  
- `uk_spc_chart_ctx (param_id, step_id, eqp_id)`，`eqp_id` 空存 `0`  
- 权限：`257 spc:view` 菜单 `/app/spc`；`258 spc:edit`  
- `mes.spc.enabled` 默认 true  
- 本切片 **无** 判异逻辑、无前端页（菜单可先挂、点进去可空）  

落地：`migrate_spc.sql` · `schema.sql` · `MesSpcChart` / `MesSpcEval` · Mapper

### SPC-3（观察者）✅

- 包 `com.mes.spc`；`@TransactionalEventListener(AFTER_COMMIT)` → `SpcFacade.onCollected`  
- 只调 `EdcFacade` + 本包 Mapper + `AlarmService`  
- 判异步骤锁死架构 §6；限空只学习不判 WE1  
- OOC **不** Hold、**不**改 Track context  
- 异常 catch 打日志；采集 HTTP 仍 200  
- `n < 25` 时 series 的 cpk/cp 为 null  
- 本切片可先无 HTTP（Bean + 监听可测）  

落地：`SpcFacade` / `SpcCollectedListener` / `SpcImr`

### SPC-4（HTTP）✅

- 前缀 `/spc`；查 `spc:view`，写 `spc:edit`；Facade 不鉴权  
- 与 Facade 方法一一对应；无旁路 SQL  
- 本切片无前端  

落地：`MesSpcController`

### SPC-5（工艺页）✅

- `/app/spc`：左列表 + 右趋势；按站/特性筛图；Drawer 维护上下文、limitMode、learningN、runN、手填限、启停  
- 趋势：I 图 + MR 图；规格限虚线「规格，不判 OOC」；OOS/OOC 标注；n<25 不展示 Cpk  
- **禁止** 改 TrackPage / context.edc / 完工按钮  

落地：`web/src/pages/SpcPage.tsx` · `web/src/api/spc.ts` · `web/src/components/spc/SpcTrendCharts.tsx`

---

## 3. 配置

| 键 | 默认 | 说明 |
|----|------|------|
| `mes.spc.enabled` | true | false：监听空操作；查询仍可读已有图。不删图、不挡采集 |

EDC 门禁键不变。`mes.edc.auto-hold-on-oos` 与 SPC 无关。

---

## 4. 验收（总）

见 `MES-SPC架构设计.md` §10。落地勾选见 `MES-SPC已完成功能.md`。

必验：

1. Track 无 `com.mes.spc` 依赖；`evaluateGate` / 完工与现网一致  
2. `com.mes.edc` 无 `com.mes.spc` 依赖  
3. SPC 无 `com.mes.edc.mapper`  
4. 无图 / 未满学习 n：采集成功、无 Alarm  
5. MANUAL 已填限 + 点出界：有 eval + `[ALARM] SPC_OOC`，Lot 不 Hold、能 TrackOut（点仍 PASS 时）  
6. 判异抛错时采集接口仍成功  
7. n<25 接口 cpk 为 null  

---

## 5. 关联

- `MES-SPC架构设计.md`
- `MES-SPC已完成功能.md`
- `docs/模块/EDC（量测）模块/MES-EDC与SPC范围说明.md`
- `docs/模块/EDC（量测）模块/MES-EdcFacade接口设计.md`
- `docs/架构/半导MES架构设计.md`
- `docs/架构/MES-实施进度与下一步.md`
