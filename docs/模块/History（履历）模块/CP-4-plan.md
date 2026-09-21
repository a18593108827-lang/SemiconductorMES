---
type: plan
module: History
status: approved
slices: [CP-4]
aligns: [MES-客诉追溯包接口设计.md]
updated: 2026-09-21
---
# CP-4 计划 — export JSON 下载 + Admin 入口（History / Lots）

> 对齐：`MES-客诉追溯包接口设计.md` §3 / §6.4 / §9 / §10 / §12 · 状态：approved  
> 前置：CP-3 ✅（build / get / list 已上线，`facade.get(id)` 装配可直接复用）  
> 已吸收架构审查 R1～R8（2026-09-21）+ 二轮 F1～F4 + 三轮 F5～F8

## 1. 目标与边界

**一句话：** 把已落库的追溯包下载成可交付的 JSON 证据文件，并在 History 调查台与 Lots 详情挂上「生成追溯包 → 预览确认 → 生成 → 下载」的完整入口。

**做：**
- 后端：`GET /complaint-packages/{id}/export?format=json` —— JSON 附件下载（根对象 = get 同形 VO + `exportedAt` / `exportedBy`）
- 前端：`ComplaintPackageDrawer` 共享组件（preview → 确认 build → 下载）+ HistoryPage（Lot 模式）与 LotsPage（详情）两处入口
- 前端基础设施：`lib/http.ts` 增 `downloadFile`（现有 `request()` 只会 `res.json()`）
- 权限：入口 `enabled && complaint:view`；生成按钮再加 `complaint:build`；export 接口 `complaint:view`

**不做（负面清单）：**
- ❌ ZIP / 封面 PDF（CP-6）
- ❌ 已生成包列表页（`list` 端点已就绪，关抽屉即丢 packageId）
- ❌ 深链 `?packageId=` 直达（P1）
- ❌ contain 遏制（CP-5）
- ❌ 导出写审计表 / 写 `mes_tx_log`（P0 纯读）
- ❌ 流式导出 / 改 Assembler 边装边写（P0 接受 VO + byte[] 双份内存；见 §8）
- ❌ 前端把 get VO 另存为文件
- ❌ `@GetMapping` 上写 `produces = APPLICATION_OCTET_STREAM_VALUE`（会污染失败时的 `R<>` JSON）
- ❌ Facade 方法名 `buildExportFile`（像写库；用 `exportFile`）

**约束：**

| # | 约束 | 说明 |
|---|------|------|
| C1 | 装配唯一实现 | 导出必须调 `get(id)` 的装配结果，禁另写一套查询 |
| C2 | 下载不走 `R<>`；失败仍走全局异常 | 成功 = `ResponseEntity<byte[]>` 自设头；**映射上不写 `produces`**。现网异常是 HTTP 200 + `Content-Type: application/json;charset=UTF-8` + body `R<>`。前端以 Content-Type **前缀** `application/json` 分流：JSON → 解析 `R<>` 抛 `ApiError`；否则存盘。401 复用 `request()` 跳登录 |
| C3 | 内存组装后一次性写出 | 禁边查边写（半截文件）；成员 ≤200、每 Lot 履历 ≤100。堆上同时持有 VO + `byte[]` |
| C4 | 文件名 | `{packageNo}.json`（**纯 ASCII**）；`Content-Disposition: attachment` 用 `ContentDisposition.attachment().filename(name)`，**不带 charset**。前端优先响应头，解析 `filename=` 时**去掉引号**，缺失用 packageNo |
| C5 | 下载带 token | `fetch` + `blob` + `<a download>`；路径前缀 `/api` 与 `request()` 相同；**禁** `window.open` |
| C6 | 文案红线 | 只用「追溯包 / 影响面 / 下载证据」；禁「良率包 / eDHR / SEMI T23」 |
| C7 | 导出零副作用 | 只读、不加锁、不写库；关开关则接口拒 + 入口不渲染 |
| C8 | 防重复 build | 抽屉内生成按钮提交中禁用；成功转 `built`。**下载失败仍停在 `built`，只重试 export**，不得退回 preview（退回再生成会按 A9 再落一包）。关抽屉才丢 packageId |
| C9 | 权限与开关 | 入口：`enabled && complaint:view`（History 的 `history:list`、Lots 的 `lot:list` 不够）。**无 `complaint:view` 时不调 `/enabled`**。生成按钮：再加 `complaint:build`。仅 view 可 preview，不可生成 |

**HTTP 成功边界：** 完整文件 **或** `R<>` JSON，不得半截文件。验失败看 body `code`，不看 HTTP status（现网业务错仍是 200）。

**序列化：** 注入 Spring 容器 `ObjectMapper`（现网 Long→字符串、JavaTime）。禁止 `new ObjectMapper()`。`exportedAt` 与 `createTime` 同一 JVM 本地时钟；写入用 `node.set("exportedAt", mapper.valueToTree(now))`，禁止 `ObjectNode.put` 硬塞 `LocalDateTime`。

## 2. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| GET | `/complaint-packages/{id}/export?format=json` | `complaint:view` | JSON 附件；`format` 为 `null` / `""` / 纯空格（trim 后空）或 `json`（**忽略大小写**）等价；其它值 `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED`。VOID 本切片不拦 |

复用既有：`GET /enabled`、`POST /preview`、`POST /`（build）、`GET /{id}`、`GET /`（list）。`list` 本切片前端不接。

规格差额**已于 2026-09-21 落地**设计文档：§6.4 下载语义（不写 produces / JSON 分流 / format 大小写）、§9 `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED`、§4.3 / §14 流式改 P0 内存组装。

## 3. 表变更

**零。** 无 DDL、无种子。

## 4. 影响的 Facade 与模块

| 项 | 变更 |
|----|------|
| `ComplaintPackageFacade` | 新增 `exportFile(Long id, String format)` → `{ fileName, content }`。**顺序固定：`assertEnabled` → format 合法性 → 复用 `get`**——开关关必须先于 format 拒，维持设计 §6「开关关一律 DISABLED」的全局约定（format 校验放 Controller 会在开关关时先抛 FORMAT_UNSUPPORTED，破坏该约定） |
| `ComplaintPackageFacadeImpl` | 注入 `ObjectMapper`；`exportedBy` 沿用 `resolveCreateBy()` |
| `MesComplaintPackageController` | `GET /{id}/export` → `ResponseEntity<byte[]>`；**不**写 `produces` |
| `lib/http.ts` | `downloadFile(path)`：`/api`、token、charset 前缀分流、401、blob 存盘、解析文件名 |
| 前端新增 | `web/src/api/complaint.ts`、`web/src/components/lot/ComplaintPackageDrawer.tsx` |
| 前端改造 | `HistoryPage.tsx`（Lot 模式）、`LotsPage.tsx`（详情） |
| 后端零新增依赖 | **不改** Lot / Alarm / History / Hold Facade |

## 5. 实现步骤

a. `ComplaintPackageExportFile`（record：`fileName` + `byte[] content`）；Facade `exportFile(Long id, String format)`：
   1. `assertEnabled()` → format 合法性（`null` / blank / `json` 忽略大小写；先 trim；否则 `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED`）——**开关先于 format**
   2. `get(id)`（含 NOT_FOUND）。注：此处 `this.get` 属同类自调用，但 `get` / `exportFile` 均无 `@Transactional`、纯只读，R9 代理陷阱不适用；**禁止**后续给二者加事务注解
   3. 容器 `ObjectMapper.convertValue(vo, ObjectNode)` → `set` `exportedAt` / `exportedBy`（扁平根对象）
   4. `writeValueAsBytes`；`fileName = packageNo + ".json"`
b. Controller：`@GetMapping("/{id}/export")` 无 `produces`、**不做业务校验**（format 原样传 Facade）；`ResponseEntity` 设 `Content-Type: application/octet-stream` + `Content-Disposition`（`ContentDisposition.attachment().filename(fileName)`，不带 charset，见 C4）
c. `downloadFile`：`Content-Type` 以 `application/json` 开头 → 解析 `R<>`（`code===401` 清 token 跳登录；其它抛 `ApiError`）；否则 blob 存盘。取 `filename=` 后剥掉首尾引号
d. `api/complaint.ts`：`enabled / preview / build / get / export`（`page` 本切片可暂不接）
e. `ComplaintPackageDrawer`：
   - 入参默认 `direction=both`、`depth=5`；`reasonCode` / `remark` 可空
   - **生成按钮仅当当前 direction/depth 与上次成功 preview 一致时可点**；改参后必须重新 preview，禁止拿旧影响面去 build
   - 三态：`preview`（成员表：批号/关系/深度/状态 + 摘要三数 + `truncated` 警告）→ `building`（按钮禁用）→ `built`（`packageNo` + 「下载证据」）
   - 下载失败保持 `built`；文案遵 C6；错误 Toast（NOT_FOUND 的 `msg` 含「追溯包不存在」即可，不必剥错码前缀）
f. HistoryPage（Lot 模式已选批次）与 LotsPage（详情）：先判 `complaint:view`，无则入口不渲染且**不请求** `/enabled`；有 view 再探开关。生成按钮另需 `complaint:build`
g. 文案自查（C6，范围见 §6.9）；`docs/INDEX.md` 重建

顺序：a → b 串行；c 可并行；d → e → f 串行。**禁止**跳过 e 在页面里拼下载。

## 6. 测试与验收

- 验收标准（可判真假）：
  1. 开关关：两处入口不渲染；curl export → body 含 `COMPLAINT_PACKAGE_DISABLED`
  2. build 后下载：文件名 `CP-yyyyMMdd-N.json`；根对象扁平含 members / 四装配块 / `exportedAt` / `exportedBy`；Long id 为**字符串**；无 Yield / OEE
  3. 包 id 不存在 → Toast **包含**「追溯包不存在」，不产生文件（含 0 字节）。失败响应 `Content-Type: application/json;charset=UTF-8` 也不得存盘
  4. 无 `complaint:build` → 生成按钮不渲染；直调 build 的 body `code=403`。无 `complaint:view` → 入口不渲染；直调 export 的 body `code=403`（HTTP 仍 200）
  5. `truncated=true` 显示截断警告；`format=zip` 等非 json 值 → `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED`；`format=JSON`、缺省、`format=`、`format=%20` 与 `json` 同等成功；开关关 + `format=zip` 同时成立时 → **`DISABLED` 优先**（F2 顺序）
  6. 连点生成：仅 1 个 packageId（相对 build 前行数 +1，仅此一次落库）。下载失败后再点下载：行数不再增、不退回 preview 重新 build。改 depth 后未重新 preview：生成按钮不可点（F6）
  7. 导出前后 `mes_complaint_package` 行数不变（相对「已 build 完成」）、无新 `mes_tx_log`
  8. `grep -r "mes_tx_log\|mes_lot_genealogy\|MesHoldMapper\|MesAlarmMapper" server/src/main/java/com/mes/complaint/` 零命中
  9. 文案：本切片新增的 `web/src/api/complaint.ts`、`web/src/components/lot/ComplaintPackageDrawer.tsx` 及改动的 History/Lots 页、`com.mes.complaint` **新增代码** 无「良率包 / eDHR」（不含既有规格文档的禁称句）
- 验证方式：
  - `curl -H "Authorization: Bearer <token>" -D- -o out.json ".../complaint-packages/1/export?format=json"` 查头与文件
  - `/app/history` 选批次、`/app/lots` 详情走完生成→下载；下载失败后仍能再下、不二次 build
  - `SELECT COUNT(*) FROM mes_complaint_package;` 导出前后比对

## 7. 回滚方式

`enabled=false` → 入口隐藏、导出拒。无 DDL、无数据写入，回滚无残留。

## 8. 遗留（本切片不做）

| 项 | 现状 | 触发条件 | 方案 |
|----|------|----------|------|
| 流式导出 | P0：`get()` 完整 VO 再 `writeValueAsBytes`（堆上 VO + byte[]；上限内可接受） | 实测单次导出堆压力不可接受 | **必须改 Assembler 边装边写**，不能再复用完整 `get` VO；并重做 C2 错误分流。只把已有 `byte[]` 塞进 `StreamingResponseBody` **不算**解决 |
| 导出审计 | 不落库 | 合规要「谁何时导出了哪个包」 | 另表或评估 `mes_tx_log` 语义（导出不是工艺状态变更） |
| 深链 / 列表页 | 关抽屉丢 packageId | 失败后关掉还要再下、或把包发给同事 | list 页或 `?packageId=` |
| VOID 拦截 | P0 仅 READY，export 不判 status | 真做 VOID | `COMPLAINT_PACKAGE_VOID` 拒 export |

## 9. 架构审查吸收

| # | 结论 |
|---|------|
| R1 | 映射不写 `produces`；成功头自设；前端 charset 前缀分流；401 走 `request()` 同款 |
| R2 | 注入 Spring `ObjectMapper`；`exportedAt` 用 `valueToTree` |
| R3 | 下载失败保持 `built`，只重试 export |
| R4 | 抽屉默认 both/5；可改方向深度再 preview；原因/备注可空 |
| R5 | P0 不流式；流式与 C1 互斥，写入 §8 |
| R6 | 规格补 FORMAT_UNSUPPORTED、format 忽略大小写、VOID 不拦 |
| R7 | 入口 `enabled && complaint:view`；按钮加 `complaint:build` |
| R8 | 方法名 `exportFile`，禁止 `buildExportFile` |
| F1 | `Content-Disposition` 用 `filename(name)` 不带 charset（Spring 带 charset 只产 `filename*=`，与双形式要求打架；packageNo 纯 ASCII 无需 5987） |
| F2 | format 校验挪进 Facade，顺序 `assertEnabled` → format → get；开关关必须先于 format 拒（维持 §6 全局约定） |
| F3 | `this.get` 同类自调用无 R9 陷阱（双方均无 `@Transactional`、纯只读）；禁止后续加事务注解 |
| F4 | 验收 5/6 措辞修正：非 json 值举例、DISABLED 优先、行数语义澄清 |
| F5 | `format` 的 null / `""` / 纯空格 trim 后当缺省 json |
| F6 | 改 direction/depth 必须重新 preview 才能 build |
| F7 | 解析 `Content-Disposition` 的 `filename=` 去掉引号 |
| F8 | 无 `complaint:view` 不调 `/enabled`；§5.g 规格差额已落地，不再当待办 |

---

**批准记录**：2026-09-21 用户批准（经二轮架构审查：R1~R8 + F1~F4 全量吸收，承载性事实已对码核验，设计文档规格差额已同步）。
