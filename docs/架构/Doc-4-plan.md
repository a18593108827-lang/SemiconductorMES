---
type: plan
module: 
status: approved
slices: [Doc-4]
aligns: [MES-AI原生SDLC文档体系重构方案.md, MES-实施进度与下一步.md]
updated: 2026-09-23
---
# Doc-4 计划 — 最小回归网（src/test + mvn test + CI 门禁）

> 对齐：`MES-AI原生SDLC文档体系重构方案.md` Doc-4 / Doc-5 · `MES-实施进度与下一步.md` §5
> 批准依据：2026-09-23 用户指令「先做现在的回归网」；Doc-4 已在重构方案中获批（状态：**approved**）
> 前置事实（2026-09-23 实测）：`server/src/test` 不存在、`pom.xml` 未引任何测试依赖、`.github/workflows` 不存在；本地仓库已有 `spring-boot-starter-test:3.3.5`（与 parent 同版本，可离线构建）

## 1. 目标与边界

**一句话**：让「改坏了」在 `mvn test` 里自动变红，而不是靠手写探针 + 人肉点页面。

**做**

- `pom.xml` 引 `spring-boot-starter-test`（**test scope**，版本随 parent 3.3.5）
- 建 `server/src/test`，首批三类用例（**全部不依赖 MySQL / Redis / 网络**）：
  1. 客诉包导出装配（ZIP 结构 + 同源 + README + 时间口径）—— 把 CP-6 那 12 项一次性探针断言**沉淀为正式用例**
  2. 履历 / Hold / 告警上限的**生效值**与钳制（`historyPerLot()` / `holdCap()` / `alarmCap()`）
  3. format 白名单与归一（`json` / `zip` / 其它 → 业务码），锁住 K3 顺序语义
- CI 三道门（均可判真假，失败即红）：
  - 后端 `mvn -B test`
  - 文档：`add_frontmatter.py --reindex` 后 `git diff --exit-code docs/INDEX.md`（INDEX 必须与文档同步 = frontmatter / 状态列门禁，即 Doc-5 的一半）
  - 前端 `npm ci && npx tsc -b`

**不做（负面清单）**

- ❌ 不引 Testcontainers / 不建需要 DB 的集成测试：本机无 Docker，且外部依赖场景已有「一次性探针 + 命令行参数起副实例」两条验证路径
- ❌ 不追求覆盖率数字（无门槛、无报告插件）
- ❌ 不改业务代码（见 K1）
- ❌ 不动前端测试基建（`tsc -b` 只做类型门禁）

## 2. 约束

| # | 约束 | 说明 |
|---|------|------|
| K1 | **零业务代码改动** | 只加测试与构建/CI 配置。若某逻辑当前不可测（私有/需重依赖），记入 §6 遗留，**不**为测试改生产代码 |
| K2 | 用例不依赖外部服务 | 无 MySQL / Redis / HTTP；`@JsonTest` 切片只起 Jackson 相关自动配置 |
| K3 | ObjectMapper 必须容器同源 | 用 `@JsonTest` 注入的容器 Bean，**禁止**手搓 `Jackson2ObjectMapperBuilder`（实测踩点：手搓时 `WRITE_DATES_AS_TIMESTAMPS` 未关，会误判代码有 bug） |
| K4 | 门禁必须能失败 | CI 步骤不得写成"仅打印"；文档门禁用 `git diff --exit-code` 实现，不新写校验逻辑 |
| K5 | 测试数据自造 | 用例内构造 VO，不读库、不依赖种子数据 |

## 3. 实施步骤

a. `pom.xml` 加 `spring-boot-starter-test`（`<scope>test</scope>`）
b. 用例一 `ComplaintPackageExporterTest`（`@JsonTest`）：恰 2 个扁平 entry / 本地头 == 中央目录时间 / ZIP 内 JSON 与 `toJsonBytes` 同参**逐字节相同** / README 关键行（含三个上限行、空块说明、口径句）/ 导出时间 == JSON `exportedAt` / 备注换行折成空格 / `jsonFileName` / `zipFileName`
c. 用例二 `ComplaintPackageAssemblerCapsTest`：`historyPerLot = 0` → 生效值 100；`= 50` → 50；`holdCap()` / `alarmCap()` = 20
d. 用例三 `ComplaintPackageFormatWhitelistTest`：`null` / `""` / `" "` → `json`；`json` / `JSON` / `" json "` → `json`；`zip` / `" ZIP "` → `zip`；`pdf` → `BusinessException` 且消息前缀 `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED`
e. `.github/workflows/ci.yml`：三 job（backend / docs / web）
f. 文档同步：`MES-实施进度与下一步.md` §5 Doc-4 行、`docs/eval/README.md` 启用说明补一句（回归网已就位）、`docs/INDEX.md` 重建

## 4. 验收（可判真假）

1. `mvn -o -q test` 全绿，且**恰有 3 个测试类**运行（用 `mvn -o test` 输出核对类数）
2. **反向验证一次**：故意把 `setTimeLocal` 挪到 `putNextEntry` 之后（临时改 → 跑 → 还原），必须有用例变红；若不变红说明该断言是假保护
3. `ci.yml` 能被 YAML 解析（本地用 python `yaml.safe_load` 校验）
4. `git diff` 只含：`pom.xml` + 新增 `src/test/**` + `.github/workflows/ci.yml` + 文档；**零生产代码改动**（K1）
5. 新依赖只在 test scope（`mvn -o dependency:list -DincludeScope=runtime` 不含 junit / mockito）

## 5. 回滚

删 `server/src/test`、`.github/workflows/ci.yml`，撤 `pom.xml` 的依赖块即可。无数据、无接口、无运行期行为变化。

## 6. 遗留（本切片不做）

| 项 | 现状 | 触发条件 | 方案 |
|----|------|----------|------|
| DB / Redis 依赖的集成测试 | 不建 | 有 CI 侧数据库能力或引入 Testcontainers | 抽 Facade 冒烟 + 状态机回归 |
| EVAL 回归用例挂载 | EVAL-0001 手工校验 | 上条完成 | 把 EVAL 的「回归校验」段落成用例并在 CI 跑 |
| 覆盖率门槛 | 无 | 团队规模扩大 | 引入 jacoco + 阈值 |

---

## 实施记录（2026-09-23 完成）

**变更文件**

| 文件 | 变更 |
|------|------|
| `server/pom.xml` | + `spring-boot-starter-test`（`<scope>test</scope>`，版本随 parent 3.3.5） |
| `server/src/test/java/.../ComplaintPackageExporterTest.java`（新增） | 6 用例：ZIP 恰 2 扁平 entry / 本地头 == 中央目录时间 / ZIP 内 JSON 与 `toJsonBytes` 同参逐字节相同 / README 20 项行 / 导出时间与 JSON 同值同形 / 文件名约定 |
| `server/src/test/java/.../ComplaintPackageAssemblerCapsTest.java`（新增） | 3 用例：`historyPerLot` 生效值（0 / 负数 → 100；50 → 50）/ `holdCap` / `alarmCap` = 20 |
| `server/src/test/java/.../ComplaintPackageFormatWhitelistTest.java`（新增） | 3 用例：null/空白 → json；大小写与 trim 归一；非法值抛业务异常且消息带 `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED` |
| `.github/workflows/ci.yml`（新增） | 三 job：后端 `mvn -B test` / 文档 reindex 后 `git diff --exit-code docs/INDEX.md` / 前端 `npm ci && npx tsc -b` |
| 文档 | 本 plan · 进度文档 §5（Doc-4 ✅、Doc-5 ✅）· `docs/eval/README.md`（回归网就位）· INDEX 重建 |

**验收结果（逐条对应 §4）**

| # | 结论 |
|---|------|
| 1 | ✅ `mvn -o test` → **Tests run: 12, Failures: 0, Errors: 0，BUILD SUCCESS**；恰 3 个测试类 |
| 2 | ✅ **反向验证**：把 `setTimeLocal` 移到 `putNextEntry` 之后（临时改）→ `localHeaderTimeMatchesCentralDirectory` 立刻变红；已还原（`git diff` 生产代码为空）——断言是真的保护，不是摆设 |
| 3 | ✅ `ci.yml` 结构校验通过（3 job + 关键步骤齐；本机无 pyyaml，用结构化粗检） |
| 4 | ✅ 改动仅 `pom.xml` + `src/test/**` + `.github/**` + 文档，**零生产代码改动**（K1 达成） |
| 5 | ✅ `mvn -o dependency:list -DincludeScope=runtime` 中 junit / mockito / assertj 命中 **0** 条 |
| **6** | ⚠️ **补做（2026-09-24）**：验收 3 只校验了 `ci.yml` 的 **YAML 结构**，**未在真实 runner 上执行过** —— 该缺口导致 docs 闸门自落地起**一直失败**（脚本硬编码 Windows 路径 + 生成物含 `date.today()`，两个根因见 `docs/eval/EVAL-0002-CI文档闸门失效（硬编码路径+生成物非幂等）.md`）；修复提交 `e6220bf`，**转绿待推送后确认** |

**实施期事实（F#）**

| # | 事实 | 影响 |
|---|------|------|
| F1 | `@JsonTest` 若加载 `MesApplication`（`@SpringBootApplication`）会触发全量组件扫描，把 MyBatis mapper 注册进来 → `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`，上下文启动失败 | 改为测试内嵌最小 `@SpringBootConfiguration`（`@EnableAutoConfiguration` + `@Import(JacksonConfig.class)`）：仍是容器同源 ObjectMapper（Boot JacksonAutoConfiguration + 项目定制），但不带 DB bean |
| F2 | `List<int[]>` 用 AssertJ `isEqualTo` 比较会**恒不等**（数组无值语义 equals，报错信息还长得一模一样） | 时间三元组改用 `List<Integer>` |
| F3 | 手搓 ObjectMapper 的坑（K3）在测试里同样会咬人 | 用内嵌配置注入，不 `new ObjectMapper()` |
| F4 | docs job 的「reindex + `git diff --exit-code`」门禁**顺带覆盖**了「frontmatter 被格式化器破坏」这一类问题（首行被转义 → 解析失败 → 状态列回落 → diff 非空 → CI 红），无需另写校验脚本。**2026-09-24 更正**：该门禁当时**并未真正生效**（自落地起每次都是路径报错而红），F4 的「兜住」承诺在修复前**不成立** —— 见上表验收 6 与 EVAL-0002 | 该破坏 2026-09-23 内生两次（`MES-厂型选型分析.md`），根因是外部格式化器/preview 重存；已用 `git checkout` 还原，CI 门禁可在下次推送时兜住 |
