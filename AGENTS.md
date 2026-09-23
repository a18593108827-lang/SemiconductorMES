# AGENTS.md — MES 项目上下文（AI 会话第一入口）

> 维护规则：像代码一样走审查提交；AI 犯同一个错两次 → 写入 §5；全文 ≤ 一页

## 1. 项目定位

半导体制造执行系统（MES），对标 SiView / Camstar / AMAT 的 Lot Tracking 模型。
**Track 事务为唯一执行真相**；状态不可被多模块改写。

## 2. 模块铁律（违反即返工）

| # | 铁律 |
|---|------|
| 1 | 状态唯一真相在 Track；禁止 WIP/Track 双写 |
| 2 | 禁止 PUT 冒充分批改 qty |
| 3 | Recipe / Reticle 不进 Route body（运行时解析） |
| 4 | 跨模块只走 Facade（Carrier / Recipe / Edc / History / Report） |
| 5 | 状态变更必写 `mes_tx_log`；表前缀按模块分（如 mes_recipe*） |
| 6 | Route 只认 `lot.route_version_id` 快照，禁读直播表跳站 |

## 3. 技术栈与运行

Java 21 + Spring Boot（单体模块化）+ MyBatis-Plus + MySQL + Redis + Sa-Token。
包结构 `com.mes.{module}`，每模块 api / application / domain / infrastructure。
前端 React（web/）：管理端 Admin Light + 现场台 Field Dark（大触控）。

## 4. 文档体系（会话必读流程）

1. 新需求：复制 `docs/_templates/INT-模板.md` → `docs/intent/INT-{N}-*.md`（编号四位递增）
2. 采纳后写规格：跨模块方案 → `docs/方案/`；模块内 → `docs/模块/{模块}/` 五件套（功能文档 / 数据库设计 / 接口设计 / 功能清单 / 已完成功能）
3. 业务知识（行业概念 / 术语 / 判据，**不写实现**）：按 `docs/业务知识/README.md` 约定新增 `BK-{N}-*.md`（`type: 业务知识`）
4. **动码前**：切片 `{切片号}-plan.md`（`docs/_templates/plan-模板.md`）必须 `status: approved`
5. **完成后**：同会话更新该模块「已完成功能」+ `docs/架构/MES-实施进度与下一步.md`
6. 事故/线上缺陷（含验收期缺陷）：`docs/_templates/EVAL-模板.md` → `docs/eval/EVAL-{N}-*.md`（四位递增；每条须产出一个「防复发」回流点，只修码不回流 = 未闭环）
7. 文档总账：`docs/INDEX.md`（按 type / module / status 检索）

规则详见：`docs/架构/MES-AI原生SDLC文档体系重构方案.md`

## 5. 常见错误（AI 犯两次即写入）

- （暂无，随项目演进追加）
