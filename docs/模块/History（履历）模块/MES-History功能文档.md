# MES 履历追溯（History）功能文档

> 定位：Track 事务的**只读调查面**；不改状态、不另写真相  
> 产品一句话：按 Lot 看全程、按设备圈影响面；10 秒扫出 Hold / 报废 / 返工 / 中止  
> 对齐：`docs/架构/半导MES架构设计.md` §5.10；`docs/业务清单/MES-半导体业务清单.md` §10；`MES-LotGenealogy接口设计.md` §0.1  
> 业界：Applied Lot Tracking 记人/机/时/Recipe；SiView History Inquiry；CM Genealogic = 谱系图 + 履历表（图不归本模块）  
> 一期状态：**P0 已落地**（写在 Track；读走 `HistoryFacade`；`/app/history` 调查台真数据；设备反查已建）  
> 更新：2026-08-19  
> 切片：`MES-History一期功能清单.md`  
> 契约：`MES-History接口设计.md`  
> 查验：`MES-History已完成功能.md`

---

## 1. 目标（最小集）

- 管理端接真数据：单 Lot 按日密排事务日志（谁 / 何时 / 何站 / 何机 / Recipe / 前后状态）
- 设备加工履历：该 `eqp_id` 上出现过的事务（出事圈批）
- 查询只走 `HistoryFacade`；写仍只在 Track（及 Hold 等已有事务管道）
- 现场 Track 侧栏**不改交互**，继续本 Lot 近流水

**不做（一期）：** 片 / Die / SEMI T23、客诉包导出、家族批量 Hold、Alarm 真页、Dashboard KPI、EDC 点明细、分表 / 独立 History 库。

---



## 2. 边界

```
Track     = 唯一写 mes_tx_log（同事务插入）；不提供调查 UI
History   = 只读 Facade + 管理端调查台；禁止 INSERT/UPDATE/DELETE 履历
Genealogy = 只回答分合批身份树；边表 mes_lot_genealogy（已闭环）
Hold      = 锁批事务仍写 tx_log；本模块只展示
EDC       = 点数据留在 mes_edc*；无 TRACK_OUT 行 = 被门禁拦住（本模块不解释 OOS 明细）
WIP/Lot   = 不双写履历；不从 History 反推当前站
Report    = 后置；禁止本模块做 Move/Yield 报表冒充履历
Alarm     = 后置真数据；禁止本页塞 mock 报警
```

**禁止**


| #   | 禁止                                      | 理由                      |
| --- | --------------------------------------- | ----------------------- |
| P1  | History 包写 `mes_tx_log` / 改 Lot 状态      | 状态只由 Track 写            |
| P2  | Track / Lot / Eqp 直查 Mapper 拼第二套时间线     | 双算法；拆库迁不动               |
| P3  | 把 Scrap/Bonus 画进谱系，或把谱系树塞进本页当主视图        | 图归 Genealogy，线归 History |
| P4  | 管理端继续用 `web/src/data/mock`              | 演示穿帮                    |
| P5  | 装饰性时间轴 / 宽留白 / 无筛选的「精美」                 | 调查台要密、要跳异常              |
| P6  | 为展示伪造 `carrierRequired` / 片号 / 设备 FDC 流 | 无源不造字段                  |
| P7  | 一期把履历改成 MQ 异步写                          | 现网已同步写；异步属阶段二           |


---



## 3. 架构决策（已拍板）


| #   | 决策          | 选择                                                                   | 理由                      |
| --- | ----------- | -------------------------------------------------------------------- | ----------------------- |
| D1  | 模块形态        | 同进程 `com.mes.history`；无新业务表                                          | 对齐 EdcFacade；阶段二才可拆只读服务 |
| D2  | 对外契约        | 唯一 `HistoryFacade`                                                   | 业务零直表                   |
| D3  | 写路径         | **不改**；仍 Track 事务内 insert                                            | 已满足「关键履历同步」             |
| D4  | 与 Genealogy | 正交；SPLIT/MERGE 行可跳 `GET /lots/{id}/genealogy`                        | 大厂拆开；谱系已有               |
| D5  | 查询入口        | ① Lot 时间线 ② 设备反查                                                     | 对应 Inquiry 两种问法         |
| D6  | 现场 vs 管理端   | Track 侧栏保留全量近流水；`/app/history` 分页+过滤+按日日志                                 | 操作工与 QE 密度不同            |
| D7  | 兼容接口        | `GET /lots/{id}/history` 保留，委托 Facade                                | 现场台不翻车                  |
| D8  | 展示          | Facade 填 stepName / eqpCode / recipeVersion；解析 `ext_json` 为 typed 字段 | UI 不 parse JSON         |
| D9  | 异常分级        | 行带 `severity`（danger/warning/info）                                   | 异常跳出来，过站压下去             |
| D10 | 设备反查索引      | 增 `idx_tx_eqp_time (eqp_id, create_time)`                            | 现网只有 lot/type 索引        |
| D11 | 分页          | 管理端必分页（默认 50，上限 200）                                                 | 全表倒给前端会炸                |
| D12 | UI 品质       | 信息设计精（密度、对比、可点开）；                                                    | 见 §6                    |


---



## 4. 与谱系 / 现场履历


|      | Track 侧栏           | `/app/history`  | Lots 谱系图             |
| ---- | ------------------ | --------------- | -------------------- |
| 用户   | 操作工                | QE / 工艺 / 客诉    | 工艺 / QA              |
| 问    | 这批刚才做了什么           | 这批全程 + 这台机跑过谁   | 血缘从哪来到哪去             |
| 数据   | `mes_tx_log` 本 Lot | 同表，可换 Lot / Eqp | `mes_lot_genealogy`  |
| 一期改动 | **不改**             | **已接真 + 设备反查** | 不改；仅从 SPLIT/MERGE 跳入 |


---



## 5. 数据（无新表）

真相表：`mes_tx_log`（见 `MES-Track数据库设计.md` §2）。  
只追加；本模块只 SELECT。

一期增量：`KEY idx_tx_eqp_time (eqp_id, create_time)`。

`ext_json` 仍 VARCHAR(512)；本模块解析，不扩列。Recipe 已有 `recipe_id` / `recipe_version_id`。

EDC 采集**不是** Track 事务；一期不写 `EDC_COLLECT`。门禁拒出 = 无 `TRACK_OUT` 行。

---



## 6. 管理端信息设计（产品约束）

调查台，不是看板，也不是锁批那种宽表。

- 工具栏一行：`按批次 | 按设备` + 搜索 + 时间（不限 / 今天 / 24h / 7天 / 自定义 `MM-DD HH:mm`）+ `全部 | 异常` 及常用码
- 主区：按日分组密排日志（时刻 + 事务名 + 站/机/人）；批次模式不重复批次号列
- 异常行字重+色（danger/warning 底）；常规行压低；**不要**每行「常规」pill、**不要**详情列
- 点行开抽屉：机台、Recipe 版本、原因码、前后站/状态、ext；SPLIT/MERGE 行内跳谱系
- 设备模式：按 `eqp_id` 列出事务，行内带 lotNo
- 自定义时间禁止原生 `date`/`datetime-local`
- **禁止**：9 列事务表、假时间轴圆点、插画、入场 stagger、KPI 卡片、表格空态里塞一行字

现场侧栏保持现密度即可。

---



## 7. 权限


| 码              | 用途                                      |
| -------------- | --------------------------------------- |
| `history:list` | 菜单 `/app/history` + 调查查询（种子 id=280 已有）  |
| `track:view`   | 仅现场本 Lot 履历（现 `GET /lots/{id}/history`） |


Facade **不**鉴权；HTTP 鉴权。设备反查与 Lot 查询同一 `history:list`。

---



## 8. 验收

1. `/app/history` 无 mock；选真实 Lot 可见与现场侧栏一致的事务（顺序可新→旧）
2. 过滤 HOLD/SCRAP 只出对应行
3. 有 `eqp_id` 的 TRACK_IN 能在设备反查中命中该 Lot
4. 无 `history:list` 进管理端调查接口 → 403；`track:view` 仍可看现场本 Lot
5. History 包无写库代码
6. SPLIT/MERGE 能跳到已有谱系，不在本页重画树
7. 未配设备的事务不出现在设备反查（`eqp_id` 空）

---



## 9. 关联

- `MES-History一期功能清单.md`
- `MES-History接口设计.md`
- `MES-History已完成功能.md`
- `MES-Track数据库设计.md` §2
- `MES-LotGenealogy接口设计.md` §0.1
- `docs/架构/半导MES架构设计.md` §5.10
- `docs/业务清单/MES-半导体业务清单.md` §10
- `PRODUCT.md`（管理端查谱系与履历）

