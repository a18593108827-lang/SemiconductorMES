---
type: 接口设计
module: Lot
status: done
slices: []
aligns: []
updated: 2026-08-10
---

# Lot Bonus 数量调整 — 接口设计（架构）

> 定位：盘点/计量导致的 WIP 数量纠偏，**不进报废口径**，数量可审计  
> 归属：**Track 执行事务**；Lot 只改 `qty`；`scrap_qty` / `status` **禁止**由 Bonus 改写  
> 对齐：`半导MES架构设计.md` §4.3 / §5.1；`MES-Lot二期功能清单.md` §2.4  
> 业界：Camstar SEMI `Lot Bonus` + Bonus Reason；Oracle OSFM Scrap/Bonus Reason Code；SiView 类 WIP 调量事务  
> 前提：一期 Lot + Track + Hold；二期 Split/Merge/Scrap 已落地  
> 更新：2026-08-10  
> 状态：**P0 已闭环**（BN-1～BN-4）；BN-5·BN-6 后置  
> **易混：** Bonus ≠ Scrap；Bonus ≠ Split/Merge；负 Bonus ≠ 报废核销

---

## 0. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| BN-1 | 权限种子 `track:bonus`；原因码白名单（常量/配置） | ✅ |
| BN-2 | `POST /track/bonus` 事务 + 锁 + `mes_tx_log`；±delta | ✅ |
| BN-3 | 现场台 Bonus 入口 + 二次确认；履历 BONUS；文案与 Scrap 隔离 | ✅ |
| BN-4 | 与 TrackIn/Split/Merge/Scrap 互斥校验；终态 hardening | ✅ 随 BN-2 |
| BN-5 | 按片 Bonus（依赖 `mes_lot_wafer`） | ⏳ **P1** |
| BN-6 | 冲正 / 双人审批 / ERP 库存过账 | ⏳ **P2** |

实施归属二期切片 **L2-4**（Scrap + Bonus 均已闭环）。

---

## 0.1 与 Scrap / Split 对照（必读）

| | Scrap 报废 | Bonus 调量 | Split 分批 |
|--|--|--|--|
| 产品含义 | 不良核销 | 盘点/计量修正 | 材料分道 |
| qty | ↓（核销） | ↑ 或 ↓ | 父↓ 子↑（守恒） |
| scrap_qty | ↑ | **不动** | 不动 |
| status | 全批→`scrapped` | **不变** | 父子仍可 Track |
| 谱系 | 不写 genealogy | 不写 | 写 `split` |
| 原因 | 必填 scrap reason | 必填 bonus reason | 可选 |
| API | `POST /track/scrap` | `POST /track/bonus` | `POST /track/split` |
| 权限 | `track:scrap` | `track:bonus`（**更严**） | `track:split` |

**禁止**用负 Bonus 抹报废；禁止用正 Bonus 代替 Merge/收货；禁止用 Split/Merge 冲盘点差。

---

## 1. 目标与产品边界

**一句话：** 在不污染 `scrap_qty` / 良率口径的前提下，用强权限事务把 Lot.`qty` 纠正到与实物一致。

| 做（P0） | 不做（本期） |
|----------|--------------|
| `delta ≠ 0`；`qty_after = qty + delta ≥ 0` | 改 `scrap_qty` / `status` |
| reason_code 必填（白名单） | Reason 主数据 CRUD |
| Hold / processing / 终态拒绝 | 双人审批 / 电子签 |
| 写 `mes_tx_log`（`tx_type=BONUS`） | ERP / 成本过账 |
| 二次确认 UI；与 Scrap 文案隔离 | 自动盘点纠数 |
| `qty_after=0` 时 status **仍不变**（见 §3.2） | Unbonus 冲正 |

---

## 2. 模块边界

```
Track  = Bonus 事务唯一入口；锁 Lot；只改 qty；写 tx_log；同步 WIP.qty
Lot    = 主数据承载 qty；不提供改量 API；详情可展示最近 BONUS（履历）
WIP    = qty 同步；qty=0 时不可派工/TrackIn（投影保留或按现有 wait 策略）
Hold   = Bonus 前 assertNoActive
Route  = 只读；不换版、不改站
History= 只写 mes_tx_log；不写 mes_lot_genealogy
Reason = P0 白名单常量；字段名 reasonCode，与 Scrap 字典隔离
```

**禁止**

- `PUT /lots/{id}` 改 `qty` 冒充 Bonus  
- LotService 私自改 qty  
- `scrapped` / `merged` / `completed` 上 Bonus  
- 负 Bonus 代替 Scrap；正 Bonus 代替收货/合批  
- Bonus 写 genealogy；Bonus 写 `scrap_qty`

**原则延续**

- 状态唯一真相在 Track  
- 单 DB 事务强一致；通知 `mes.lot.changed`  
- Bonus **不改变生命周期状态**；归零不等于报废

---

## 3. 状态与前置

### 3.1 允许 Bonus 的状态

```
wait ──Bonus(±delta)──► wait（qty 变，status 不变，scrap_qty 不变）
```

| status | Bonus |
|--------|-------|
| `created` | ❌ 未进生产；创建态数量走 Lot 创建/编辑策略，不走 Bonus |
| `wait` | ✅ |
| `processing` | ❌ 先 TrackOut |
| `held` | ❌ 先 ReleaseHold |
| `completed` / `scrapped` / `merged` | ❌ 终态 |

### 3.2 数量规则（硬）

```
输入 delta：整数，delta ≠ 0
qty_after = qty_before + delta
约束：qty_after ≥ 0
       qty_after ≤ Integer.MAX_VALUE（防溢出）

scrap_qty：禁止读写变更
status：禁止变更（即使 qty_after = 0）
```

| 边界 | 规则 |
|------|------|
| `delta = 0` | 拒绝 |
| `qty_after < 0` | 拒绝 |
| `qty_after = 0` | **允许**；status 仍 `wait`；后续 TrackIn/Split/Scrap 按 qty 校验失败或按既有规则；UI **强提示**「归零请确认是否应走 Scrap」 |
| 正向上限 | P0 不设业务软顶；P1 可配 `bonus.maxAbsDelta` |

架构取舍：归零不自动 `scrapped`，避免 Bonus 污染报废/良率；故意核销必须走 Scrap。

### 3.3 原因码（硬）

| 规则 | 说明 |
|------|------|
| 必填 | `reasonCode` 非空 |
| 白名单 | **独立于 Scrap 字典**；串用拒绝 |
| 自由备注 | `remark` 可选；`OTHER` 必填 |
| 主数据 | P1 换表后字段名不变 |

P0 建议种子：

| code | 含义 | 典型方向 |
|------|------|----------|
| `CYCLE_COUNT` | 盘点差异 | ± |
| `RECEIPT_CORR` | 收货/点片修正 | ± |
| `METROLOGY_ADJ` | 计量/点料修正 | ± |
| `SYSTEM_CORR` | 系统录入错误纠正 | ± |
| `OTHER` | 其他（须填 remark） | ± |

---

## 4. 数据契约

### 4.1 `mes_lot`

| 字段 | Bonus 行为 |
|------|------------|
| `qty` | `+= delta` |
| `scrap_qty` | **不改** |
| `status` | **不改** |
| `parent_lot_id` / `merged_to_lot_id` | 不改 |
| `route_version_id` / `current_*` | 不改 |
| `version` | ++ |

无新表；无 `bonus_qty` 累计字段（P0）。若报表需要累计调量，从 `mes_tx_log` 聚合 `tx_type=BONUS`。

### 4.2 不写 `mes_lot_genealogy`

调量不是谱系转移。追溯靠 `mes_tx_log`（`tx_type=BONUS` + `ext_json`）。

### 4.3 `mes_tx_log`

| 字段 | 值 |
|------|-----|
| `tx_type` | `BONUS` |
| `lot_id` / `lot_no` | 本批 |
| `from_status` / `to_status` | 同为当前 status（通常 `wait→wait`） |
| `from_sort_no` / `to_sort_no` | 同当前站 |
| `step_id` / `eqp_id` | 当前站/机（可空） |
| `route_version_id` | 当前快照 |
| `remark` | 请求备注 |
| `ext_json` | 见下 |
| `oper_user_*` | 操作人 |

`ext_json`：

```json
{
  "txn": "bonus",
  "delta": -2,
  "qtyBefore": 25,
  "qtyAfter": 23,
  "reasonCode": "CYCLE_COUNT"
}
```

`ext_json` 受 `VARCHAR(512)` 约束：超长 **拒绝**，禁止静默截断。

---

## 5. 事务流水（Track）

```
1. 锁 Lot（单批锁 + version）
2. Hold.assertNoActive(lotId)
3. 校验：status=wait；delta≠0；qty+delta≥0；无溢出；reasonCode∈Bonus 白名单
   （OTHER → remark 必填）
4. UPDATE mes_lot：qty = qtyAfter，version++
   （显式不碰 scrap_qty / status）
5. INSERT mes_tx_log（BONUS + ext_json）
6. 同步 WIP.qty；qty=0 时不可派工（与现有 qty 校验对齐）
7. 提交 → mes.lot.changed
```

失败整单回滚。

---

## 6. API

### 6.1 执行调量

`POST /track/bonus`  
权限：`track:bonus`

```json
{
  "lotId": 100,
  "delta": -2,
  "reasonCode": "CYCLE_COUNT",
  "remark": "盘点少 2 片"
}
```

| 字段 | 约束 |
|------|------|
| `lotId` | 必填 |
| `delta` | 必填；非 0 整数 |
| `reasonCode` | 必填；Bonus 白名单 |
| `remark` | 可选；`OTHER` 必填 |

成功 `data`：

```json
{
  "lotId": 100,
  "lotNo": "LOT-…",
  "delta": -2,
  "qty": 23,
  "scrapQty": 0,
  "status": "wait",
  "txId": 9301
}
```

错误（示例）：

| 条件 | 行为 |
|------|------|
| 非 wait / held / processing / 终态 | 拒绝 |
| delta=0 / qty_after\<0 | 拒绝 |
| reasonCode 非法或误用 Scrap 码 | 拒绝 |
| 乐观锁冲突 | 拒绝可重试 |
| 无 `track:bonus` | 401/403 |

### 6.2 原因码列表（供 UI）

`GET /track/bonus/reason-codes`  
权限：`track:bonus` 或 `lot:list`

返回 `[{ "code": "CYCLE_COUNT", "label": "盘点差异" }, …]`。  
**不得**复用 `/track/scrap/reason-codes`。

### 6.3 履历

复用 `GET /lots/{id}/history`；展示 `tx_type=BONUS`。

---

## 7. 前端（已落地）

| 面 | 行为 |
|----|------|
| TrackPage | 「数量调整」次要按钮（`canBonus`）；与「报废」分按钮；互斥展开 |
| 面板 | qty / scrap 只读；Δ 输入（可负）；qtyAfter 预览；`qtyAfter=0` 红字；原因下拉（`/track/bonus/reason-codes`）；备注 |
| 确认 | `window.confirm` 明示不计入报废、不可代替 Scrap；`OTHER` 备注必填 |
| 履历 | `TX_LABEL.BONUS`；图标 Diff；解析 extJson（reason / Δ / before→after） |
| 动效 | GSAP `autoAlpha+y`，时长 `motionMs()`（约 200ms；reduced-motion=0） |
| LotsPage | P0 无单独累计字段；Track 履历可看 BONUS |

脚本：`migrate_lot_bonus.sql`（`301` `track:bonus`；角色 1/3/4）；新库已进 `schema.sql`。

代码：`web/src/api/track.ts` · `TrackPage.tsx`；后端 `TrackController` / `TrackServiceImpl.bonus`。

---

## 8. 并发与一致性

- 单 Lot 锁 + `version`；并发 Bonus 按序；第二笔以最新 qty 再算  
- 不改 `route_version_id` / 站位 / `scrap_qty` / `status`  
- 终态 Lot：TrackIn / Split / Merge / Scrap / Bonus 一律拒绝  
- 与 Scrap 并发：互不写对方字段；以提交顺序为准

---

## 9. 与周边事务边界

| 事务 | 关系 |
|------|------|
| Scrap | 唯一写 `scrap_qty` / 可置 `scrapped`；Bonus 禁止冒充 |
| Split / Merge | 数量守恒转移；盘点差禁止用分合批 |
| Hold | 解锁前禁止 Bonus |
| TrackIn/Out | processing 须先 Out；`qty<1` TrackIn 拒绝 |
| Rework / Skip | 改站后仍可 Bonus（仍为 wait） |
| Unbonus / ERP | **P2** |

---

## 10. 验收要点

1. 正/负 delta：`qty` 变、`scrap_qty`/`status` 不变；可继续 Track（qty≥1）  
2. `qty_after=0`：成功且 status≠`scrapped`；TrackIn 拒绝  
3. tx_log 可还原 delta / before / after / reasonCode  
4. `PUT /lots/{id}` 改 qty → 失败  
5. held / processing / created / 终态 → 失败  
6. delta=0、越界、非法 reason → 失败  
7. 用 Scrap 原因码调 Bonus → 失败  
8. 无 `track:bonus` → 入口隐藏且 API 403  

---

## 11. 业界对齐（架构取舍）

| 点 | 大厂 | 本系统 P0 |
|----|------|-----------|
| Bonus 独立 WIP 事务 | Camstar Lot Bonus | ✅ `POST /track/bonus` |
| 与 Reject/Scrap 字典分离 | 标配 | ✅ 独立 reason 白名单 |
| Reason Code 必填 | 标配 | ✅ |
| 不进 scrap/yield | 财务/良率隔离 | ✅ 不碰 `scrap_qty` |
| 强权限 | 高于普通过站 | ✅ `track:bonus` |
| 片级 Bonus | 先进 Fab | P1 |
| 审批 + ERP | 有 | P2 |
| qty→0 自动 Scrap | 少见/厂规 | ❌ 硬不自动 |

---

## 12. 关联

- `MES-Lot二期功能清单.md` §2.4 / L2-4  
- `MES-LotScrap接口设计.md` §0.1 / §9  
- `MES-LotSplit接口设计.md` / `MES-LotMerge接口设计.md`  
- `MES-Lot数据库设计.md`  
- `MES-Track功能文档.md`  
- `MES-半导体业务清单.md` §1 Lot Scrap/Bonus  
- `半导MES架构设计.md` §4.3 / §5.1  
