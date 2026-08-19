# MES 执行引擎（Track）— 功能查验清单

> 更新：2026-08-13  
> 状态：**Track 核心 + Split/Merge/Scrap/Bonus + Queue Time（到期自动 Hold）+ Process Time + Abort + Move 已落地**  
> 需求依据：`MES-Track功能文档.md` · `MES-ProcessTime接口设计.md` · `MES-TrackAbort接口设计.md` · `MES-TrackMove接口设计.md` · Split/Merge/Scrap/Bonus 设计

图例：✅ 已完成 · ⏳ 未做 · — 不适用

---

## 0. 完成度总览

| 能力 | 后端 | 前端 | 权限码 |
|------|------|------|--------|
| Release（Track 入口） | ✅ `POST /track/release` | ✅ Lot 页放行 | `track:release` / `lot:release` |
| Move（独立移站） | ✅ `POST /track/move` | ✅ MovePanel（wait→下一站 wait） | `track:move` |
| TrackIn | ✅ `POST /track/track-in` | ✅ 现场台 | `track:track-in` |
| TrackOut（含自动进站） | ✅ `POST /track/track-out` | ✅ 现场台 | `track:track-out` |
| Rework / Skip / Off-Flow | ✅ | ✅ 现场台面板 | `track:rework` / `skip` / `off-flow` |
| Split 分批 | ✅ `POST /track/split` | ✅ 分批面板 | `track:split` |
| Merge 合批 | ✅ `POST /track/merge` + candidates | ✅ 合批面板 + 二次确认 | `track:merge` |
| Scrap 报废 | ✅ `POST /track/scrap` + reason-codes | ✅ 报废面板 + 二次确认 | `track:scrap` |
| Bonus 数量调整 | ✅ `POST /track/bonus` + reason-codes | ✅ 数量调整面板 + 二次确认 | `track:bonus` |
| Abort 加工中止 | ✅ `POST /track/abort` + reason-codes | ✅ AbortPanel（回本站 wait，不 Hold） | `track:abort` |
| 执行上下文查询 | ✅ context（含 canAbort / canMove / next*） | ✅ 现场台 | `track:view` |
| 事务履历 mes_tx_log | ✅ 写在 Track；读 `HistoryFacade`（`GET /lots/{id}/history` + `GET /history`） | ✅ 现场侧栏 + Admin `/app/history` 调查台 | `history:list` / `track:view` |
| Hold 校验钩子 | ✅ `assertNoActive` | ✅ 现场锁批/解锁 | 见 Hold 模块 |
| Process Time | ✅ In 开计时；&lt;min 拒 Out；&gt;max Out+Hold(8008) | ✅ 工序库 + Banner | 沿用 track-in/out |
| Queue Time | ✅ 开窗/结算/到期自动 Hold+清窗 | ✅ Banner + QTIME 解锁备注 | 见 QueueTime 设计；Job=`@Scheduled` |

---

## 1. 模型契约（文档已定）

| 项 | 状态 |
|----|------|
| Track = 唯一执行引擎 | ✅ |
| qty/status 变更走事务 | ✅ Split/Merge/Scrap/Bonus |
| 按 route_version_id 防跳站 | ✅ |
| WIP 只读投影 | ✅；`merged` / `scrapped` 移出 WIP |

---

## 2. 验收要点（落地后勾）

- [x] `/track/release` 或收敛后的唯一放行入口  
- [x] TrackIn/Out 防跳站  
- [x] 并发乐观锁  
- [x] 每笔事务写 mes_tx_log  
- [x] 现场台接真 API  
- [x] Split 数量守恒 + genealogy  
- [x] Merge 同质校验 + 源 `merged` + 候选接口  
- [x] Scrap 部分/全批 + reason 白名单 + `SCRAP` 履历  
- [x] Bonus ±delta + 独立 reason 白名单 + `BONUS` 履历；不改 scrap_qty/status  
- [x] Hold 中拒绝 Split/Merge/Scrap/Bonus  
- [x] `scrapped` 不可 TrackIn  
- [x] Move：wait→下一站 wait；履历 MOVE；Hold/Off-Flow/末站拒绝；现场台刷新站号+派工  
