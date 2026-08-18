# Future Hold — 接口设计（架构）

> 定位：预约锁批——到站才变成真正的 Hold  
> 归属：**Hold 模块**（真相）；Track 到站点火；Route 模板后置  
> 对齐：`半导MES架构设计.md` §5.9 / §6.2；业界 SiView Pre/Post、Opcenter Future Hold  
> 前提：一期即时 Hold（`mes_hold` + Track `assertNoActive`）已落地  
> 更新：2026-08-06  
> 状态：**P0 已闭环**（FH-1～FH-3 前后端）；FH-4 / FH-5 **明确后置、本期不做**  
> 关键修复：`tryActivate` = `REQUIRES_NEW`，避免 TrackIn assert 回滚激活

---

## 0. 完成度

| 切片 | 交付 | 状态 |
|------|------|------|
| FH-1 | DDL + API Set/Cancel/List | ✅ `migrate_future_hold.sql` / `com.mes.hold` |
| FH-2 | Track PRE/POST + 改站钩子 + 履历 | ✅ |
| FH-3 | Hold 页 Tab + 现场台预约/pending | ✅ |
| FH-4 | RouteStep 挂点模板 → Release 实例化 | ⏳ **后置（本期不做）** |
| FH-5 | 通知 / Matrix / SPC 自动挂 | ⏳ **后置（本期不做）** |

**后置原因（产品结论 2026-08-06）：**  
- FH-4：日常主路径是 Lot ad-hoc；无「每批必卡某站」稳定规则前不做  
- FH-5：依赖通知通道、EDC/SPC；手工预约已够用  

---

## 1. 目标与产品边界

**产品一句话：** 在还不该停时先预约停点；Lot 到达目标站时激活为 active Hold。

| 做（P0 已落地） | 不做（本期 / 后置） |
|----------------|---------------------|
| Lot 级 Set / Cancel Future Hold | RouteStep 默认挂点模板（FH-4） |
| PRE / POST 触发 | Future Hold Matrix（FH-5） |
| 多条 pending；激活走现有 Hold | 多层并发 active Hold |
| Track 到站自动激活 | SPC / Q-Time / Alarm 自动挂（FH-5） |
| 履历可追；Hold/Track UI | 通知主备工程师（FH-5）；Future Action |

**与即时 Hold 关系**

```
即时 Hold     = 立刻刹车（mes_hold.active → Lot.held）
Future Hold   = 预约刹车（mes_future_hold.pending → 到站再 create mes_hold）
```

激活成功后的拦截、解锁、WIP、权限，**全部复用**现有 Hold，不另造一套锁批语义。

---

## 2. 模块边界

```
Hold   = mes_future_hold 真相；Set/Cancel/Activate；API
Track  = PRE@TrackIn前 / POST@TrackOut后 调 Hold.tryActivate
Route  = P2 才提供草稿模板；实例化仍写 Hold 表
Lot    = 只展示 pending；不存规则
WIP    = 仅 active Hold 时 held；pending 不改 WIP
```

**禁止**

- Route / Track 私自改 Lot.status 冒充 Future Hold  
- pending 阶段拦截 TrackIn/Out（违背预约语义）  
- 激活时绕过 `HoldService.create`（须走同一管道写 mes_hold + held + tx_log）  
- 绑「当前路线最新 active 版本」——须绑 Lot 的 `route_version_id` 快照  

**原则延续**

- Route 只定义；Track 只执行位置变迁；Hold 只负责拦不拦  
- 在途只认放行时 `route_version_id`（同 Rework/Skip/Off-Flow）

---

## 3. 状态机

### 3.1 mes_future_hold.status

```
pending ──Activate──► activated ──(mes_hold ReleaseHold)──► （future 行保持 activated）
   │
   └──Cancel──► cancelled
```

| 状态 | Lot / Track 影响 |
|------|------------------|
| `pending` | 不拦推进；可 Cancel |
| `activated` | 已生成 `mes_hold`；拦截由 active Hold 负责 |
| `cancelled` | 终态；未激活 |

`activated` 后不能 Cancel；只能 ReleaseHold 解真实锁。

### 3.2 激活与即时 Hold

```
tryActivate(lot, sortNo, timing)
  → 命中 pending（同 version + target_sort + timing）
  → 若已有 active Hold：拒绝激活并失败当前事务（或跳过本条——见 §8 决策）
  → HoldService.create(同一 reason) → mes_hold.active、Lot→held
  → future 行 → activated，记 hold_id
  → tx_log: FUTURE_HOLD_ACTIVATE
```

一期约定：**同一 Lot 仍最多一条 active Hold**。  
pending 允许多条（不同目标站）；同时刻只允许激活一条。

### 3.3 触发时机

| timing | 钩子位置 | 语义 |
|--------|----------|------|
| `PRE`（默认） | TrackIn **前**（`assertNoActive` 之前或合并） | 进站前拦住 |
| `POST` | TrackOut **成功**且新当前站落库后 | 出站落到该站后再锁 |

Off-Flow / Rework / Skip 等改站事务：凡「当前站发生变化」的路径，须在等价时机调用激活（至少 PRE：进入目标站可执行事务前）。

---

## 4. 数据模型

### 4.1 新表 `mes_future_hold`

| 字段 | 类型 | 空 | 说明 |
|------|------|----|------|
| id | BIGINT PK | N | |
| lot_id | BIGINT | N | |
| lot_no | VARCHAR(64) | Y | 冗余 |
| route_version_id | BIGINT | N | 放行快照；激活校验必须一致 |
| target_sort_no | INT | N | 目标站序 |
| timing | VARCHAR(8) | N | `PRE` / `POST` |
| reason_id | BIGINT | N | → mes_hold_reason |
| reason_code | VARCHAR(32) | N | 冗余 |
| status | VARCHAR(16) | N | pending / activated / cancelled |
| hold_id | BIGINT | Y | 激活后指向 mes_hold.id |
| remark | VARCHAR(512) | Y | |
| owner_user_id | BIGINT | Y | 主责（通知后置） |
| owner_user_name | VARCHAR(64) | Y | |
| create_user_id / create_user_name | | Y | |
| create_time | DATETIME | N | |
| activate_time | DATETIME | Y | |
| cancel_user_id / cancel_user_name / cancel_time | | Y | |
| cancel_remark | VARCHAR(512) | Y | |
| update_time | DATETIME | | |
| deleted | TINYINT | N | |

索引建议：

- `idx_fh_lot_status (lot_id, status)`  
- `idx_fh_activate (lot_id, route_version_id, target_sort_no, timing, status)`  

唯一约束（应用层即可）：同一 Lot 上同一 `(version, target_sort, timing)` 最多一条 **pending**（避免重复预约）。

### 4.2 不改 / 弱改

| 对象 | 说明 |
|------|------|
| `mes_hold` | 不扩语义；激活 = insert active |
| `mes_lot` | 可不加列；pending 靠查表。可选冗余 `has_future_hold` 后置 |
| `mes_route_step` | P0 **不**加挂点字段 |

### 4.3 事务码（mes_tx_log）

| tx_type | 说明 |
|---------|------|
| `FUTURE_HOLD_SET` | 预约 |
| `FUTURE_HOLD_CANCEL` | 取消未生效 |
| `FUTURE_HOLD_ACTIVATE` | 到站激活（可与 HOLD 同事务连写，或 Activate 内再写 HOLD） |

推荐：Activate 内先写 `HOLD`（复用 create），再写 `FUTURE_HOLD_ACTIVATE` 关联 `hold_id`；或 ext JSON 带 `futureHoldId`。

---

## 5. 接口

包：`com.mes.hold`；统一 `{ code, msg, data }`；Bearer。

### 5.1 设置

`POST /holds/future`  
权限：`hold:create`

```json
{
  "lotId": "1001",
  "targetSortNo": 50,
  "timing": "PRE",
  "reasonCode": "E_REVIEW",
  "remark": "到站工程确认"
}
```

规则：

- Lot 须在制且非终态；建议允许 `wait` / `processing` / `held`  
- `route_version_id` 取自 Lot 当前快照  
- `targetSortNo` 必须存在于该版本步骤  
- 目标站须为**当前站及之后**可到达站（主路径前向；Off-Flow 内另议，P0 可要求在主路径）  
- `OTHER` 须备注  
- 同 `(lot, version, target, timing)` 已有 pending → 拒绝或覆盖（默认**拒绝**）

### 5.2 取消

`POST /holds/future/{id}/cancel`  
权限：`hold:create`（或拆 `hold:future:cancel`，P0 复用 create）  
body：`{ "remark"? }`  
仅 `pending`。

### 5.3 查询

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/holds/future` | `hold:list` | 分页；默认 status=pending；筛选 lotNo / reason |
| GET | `/holds/future/{id}` | `hold:list` | 详情 |
| GET | `/lots/{lotId}/future-holds` | `hold:list` 或 `track:view` | 某批全部（含终态） |

### 5.4 Track 上下文增量

`GET /track/lots/{lotId}/context`（或现有 context）增加：

```json
{
  "pendingFutureHolds": [
    {
      "id": "...",
      "targetSortNo": 50,
      "timing": "PRE",
      "reasonCode": "E_REVIEW",
      "reasonName": "工程评审"
    }
  ]
}
```

不新增 Track 写接口；激活是钩子副作用。

### 5.5 权限

| 码 | 用途 |
|----|------|
| `hold:create` | Set Future Hold（P0 复用） |
| `hold:create` | Cancel pending（P0 复用） |
| `hold:release` | 仅解锁已激活的 mes_hold |
| `hold:list` / `track:view` | 查询 |

不新增 `track:*` 执行码——Future Hold 不是改站事务。

---

## 6. Track 集成（伪流程）

### 6.1 TrackIn

```
1. Hold.tryActivate(lot, currentSort, PRE)   // 可能已变成 held
2. Hold.assertNoActive(lot)                  // 含刚激活的
3. …既有 TrackIn
```

### 6.2 TrackOut

```
1. Hold.assertNoActive(lot)
2. …既有 TrackOut（含边解析、落新站）
3. Hold.tryActivate(lot, newSort, POST)
   // 若 POST 激活成功：Lot→held；本次 Out 已成功，下一次推进被拦
```

### 6.3 Rework / Skip / Off-Flow / Resume

站变更成功后：对**新当前站**执行 `tryActivate(..., PRE)`（进入新站时若存在 PRE 预约则立刻锁住，避免继续误加工）。  
细节与 Off-Flow 互斥：`off_flow=true` 时 P0 可禁止 Set，或允许但目标须在 Off-Flow 子序内——**P0 建议：Off-Flow 中禁止 Set Future Hold**。

---

## 7. 前端增量

| 入口 | 改动 |
|------|------|
| `/app/hold` | Tab 或区：Future Hold 列表 + 取消 |
| 现场台 Track | 「预约锁批」：选目标站 / timing / 原因；展示 pending |
| Lot / context | 显示未生效预约 |

激活后 UI 与现有 held 一致；解锁走原 ReleaseHold。

---

## 8. 架构决策记录

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 归属模块 | Hold | 拦截真相在 Hold；Route 只应提供模板 |
| D2 | 表结构 | 独立 `mes_future_hold` | pending≠active；避免污染 mes_hold 状态机 |
| D3 | 激活实现 | 调 `HoldService.create`；`tryActivate`=`REQUIRES_NEW` | 复用 held/WIP；避免 TrackIn assert 回滚激活 |
| D4 | 绑站方式 | `route_version_id + sort_no` | 与 Track 快照一致 |
| D5 | 默认 timing | PRE | 半导进站前卡控最常见 |
| D6 | 同时 active | 仍最多 1 条 | 延续一期；降低并发解锁复杂度 |
| D7 | 激活时已有 active | 事务失败 | 避免静默丢预约；人工先解再推 |
| D8 | Route 挂点 | P2 | 日常主路径是 Lot ad-hoc |

---

## 9. 实施切片

| 切片 | 交付 | 状态 |
|------|------|------|
| FH-1 | DDL + Entity/Mapper + Set/Cancel/List API | ✅ |
| FH-2 | TrackIn PRE / TrackOut POST 钩子 + 履历；`REQUIRES_NEW` | ✅ |
| FH-3 | context `pendingFutureHolds` + Hold 页 Tab + 现场台 | ✅ |
| FH-4 | RouteStep 模板 → Release 时实例化 pending | ⏳ 后置，本期不做 |
| FH-5 | 通知主备工程师；Matrix；SPC 自动挂 | ⏳ 后置，本期不做 |

---

## 10. 验收要点

P0 已对齐实现：

1. [x] Set 后上游站可正常 In/Out；pending 不出现 held  
2. [x] 到目标站 PRE：TrackIn 触发激活（独立事务）→ Lot held → In 失败  
3. [x] ReleaseHold 后可继续；future 行保持 activated 可查  
4. [x] Cancel 后到站不再激活  
5. [x] 升版发布不影响已挂 pending（认 Lot.route_version_id）  
6. [x] 无 `hold:create` 不能 Set；激活出的锁无 `hold:release` 不能解  
7. [x] 每笔 Set/Cancel/Activate（及内嵌 HOLD）可追 tx_log  
8. [x] TrackIn 失败后现场台刷新 context（状态 / pending 联动）

---

## 11. 与 Route 二期清单的关系

`MES-Route二期功能清单.md` §3.3「Future Hold 挂点」原表述偏 Route。

**纠正：**

- P0 Lot 级预约锁批在 **Hold**（本文档）✅ 已闭环  
- Route 侧「挂点」= FH-4，**本期不做**  
- FH-5 通知 / Matrix / SPC：**本期不做**  
- Queue Time 超时走 **立即 Hold**（`QTIME_EXCEED`，扫描或 TrackIn）；解锁必填备注后放行。Future Hold / QMS 处置不在本文范围。

---

## 12. 关联

- `MES-Hold功能文档.md`  
- `MES-Hold数据库设计.md`  
- `MES-Hold已完成功能.md`  
- `docs/模块/Route（工艺路线）模块/MES-Route二期功能清单.md` §3.3  
- `docs/架构/半导MES架构设计.md` §5.9、§6.2  
- Track / Rework / Skip / Off-Flow 接口设计（改站钩子对齐）
