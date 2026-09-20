---
type: 接口设计
module: History
status: done
slices: []
aligns: []
updated: 2026-08-19
---

# HistoryFacade — 接口设计（架构）

> 范围：H-1～H-5 = 只读门面 + 分页查询 + 设备反查索引 + 管理端调查台  
> 原则：状态仍只由 Track 写；History 只回答「发生过什么」；业务零直表  
> 产品口径：Lot 时间线 + 设备圈批；信息密、异常跳；不是装饰时间轴  
> 对齐：`MES-History功能文档.md`；`MES-History一期功能清单.md`；Genealogy §0.1  
> 前提：`mes_tx_log` 已随 Track 落地；`history:list` 菜单种子 id=280 已有  
> 状态：**一期 P0 已落地**  
> 更新：2026-08-19

---

## 1. 边界

```
History = 只读 Facade（本期同进程）
Track   = 唯一 INSERT mes_tx_log；history() 改为委托 Facade
Lot     = 提供 lot 存在性；不拼履历 SQL
Eqp     = 提供 eqp 存在性 / 编码名；不存第二份加工流水
Genealogy = SPLIT/MERGE 跳转目标；本 Facade 不返回树
EDC/SPC/Alarm/Report = 不进本期接口
```

**本切片做什么**

- 包内唯一对外入口：`HistoryFacade` / `HistoryFacadeImpl`
- `listByLot` / `query` / `getByTxId`（设备反查走 `query(eqpId)`，无单独 `listByEqp`）
- HTTP：保留 `GET /lots/{id}/history`；新增 `GET /history`
- 索引 `idx_tx_eqp_time`
- 权限沿用 `history:list`（管理端）、`track:view`（现场兼容接口）

**本切片不做什么**

- 改 Track 写路径 / 新 `tx_type`
- 改 TrackPage 侧栏布局
- 重做 GenealogyTree
- 分表、MQ 异步写、独立 History 服务

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Track / Lot / Eqp 注入 `com.mes.history.mapper` 之外再写一套查询 | 双算法 |
| P2 | History 内 `insert` / `update` / `delete` `mes_tx_log` | 只追加在 Track |
| P3 | 管理端 HTTP 调完再前端 parse `ext_json` 当主路径 | 契约在 VO |
| P4 | `eqpId` 查询全表扫 lot 索引 | 必须 H-3 索引 |
| P5 | Track 经 HTTP 调 `/history` 给侧栏 | 同进程走 Bean |
| P6 | 无 `eqp_id` 时返回「全部设备履历」 | 参数非法，拒查 |

---

## 2. 架构决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| D1 | 形态 | 同进程 Facade，对齐 `EdcFacade` | 一期不拆服务 |
| D2 | 消费 | HTTP 给人；Track 侧栏委托 Bean | 现场与调查同一套行模型 |
| D3 | 兼容 | `GET /lots/{id}/history` 不分页；最近 500 倒序截断再 reverse 成 ASC | 现场侧栏已依赖；长链保留最近 |
| D4 | 调查查询 | `GET /history` 必带 `lotId` **或** `eqpId` 之一 | 禁无过滤全表 |
| D5 | 排序 | 调查默认 `create_time DESC, id DESC` | QE 先看最近 |
| D6 | 现场兼容序 | 仍 ASC（侧栏 reverse 展示） | 不改前端假设 |
| D7 | severity | Facade 按 `tx_type` 映射，不入库 | UI 不硬编码散落 |
| D8 | 设备行 | `eqp_id IS NOT NULL`；ABORT 若当时有机也算 | 圈影响面 |
| D9 | 名称填充 | 查询时批量填 stepName / eqpCode / eqpName / recipeVersionNo | 不冗余进 log 表 |
| D10 | 分页 | page/size；size 默认 50、max 200 | |

---

## 3. 行模型

沿用并扩展为 History 专用 `HistoryTxVO`（与侧栏字段兼容）：

| 字段 | 来源 |
|------|------|
| id / lotId / lotNo / txType | 表 |
| from/to status、sortNo、stepId、eqpId、recipe*、routeVersionId | 表 |
| remark / extJson / oper* / createTime | 表 |
| stepName / eqpCode / eqpName / recipeVersionNo | 查询填充 |
| severity | 映射：`danger` = HOLD/SCRAP；`warning` = ABORT/REWORK/SKIP/OFF_FLOW/BONUS；其余 `info` |
| ext | 解析后的结构化对象（reasonCode、qty、moveKind…）；`extJson` 仍可保留 |

未知 `tx_type` → `info`，原样展示码，禁止丢行。

---

## 4. HTTP

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/lots/{id}/history` | `history:list` **或** `track:view` | 兼容；ASC；最多 500 |
| GET | `/history` | `history:list` | 调查；见下 |
| GET | `/history/{txId}` | `history:list` | 单行详情（抽屉） |

`GET /history` 查询参数：

| 参数 | 必填 | 说明 |
|------|------|------|
| lotId | 与 eqpId 二选一 | |
| eqpId | 与 lotId 二选一 | 同时传 → 该机上该批（AND） |
| txType | N | 精确匹配一个码 |
| fromTime / toTime | N | `create_time` 闭区间 |
| page | N | 从 1 |
| size | N | 默认 50 |

空 `lotId` 且空 `eqpId` → 400。  
Lot / Eqp 不存在 → 404。

响应：`{ records, total, page, size }`。

---

## 5. Facade

```
HistoryFacade
  listByLot(lotId)                         // 兼容侧栏：ASC，≤500
  query(HistoryQuery)                      // 调查分页
  getByTxId(txId)
```

`TrackService.history` **只**调 `listByLot`，禁止继续自己查 Mapper。

包约定：`com.mes.history.facade` / `impl` / `mapper`（只读）。  
**禁止** `com.mes.track.mapper.MesTxLogMapper` 泄漏给前端 Controller 新接口。现 Track 写路径可继续用原 Mapper。

---

## 6. 数据增量

无新表。已有库执行 `migrate_history.sql`；新库 `schema.sql` 已含：

```
KEY idx_tx_eqp_time (eqp_id, create_time)
```

写库仍只 Track。本模块无配置项。

---

## 7. 前端

| 页 | 改动 |
|----|------|
| `/app/history` | 无 mock；按批次/按设备；时间预设+自定义 Mono；全部/异常/常用码；按日密排日志；severity 底色；抽屉看 ext；SPLIT/MERGE 跳 `/app/lots?lotId=` |
| TrackPage 侧栏 | **不改** |
| LotsPage 谱系 | 读 `?lotId=` 开详情+树；不重画 |

Lot 模式：搜 lotNo 点选 → `GET /history?lotId=`  
设备模式：搜 eqp 点选 → `GET /history?eqpId=`  
前端：`web/src/api/history.ts` · `HistoryPage.tsx`

---

## 8. 验收

与 `MES-History功能文档.md` §8 相同；另：

1. 无索引时设备查询不得合并上线（H-3 与 H-2 设备分支同发）
2. `GET /history` 无 lotId/eqpId → 400
3. 兼容接口条数 >500 截断（现场极端长链可后置「加载更多」，本期不做）

---

## 9. 关联

- `MES-History功能文档.md`
- `MES-History一期功能清单.md`
- `MES-History已完成功能.md`
- `MES-Track数据库设计.md` §2
- `MES-LotGenealogy接口设计.md`
- `docs/架构/半导MES架构设计.md` §5.10
