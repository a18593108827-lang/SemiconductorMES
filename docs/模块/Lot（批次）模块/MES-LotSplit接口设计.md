# Lot Split 分批 — 接口设计（架构）

> 定位：把一个在制 Lot 拆成可独立 Track 的子 Lot，数量守恒 + 谱系可追  
> 归属：**Track 执行事务**；Lot 存实体/谱系；WIP 投影同步  
> 对齐：`半导MES架构设计.md` §4.3 / §5.1；`MES-Lot二期功能清单.md` §2.1  
> 业界：SiView / Camstar Parent-Child Genealogy；按片拆属 P1  
> 前提：一期 Lot + Track（Release/In/Out）+ Hold 已落地  
> 更新：2026-08-07  
> 状态：**P0 已闭环**（SP-1～SP-4）；SP-5·SP-6 后置  
> **易混：** 分批 ≠ Route「+ 分支」；分支见 `MES-Branch接口设计.md`

---

## 0. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| SP-1 | DDL：`mes_lot` 增量 + `mes_lot_genealogy` + status=`merged` 预留 | ✅ `migrate_lot_split.sql` |
| SP-2 | `POST /track/split` 事务 + 锁 + 履历 | ✅ |
| SP-3 | `GET /lots/{id}/genealogy` + 详情谱系 UI | ✅ API + LotsPage 谱系 |
| SP-4 | 现场台 Split 入口 + 二次确认 | ✅ TrackPage 分批面板 |
| SP-5 | 按片 Split（依赖 `mes_lot_wafer`） | ⏳ **P1 后置** |
| SP-6 | Carrier/Sorter 联动换盒 | ⏳ **P1 后置** |

---

## 0.1 与「分支 Branch」对照（必读）

| | 分批 Split | 分支 Branch |
|--|--|--|
| 产品含义 | 一批材料拆成多批 | 一批材料选下一条路径 |
| UI | 现场台 **「分批」** | Route 维护 **「+ 分支」** + Track **完工选码** |
| Route 是否配置 | **否** | **是**（`edge_type=branch`） |
| 结果 | 多个 Lot + 谱系 | 仍一个 Lot，站序变化 |

**禁止**在 Route「边」区用分支边模拟分批。

---

## 1. 目标与产品边界

**一句话：** 材料开始分道——子 Lot 独立过站，父 Lot 留下余量，全链路可追溯。

| 做（P0） | 不做（本期） |
|----------|--------------|
| 按 qty 拆 1→N 子 Lot | 按 Wafer/Slot 选片（SP-5） |
| 父保留余量；子吃指定量 | 父 qty 清零强制「全拆」为唯一模式 |
| 继承产品 / Route 快照 / 当前站 | 子 Lot 换 Route 版本 |
| 写 genealogy + tx_log | Future Split / 预置分批点 |
| Hold 中拒绝 Split | Sorter/AMHS 物理搬片（SP-6） |
| 子 Lot 立即可 Track | Split 同时改路径（应另走 Rework） |

---

## 2. 模块边界

```
Track  = Split 事务唯一入口；改 qty / 建子 Lot / 复制运行态 / 写 tx_log
Lot    = 主数据表 + genealogy 存储与查询 API
WIP    = 父更新 qty；子插入投影行（由 Track 同事务写，或听 mes.lot.changed）
Hold   = Split 前 assertNoActive（父）；子不继承 active Hold
Route  = 只读；子认父的 route_version_id，不重新取 active
History= mes_tx_log + mes_lot_genealogy（谱系表可查树）
```

**禁止**

- `PUT /lots/{id}` 改 qty 冒充分批  
- LotService 私自 insert 子 Lot 不走 Track  
- 子 Lot 重新 `release` 绑「当前 active」版本（必须拷贝父快照）  
- WIP 与 Track 各写一套 current_sort_no  
- processing / held / created / scrapped / completed / merged 上 Split（见 §3）

**原则延续**

- 状态唯一真相在 Track  
- 主路径强一致（单 DB 事务）；履历可同步写 tx_log，通知走 MQ  
- 在途只认放行时 `route_version_id`

---

## 3. 状态与前置

### 3.1 允许 Split 的父状态

```
wait  ──Split──► wait（qty↓） + 子 Lot(s) wait
```

| 父 status | Split |
|-----------|-------|
| `created` | ❌ 未进生产；先 Release |
| `wait` | ✅ |
| `processing` | ❌ 机台上不可拆（防实物不一致） |
| `held` | ❌ 先 ReleaseHold |
| `completed` / `scrapped` / `merged` | ❌ 终态 |

### 3.2 子 Lot 初始态

- `status = wait`
- `route_id` / `route_version_id` = 父拷贝（锁定）
- 运行态：`current_sort_no` / `current_step_id` = 父拷贝；`current_eqp_id = null`
- `parent_lot_id` = 父 id（冗余便查；谱系表仍是真相）
- **不**拷贝 active Hold / Future Hold pending（子干净；需要再挂）

### 3.3 数量守恒（硬约束）

```
父.qty_before = 父.qty_after + Σ 子.qty
∀ 子.qty ≥ 1
父.qty_after ≥ 0（允许拆光：父 qty=0 且 status 仍 wait，或拆光后标 empty——P0 允许 qty=0 留壳，禁止再 TrackIn）
```

**P0 建议：** 允许 `qty_after=0`；`TrackIn` 增加 `qty≥1` 校验。不另造 `empty` 状态（减枚举）。

---

## 4. 数据契约

### 4.1 `mes_lot` 增量

| 字段 | 类型 | 说明 |
|------|------|------|
| parent_lot_id | BIGINT NULL | 直系父；根为 NULL |
| scrap_qty | INT NOT NULL DEFAULT 0 | Scrap 用；Split 不写 |
| hot_flag | TINYINT NOT NULL DEFAULT 0 | P1；Split 时可继承父 |
| merged_to_lot_id | BIGINT NULL | Merge 用；Split 不写 |

### 4.2 `mes_lot_genealogy`（新增）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| txn_type | VARCHAR(16) | `split` / `merge` |
| parent_lot_id | BIGINT | Split=父；Merge=保留主 Lot |
| child_lot_id | BIGINT | Split=子；Merge=被吞源 |
| qty | INT | 本次转移数量 |
| tx_id | BIGINT/VARCHAR | 关联 `mes_tx_log.id` |
| reason_code | VARCHAR(64) | 可空 |
| create_by / create_time | | 审计 |

索引：`(parent_lot_id)`、`(child_lot_id)`、`(tx_id)`。  
一次 Split 多子 → **多行** genealogy（每子一行）+ **一条** tx_log（payload 含子列表）。

### 4.3 `mes_tx_log` payload（约定）

```json
{
  "txn": "split",
  "parentLotId": 100,
  "parentQtyBefore": 25,
  "parentQtyAfter": 20,
  "children": [
    { "lotId": 101, "lotNo": "LOT-20260806-001.01", "qty": 5 }
  ],
  "reasonCode": "ENG_DOE"
}
```

---

## 5. 事务流水（Track）

```
1. 锁父 Lot（SELECT … FOR UPDATE 或 version 乐观锁，与现有 Track 一致）
2. Hold.assertNoActive(parent)
3. 校验 status=wait、qty、ΣchildQty、childQty≥1
4. 生成子 lot_no（规则见 §6）
5. INSERT 子 mes_lot（继承快照+站）
6. UPDATE 父 qty（+ version++）
7. INSERT mes_lot_genealogy × N
8. INSERT mes_tx_log
9. 同步 WIP：父 qty；子新行（同事务）
10. 提交 → 发 mes.lot.changed（父+子）
```

失败任一一步整单回滚。  
多子一次请求内完成，禁止「先建子再改父」的两段 API。

---

## 6. 编号规则

| 策略 | 规则 | 选用 |
|------|------|------|
| A 派生 | `{parentLotNo}.{seq}` 两位，如同日冲突则累加 | **P0 默认** |
| B 全新 | 走 `mes_lot_no_seq` 日流水 | 可选：请求显式 `lotNo` 时 |

校验：子 `lot_no` 全局唯一；手动传入则占用，不生成。

---

## 7. API

### 7.1 执行分批

`POST /track/split`  
权限：`track:split`

```json
{
  "parentLotId": 100,
  "reasonCode": "ENG_DOE",
  "children": [
    { "qty": 5, "lotNo": null },
    { "qty": 3, "lotNo": "LOT-CUSTOM-01" }
  ]
}
```

成功 `data`：

```json
{
  "parent": { "lotId": 100, "lotNo": "LOT-…", "qty": 17 },
  "children": [
    { "lotId": 101, "lotNo": "LOT-….01", "qty": 5 },
    { "lotId": 102, "lotNo": "LOT-CUSTOM-01", "qty": 3 }
  ],
  "txId": 9001
}
```

错误（示例码）：

| 条件 | 行为 |
|------|------|
| 非 wait / held / processing | 拒绝 |
| Σchild > parent.qty | 拒绝 |
| 无 active 权限 | 401/403 |
| lotNo 冲突 | 拒绝 |
| 乐观锁冲突 | 拒绝可重试 |

### 7.2 谱系查询

`GET /lots/{id}/genealogy?direction=both|up|down&depth=5`  
权限：`lot:list`

返回树节点：`lotId, lotNo, qty, status, txnType, txnTime, children[]`。

---

## 8. 并发与一致性

- 与现有 Track 相同：**单 Lot 串行**（锁 parentLotId）  
- 子 Lot 新建无并发读者问题；创建后各自锁自己  
- Split 不改 `route_version_id`  
- 父 Future Hold pending：**P0 不拷贝到子**；父 pending 保留（数量变了但预约仍对父生效——产品可接受；若要按 qty 失效属 P2）

---

## 9. 与 Merge / Scrap 的边界

| 事务 | 关系 |
|------|------|
| Merge | 逆操作语义；约束更严（同站同快照）；见 `MES-LotMerge接口设计.md` |
| Scrap | 减量不建子；禁止用 Split 假装报废 |
| Bonus | 无谱系；禁止用 Bonus 代替 Split |
| Rework | 改站不改家族；Split 后子可再 Rework |
| Branch | 改路径不拆批；见 `MES-Branch接口设计.md` |

---

## 10. 验收要点

1. Σ 守恒；tx_log + genealogy 可还原当次拆分  
2. 子过站不影响父；父升版 Route 不影响已拆子（快照）  
3. `PUT /lots/{id}` 改 qty → 失败  
4. held / processing Split → 失败  
5. 子 `route_version_id` == 父；无二次 Release  
6. qty=0 的父不可 TrackIn  

---

## 11. 关联

- `MES-Lot二期功能清单.md` §2.1 / §8 L2-2  
- `MES-LotGenealogy接口设计.md`  
- `MES-Lot功能文档.md`  
- `MES-Lot数据库设计.md` §3  
- `MES-Branch接口设计.md`（易混：分支 ≠ 分批）  
- `MES-Track功能文档.md`  
- `半导MES架构设计.md` §4.3 / §5.1 / §7  
