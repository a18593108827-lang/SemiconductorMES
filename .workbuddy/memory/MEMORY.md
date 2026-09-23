# MES 项目长期记忆

## Git 仓库约定
- **单一仓库**：根目录 `D:/java/xm/2026_07/MES`，分支 `main`，remote origin = `github.com/a18593108827-lang/SemiconductorMES`（public）；提交身份 guocong <a18593108827@163.com>
- 历史遗留：`server/.git` 曾是**独立仓库**（remote `.../server.git`，master，91 提交）导致「代码提交进 server 仓库、文档提交进根仓库」双历史；2026-09-20 已移至 `D:/java/xm/2026_07/_git-backup/server-dotgit-20260920` 备份并停用，现全仓单一 Git。**禁止再在子目录 `git init`**
- 提交信息风格：`feat（模块）简述` + 正文分点；实测 `git push` 在本机沙箱/agent 环境不可用，须用户在自带代理的客户端执行
- 推送环境实测（2026-09-22 15:35）：本机**代理 7897 是通的**（`curl -x http://127.0.0.1:7897 https://api.github.com` → 200；直连 github 失败），但**无可用凭据**——`credential.helper=helper-selector`、无 `~/.git-credentials`、无 `GITHUB_TOKEN/GH_TOKEN` 环境变量、`gh` 未安装、`git credential fill` 返回空（GCM 非交互提示被禁）→ **agent 侧只能 commit，push 必须用户执行**；用户侧命令：`git -c http.proxy=http://127.0.0.1:7897 push origin main`
- plan 状态语义（2026-09-22 统一）：`draft`（未批）→ `approved`（可动码）→ **`done`（已实现+已验收）**；完成态同时落「已完成功能」+ 进度文档，plan 也要改 `done`（CP-3~CP-6 四份已统一，INDEX 状态列显示 `done ✅`）
- 待处理：`server/src/main/resources/application-dev.yml` 含本机弱口令与内网 IP，已随公开仓库提交，建议改环境变量占位或转 private

## 项目定位
- **厂型已定（2026-09-23）：后道封测（OSAT）**，优先功率器件 / SiC 车规场景；主线 = 封测能力完善（颗级/Strip 层级 + 测试分档 Bin 回流 + 不良 Bin → Hold/Rework + 客诉证据链延伸到颗级，意图 `INT-0001`），**AI 赋能并行**（先 L0 只读数据暴露，意图 `INT-0002`）；前道 Fab 留远期（现实入口 = 8 寸 / 特色工艺 + Adapter 能力），12 寸量产线不进路线图。依据 `docs/方案/MES-厂型选型分析.md`（定稿，含 §0 决策记录）
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

## 进度（截至 2026-09-22 文档）
- 已完成：权限用户、Route、Lot、Track（一期+二期部分）、WIP、Hold（+Future Hold P0）、Equipment、Dispatch、Recipe、EDC 一期 P0、History、SPC 1~5、Alarm 1~4、Dashboard、Report 一期
- Carrier：C0+C1（Car-1~5）✅，C2 扫码比对（Car-6/7/8）✅
- **客诉追溯包（History 模块，`com.mes.complaint`）：CP-1～CP-6 全部落地**——preview / build / get / list / export(JSON+ZIP) / contain；CP-6 ZIP = `{packageNo}.zip`（`{packageNo}.json` + `README.txt` 封面），装配在 `ComplaintPackageExporter`（2026-09-22，commit 6595d83）；plan 保持 `approved`、完成态落「已完成功能」（CP-3/4/5/6 同惯例）
- 规划未实施：Agent 数据暴露架构（Tool Facade + 可选 MCP，2026-09-18 立项）、APS、数采、AI/RAG
- 后置：Adapter(SECS/GEM)、片级 Wafer、MCS/E87、XXL-JOB

## 本机环境事实（影响验证与联调）
- MySQL 在 `localhost:3306`（库 `mes`）；dev Redis 在 `192.168.187.128:6379`（2026-09-22 上午不可达、15:24 起可达）；后端 `127.0.0.1:8080`（用户常自己起，**不要随意重启**）
- dev 登录账号见文档登记：`admin / 123456`（`DataInitializer` 空库创建）；`POST /auth/login {userCode,password}` → `data.token`；接口无 `/api` 前缀（`/api` 只是 vite 代理重写）
- **错误响应口径**（全项目通用）：业务异常经 `GlobalExceptionHandler` → `R.fail(e.getCode(), msg)`，**`code` 恒为 500，业务码在 `msg` 前缀**（如 `"COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED: 不支持的导出格式"`）；前端 `lib/http` 用 `msg` 展示 → 断言业务错要取 `msg`，不取 `code`
- 项目**无 `src/test`**（Doc-4 后置）：验证一律 curl + SQL + 页面 + 静态核对；环境不可用时用**一次性探针**（真类 + 真容器 Bean，如 `SpringApplication` 取真 `ObjectMapper`）跑 PASS/FAIL 断言，探针放 `.workbuddy/tmp/` 不入库（CP-6 留了 `cp6-probe/`：`Cp6Probe.java` 进程内探针 + `http_acceptance.py` 真机 HTTP 验收 + `disabled_branch.py` 开关关分支，均可重跑）
- 需要「改配置才能验」的分支：**不**改 `application.yml`、**不**重启用户实例，改用命令行参数覆盖起第二实例（如 `java -cp target/classes com.mes.MesApplication --server.port=18080 --mes.complaint-package.enabled=false`，共享 MySQL/Redis）；前提先确认启动不执行 DDL 且初始化器幂等。注意 Git Bash 里 `nohup ... &` 起的服务会随 tool 调用结束被回收，长驻进程需用后台任务启动
- 踩点：`LocalDateTime` 的 JSON 形状取决于**容器** ObjectMapper——Spring Boot 自动配置出 ISO 文本，手搓 `Jackson2ObjectMapperBuilder.json()` 出数组（`WRITE_DATES_AS_TIMESTAMPS` 未关）→ 判断序列化形状必须用真容器 Bean
- 踩点：ZIP 的 DOS 时间秒字段是 `seconds/2`（解析本地头要 ×2）；`setTimeLocal` 必须早于 `putNextEntry`，否则本地头与中央目录时间不一致
- 踩点：**Git Bash 的 heredoc 会吃掉 Python 代码里的反斜杠**（`re` 的 `\s` 变成 `/s`，导致正则静默不匹配）→ 需要写脚本时用 Write 工具落盘到 `.workbuddy/tmp/` 再 `python <file>`，别用 `python - <<'PY'` 内联（简单无正则的可以）
- 文档体检基线（2026-09-22）：`docs/` 105 篇 md，INDEX 收录 98 篇；`updated` 已全量补齐（模板留空是设计如此）；**3 篇刻意不带 frontmatter**——`docs/eval/README.md`、`docs/intent/README.md`（目录导航）、`docs/_templates/AGENTS-骨架.md`（模板骨架）
- 踩点：**新写的 md 可能被 IDE/格式化器改造**（实测：`docs/方案/MES-厂型选型分析.md` 首行 `---` 被转义成 `\---`、行尾被追加两空格 → frontmatter 解析失败 → INDEX 状态列回落）；改这类文件前先 Read 最新内容，改完用字节级检查（首行是否 `---`、行尾是否多空格）并重建 INDEX 复核状态列

## 关键文档入口
- 根级上下文：`AGENTS.md`（AI 会话第一入口，含模块铁律与文档流程）
- 文档总账：`docs/INDEX.md`（自动生成，按 type/module/status 检索；再生成：`python .workbuddy/scripts/add_frontmatter.py --reindex`）
- 架构总册：`docs/架构/半导MES架构设计.md`
- 进度：`docs/架构/MES-实施进度与下一步.md`
- 业务总单：`docs/业务清单/MES-半导体业务清单.md`
- 业务知识库：`docs/业务知识/`（`BK-{N}-{主题}.md`，四位递增，`type: 业务知识`，status active/draft）——行业概念/术语/判据，回答「是什么·为什么」，正文**不引用本系统表名/类名**；导航页 `README.md` 不带 frontmatter（与 intent/eval 的 README 同例）；已收录 BK-0001 测试与 Bin 分档，待补清单见 README
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
- plan 定稿 ≠ plan 正确：审查（含「我改了文档你再审一遍」的复审）必须落到**代码行号 / 实测 / 反编译证据**，结论以 `F#`（事实修正）+ `C#`（绑定约束）行追加进 plan 并标注轮次；多轮审查只追加，不静默改写已批准内容
- 实施完 = 编译 + 构建 + 静态核对 + 可执行探针断言；环境阻塞的验收条目在 plan「实施记录」显式登记「未执行 + 补做条件」，禁止拿「编译通过」当功能验收
