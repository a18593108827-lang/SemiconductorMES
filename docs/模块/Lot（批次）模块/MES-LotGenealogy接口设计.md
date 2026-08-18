# Lot Genealogy 谱系查询 — 架构设计

> 定位：只读还原 Lot 分合批血缘（向上祖先 / 向下子孙），支撑遏制与客诉追溯  
> 归属：**Lot 读模型**；边由 Track Split/Merge **事务瞬间写入**；禁止手工改边  
> 对齐：`半导MES架构设计.md` §4.3 / §5.1；`MES-Lot二期功能清单.md` §2.5  
> 业界：SiView / Camstar Parent-Child Genealogy；CM Genealogy 实时查；履历 splits&combines  
> 前提：`mes_lot_genealogy` + Split/Merge 事务已落地  
> 更新：2026-08-10  
> 状态：**P0 已闭环**（API + 管理端谱系简图）；片级 / 家族遏制 / 客诉包后置  
> **易混：** Genealogy ≠ History；Genealogy ≠ Scrap/Bonus 履历

---

## 0. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| GN-1 | 表 `mes_lot_genealogy` + 索引 | ✅ `migrate_lot_split.sql` / `schema.sql` |
| GN-2 | Split/Merge 同事务写边 + `tx_id` | ✅ Track |
| GN-3 | `GET /lots/{id}/genealogy`（up/down/both + depth） | ✅ |
| GN-4 | LotsPage 谱系简图（当前居中 / 直系默认 / 深链折叠 / 影响面） | ✅ `GenealogyTree.tsx` |
| GN-5 | 节点带 `qtyTransferred` / `txId` / `reasonCode`（边挂 child 端） | ✅ |
| GN-6 | 按家族批量 Hold / 客诉追溯包导出 | ⏳ **P2** |
| GN-7 | Wafer/Die 级谱系 | ⏳ **P1**（依赖 `mes_lot_wafer`） |

**P0 结论：** 查谱系主路径已闭环。白名单 / Hot Lot 属 L2-5 其它项，不阻塞 Genealogy。

---

## 0.1 与 History 对照（必读）

| | Genealogy 谱系 | History 履历 |
|--|--|--|
| 回答 | 材料身份从哪来、去哪了 | 过了哪些站、谁干的、报废/调量 |
| 边/事件 | 仅 `split` / `merge` | RELEASE / IN / OUT / HOLD / SCRAP / BONUS… |
| 存储 | `mes_lot_genealogy` | `mes_tx_log` |
| 写入口 | 仅 Track Split/Merge | 所有 Track 事务 |
| UI | 家族简图（影响面 + 直系） | 事务时间线 |
| Scrap/Bonus | **不写边** | **只写履历** |

**原则：** 图归身份，线归履历。禁止把 Scrap/Bonus 画进谱系。

---

## 0.2 产品用语（UI 文案）

| 文案 | 含义 |
|------|------|
| **分出** | Split：父批留下余量，子批带走指定数量独立 Track。边 `分出 n` = 本次拆走 n |
| **并入** | Merge：源批数量合进主批后源→`merged`。边 `并入 n` = 本次合进 n |
| **父批 / 子批** | Split 关系的上下级 |
| **合批主批 / 并入源** | Merge：主批保留；并入源为被吞 Lot |
| **当前** | 详情抽屉正在查看的 Lot |
| **家族 / 在制 / 已合批 / 报废** | 本响应树内汇总（影响面），非全厂 |

禁止 UI 主路径出现 `Δ` / `tx` / `split|merge` 英文 jargon；事务号、原因码默认不展示（VO 仍返回，供后续点边明细）。

---

## 1. 目标与产品边界

**一句话：** 执行时建血缘，查询时只读——30 秒看清直系与影响面；深追按需展开。

| 做（P0） | 不做（本期） |
|----------|--------------|
| 以 Lot 为锚点展开 up / down / both | 手工维护父子、后台改边 |
| depth 默认 5、上限 20 | 无限深全库 DFS |
| 节点：lotNo / qty / status + 边字段 | 树上直接 Split/Merge/调量 |
| 边真相在 genealogy；冗余字段仅便查 | 用冗余字段当唯一真相 |
| 管理端：居中简图 + 影响面 + 展开祖先/子孙 | 客诉一键打包（GN-6） |
| API `direction` 保留（前端 P0 固定 `both`） | 片/槽位级血缘（GN-7） |
| | 力导向图 / 全屏族谱秀 |

---

## 2. 模块边界

```
Track  = Split/Merge 唯一写边方；同事务 INSERT genealogy + tx_log；改 qty/status
Lot    = 谱系存储 + 只读查询 API；组装树 VO
WIP    = 不参与谱系读写
Hold   = 不写边；后续可消费谱系做家族遏制（P2）
History= tx_log 细节；genealogy 给身份拓扑
UI     = LotsPage 详情谱系区消费 GET；现场台可复用 GenealogyTreeField（只读）
```

**禁止**

- `PUT /lots/{id}` 或任意 Admin 接口改 `mes_lot_genealogy`
- LotService 在非 Track 路径插入/删除边
- Scrap / Bonus / Rework / Hold 写 genealogy
- 用 `parent_lot_id` 递归代替读 genealogy（合批边不在该字段）
- 查询接口带副作用

**原则延续**

- 状态只由 Track 写；Genealogy 只读投影身份关系
- 边与事务同事务提交：有边必有 `tx_id`（可对上 tx_log）
- 单库强一致；读侧直读主库

---

## 3. 边模型（核心）

### 3.1 统一边语义

| txn_type | parent_lot_id | child_lot_id | qty 含义 |
|----------|---------------|--------------|----------|
| `split` | 父（保留） | 新建子 | 子吃走量 |
| `merge` | 主（保留） | 被吞源 | 源并入量 |

一次 Split N 子 → **N 行**边 + **1 条** tx_log。  
一次 Merge N 源 → **N 行**边 + **1 条** tx_log。

```
Split:  Parent ──split──► Child1
                     └──► Child2

Merge:  Main   ◄──merge── Source1   （表：parent=Main, child=Source）
                     ◄── Source2
```

### 3.2 VO 边字段挂载（已实现）

**边字段一律挂在 child 端节点**（描述「本节点相对 parent 如何产生」）：

`txnType` / `txnTime` / `qtyTransferred` / `txId` / `reasonCode`

| 遍历 | 行为 |
|------|------|
| down | 新建 child 节点时带边 |
| up | 包祖先前，把边写回当前 child（`applyEdge`），parent 节点边字段为空 |

前端读边：看「下级节点」或「当前相对父」的字段，不看父节点自身边字段。

### 3.3 冗余字段（非真相）

| 字段 | 用途 | 限制 |
|------|------|------|
| `mes_lot.parent_lot_id` | Split 直系父便查 | Merge **不改** |
| `mes_lot.merged_to_lot_id` | 源→主便查 | 须与 genealogy.merge 同行写入 |

**真相顺序：** `mes_lot_genealogy` > 冗余字段。

### 3.4 向上遍历

从锚点沿 `child_lot_id = 当前` 取边，`create_time DESC LIMIT 1`，包一层祖先，最多 depth；`visited` 防环。

| 场景 | 行为 |
|------|------|
| 纯 Split 子 | 回到拆批父 |
| `merged` 源 | 优先回到合批主（最新边常为 merge） |
| 先拆后合 | **以最新边为准**上溯 |

全祖先集合（非主链）→ P2 `mode=all-parents`。

### 3.5 向下遍历

`parent_lot_id = 当前` 全部 split/merge 边，`create_time ASC`，递归至 depth。

---

## 4. 数据

### 4.1 `mes_lot_genealogy`

| 字段 | 说明 |
|------|------|
| id | PK |
| txn_type | `split` / `merge` |
| parent_lot_id / child_lot_id | 见 §3.1 |
| qty | 本次转移量 → VO `qtyTransferred` |
| tx_id | `mes_tx_log.id` |
| reason_code | 可空 |
| create_by / create_time | 审计 |

索引：`parent_lot_id`、`child_lot_id`、`tx_id`。

### 4.2 与 tx_log

| 关系 | 说明 |
|------|------|
| 写 | Track 同事务写边，`tx_id` 指向本笔日志 |
| 读 | 简图给拓扑；点边拉 history 滤 `tx_id` 属增强（未做强制） |
| Scrap/Bonus | 仅 tx_log，无边 |

---

## 5. API

### 5.1 查询

`GET /lots/{id}/genealogy?direction=both|up|down&depth=5`  
权限：`lot:list`  
实现：`MesLotController` / `MesLotServiceImpl.genealogy`

| 参数 | 默认 | 说明 |
|------|------|------|
| direction | `both` | `up` 祖先链；`down` 子孙树；`both` 先 down 再 up（子孙挂在锚点下） |
| depth | `5` | ≤0 当 5；上限 **20** |

响应：`MesLotGenealogyNodeVO` 树

```json
{
  "lotId": 100,
  "lotNo": "LOT-…",
  "qty": 20,
  "status": "wait",
  "txnType": null,
  "txnTime": null,
  "qtyTransferred": null,
  "txId": null,
  "reasonCode": null,
  "children": [
    {
      "lotId": 101,
      "lotNo": "LOT-….01",
      "qty": 5,
      "status": "wait",
      "txnType": "split",
      "txnTime": "2026-08-07T10:00:00",
      "qtyTransferred": 5,
      "txId": 9001,
      "reasonCode": "ENG_DOE",
      "children": []
    }
  ]
}
```

| 条件 | 行为 |
|------|------|
| Lot 不存在 | 业务错误 |
| 无边 | 单节点（无 children） |
| depth 触顶 | 该层 `children=[]` |

**禁止 POST/PUT 谱系。**

### 5.2 写边（非本 API）

| 事务 | 路径 | 权限 |
|------|------|------|
| Split | `POST /track/split` | `track:split` |
| Merge | `POST /track/merge` | `track:merge` |

见 `MES-LotSplit接口设计.md` / `MES-LotMerge接口设计.md`。

---

## 6. 一致性与性能

| 项 | 决策 |
|----|------|
| 写一致 | 与 Split/Merge 同 DB 事务；失败整单回滚 |
| 读一致 | 直读主库；请求时快照 |
| 防环 | up `visited`；成环截断 |
| 深度帽 | 20 |
| 索引 | parent/child/tx |
| 缓存 | P0 不做 |

---

## 7. 前端（P0 形态）

组件：`web/src/components/lot/GenealogyTree.tsx`  
入口：LotsPage 详情抽屉「谱系」；可选 `GenealogyTreeField`（现场台深色）

### 7.1 布局

```
[影响面] 家族 n · 在制 n · 已合批 n · 报废 n

        [展开祖先（k）]          ← 默认折叠更早层
           更早 …
           父批 / 合批主批
              │ 分出 n / 并入 n
           【当前】★
              │ 分出 n
           子批 …     │ 并入 n
                      并入源 …
        [展开子孙（k）]          ← 默认只直系
```

| 规则 | 说明 |
|------|------|
| 默认 | 只渲染直系父 1 + 直系子/并入源 N |
| 边文案 | ≤6 字量级：`分出 n` / `并入 n` |
| 点 Lot | 切换详情锚点并重拉 `direction=both` |
| 选中高亮 | GSAP 200ms（遵 `prefers-reduced-motion`） |
| 空态 | 「还没有分批或合批记录…去现场台」 |

### 7.2 影响面算法

对本次 API 返回树做去重 walk：

| 指标 | 计数规则 |
|------|----------|
| 家族 | 去重 lotId 数 |
| 在制 | status ∈ wait / processing / held / released |
| 已合批 | `merged` |
| 报废 | `scrapped` |

范围 = 本响应子树，非全厂实时 WIP。

### 7.3 与 API direction

P0 前端固定请求 `both&depth=5`；深追用本地「展开」，不再做双向/向上/向下主切换（避免术语干扰）。

---

## 8. 边界矩阵

| 事务/动作 | 写 genealogy | 说明 |
|-----------|--------------|------|
| Split | ✅ | parent→child |
| Merge | ✅ | 表：parent=main, child=source |
| Scrap | ❌ | 仅 tx_log |
| Bonus | ❌ | 仅 tx_log |
| Rework / Skip / Hold | ❌ | |
| Release / TrackIn/Out | ❌ | |
| `PUT /lots/{id}` | ❌ | 禁改 qty/status/边 |

---

## 9. 验收

1. Split 后：父见子、子见父；边 qty = 当次转移  
2. Merge 后：主见源、源见主；源 `merged` 不可 TrackIn  
3. Scrap/Bonus 后：谱系边数不变  
4. API `direction` / `depth` 符合 §5  
5. 无 `lot:list` → 403  
6. 无绕过 Track 的写边入口  
7. UI：默认直系可读；展开后可见更深；影响面数字与树节点一致  
8. 边字段在 child 端；上溯后当前节点带相对父的 `txnType`

---

## 10. 业界对齐

| 点 | 大厂 | 本系统 P0 |
|----|------|-----------|
| 事务瞬间写边 | SiView / Camstar / CM | ✅ |
| 正向 + 反向 | 标配 | ✅ |
| Lot 级谱系 | 标配 | ✅ 简图 |
| Scrap/Bonus 不进谱系 | 常见 | ✅ |
| Wafer/Die | 标配 | P1 |
| 家族遏制 / 追溯包 | QA 闭环 | P2 |
| 跨厂/YMS | 进阶 | 不做（二期） |

---

## 11. 关联

- `MES-Lot二期功能清单.md` §2.5 / §8 L2-5  
- `MES-LotSplit接口设计.md` §7.2  
- `MES-LotMerge接口设计.md` §7.3  
- `MES-LotScrap接口设计.md` §4.2  
- `MES-LotBonus接口设计.md` §4.2  
- `MES-Lot数据库设计.md` §3  
- `MES-Lot功能文档.md`  
- `MES-Lot已完成功能.md`  
- `MES-Track功能文档.md`  
- `半导MES架构设计.md` §5.1  
- `MES-半导体业务清单.md` §1 / §10  
- `PRODUCT.md` / `DESIGN.md`（Operate：工具可读，禁装饰动效）  
