# 半导体 / 芯片 MES 业务清单（目标版）

> 定位：晶圆厂（Fab）+ 封测厂（OSAT）制造执行目标蓝图  
> 对齐现有系统：Lot / WIP / Track / Route / Dispatch / Recipe / Equipment / Hold / Alarm / History / Report / Auth  
> 标准参照：SEMI E10 / E30 / E40 / E90 / E94、ISA-95（半导裁剪）

---

## 目标分层

| 层级 | 含义 | 对应现状 |
|------|------|----------|
| P0 必达 | 能跑通 Lot 全生命周期 | 已有/在建核心 |
| P1 增强 | 半导标配，量产可用 | 架构已规划 |
| P2 进阶 | 大厂竞争力 | 远期 |
| P3 生态 | 厂务/供应链/智能 | 可选扩展 |

---

## 1. 批次与层级实体（Lot Hierarchy）— P0/P1

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Lot 创建 / 放行 / 完工 | P0 | 生命周期主实体 |
| Lot Split / Merge | P0 | 分批、合批 |
| Lot Scrap / Bonus | P0 | 报废、数量调整 |
| Lot 属性 | P0 | 产品、优先级、客户 Lot、数量 |
| Wafer / Unit 级管理 | P1 | 片级/颗级（按厂需要） |
| Slot Map | P1 | FOUP 槽位映射 |
| Lot 家族 / 父子关系 | P1 | Split 谱系 |
| 多产品 / 多工艺版本共存 | P0 | 同线多产品 |

---

## 2. 工艺路线与工序（Route / Step）— P0

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Route 版本管理 | P0 | 生效/切换/归档 |
| Step / Operation 定义 | P0 | 可复用工序 |
| 工序顺序与分支 | P0 | 主路径、可选路径 |
| Rework 回流路线 | P0 | 返工次数上限 |
| Skip 规则 | P1 | 严格授权跳站 |
| Future Hold / Future Action | P1 | 预置后续动作 |
| Process Program 绑定 | P1 | Step↔Recipe↔Eqp |
| 工艺变更切换点 | P1 | 某 Lot/某站生效 |

---

## 3. 生产执行（Track）— P0

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Release | P0 | 放行进入生产 |
| Move / Arrive | P0 | 站点移动 |
| Reserve / Dispatch | P0 | 预约设备 |
| Track In / Track Out | P0 | 开工 / 完工 |
| Hold / Release Hold | P0 | 锁批 / 解锁 |
| Skip / Rework | P0 | 跳站 / 返工 |
| 防跳站 / 事务校验 | P0 | 状态机唯一真相 |
| 并行站 / 批处理站 | P1 | Batch / Cascade |
| Queue Time 管控 | P1 | 站间时间窗；触发站 Out→目标站 In；中间站保留倒计时；到期自动 Hold/Alarm 并清窗；解锁填备注后放行（Pre-Gate/QMS 属 P2） |
| Process Time 校验 | P1 | 加工时长上下限 |

---

## 4. 派工与排程（Dispatch）— P1

| 业务 | 优先级 | 说明 |
|------|--------|------|
| 按设备能力选机 | P1 | Equipment Group / Capability |
| 按 Recipe 兼容选机 | P1 | 配方匹配 |
| 优先级 / 交期规则 | P1 | Hot Lot、急单 |
| 设备负载均衡 | P1 | 避免饥饿/拥堵 |
| 禁跑规则 | P1 | Hold、PM、Alarm 禁派 |
| What-Next 推荐 | P2 | 下一站/下一机建议 |
| 与 APS 对接 | P2 | 日计划细排回写 |

---

## 5. 配方管理（Recipe）— P1

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Recipe 版本 | P1 | 草稿/审批/生效 |
| Step + Eqp 绑定 | P1 | 加工参数集 |
| Recipe Download | P1 | 下发至设备（经 Adapter） |
| Chamber 级配方 | P1 | 多腔差异 |
| 配方比对 / 差异审计 | P2 | 防错配 |
| 黄金配方 / 基线 | P2 | 最佳参数沉淀 |

---

## 6. 设备管理（Equipment）— P0/P1

| 业务 | 优先级 | 说明 |
|------|--------|------|
| 设备主数据 | P0 | 型号、能力、所属区域 |
| 设备状态 | P0 | Idle / Run / Down / PM / Engineering |
| Chamber / Port 建模 | P1 | 多腔、装载口 |
| 设备组 / 能力标签 | P0 | 派工约束 |
| OEE / 稼动（SEMI E10） | P1 | 可用率统计 |
| PM 保养计划 | P1 | 预防保养与锁机 |
| 工程机 / 量产机隔离 | P1 | 模式切换 |
| 设备互锁 | P1 | 状态不允许 TrackIn |

---

## 7. 载具与物流（Carrier / AMHS）— P1/P2

| 业务 | 优先级 | 说明 |
|------|--------|------|
| FOUP / Carrier 台账 | P1 | 载具 ID、类型、洁净状态 |
| Lot ↔ Carrier 绑定 | P1 | 装载/卸载 |
| Slot Map 校验 | P1 | 片位一致性 |
| Stocker / Port 位置 | P1 | 当前位置 |
| AMHS / OHT 任务 | P2 | 自动搬运对接 |
| 空载具调度 | P2 | Empty FOUP 流转 |
| 载具清洗 / 寿命 | P2 | 污染与周期管控 |

---

## 8. 锁批与异常（Hold / Alarm）— P0/P1

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Hold 原因码 | P0 | 质量/设备/工艺/物料 |
| Hold 拦截 Track | P0 | 事务前强制校验 |
| Release 权限分级 | P0 | 敏感操作二次确认 |
| Alarm 采集与推送 | P0 | 实时告警 |
| Alarm → Hold 策略 | P1 | 自动/半自动锁批 |
| SPC 超规 Alarm | P1 | 与 EDC 联动 |
| 升级与通知 | P1 | 班组长/工程师 |

---

## 9. 量测与数据采集（EDC / SPC）— P1

> **现状（2026-08-19）**：EDC **一期 P0 已齐**（含现场完工旁拒出提示）；SPC 未建。  
> **顺序**：① EDC 最小集 ✅ → ② TrackOut 门禁 T2-7 ✅ → ③ SPC（可再后置，**不挡门禁**）。  
> 详设：`docs/模块/EDC（量测）模块/MES-EDC功能文档.md`  
> 范围说明：`docs/模块/EDC（量测）模块/MES-EDC与SPC范围说明.md`  
> Track 门禁：`docs/模块/Track（执行引擎）模块/MES-Track二期功能清单.md` §3.2 / T2-7

| 业务 | 优先级 | 说明 |
|------|--------|------|
| 量测数据录入 | P1 | 站点 EDC（API + TrackPage 抽屉 ✅） |
| 规格上下限校验 | P1 | Spec / Control Limit（随 EDC ✅） |
| 与 TrackOut 卡控 | P1 | 不合格不可过站（钩子 + 现场提示 ✅；**不依赖 SPC**） |
| SPC 控制图 | P1 · **可后置** | Xbar-R、CPK、趋势报警；量产建议做，晚于门禁亦可 |
| SPC 超规 Alarm / Hold | P1 · **可后置** | 与 EDC 点数据联动；见 Hold 自动挂后置 |
| 设备过程量采集 | P1 | 温/压等时序（见数采方案） |
| FDC 故障检测 | P2 | 多变量异常（≠ SPC） |
| 量测机台自动回传 | P2 | Metrology 对接 |

---

## 10. 追溯与履历（History）— P0

> **现状（2026-08-19）**：`mes_tx_log` 随 Track 写；现场侧栏可看；谱系 P0 已闭环；**管理端 `/app/history` 调查台 + 设备反查已落地**。  
> **顺序**：① ~~HistoryFacade + Lot 调查台 + 设备反查（一期）~~ ✅ → ② 片级 / 客诉包后置。  
> 详设：`docs/模块/History（履历）模块/`  
> 与谱系分工：`MES-LotGenealogy接口设计.md` §0.1（图归身份，线归履历）

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Track 事务履历 | P0 | 谁/何时/何站/何机（写 ✅；管理端调查台 ✅） |
| 正向追溯 | P0 | Lot → 下游（谱系 down ✅） |
| 反向追溯 | P0 | 成品 → 原料/设备/Recipe（谱系 up ✅；设备反查 ✅） |
| 设备加工履历 | P0 | Lot 在机历史（✅ History 一期） |
| Recipe / 参数履历 | P1 | 加工时实际配方（tx_log 已记 version_id） |
| Carrier / Slot 履历 | P1 | 载具与片位变化 |
| 审计追踪 | P0 | 敏感变更不可篡改（tx_log 只追加） |
| 客诉追溯包导出 | P2 | 一键打包 |

---

## 11. 在制品与看板（WIP / Dashboard）— P0

| 业务 | 优先级 | 说明 |
|------|--------|------|
| 站点 WIP 视图 | P0 | 各站排队 |
| Lot 实时状态 | P0 | 能否继续加工 |
| 设备可用性看板 | P0 | Run/Down/PM |
| Hold / Alarm 总览 | P0 | 异常集中看 |
| Cycle Time / TAT | P1 | 周转周期 |
| 瓶颈站识别 | P1 | 拥堵预警 |
| 产出 / 良率日报 | P1 | Move、WPH、Yield |

---

## 12. 报表与分析（Report）— P1/P2

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Move 报表 | P1 | 过站量 |
| Yield 报表 | P1 | 站良率 / 累积良率 |
| Hold 分析 | P1 | 原因分布、时长 |
| 设备 OEE / WPH | P1 | 效率 |
| Scrap 分析 | P1 | 报废原因 |
| Lot 历史时间线 | P0 | 单 Lot 全程 |
| 自定义导出 | P2 | Excel / API |

---

## 13. 设备自动化对接（Equipment Adapter）— P1

| 业务 | 优先级 | 说明 |
|------|--------|------|
| SECS/GEM 通信 | P1 | 状态、事件、报警 |
| Process Program 管理 | P1 | 配方同步 |
| 自动 TrackIn/Out | P1 | 事件驱动过站 |
| Carrier ID 校验 | P1 | 装载防错 |
| 控制状态 Remote/Local | P1 | 模式管理 |
| 多厂商协议适配 | P2 | 插件化 Adapter |

---

## 14. 用户权限与现场作业 — P0

| 业务 | 优先级 | 说明 |
|------|--------|------|
| 用户 / 角色 / 权限码 | P0 | RBAC |
| 现场 Track 台 | P0 | 大触控、扫码 |
| 管理端表格作业 | P0 | 工程师配置 |
| 敏感事务二次确认 | P0 | Scrap、Hold Release、Skip |
| 操作审计 | P0 | 关联 History |
| 技能/上岗资质 | P2 | 无资质禁操作 |

---

## 15. 主数据与工厂建模 — P0/P1

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Fab / Area / Bay / Eqp | P0 | 物理布局 |
| 产品 / 工艺版本 | P0 | Product + Route |
| 原因码字典 | P0 | Hold/Scrap/Alarm |
| 班次 / 日历 | P1 | 报表按班 |
| 洁净等级 / 区域约束 | P2 | 物流约束 |

---

## 16. 封测特化（OSAT，按需）— P1/P2

| 业务 | 优先级 | 说明 |
|------|--------|------|
| Die / Strip / Tray 层级 | P1 | 封装单元 |
| 测试程序 / Bin 结果 | P1 | 电测分 Bin |
| 不良 Bin 映射 | P1 | 与 Hold/Rework |
| 打标 / 外观检对接 | P2 | 后道自动化 |
| 编带 / 包装入库 | P2 | 出货衔接 |
| 客户 Lot 映射 | P1 | 来料与出货 Lot |

---

## 17. 晶圆厂特化（Fab，按需）— P1/P2

| 业务 | 优先级 | 说明 |
|------|--------|------|
| 光罩 / Reticle 管理 | P2 | 版次、使用次数 |
| 光刻套刻约束 | P2 | Layer 顺序 |
| 湿法 / 炉管 Batch | P1 | 批处理成组 |
| 污染控制 /  Dedicated | P2 | 专用设备约束 |
| Wafer Start / Out | P1 | 投片与出片 |

---

## 18. 集成与平台 — P1/P2

| 业务 | 优先级 | 说明 |
|------|--------|------|
| ERP 工单同步 | P1 | 创建 Lot 来源 |
| WMS / 线边物料（关键物料） | P2 | 光阻、气体等（非核心） |
| BI / 数据中台 | P2 | 指标汇聚 |
| 消息推送（企微/邮件） | P1 | Hold/Alarm |
| 多工厂部署 | P2 | 集团 |
| API / 事件开放 | P1 | MQ + REST |

---

## 19. 智能增强（可选）— P2/P3

| 业务 | 优先级 | 说明 |
|------|--------|------|
| 设备健康 / 预测 | P2 | 数采 + 异常趋势 |
| 派工智能推荐 | P3 | What-Next |
| 知识助理 / RAG | P3 | 工艺/Alarm 问答 |
| 根因辅助分析 | P3 | Hold/Yield 下钻 |

---

## 与当前系统对照

| 目标模块 | 当前状态 | 目标动作 |
|----------|----------|----------|
| Lot / WIP / Track / Route / Hold / Equipment / Alarm / History / Auth / Dashboard | 已有页面与产品定义 | 做深做稳，状态机与权限闭环 |
| Dispatch / Recipe | 架构 P1 | 补齐选机与配方版本 |
| Carrier / EDC / SECS Adapter | 架构建议 | 半导量产必补 |
| Report | 架构已定 · 一期待实现 | 先 Move/Hold；见 `docs/模块/Report（报表）模块/`；Yield 后置 |
| 数采分析 / AI / RAG | 已有方案文档 | 不挡主路径，并行演进 |
| 通用 MES 的 IQC/WMS/安灯/能源等 | 非目标 | 不纳入本清单主路径 |

---

## 推荐建设顺序（目标里程碑）

1. **M1 核心闭环**：Lot + Route + Track + Hold + Equipment + History + Auth + 现场台  
2. **M2 量产增强**：Dispatch + Recipe + Alarm 策略 + WIP/产出看板 + 基础报表  
3. **M3 半导标配**：Carrier/SlotMap + EDC/SPC + SECS/GEM Adapter  
4. **M4 效率与智能**：OEE/QueueTime + 数采分析 + 自动过站 + 智能辅助  

---

## 成功标准（对齐 PRODUCT）

- 现场 3 步内完成 Track In/Out  
- 任一屏能回答：「现在能不能动这批货」  
- 状态唯一真相：仅 Track 事务改 Lot 当前状态  
- 全流程可追溯：人、机、站、配方、时间可反查  
