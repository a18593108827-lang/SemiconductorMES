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
- 部署：阶段一单体，包结构 `com.mes.{module}`。**模块内分层 = 现网实际**（2026-09-23 实测 lot / hold / complaint）：`controller / dto / entity / mapper / service(+service/impl) / vo`，另有 `facade` / `support` / `event` / `job` / `listener` / `aspect` / `annotation` / `ws`。原写的 `api/application/domain/infrastructure` 系**目标态、从未采用**；**用户决策（2026-09-23）：按现网分层、已同步 `AGENTS.md` §3、代码零改动** → 新建模块一律按现网分层

## 模块边界（架构铁律）
- Route=定义 / Track=执行 / Dispatch=选机 / WIP=只读投影 / Hold=拦截 / History=只追加 / EDC=点真相+Spec门禁 / SPC=只读趋势+OOC Alarm 不挡 TrackOut
- 跨模块只走 Facade（CarrierFacade / RecipeFacade / EdcFacade / HistoryFacade / ReportFacade）
- 状态变更写 `mes_tx_log`；表前缀分模块（如 mes_recipe*）

## 进度（截至 2026-09-22 文档）
- 已完成：权限用户、Route、Lot、Track（一期+二期部分）、WIP、Hold（+Future Hold P0）、Equipment、Dispatch、Recipe、EDC 一期 P0、History、SPC 1~5、Alarm 1~4、Dashboard、Report 一期
- Carrier：C0+C1（Car-1~5）✅，C2 扫码比对（Car-6/7/8）✅
- **客诉追溯包（History 模块，`com.mes.complaint`）：CP-1～CP-6 全部落地**——preview / build / get / list / export(JSON+ZIP) / contain；CP-6 ZIP = `{packageNo}.zip`（`{packageNo}.json` + `README.txt` 封面），装配在 `ComplaintPackageExporter`（2026-09-22，commit 6595d83）；plan 保持 `approved`、完成态落「已完成功能」（CP-3/4/5/6 同惯例）
- **封测主线 INT-0001（2026-09-23 采纳，`status: approved`）**：切片 TD-1（Strip 条级 + 测试记录与 Bin 汇总回流 + Bin 独立字典 + 客户 Lot 映射数据模型 + 客诉包增 `testSummaryByLot` 块）→ TD-2（不良 Bin → Hold / Rework **建议**，复用 `mes_route_edge` 的 `rework` 边 + `reason_codes` 与 `mes_hold_reason` 字典，不新造返工路径）→ TD-3（颗级 Die / 条级 Bin 细分 / STDF 解析）；规格 `docs/方案/MES-封测测试数据与Bin回流方案.md`；切片 plan `docs/模块/测试数据（Test）模块/TD-1-plan.md`（**draft，未批不动码**）。**封测特化现状 = 零**（无 strip / bin / wafer 表；`mes_lot_wafer` 仍 P1 后置）。关键层级判断：**Lot 是执行/状态单位，Strip 是身份/位置单位**（Strip 不建 Lot、不写 tx_log）；**写入即对账** `Σ bin_qty(HARD)==total_qty` 是「追溯不靠人工补录」的落地手段；客户 Lot 映射**不进 genealogy**（图只认 split/merge）
- 规划未实施：Agent 数据暴露架构（Tool Facade + 可选 MCP，2026-09-18 立项；**Doc-3 的试运行对象已改为 INT-0001 全链**）、APS、数采、AI/RAG
- 后置：Adapter(SECS/GEM)、片级 Wafer、MCS/E87、XXL-JOB

## 现网 DB / ORM 机制事实（2026-09-23 实测，设计新表前必读）
- **全局逻辑删除**：`application.yml` mybatis-plus 段 —— `id-type: assign_id`（雪花，19 位）、`logic-delete-field: deleted`、`logic-delete-value: 1`、`logic-not-delete-value: 0`；`common/BaseEntity.java:25-27` 为 `@TableLogic private Integer deleted`，**25 个实体继承它**（另 2 处自声明 `Integer deleted`：`MesEdcCollectionItem:39` / `MesFutureHold:58`）。**推论（硬约束）**：deleted 列全库 27 处**一律 tinyint**、UK **一律不含 deleted**（实测 0 例外）；MP 的 `logic-delete-value` **只能固定值 → 无法「deleted 置主键 id」**（**反编译证据**：`GlobalConfig.java:184` 是 `private String logicDeleteValue = "1"` 常量，`AbstractMethod.java:106` 的 `sqlLogicSet` 直接 `"SET " + table.getLogicDeleteSql(...)` 拼成字面量，**没有任何列引用解析**）；`Integer` 装不下雪花 id（实测 `mes_lot.MAX(id)=2101886136844746754` 19 位 vs `Integer.MAX_VALUE=2147483647` 10 位 → 溢出 9.8 亿倍）→ 任何「置 id」式软删必须自声明 `Long deleted` + 手写 UPDATE + 登记全仓例外
- **乐观锁有现成机制**：`config/MybatisPlusConfig.java:23` 已注册 `OptimisticLockerInnerInterceptor`；新增乐观锁直接用 `@Version` + `updateById` 判影响行数（先例：MesCarrier / MesDispatchReserve / MesEdcParam / MesEdcPlan / MesEdcSpec / MesEqp / MesLot；判冲突写法见 `MesEdcPlanServiceImpl:141-142`），**不要手写 version 条件更新**
- DB 实测（MySQL **8.4.8**，库 `mes`）：隔离级别 `REPEATABLE-READ`（配置无 isolation 覆盖）；`sys_permission` MAX(id)=**332**；`mes_route_edge.edge_type` 实有 `normal / rework / skip_allow / time_link / off_flow`
- **只读 DB 探针（新增可复用能力）**：managed venv 已装 `pymysql` 2.2.8 → `C:/Users/Admin/.workbuddy/binaries/python/envs/default/Scripts/python.exe`；dev 凭据 `root/123456 @127.0.0.1:3306/mes`；探针脚本落 `.workbuddy/tmp/`（不入库）。**审查时优先用它核实文档里的库内断言**（information_schema / @@变量），比读 SQL 脚本更硬
- **索引脚本判据（易踩，比 INDEX 回落更隐蔽）**：`add_frontmatter.py:100` 用 `body.startswith(b"---")`、`:138` 用 `text.startswith("---")` → 首行被写成 `\---` 时会被判为「无 frontmatter」，**status 流转静默失效**；而启发式常能推出同值，使 reindex 显示「无差异」→ **禁止以 reindex 无差异当作 frontmatter 完好**，要直接断言 `text.startswith('---')`

## 本机环境事实（影响验证与联调）
- **沙箱（2026-09-23 实测）**：长链 git 命令（`&&` 串联 + 多条 `-m` + 内嵌 python 调用）**可能被拦**——实测一次 SIGTERM，报 `wmic/reg/sc` 程序黑名单（与命令本身无关），但 **commit 其实已成功、仅输出流被中断** → **判断命令是否成功必须复核 `git log` / `git status`，不要只看 exit code**；提交优先用短命令或 `git commit -F <消息文件>`
- MySQL 在 `localhost:3306`（库 `mes`）；dev Redis 在 `192.168.187.128:6379`（2026-09-22 上午不可达、15:24 起可达）；后端 `127.0.0.1:8080`（用户常自己起，**不要随意重启**）
- dev 登录账号见文档登记：`admin / 123456`（`DataInitializer` 空库创建）；`POST /auth/login {userCode,password}` → `data.token`；接口无 `/api` 前缀（`/api` 只是 vite 代理重写）
- **错误响应口径**（全项目通用）：业务异常经 `GlobalExceptionHandler` → `R.fail(e.getCode(), msg)`，**`code` 恒为 500，业务码在 `msg` 前缀**（如 `"COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED: 不支持的导出格式"`）；前端 `lib/http` 用 `msg` 展示 → 断言业务错要取 `msg`，不取 `code`
- **测试与 CI（Doc-4/Doc-5，2026-09-23 落地）**：后端 `server/src/test`（12 用例，3 类：客诉包导出装配 / 上限生效值 / format 白名单；`cd server && mvn -o test`）；前端 `web/src/**/*.test.tsx`（vitest + jsdom + Testing Library，`cd web && npm test`；首条 = EVAL-0001 抽屉并发下载重入 2 用例）；`.github/workflows/ci.yml` 三闸门——后端 `mvn -B test`、文档「reindex 后 `git diff --exit-code docs/INDEX.md`」（= frontmatter + 状态列门禁）、前端 `npm ci && npx tsc -b && npm test`。测试**不依赖 DB/Redis/网络**（需外部依赖的场景仍走一次性探针 + 副实例）；npm registry 本机**直连可达**，无需代理
- 踩点：**`@JsonTest` 不能加载 `MesApplication`**（`@SpringBootApplication` → 全量扫描把 MyBatis mapper 带进来 → `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`）；改法 = 测试内嵌最小 `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@Import(JacksonConfig.class)`
- 踩点：AssertJ 比较 `List<int[]>` **恒不等**（数组无值语义 equals）→ 用 `List<Integer>`
- 踩点（前端测试三连）：① `user.click` 连点**测不到同步 ref 闸门**（`disabled` 已挡住第二次派发）→ 竞态要用同一 `act()` 内 `dispatchEvent(new MouseEvent('click'))` 连发；② `Button` loading 时用「处理中…」**替换 children**（可访问名会变）；③ vitest 未开 `globals` 时 RTL **不自动 cleanup** → setup 里显式 `afterEach(cleanup)`
- **反向验证**是判断断言真假的标准动作：新写断言后，临时把被测逻辑改坏一次，确认用例真的变红（否则是「假保护」——EVAL-0001 首版用例就是这样被抓出问题的）
- 项目**无 `src/test`** 的旧事实已作废（见上「测试与 CI」）；验证仍优先 curl + SQL + 页面 + 静态核对，能用用例兜住的就落成用例
- 验证套路补一条：**反向验证**——新写的断言要临时把被测逻辑改坏一次，确认用例真的变红（否则是假保护）。Doc-4 里用「把 `setTimeLocal` 移到 `putNextEntry` 之后」验证过
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
- 封测规格与切片：`docs/方案/MES-封测测试数据与Bin回流方案.md`（跨模块 · A/P/D 编号体例）· `docs/模块/测试数据（Test）模块/TD-1-plan.md`（切片 TD-1，含 M1~M4 实施前置核对项）
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
