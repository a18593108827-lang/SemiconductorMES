# MES 项目长期记忆

## 项目定位
- 半导体制造执行系统（MES），对标 SiView / Camstar / AMAT 的 Lot Tracking 模型
- 核心：**Track 事务为唯一执行真相**；Lot 主数据 + Route 版本快照 + Hold/Dispatch 叠加校验
- 禁止：WIP/Track 双写状态、PUT 冒充分批改 qty、Recipe/Reticle 进 Route body

## 技术栈
- 后端：Java 21 + Spring Boot（单体模块化）+ MyBatis-Plus + MySQL + Redis + Sa-Token + Spring Events（RocketMQ 后置）
- 前端：React（web/）；管理端 Admin Light + 现场台 Field Dark（大触控）
- 部署：阶段一单体，包结构 `com.mes.{module}`，每模块 api/application/domain/infrastructure

## 模块边界（架构铁律）
- Route=定义 / Track=执行 / Dispatch=选机 / WIP=只读投影 / Hold=拦截 / History=只追加 / EDC=点真相+Spec门禁 / SPC=只读趋势+OOC Alarm 不挡 TrackOut
- 跨模块只走 Facade（CarrierFacade / RecipeFacade / EdcFacade / HistoryFacade / ReportFacade）
- 状态变更写 `mes_tx_log`；表前缀分模块（如 mes_recipe*）

## 进度（截至 2026-09-14 文档）
- 已完成：权限用户、Route、Lot、Track（一期+二期部分）、WIP、Hold（+Future Hold P0）、Equipment、Dispatch、Recipe、EDC 一期 P0、History、SPC 1~5、Alarm 1~4、Dashboard、Report 一期
- Carrier：C0+C1（Car-1~5）✅，C2 扫码比对（Car-6/7/8）✅
- 规划未实施：Agent 数据暴露架构（Tool Facade + 可选 MCP，2026-09-18 立项）、APS、数采、AI/RAG
- 后置：Adapter(SECS/GEM)、片级 Wafer、MCS/E87、XXL-JOB

## 关键文档入口
- 根级上下文：`AGENTS.md`（AI 会话第一入口，含模块铁律与文档流程）
- 文档总账：`docs/INDEX.md`（自动生成，按 type/module/status 检索；再生成：`python .workbuddy/scripts/add_frontmatter.py --reindex`）
- 架构总册：`docs/架构/半导MES架构设计.md`
- 进度：`docs/架构/MES-实施进度与下一步.md`
- 业务总单：`docs/业务清单/MES-半导体业务清单.md`
- 模块文档按 `docs/模块/{模块名}/` 组织，命名固定：功能文档 / 数据库设计 / 接口设计 / 已完成功能 / 功能清单
- 意图规格：`docs/intent/INT-{N}-*.md`（新需求唯一入口，四位编号）
- 模板：`docs/_templates/`（INT / plan / EVAL / AGENTS 骨架）
- 文档体系重构方案（AI 原生 SDLC 对齐）：`docs/架构/MES-AI原生SDLC文档体系重构方案.md`（2026-09-20 批准，Doc-1/Doc-2 已实施、Doc-3 待试运行；核心：INT 意图目录 + 切片 plan.md + 根级 AGENTS.md + frontmatter 六字段，存量 92 篇只加头不动正文）

## 文档写作风格约定
- 每篇头部：frontmatter 六字段（type/module/status/slices/aligns/updated，机器可读）+ blockquote 头部（版本/对齐/更新日期/状态，人读），两者并存
- 大量使用「约束表(A#)」「禁止表(P#)」「决策表(D#)」编号体例
- 切片交付（如 Car-6→7→8），明确顺序与依赖
- 目录路由：INT→`docs/intent/`；EVAL→`docs/eval/`；切片 plan→所属模块目录 `{切片号}-plan.md`；其余老目录原位
- 流程铁律：plan 未批不动码（A2）；完成后同会话更新「已完成功能」+进度文档（A3）；每起事故产一条 EVAL（G5）
