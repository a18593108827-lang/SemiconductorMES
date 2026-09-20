---
type: 功能文档
module: Hold
status: done
slices: []
aligns: []
updated: 2026-07-28
---

# MES 锁批（Hold）功能文档

> 定位：质量闸门——**暂停** Lot 前进；不替代 Track，只在 Track 前拦截  
> 对齐：`docs/架构/半导MES架构设计.md` §5.9、§6.2；业界 Hold Reason / Lot on Hold  
> 一期状态：**最小集已落地**（后端 + Admin/现场 UI；原因码维护页可后补）  
> 更新：2026-07-28  
> 查验：`MES-Hold已完成功能.md`

---

## 1. 目标（最小集）

- 对 Lot 发起 Hold / 解除 Hold  
- 强制原因码 + 备注  
- TrackIn / TrackOut / Release（及日后 Move）遇 **active Hold** 硬拒绝  
- 写履历；Lot.status → `held`，解锁后恢复原可执行态

**产品一句话：** Track 往前走，Hold 紧急刹车。

---

## 2. 边界

```
Track   = 唯一改加工位置/加工态的引擎
Hold    = 叠加拦截（active 时禁止推进类事务）
WIP     = 展示 held（投影已含 held）
History = mes_tx_log（HOLD / RELEASE_HOLD）
```

**一期做：**


| 能力          | 说明                                    |
| ----------- | ------------------------------------- |
| 原因码字典       | 预置种子码 + 后端启停 API；前端**只读消费**（Hold 下拉） |
| Hold        | 选 Lot + 原因码 + 备注 → active             |
| ReleaseHold | 解锁；权限可严于上锁                            |
| Track 钩子    | 存在 active Hold → 拒绝 In/Out（及放行若已在制相关） |
| 列表 / 详情     | 当前锁批、历史（同 Lot）                        |


**一期不做（后置不难，见 §9）：**

- 原因码**独立维护页**（CRUD / 启停 UI；见 §5.1）
- 同一 Lot 多层并发 Hold  
- Mass Hold / Mass Release  
- Release Code / 特批解锁  
- 自动 Hold（SPC/Alarm）  
- 片级 / 子批随挂  
- 完整 Disposition（重测/报废工作流）

---



## 3. 角色与权限（种子已有）


| 权限码            | 用途                    |
| -------------- | --------------------- |
| `hold:list`    | 锁批列表 / 详情 `/app/hold` |
| `hold:create`  | 发起锁批                  |
| `hold:release` | 解锁（建议默认不授予一线随意账号）     |


现场台也可调 Hold API（需权限）；按钮一期可做，与 Admin 同逻辑。

---



## 4. 状态与事务



### 4.1 Lot.status


| 变迁                             | 说明                           |
| ------------------------------ | ---------------------------- |
| wait / processing → `held`     | Hold 成功                      |
| `held` → 原态（wait 或 processing） | ReleaseHold；**须记住 hold 前状态** |


一期约定：Hold 记录保存 `prev_status`；解锁写回该值。若 `prev_status` 缺失，默认回 `wait`。

### 4.2 事务码（mes_tx_log）


| tx_type        | 说明  |
| -------------- | --- |
| `HOLD`         | 锁批  |
| `RELEASE_HOLD` | 解锁  |




### 4.3 Track 拦截

```
TrackIn / TrackOut（及日后 Move）
  → 查 lot 是否存在 status=active 的 mes_hold
  → 有则失败：「批次已锁批：{reasonName}」
ReleaseHold 不经此拦截
```

一期：**同一 Lot 同时最多一条 active Hold**（简化；表结构预留多条，见库表）。

---



## 5. 接口（已落地）


| 方法   | 路径                    | 权限                         | 说明 |
| ---- | --------------------- | -------------------------- | ---- |
| GET  | `/holds/reasons`      | `hold:list`                | 默认启用；`?all=true` 含停用 |
| PUT  | `/holds/reasons/{id}/status` | `hold:create`       | `{ status: 0\|1 }` |
| GET  | `/holds`              | `hold:list`                | 分页；默认 `status=active`；`all` 查全部；`keyword`/`reasonCode` |
| GET  | `/holds/{id}`         | `hold:list`                | 详情 |
| GET  | `/lots/{lotId}/holds` | `hold:list` 或 `track:view` | 某批 Hold 历史 |
| POST | `/holds`              | `hold:create`              | `{ lotId, reasonCode, remark }`；`OTHER` 须备注 |
| POST | `/holds/{id}/release` | `hold:release`             | `{ remark? }` |

包：`com.mes.hold`；Track In/Out → `HoldService.assertNoActive`。  
统一响应：`{ code, msg, data }`；Token：Bearer。

### 5.1 原因码字典：一期范围（产品结论）

**一期做「只读消费」，不做独立维护页。**

| 层 | 一期 | 说明 |
|----|------|------|
| 数据 | ✅ | SQL 预置 6 码（`migrate_hold.sql`） |
| 后端 | ✅ | list + 启停 API 已备；**无**新增/改名 CRUD |
| 前端消费 | ✅ | Hold 抽屉 / 现场台下拉只拉启用码 |
| 前端维护 | ❌ 后置 | 独立页做启停/CRUD |

**为何后置维护页：**

1. 使用频率低——日常是 Hold/Release，极少改字典  
2. 种子码已覆盖最小集场景；临时停用可走 SQL 或调 `PUT .../status`  
3. 启停挂在 `hold:create` 上，能上锁就能改字典，权限不干净；要做页应先拆 `hold:reason:edit`  
4. 后端尚无原因码新增/改名接口，单独做开关页价值有限  

**二期建议：** CRUD + 启停 UI + 独立权限，可与「原因绑解锁角色 / release code」同批。

---



## 6. 页面


| 入口                | 说明                      |
| ----------------- | ----------------------- |
| Admin `/app/hold` | ✅ 列表 + 发起 + 解锁 |
| 现场台（可选）           | ✅ Track 锁批/解锁面板 |


状态 pill：`held` 已有（琥珀 + 文案 + 图标）。

---



## 7. 验收要点（最小集）

1. 无原因码不能 Hold
2. active Hold 时 TrackIn/Out 失败
3. 解锁后可继续 In/Out；Lot 回到 prev_status
4. `QTIME_EXCEED` 解锁必须填备注
5. 每笔 Hold/解锁写 `mes_tx_log`
6. 无 `hold:release` 不能解锁
7. WIP 投影：Hold→held upsert；解锁→sync 回 wait/processing

---



## 8. 与已落地模块


| 模块    | 关系                             |
| ----- | ------------------------------ |
| Track | 事务前校验；可选提供 Hold/Release 事务入口   |
| Lot   | status=`held`；运行态字段保留          |
| WIP   | `mes_wip_lot.status=held`      |
| Auth  | `hold:list/create/release` 已种子 |


---



## 9. 后续扩展难吗？

**不难——前提是最小集按「Hold 记录表 + 原因码 + Track 钩子」落地。**


| 扩展             | 改动面                                     | 难度     |
| -------------- | --------------------------------------- | ------ |
| 原因码独立维护页       | 接已有 list/启停；补 CRUD API；拆 `hold:reason:edit`；Admin 抽屉 | 低 |
| 多层并发 Hold      | 校验改为「任意 active」；解锁按条；Lot.held 当 count>0 | 低      |
| Mass Hold      | 循环/批 API + 事务                           | 低      |
| Release 角色绑原因码 | reason 表加 `release_perm`；解锁校验           | 低      |
| Release Code   | hold 表加字段；解锁比对                          | 低      |
| 自动 Hold        | Alarm/SPC 调同一 `HoldService.create`      | 中（触发源） |
| Future Hold      | ✅ P0 已闭环；FH-4/FH-5 后置见 `MES-FutureHold接口设计.md` | — |
| Disposition    | 独立流程，Hold 只负责暂停                         | 中（新产品） |


**扩展友好约定（一期就遵守）：**

1. Hold **实体化**（`mes_hold`），不靠只改 Lot.status
2. Track **只调** `HoldService.hasActive(lotId)`，不写死业务
3. 原因码独立表，不写死枚举在代码里
4. tx_log 用 `HOLD` / `RELEASE_HOLD`，后续类型可加

---



## 10. 关联

- `MES-Hold数据库设计.md`  
- `MES-Hold已完成功能.md`  
- `MES-FutureHold接口设计.md`（预约锁批，**P0 已闭环**；FH-4/FH-5 后置）  
- Track / Lot / WIP 文档  
- 进度：`docs/架构/MES-实施进度与下一步.md`  
- 定时：`docs/架构/MES-SpringScheduled使用.md`  
- Queue Time：`MES-QueueTime接口设计.md`

