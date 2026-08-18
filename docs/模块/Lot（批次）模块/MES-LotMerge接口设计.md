# Lot Merge 合批 — 接口设计（架构）

> 定位：把 ≥2 个可合源 Lot 收拢进一个主 Lot，数量守恒 + 谱系闭合  
> 归属：**Track 执行事务**；Lot 存实体/谱系；WIP 投影同步  
> 对齐：`半导MES架构设计.md` §4.3 / §5.1；`MES-Lot二期功能清单.md` §2.2  
> 业界：CM Merge Material（同 Product/Step/Flow）；Camstar splits&combines 履历；Oracle WIP Lot Merge（Representative Lot）  
> 前提：一期 Lot + Track + Hold；Split 谱系表（`mes_lot_genealogy` / `merged` 状态）已落地  
> 更新：2026-08-07  
> 状态：**P0 已闭环**（MG-1～MG-4）；MG-5·MG-6 后置  
> **易混：** 合批 ≠ Bonus 调量；合批 ≠ 物理并盒（Carrier 属 P1）

---

## 0. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| MG-1 | 复用 DDL：`status=merged` + `merged_to_lot_id` + genealogy.`txn=merge` | ✅ 已随 Split SP-1 |
| MG-2 | `POST /track/merge` 事务 + 多 Lot 锁序 + 履历 | ✅ |
| MG-3 | 谱系查询支持 merge 边（与 Split 共用 API） | ✅ |
| MG-4 | 现场台 Merge 入口 + 候选过滤 + 二次确认 | ✅ |
| MG-5 | 片级合入 / SlotMap 校验 | ⏳ **P1** |
| MG-6 | Carrier 容量 / 一盒多 Lot / Future Hold 继承 | ⏳ **P1** |

---

## 0.1 与 Split 对照（必读）

| | Split 分批 | Merge 合批 |
|--|--|--|
| 产品含义 | 一批拆成多批 | 多批收成一批 |
| 数量 | 父↓ 子↑ | 主↑ 源→0 |
| 谱系边 | parent→child（新建子） | source→main（源终态） |
| 约束松紧 | 单 Lot 状态机 | **更严**：同产品+同快照+同站 |
| 编号 | 生子 lot_no | **主 lot_no 不变**；源号保留作考古 |
| API | `POST /track/split` | `POST /track/merge` |

**禁止**用 Bonus 正负调量假装合批；禁止无 genealogy 直接改 qty。

---

## 1. 目标与产品边界

**一句话：** WIP 收拢——主 Lot 继续 Track，源 Lot 闭合成 `merged`，全链路可反查「谁并进谁」。

| 做（P0） | 不做（本期） |
|----------|--------------|
| 主 Lot + N 源 Lot（N≥1，合计实体 ≥2） | 加工中（`processing`）合批 |
| 同 `product_code` / `route_version_id` / `current_sort_no` | 跨产品、跨快照、跨站 |
| 主 qty += Σ源 qty；源 qty=0 → `merged` | 新建第三 Lot 作为合批结果 |
| 写 genealogy(`merge`) + tx_log | 按片合入（MG-5） |
| Hold 中拒绝 | Carrier 容量 / 并盒（MG-6） |
| 源不可再 TrackIn | Future Hold 自动继承（可选 P1） |
| UI 只列出可合候选 | 可配置「允许异站合」策略引擎 |

---

## 2. 模块边界

```
Track  = Merge 事务唯一入口；锁主+源；改 qty/status；写 tx_log；同步 WIP
Lot    = 主数据 + genealogy 存储；merged_to_lot_id；谱系查询
WIP    = 主 qty↑；源投影关闭/删除（同事务，与现有 Track 一致）
Hold   = 主+每一源 assertNoActive
Route  = 只读校验快照一致；不换版
History= mes_tx_log + mes_lot_genealogy
```

**禁止**

- `PUT /lots/{id}` 改 qty/status 冒充合批  
- LotService 私自把源标 `merged` 不走 Track  
- 源 Lot 合批后仍可 TrackIn / Split / Scrap（除专用解禁 P2）  
- 跨 `route_version_id` 强合（中途切版属 P2 另文）  
- 用 Merge 吞报废量或冲盘点差（走 Scrap / Bonus）

**原则延续**

- 状态唯一真相在 Track  
- 单 DB 事务强一致；通知 `mes.lot.changed`（主+源）  
- 在途只认各自放行时快照；合批要求快照已相同

---

## 3. 状态与前置

### 3.1 允许参与合批的状态

```
主 wait  + 源 wait(s)  ──Merge──►  主 wait(qty↑)  + 源 merged(qty=0)
```

| status | 作主 | 作源 |
|--------|------|------|
| `created` | ❌ | ❌ |
| `wait` | ✅ | ✅ |
| `processing` | ❌ P0 | ❌ P0 |
| `held` | ❌ | ❌ |
| `completed` / `scrapped` / `merged` | ❌ | ❌ |

### 3.2 同质约束（硬）

主与每一源须同时满足：

| 字段 | 规则 |
|------|------|
| `product_code` | 相等（空亦须同空——P0 建议 Release 后必有值） |
| `route_version_id` | 相等且非空 |
| `current_sort_no` | 相等（同当前站序） |
| `current_step_id` | 建议相等（与站序双保险） |
| `qty` | 源 qty≥1；主 qty≥0（允许主先为 0 壳批吞源——P0 允许） |

可选软约束（P0 不做，记入拒绝扩展）：`priority` / `hot_flag` / `customer_lot` 不一致仅告警。

### 3.3 数量守恒（硬）

```
主.qty_after = 主.qty_before + Σ 源.qty_before
∀ 源：qty_after = 0 且 status = merged 且 merged_to_lot_id = 主.id
Σ genealogy.qty(merge, 本 tx) = Σ 源.qty_before
```

### 3.4 主 Lot 选取

- 请求显式传 `mainLotId`（**必填**）  
- 不自动「最大 qty 者为主」（避免现场歧义）  
- 主 lot_no **不变**；源 lot_no 保留，状态终态

---

## 4. 数据契约

### 4.1 `mes_lot`（已有字段用法）

| 字段 | Merge 行为 |
|------|------------|
| `qty` | 主累加；源置 0 |
| `status` | 源 → `merged`；主保持 `wait` |
| `merged_to_lot_id` | 源写入主 id；主保持 NULL |
| `parent_lot_id` | **不改**（家族树与合批边分离；合批只写 genealogy） |
| `route_version_id` / 运行态 | 主不变；源冻结 |

### 4.2 `mes_lot_genealogy`

| 字段 | Merge 语义 |
|------|------------|
| `txn_type` | `merge` |
| `parent_lot_id` | **主 Lot**（保留方） |
| `child_lot_id` | **源 Lot**（被吞方） |
| `qty` | 本源并入数量（= 源合批前 qty） |
| `tx_id` | 关联本笔 `mes_tx_log` |

一次 Merge 多源 → **多行** genealogy + **一条** tx_log。

查询约定：

- `direction=down` 从主：看到 merge 边指向各源（历史被吞）  
- `direction=up` 从源：经 `merged_to_lot_id` / genealogy 回到主  

### 4.3 `mes_tx_log` payload

```json
{
  "txn": "merge",
  "mainLotId": 100,
  "mainQtyBefore": 10,
  "mainQtyAfter": 25,
  "sources": [
    { "lotId": 101, "lotNo": "LOT-….01", "qty": 10 },
    { "lotId": 102, "lotNo": "LOT-….02", "qty": 5 }
  ],
  "reasonCode": "WIP_CONSOLIDATE"
}
```

---

## 5. 事务流水（Track）

```
1. 规范化锁序：按 lotId 升序锁 主+全部源（防死锁）
2. Hold.assertNoActive(主 + 每一源)
3. 校验：实体数≥2；主/源状态=wait；同产品/快照/站；源 qty≥1；主∉源列表
4. 计算 mainQtyAfter；校验无溢出（INT）
5. UPDATE 主 qty / version++
6. 逐源：qty=0, status=merged, merged_to_lot_id=主, version++
7. INSERT mes_lot_genealogy × N（parent=主, child=源）
8. INSERT mes_tx_log
9. 同步 WIP：主 qty；源投影关闭（不可再派工）
10. 提交 → mes.lot.changed（主+源）
```

失败整单回滚。  
禁止「先合一半」的多段 API。

---

## 6. 编号与标识

| 项 | 规则 |
|----|------|
| 主 lot_no | 不变 |
| 源 lot_no | 保留；列表可灰显 / 终态过滤 |
| 新号 | **不生成** |
| 代表批 | = 请求 `mainLotId`（对齐 Oracle Representative Lot） |

---

## 7. API

### 7.1 执行合批

`POST /track/merge`  
权限：`track:merge`

```json
{
  "mainLotId": 100,
  "sourceLotIds": [101, 102],
  "reasonCode": "WIP_CONSOLIDATE"
}
```

成功 `data`：

```json
{
  "main": { "lotId": 100, "lotNo": "LOT-…", "qty": 25 },
  "merged": [
    { "lotId": 101, "lotNo": "LOT-….01", "qtyMerged": 10 },
    { "lotId": 102, "lotNo": "LOT-….02", "qtyMerged": 5 }
  ],
  "txId": 9101
}
```

错误（示例）：

| 条件 | 行为 |
|------|------|
| 源/主非 wait、held、processing | 拒绝 |
| 产品 / 快照 / 站不一致 | 拒绝（文案指明哪条、哪字段） |
| source 空或含 main | 拒绝 |
| 源已 merged / qty=0 | 拒绝 |
| 乐观锁冲突 | 拒绝可重试 |
| 无 `track:merge` | 401/403 |

### 7.2 可合候选

`GET /track/merge/candidates?mainLotId=100`  
权限：`track:merge` 或 `lot:list`

返回同站同快照同产品且 `wait`、非 Hold、非自身的 Lot 摘要列表。**已落地。**

### 7.3 谱系

复用 `GET /lots/{id}/genealogy`；节点区分 `txnType=merge|split`。**已落地。**

---

## 7.4 前端（已落地）

| 面 | 行为 |
|----|------|
| TrackPage | 「合批」按钮（`canMerge`）；拉 `/track/merge/candidates`；勾选源批；预览合后 qty |
| 确认 | `window.confirm` 二次确认后 `POST /track/merge` |
| 动效 | 与分批一致：GSAP 面板显隐 150–250ms |
| 权限 | 无 `track:merge` 则按钮不可用 |

脚本：`migrate_lot_merge.sql`（权限 299）。

---

## 8. 并发与一致性

- 多 Lot：**按 id 升序加锁**，与单 Lot Track 锁策略兼容  
- Merge 期间任一源被其他事务改状态 → 乐观锁/版本失败整单回滚  
- 合批不改主的 `route_version_id` / `current_*`（站位以主为准；已要求源同站）  
- 源 Future Hold pending：**P0 丢弃（随源终态失效）**；拷到主属 MG-6  
- `merged` Lot：TrackIn / Split / Merge / Scrap / Bonus 一律拒绝

---

## 9. 与周边事务边界

| 事务 | 关系 |
|------|------|
| Split | 语义逆操作；合批不要求必须曾是父子 |
| Scrap | 减量报废；禁止 Merge 吞不良充数 |
| Bonus | 无谱系调量；禁止代替 Merge |
| Rework | 改站后须仍同站才能合；合批本身不改站 |
| Branch | 路径选择 ≠ 合批 |
| Hold | 解锁前禁止 |

---

## 10. 验收要点

1. 数量守恒；tx_log + genealogy 可还原  
2. 跨产品 / 跨 `route_version_id` / 跨 `current_sort_no` → 拒绝  
3. 源 `merged` 后 TrackIn → 拒绝  
4. `PUT /lots/{id}` 改 qty → 失败  
5. held / processing Merge → 失败  
6. 并发双请求合同一源 → 至多一笔成功  
7. 谱系从主能列源，从源能回主  

---

## 11. 业界对齐（架构取舍）

| 点 | 大厂 | 本系统 P0 |
|----|------|-----------|
| Main/Child 模型 | CM / Oracle Representative | ✅ `mainLotId` |
| 同产品+同站+同流程版本 | CM 硬前置 | ✅ product + step + route_version |
| 禁 Hold | CM | ✅ |
| 加工中合批 | CM 允许但同 Resource/Lane | ❌ 一律禁（降风险） |
| 履历 splits&combines | Camstar | ✅ genealogy + tx_log |
| Carrier / 片级 | SiView / CM | P1 |

---

## 12. 关联

- `MES-Lot二期功能清单.md` §2.2 / §8 L2-3  
- `MES-LotSplit接口设计.md` §9  
- `MES-LotGenealogy接口设计.md`  
- `MES-Lot功能文档.md`  
- `MES-Lot数据库设计.md`  
- `MES-Track功能文档.md`  
- `半导MES架构设计.md` §4.3 / §5.1 / §7  
