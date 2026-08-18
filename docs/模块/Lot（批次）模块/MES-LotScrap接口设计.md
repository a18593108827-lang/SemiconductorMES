# Lot Scrap 报废 — 接口设计（架构）

> 定位：把在制 Lot 的不良数量从 WIP 中正式核销，数量/状态可审计  
> 归属：**Track 执行事务**；Lot 存 `qty` / `scrap_qty` / `status`；WIP 投影同步  
> 对齐：`半导MES架构设计.md` §4.3 / §5.1；`MES-Lot二期功能清单.md` §2.3  
> 业界：SiView / Camstar Scrap 事务 + Reason Code；Disposition/NCR 属后置；片级属 P1  
> 前提：一期 Lot + Track + Hold；二期 Split/Merge 已落地；`mes_lot.scrap_qty` 已有  
> 更新：2026-08-10  
> 状态：**P0 已闭环**（SC-1～SC-4）；SC-5·SC-6 后置  
> **易混：** Scrap ≠ Bonus 调量；Scrap ≠ Split 分走；全批 Scrap ≠ Hold

---

## 0. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| SC-1 | 权限种子 `track:scrap`；原因码白名单（常量/配置，非主数据表） | ✅ |
| SC-2 | `POST /track/scrap` 事务 + 锁 + `mes_tx_log`；全批/部分 | ✅ |
| SC-3 | 现场台 Scrap 入口 + 二次确认；Lot 详情 scrap_qty / 履历 SCRAP | ✅ |
| SC-4 | 与 TrackIn/Split/Merge/Bonus 互斥校验；`scrapped` 终态 hardening | ✅ 随 SC-2 |
| SC-5 | 按片 Scrap（依赖 `mes_lot_wafer`） | ⏳ **P1** |
| SC-6 | Unscrap / NC Disposition / ERP 过账 | ⏳ **P2** |

同实施切片 L2-4 可并行交付 Bonus，见 `MES-LotBonus接口设计.md`（禁止混用）。

---

## 0.1 与 Bonus / Split 对照（必读）

| | Scrap 报废 | Bonus 调量 | Split 分批 |
|--|--|--|--|
| 产品含义 | 不良核销 | 盘点/计量修正 | 材料分道 |
| qty | ↓（核销） | ↑ 或 ↓ | 父↓ 子↑（守恒） |
| scrap_qty | ↑ | **不动** | 不动 |
| status | 全批→`scrapped` | 不变 | 父子仍可 Track |
| 谱系 | **不写** genealogy | 不写 | 写 `split` |
| 原因 | **必填** reason_code | 必填（调量原因） | 可选 |
| API | `POST /track/scrap` | `POST /track/bonus` | `POST /track/split` |

**禁止**用 Bonus 负向抹报废；禁止用 Split/Merge 吞不良充数。

---

## 1. 目标与产品边界

**一句话：** WIP 正式减料——部分报废余量继续跑，全批报废闭合成 `scrapped`，全链路可按原因追溯。

| 做（P0） | 不做（本期） |
|----------|--------------|
| 全批 Scrap：`status→scrapped`，`qty→0`，`scrap_qty+=原qty` | Unscrap / 数量回滚 |
| 部分 Scrap：`qty-=n`，`scrap_qty+=n`；`n=原qty` 时升格全批 | 按 Wafer/Slot 选片（SC-5） |
| reason_code 必填（白名单校验） | Reason 主数据 CRUD / 多语言 |
| Hold / processing / 终态拒绝 | NC→Disposition 审批流 |
| 写 `mes_tx_log`（`tx_type=SCRAP`） | ERP 库存过账 |
| 二次确认 UI | 自动 Scrap（SPC 闭环触发） |

---

## 2. 模块边界

```
Track  = Scrap 事务唯一入口；锁 Lot；改 qty/scrap_qty/status；写 tx_log；同步 WIP
Lot    = 主数据字段承载；列表/详情展示 scrap_qty；不提供改量 API
WIP    = qty 同步；全批后投影关闭/删除（与 merged 终态同策略）
Hold   = Scrap 前 assertNoActive
Route  = 只读（记录当前站进 tx_log）；不换版、不改站
History= 只写 mes_tx_log；不写 mes_lot_genealogy
Reason = P0 白名单常量；P1+ 可换主数据服务，接口字段名不变
```

**禁止**

- `PUT /lots/{id}` 改 `qty` / `scrap_qty` / `status` 冒充报废  
- LotService 私自 `status=scrapped` 不走 Track  
- `scrapped` / `merged` / `completed` 上再 Scrap / TrackIn / Split / Merge / Bonus  
- 用负 Bonus 代替 Scrap  
- Scrap 写 genealogy（谱系仅 Split/Merge）

**原则延续**

- 状态唯一真相在 Track  
- 单 DB 事务强一致；通知 `mes.lot.changed`  
- 不可逆（P0）：无 Unscrap；误操作靠管理制度，不靠系统复活

---

## 3. 状态与前置

### 3.1 允许 Scrap 的状态

```
wait ──部分 Scrap──► wait（qty↓, scrap_qty↑）
wait ──全批 Scrap──► scrapped（qty=0, scrap_qty↑）
```

| status | Scrap |
|--------|-------|
| `created` | ❌ 未进生产；先 Release（或业务上不允许废未放行批） |
| `wait` | ✅ |
| `processing` | ❌ 机台上实物未结清；先 TrackOut（P0 硬禁，对齐 Split/Merge） |
| `held` | ❌ 先 ReleaseHold（与二期清单 §9 一致） |
| `completed` / `scrapped` / `merged` | ❌ 终态 |

### 3.2 数量规则（硬）

```
输入 scrapQty：整数，1 ≤ scrapQty ≤ lot.qty
qty_after      = qty_before - scrapQty
scrap_qty_after= scrap_qty_before + scrapQty

若 qty_after = 0：
  status → scrapped
  WIP 投影关闭
否则：
  status 保持 wait
```

- `scrap_qty` 只增不减（P0）  
- 累计：`qty + scrap_qty` **不必**等于创建初值（中间可有 Bonus/Split）；以本笔前后差为准  
- INT 溢出防护：`scrap_qty_after` 不得负、不得超 `Integer.MAX_VALUE`

### 3.3 原因码（硬）

| 规则 | 说明 |
|------|------|
| 必填 | `reasonCode` 非空 |
| 白名单 | P0 固定枚举（见 §6）；不在表内 → 拒绝 |
| 自由备注 | `remark` 可选，写入 tx_log |
| 主数据 | P1 换表后仍传同一字段；Track 调 Reason 服务校验 |

P0 建议种子（可裁剪，须至少覆盖现场高频）：

| code | 含义 |
|------|------|
| `BREAKAGE` | 破片/物理损坏 |
| `PROCESS_FAIL` | 工艺失败 |
| `EQP_DAMAGE` | 设备致损 |
| `CONTAMINATION` | 污染 |
| `METROLOGY_FAIL` | 量测不合格 |
| `OTHER` | 其他（须填 remark） |

---

## 4. 数据契约

### 4.1 `mes_lot`（已有字段用法）

| 字段 | Scrap 行为 |
|------|------------|
| `qty` | -= scrapQty；全批后 = 0 |
| `scrap_qty` | += scrapQty |
| `status` | 全批 → `scrapped`；部分保持 `wait` |
| `parent_lot_id` / `merged_to_lot_id` | **不改** |
| `route_version_id` / `current_*` | **不改**（全批后冻结；部分继续用） |
| `version` | ++ |

### 4.2 不写 `mes_lot_genealogy`

报废不是父子转移，是数量核销。追溯靠 `mes_tx_log`（`tx_type=SCRAP` + `ext_json`）。

### 4.3 `mes_tx_log`

| 字段 | 值 |
|------|-----|
| `tx_type` | `SCRAP` |
| `lot_id` / `lot_no` | 本批 |
| `from_status` / `to_status` | 如 `wait→wait` 或 `wait→scrapped` |
| `from_sort_no` / `to_sort_no` | 同当前站（不改站） |
| `step_id` / `eqp_id` | 当前站/机（可空） |
| `route_version_id` | 当前快照 |
| `remark` | 请求备注 |
| `ext_json` | 见下 |
| `oper_user_*` | 操作人 |

`ext_json` payload：

```json
{
  "txn": "scrap",
  "mode": "partial|full",
  "scrapQty": 3,
  "qtyBefore": 25,
  "qtyAfter": 22,
  "scrapQtyBefore": 0,
  "scrapQtyAfter": 3,
  "reasonCode": "BREAKAGE"
}
```

`ext_json` 长度受 `VARCHAR(512)` 约束：字段精简；超长截断策略 = **拒绝请求**（勿静默截断审计字段）。

---

## 5. 事务流水（Track）

```
1. 锁 Lot（与现有 Track 单批锁一致；乐观锁 version）
2. Hold.assertNoActive(lotId)
3. 校验：status=wait；qty≥1；1≤scrapQty≤qty；reasonCode∈白名单
   （reasonCode=OTHER → remark 必填）
4. 计算 qtyAfter / scrapQtyAfter / mode(full|partial) / toStatus
5. UPDATE mes_lot：qty, scrap_qty, status?, version++
6. INSERT mes_tx_log（SCRAP + ext_json）
7. 同步 WIP：
   - partial：投影 qty 更新
   - full：投影关闭/删除（不可再派工）
8. 提交 → mes.lot.changed
```

失败整单回滚。  
同一请求内 partial 升格 full（`scrapQty==qty`）视为一笔 `mode=full`，不拆两笔。

---

## 6. API

### 6.1 执行报废

`POST /track/scrap`  
权限：`track:scrap`

```json
{
  "lotId": 100,
  "scrapQty": 3,
  "reasonCode": "BREAKAGE",
  "remark": "FOUP 掉落 slot 3-5"
}
```

| 字段 | 约束 |
|------|------|
| `lotId` | 必填 |
| `scrapQty` | 必填；正整数 |
| `reasonCode` | 必填；白名单 |
| `remark` | 可选；`OTHER` 时必填 |

成功 `data`：

```json
{
  "lotId": 100,
  "lotNo": "LOT-…",
  "mode": "partial",
  "qty": 22,
  "scrapQty": 3,
  "status": "wait",
  "txId": 9201
}
```

全批时：`mode=full`，`qty=0`，`status=scrapped`。

错误（示例）：

| 条件 | 行为 |
|------|------|
| 非 wait / held / processing / 终态 | 拒绝 |
| scrapQty 越界或非正 | 拒绝 |
| reasonCode 非法 / OTHER 无 remark | 拒绝 |
| 乐观锁冲突 | 拒绝可重试 |
| 无 `track:scrap` | 401/403 |

### 6.2 原因码列表（供 UI）

`GET /track/scrap/reason-codes`  
权限：`track:scrap` 或 `lot:list`

返回 `[{ "code": "BREAKAGE", "label": "破片/物理损坏" }, …]`。  
P0 可写死后端常量；避免前端散落魔法字符串。

### 6.3 履历

复用 `GET /lots/{id}/history`；过滤/展示 `tx_type=SCRAP`。无需新谱系 API。

---

## 7. 前端（已落地）

| 面 | 行为 |
|----|------|
| TrackPage | 「报废」危险按钮（`canScrap`）；与分批/合批互斥展开 |
| 面板 | qty/scrap 预览；数量；原因下拉（`/track/scrap/reason-codes`）；备注；整批提示 |
| 确认 | `window.confirm` 明示不可撤销；`OTHER` 备注必填 |
| LotsPage | 详情「累计报废」；履历 `SCRAP` |
| 动效 | GSAP 面板 150–250ms（同 Split/Merge） |

脚本：`migrate_lot_scrap.sql`（权限码接续 Split/Merge 种子）。

---

## 8. 并发与一致性

- 单 Lot 锁 + `version`；并发双 Scrap → 至多按序成功，超量第二笔失败  
- Scrap 不改 `route_version_id` / 站位  
- `scrapped` 后：TrackIn / TrackOut / Split / Merge / Scrap / Bonus / Hold 一律拒绝  
- Future Hold pending：全批 Scrap 后随终态失效（与 Merge 源批策略一致）；不单开清理任务（P0）

---

## 9. 与周边事务边界

| 事务 | 关系 |
|------|------|
| Bonus | 调量≠报废；`scrap_qty` 只由 Scrap 写 |
| Split / Merge | 禁在 held/processing/终态；禁吞不良 |
| Hold | 解锁前禁止 Scrap |
| TrackIn/Out | `scrapped` 拒绝；processing 须先 Out 再 Scrap |
| Rework / Skip | 改站后仍可 Scrap（状态仍为 wait） |
| Unscrap | **P2**；须审批 + 冲 tx +（可选）ERP |

---

## 10. 验收要点

1. 部分：`qty`↓、`scrap_qty`↑、status 仍 `wait`；可继续 TrackIn  
2. 全批（含 scrapQty=原 qty）：`status=scrapped`，qty=0；TrackIn 拒绝  
3. tx_log 可还原 before/after 与 reasonCode  
4. `PUT /lots/{id}` 改 qty/scrap_qty/status → 失败  
5. held / processing / created Scrap → 失败  
6. 无 reasonCode 或非法码 → 失败  
7. 并发超量 Scrap → 第二笔失败  
8. 权限无 `track:scrap` → 按钮不可用且 API 403  

---

## 11. 业界对齐（架构取舍）

| 点 | 大厂 | 本系统 P0 |
|----|------|-----------|
| Scrap 走 WIP 事务 | SiView / Camstar | ✅ `POST /track/scrap` |
| Reason Code 必填 | 标配 | ✅ 白名单 |
| 全批 / 部分 | 标配 | ✅ |
| 片级 Scrap | SiView / CM 标配 | P1 |
| NC Disposition→Scrap | Camstar/Opcenter Quality | P2（Hold+人工 Scrap 顶住） |
| Unscrap + ERP | 有，强管控 | P2 |
| processing 上 Scrap | 厂规不一 | ❌ 硬禁（降实物风险） |

---

## 12. 关联

- `MES-Lot二期功能清单.md` §2.3 / L2-4  
- `MES-LotSplit接口设计.md` / `MES-LotMerge接口设计.md`  
- `MES-Lot数据库设计.md`  
- `MES-Track功能文档.md`  
- `MES-半导体业务清单.md` §1 Lot Scrap/Bonus；§ 原因码字典  
- `半导MES架构设计.md` §5.1  
