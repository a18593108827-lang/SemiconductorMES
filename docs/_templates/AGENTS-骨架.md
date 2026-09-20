# AGENTS-骨架（根级上下文模板）

> 用法：根级 `AGENTS.md` 被覆盖或重建时，从本骨架起稿；内容约束见重构方案 A6（一页以内、犯两次才写入 §5）

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
| 5 | 状态变更必写 `mes_tx_log`；表前缀按模块分 |
| 6 | Route 只认 `lot.route_version_id` 快照，禁读直播表跳站 |

## 3. 技术栈与运行

（按现行根级 AGENTS.md §3 同步）

## 4. 文档体系（会话必读流程）

（按现行根级 AGENTS.md §4 同步）

## 5. 常见错误（AI 犯两次即写入）

- （随项目演进追加）
