# MES 项目长期记忆

> 详细踩点/实测证据见同目录 `踩点与实测.md`。本文件只留必读铁律与关键事实。

## 1. Git / 文档流程
- 单一仓库 `D:/java/xm/2026_07/MES`，main，origin=`github.com/a18593108827-lang/SemiconductorMES`（public）；提交身份 guocong <a18593108827@163.com>；**禁止子目录 `git init`**
- agent 侧只能 commit，**push 须用户执行**（代理 7897 通、无凭据）：`git -c http.proxy=http://127.0.0.1:7897 push origin main`
- plan 状态：`draft` → `approved`（可动码）→ `done`（已实现+已验收）；完成同时更新「已完成功能」+ 进度文档 + plan 改 done
- 待处理：`server/src/main/resources/application-dev.yml` 含本机弱口令与内网 IP 且已进公开仓库 → 改占位或转 private

## 2. 项目定位
- 后道封测（OSAT），优先功率器件 / SiC 车规；主线 **INT-0001**（封测能力：Strip 条级 + Bin 回流 + 不良 Bin→Hold/Rework 建议 + 客诉证据链到颗级），AI 赋能并行 **INT-0002**；前道留远期（现实入口 8 寸/特色工艺），**12 寸量产线不进路线图**。依据 `docs/方案/MES-厂型选型分析.md`
- 核心：**Track 事务为唯一执行真相**；Lot 主数据 + Route 版本快照 + Hold/Dispatch 叠加校验
- 禁止：WIP/Track 双写状态、PUT 冒充分批改 qty、Recipe/Reticle 进 Route body
- 封测特化现状=零（无 strip/bin/wafer 表，`mes_lot_wafer` P1 后置）；**Lot=执行/状态单位，Strip=身份/位置单位**（Strip 不建 Lot、不写 tx_log）；写入即对账 `Σbin_qty(HARD)==total_qty`；客户 Lot 映射**不进 genealogy**（图只认 split/merge）

## 3. 技术栈与模块边界
- Java 21 + Spring Boot（单体模块化）+ MyBatis-Plus + MySQL + Redis + Sa-Token + Spring Events（RocketMQ 后置）；前端 React（web/，管理端 Admin Light + 现场台 Field Dark 大触控）
- 包结构 `com.mes.{module}`，**现网实际分层**（2026-09-23 实测，已同步 `AGENTS.md` §3）：`controller / dto / entity / mapper / service(+service/impl) / vo`，按需加 `facade / support / event / job / listener / aspect / annotation / ws`；`api/application/domain/infrastructure` 系目标态**从未采用**；新建模块一律按现网分层
- 铁律：Route=定义 / Track=执行 / Dispatch=选机 / WIP=只读投影 / Hold=拦截 / History=只追加 / EDC=点真相+Spec门禁 / SPC=只读趋势；**跨模块只走 Facade**；状态变更必写 `mes_tx_log`；表前缀分模块
- 权限：`perm_code` 是不透明字符串，**无任何前缀/冒号解析**；菜单树靠 `parent_id` + `perm_type`；两级命名 `资源:动作` 仅为一致性（先例 `track:track-in`）

## 4. 现网 DB / ORM 硬约束（设计新表前必读）
- 全局逻辑删除 `deleted`（删=1/未删=0；id 雪花 19 位）；deleted 列一律 tinyint、UK 一律不含 deleted；**MP 无法「deleted 置主键 id」**（logic-delete-value 是固定常量）→ 置 id 式软删须自声明 `Long deleted` + 手写 UPDATE + 登记例外
- 乐观锁已注册 `OptimisticLockerInnerInterceptor` → 用 `@Version` + `updateById` 判影响行数，**勿手写 version 条件更新**
- MP 的 `eq(col, null)` **不跳过条件**（生成 `= NULL`，静默失效）→ 可空字段必须 `.eq(val != null, col, val)` / `.isNull(col)`
- MySQL **8.4.8** 库 `mes`，REPEATABLE-READ；`sys_permission` MAX(id)=332；`route_edge.edge_type` 有 `normal/rework/skip_allow/time_link/off_flow`
- 只读 DB 探针：`C:/Users/Admin/.workbuddy/binaries/python/envs/default/Scripts/python.exe`（已装 pymysql），dev `root/123456@127.0.0.1:3306/mes`，脚本落 `.workbuddy/tmp/`；**核实文档里的库内断言优先用探针**

## 5. 本机环境 / 验证
- 沙箱：长链 git 命令可能被拦（wmic/reg/sc 黑名单），**报错与命令实际结果无必然关系** → 必须复核 `git log`/`git status`，勿信 exit code；提交用短命令或 `git commit -F <消息文件>`
- 后端 `127.0.0.1:8080`（用户常自起，**勿随意重启**）；dev Redis `192.168.187.128:6379`；登录 `admin/123456`，`POST /auth/login {userCode,password}` → `data.token`；接口**无 `/api` 前缀**（vite 代理重写）
- **错误响应口径**：`GlobalExceptionHandler` → `code` 恒 500，**业务码在 `msg` 前缀** → 断言业务错取 `msg`，不取 `code`
- 测试与 CI：后端 `cd server && mvn -o test`；前端 `cd web && npm test`（vitest+jsdom+RTL）；`.github/workflows/ci.yml` 三闸门（后端测试 / 文档 reindex 零 diff / 前端 tsc+test）
- **反向验证**是判断断言真假的标准动作：新写断言后临时改坏被测逻辑，确认用例真变红（否则是假保护）
- 需「改配置才能验」的分支：**不**改 `application.yml`、**不**重启用户实例 → 命令行参数起第二实例（共享 MySQL/Redis）

## 6. 文档入口与写作约定
- `AGENTS.md`（AI 会话第一入口）· `docs/INDEX.md`（总账；再生成 `python .workbuddy/scripts/add_frontmatter.py --reindex`）· `docs/架构/` · `docs/业务知识/`（`BK-N`，不写实现）· `docs/模块/{模块}/` 五件套（功能/数据库/接口/已完成功能/功能清单）· `docs/intent/INT-N` · `docs/eval/EVAL-N` · `docs/_templates/`
- 铁律：**plan 未批不动码**；完成后同会话更新已完成功能+进度；每起事故产一条 EVAL（只修码不回流 = 未闭环）
- 写作：frontmatter 六字段 + blockquote 人读头并存；约束表 A# / 禁止表 P# / 决策表 D# 编号体例
- **plan 定稿 ≠ 正确**：审查结论须落代码行号 / 实测 / 反编译证据，以 `F#`（事实修正）+ `C#`（绑定约束）行**追加**进 plan 并标轮次，不静默改写已批准内容
- 实施完 = 编译 + 构建 + 静态核对 + 可执行探针断言；环境阻塞的验收条目在 plan「实施记录」显式登记，禁止拿「编译通过」当功能验收
