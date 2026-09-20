---
type: 已完成功能
module: History
status: done
slices: [CP-1, CP-2, CP-3]
aligns: []
updated: 2026-09-20
---

# MES 履历追溯（History）— 已完成功能（查验清单）

> 对齐：`MES-History功能文档.md` · `MES-History接口设计.md`  
> 现状：只读 Facade + 调查查询 + 设备反查 + Admin 调查台已落地；写仍只在 Track；客诉追溯包 CP-1～CP-3 已落地  
> 更新：2026-09-20

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

## 6. 客诉追溯包（Complaint Package）

> 对齐：`MES-客诉追溯包接口设计.md`（CP-1～CP-3 完成登记，2026-09-20）

| 项 | 状态 |
|----|------|
| DDL `mes_complaint_package` / `mes_complaint_package_member` | ✅ `migrate_complaint_package.sql`；ASSIGN_ID 雪花主键 |
| 权限 `complaint:view` / `build` / `contain`（330/331/332，挂 280 下） | ✅ admin / process_eng / supervisor 三角色种子 |
| 开关 `mes.complaint-package.enabled`（默认 false）+ `max-members`（200）+ `history-per-lot`（100） | ✅ |
| 包结构 `com.mes.complaint`（controller / dto / entity / facade / mapper / vo） | ✅ |
| `GET /complaint-packages/enabled`（`complaint:view`，不因 false 抛错） | ✅ 联调探测 |
| `POST /complaint-packages/preview`（`complaint:view`） | ✅ 复用 `MesLotService.flattenImpact`；含 truncated / memberCount / summary |
| 摘要 activeHoldCount / scrapLotCount / openAlarmCount | ✅ `HoldService.hasActive` + `AlarmFacade.countUnclearedForLots`（只读 Facade，零业务表 Mapper） |
| 成员超上限 `COMPLAINT_PACKAGE_TOO_LARGE`；开关关 `COMPLAINT_PACKAGE_DISABLED` | ✅ |
| CP-3 `POST /complaint-packages` build（`complaint:build`） | ✅ 事务只包包头+成员；Writer Bean；UK 重读 max+抖动；装配提交后 |
| CP-3 `GET /complaint-packages/{id}` | ✅ 成员以表为准；装配块现查；与 build 同 `ComplaintPackageVO` |
| CP-3 `GET /complaint-packages` list | ✅ 包头分页；size 截 100；create_time 倒序 |
| 装配 genealogy / historiesByLot / holdsByLot / alarmsByLot | ✅ flatten 回带树；履历尾端 N；Hold `active`/`released` 各 20；Alarm `listUnclearedForLots` |
| 包内 Allocator / Writer / Assembler | ✅ Facade 只编排；`build()` 无 `@Transactional` |
| CP-4 `export`+Admin / CP-5 `contain` / CP-6 ZIP | ⏳ 未做（接口设计 §3） |

---

## 关联

- `MES-History一期功能清单.md`
- `MES-History功能文档.md`
- `MES-History接口设计.md`
- `MES-客诉追溯包接口设计.md`
- `MES-Track数据库设计.md` §2
