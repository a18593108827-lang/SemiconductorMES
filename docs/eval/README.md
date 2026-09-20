# docs/eval/ — 事故回归用例目录

每起生产事故 / 线上缺陷沉淀一条 `EVAL-{N}-*.md`（编号四位递增，EVAL-0001 起），从 `docs/_templates/EVAL-模板.md` 起稿。治理规则 G5。

**状态语义**：`active`（落档即生效）。
**启用说明**：`server/src/test` 与 CI 未建前先落档积累；建后补挂回归，作为配置与代码的回归门（Doc-5）。

规范详见 `docs/架构/MES-AI原生SDLC文档体系重构方案.md`。
