---
type: 已完成功能
module: History
status: done
slices: []
aligns: []
updated: 2026-08-19
---

# MES 履历追溯（History）— 已完成功能（查验清单）

> 对齐：`MES-History功能文档.md` · `MES-History接口设计.md`  
> 现状：只读 Facade + 调查查询 + 设备反查 + Admin 调查台已落地；写仍只在 Track  
> 更新：2026-08-19

---

## 1. Facade / HTTP

| 项 | 状态 |
|----|------|
| `HistoryFacade` / `HistoryFacadeImpl` | ✅ `com.mes.history` |
| `listByLot` | ✅ 最近 500 倒序截断再 reverse 成 ASC；Lot 不存在 404 |
| `query` | ✅ lotId / eqpId 至少一；空则 400；Lot/Eqp 不存在 404；size 默认 50、max 200 |
| `getByTxId` | ✅ 无则 404 |
| `GET /lots/{id}/history` | ✅ `MesLotController` 委托 Facade；`history:list` 或 `track:view` |
| `TrackService.history` | ✅ 只调 `listByLot` |
| `GET /history` | ✅ `MesHistoryController`；`history:list` |
| `GET /history/{txId}` | ✅ `history:list` |
| 独立只读 `HistoryTxLogMapper` | ✅ 实体仍 `com.mes.track.entity.MesTxLog` |
| History 包无写库 | ✅ |

## 2. 行模型

| 项 | 状态 |
|----|------|
| `HistoryTxVO` 填 step/eqp/recipe | ✅ `HistoryTxAssembler` |
| `severity` | ✅ danger=HOLD/SCRAP；warning=ABORT/REWORK/SKIP/OFF_FLOW/BONUS；其余 info |
| 未知 `tx_type` | ✅ info，不丢行 |
| `ext` 解析 `ext_json` | ✅ 前端不 parse JSON 当主路径 |

## 3. 索引

| 项 | 状态 |
|----|------|
| `idx_tx_eqp_time (eqp_id, create_time)` | ✅ `schema.sql`；已有库 `migrate_history.sql` |
| 设备反查 `eqp_id IS NOT NULL` | ✅ |

## 4. 管理端

| 项 | 状态 |
|----|------|
| `/app/history` 真数据 | ✅ `queryHistoryApi`；无 mock |
| 按批次 / 按设备 | ✅ 搜 Lot / Eqp 点选后查 |
| 时间预设 + 自定义 Mono 输入 | ✅ 不限 / 今天 / 24h / 7天 / `MM-DD HH:mm` |
| 事务过滤 | ✅ 全部 / 异常 / 锁批 / 报废 / 中止 / 返工 / 分批 |
| 主区按日密排日志 | ✅ 非 9 列表格；无假圆点时间轴 |
| 异常行加重 | ✅ danger/warning 底；无「常规」pill |
| 行点开抽屉 | ✅ 时间 / 码 / 站 / 机 / Recipe / 前后状态 / 人 / 备注 / ext |
| SPLIT/MERGE 跳谱系 | ✅ `/app/lots?lotId=`；LotsPage 开详情+树 |
| 空态 / 失败重试 / 分页 | ✅ |
| Track 侧栏 | ✅ 不改 |

## 5. 权限

| 码 | 状态 |
|----|------|
| `history:list` | ✅ 菜单 + 调查接口（种子 id=280） |
| `track:view` | ✅ 仅现场本 Lot `GET /lots/{id}/history` |

---

## 关联

- `MES-History一期功能清单.md`
- `MES-History功能文档.md`
- `MES-History接口设计.md`
- `MES-Track数据库设计.md` §2
