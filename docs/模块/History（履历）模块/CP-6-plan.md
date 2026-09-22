---
type: plan
module: History
status: approved
slices: [CP-6]
aligns: [MES-客诉追溯包接口设计.md]
updated: 2026-09-22
---
# CP-6 计划 — ZIP 证据包（JSON 全文 + README.txt 封面）

> 对齐：`MES-客诉追溯包接口设计.md` §3（ZIP 行 / 切片表）· §4.3（不落库）· §6.4（export，P1 合同原文）· §8.1（只读不加锁）· §9 · §11 · §14 · 状态：**approved**（2026-09-22 用户批准，可动码）  
> 前置：CP-4 ✅（`exportFile` + `downloadFile` + 入口）· CP-5 ✅  
> 已核实现网（2026-09-22 对码）：`ComplaintPackageFacade.exportFile(Long, String)` 已存在，链路 `assertEnabled → assertFormatJson → get(id) → ObjectMapper 拼 ObjectNode(+exportedAt/exportedBy) → ComplaintPackageExportFile(fileName, byte[])`；Controller `/export` 已 `ResponseEntity<byte[]>` + `APPLICATION_OCTET_STREAM` + `ContentDisposition.attachment().filename()`，**映射未写 `produces`**。本切片 Controller **只加** `Cache-Control: no-store`，不组包  
> 现网事实：`hutool-all` 与 JDK 均可用（本切片只用 JDK `java.util.zip`，**不引依赖**）；`application.yml` 已有 `mes.complaint-package.*` 段；`docs` 内 ZIP 仅三处「后置」待更新；`History 接口设计` 无 export 条目，不动
> 二轮源码级审查（2026-09-22：实测 + 对码，结论已并入 K8 / K10 / K15 / D7 / 验收 2 / 验收 13 / F1~F9）：`ZipEntry.setTimeLocal` 在 JDK 21 可用，但 ZIP **DOS 时间 2 秒粒度**（实测 `setTimeLocal(10:48:17)` 回读 `10:48:16`）；`JacksonConfig` 只定制 Long→String，`LocalDateTime` 走默认 JavaTimeModule ISO 文本；`ComplaintPackageAssembler` 已持有 `history-per-lot` 绑定，故改由访问器取用  
> 三轮（并入 K8–K10 / K14 / K15 / D7 / D9 / 验收 4 / 9 / 13 / F5 / F8 / F10–F12）：`historyPerLot()` 返回生效值且 `loadHistories` 改调它；`setTimeLocal` 在 `putNextEntry` 之前；堆峰值含 VO + ObjectNode；Hold / 告警上限与履历上限同一路传入封面
> 四轮源码级复核（2026-09-22：实测 + 反编译 + 对码，结论并入 K8 / K10 / 验收 2 / F13–F15）：`setTimeLocal` 晚于 `putNextEntry` 会做出**两个头**（本地头 vs 中央目录），已在 K8 写明失败模式、验收 2 加两头一致性检查；`writeValueAsBytes` 经 `ByteArrayBuilder.toByteArray()` 必有一次整段拷贝 → **JSON 路径字节侧 ≈ 2N**（原 K10 低估），已改「JSON 2N / ZIP 3N、预算按 3N」；现网 `loadHistories:171` 的 `historyPerLot < 1 ? 100` 钳制 + `listByLots(keySet, cap)` 与 K15 完全吻合

## 1. 目标与边界

**一句话：** 把「一个 JSON 文件」升级为「一个可直接转交客户/审计的压缩证据包」——`format=zip` 产出 `{packageNo}.zip`，内含与 JSON 导出口径同源的 `{packageNo}.json` 与 `README.txt` 封面（包号 / 锚点 / 成员数 / 生成时间 / 文件清单）。同一次调用内 JSON 与 README 共用同一个 `exportedAt` 与 `exportedBy`；`exportedBy` 随导出人变化。

**做：**
- 后端：`GET /complaint-packages/{id}/export?format=zip`（沿用 `complaint:view`）→ ZIP 字节
- ZIP 内容契约（**恰好 2 个 entry**，扁平无目录）：
  - `{packageNo}.json` —— 与 `format=json` 同源同 ObjectMapper（同一次调用内 `exportedAt` / `exportedBy` 同值）
  - `README.txt` —— 包号、锚点 Lot（id + no）、direction、depth、成员数、truncated（仅影响面）、每 Lot 履历上限、每 Lot Hold 各状态上限、每 Lot 未关闭告警上限、status、reasonCode、remark、生成人/生成时间、导出人/导出时间、文件清单、口径免责句、空块说明、`CONTAINING` 非结案说明
- 抽 `ComplaintPackageExporter`（support）：JSON 组装 + README 渲染 + ZIP 打包 + 文件名；Facade 只编排与 format 校验（A1）。全仓只允许 `ComplaintPackageFacadeImpl` 调用 Exporter
- format 白名单扩到 `json` / `zip`（`trim` + `equalsIgnoreCase`），校验顺序保持「开关 → format → get」
- 前端：抽屉 built 态拆两个下载按钮（JSON / ZIP），loading 分流
- 文档：接口设计 §3/§6.4、已完成功能、进度文档三处「ZIP 后置」、`docs/INDEX.md` 重建

**不做（负面清单）：**
- ❌ PDF 封面 / HTML 版式封面（不引 PDF 依赖，README.txt 即封面；进 §8 遗留）
- ❌ 流式导出（`StreamingResponseBody` / 边装边写）——P0 内存组装，与 §14 口径一致
- ❌ 文件落磁盘 / 导出留痕表 / 下载审计表（§4.3 明示 export 不落盘）
- ❌ export 拒 VOID 行（保持 §6.4「P0 不拦」；作废流程整体后置）
- ❌ 每 Lot 单独文件、截图/附件上传、manifest.json 等第二清单（避免第二真相）
- ❌ 前端本地打包（服务端产出，A1 禁止客户端拼装当真相）
- ❌ 新增第三方依赖（`java.util.zip`；hutool 已有也**不用**）
- ❌ 改动既有 JSON 导出口径（只搬类与加分支，字节结构不变）
- ❌ 新增错误码 / 新增配置 key / 新增权限码
- ❌ 导出占 contain token、加包行锁、加信号量（与 §8.1 交错；并发闸门进遗留「大包限流」）
- ❌ 为封面再查用户姓名、履历、Hold、Alarm；Exporter 不读 yml、不注入 Assembler

**约束：**

| # | 约束 | 说明 |
|---|------|------|
| K1 | 唯一入口 | export 只经 `ComplaintPackageFacade.exportFile`。Controller 只追加 `Cache-Control: no-store`，禁止在 Controller / 前端组 zip。`ComplaintPackageExporter` 只许 `ComplaintPackageFacadeImpl` 调用 |
| K2 | 零 DDL / 零状态写 / 压缩不占连接 | 无表变更、无种子、无事务（`exportFile` 不加 `@Transactional`）。`toZipBytes` 在 `get()` 返回之后执行。不占 contain token、不加包行锁 |
| K3 | format 归一与顺序 | `normalized = hasText(format) ? format.trim().toLowerCase(Locale.ROOT) : "json"`；白名单 `{json, zip}`；否则 `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED`。顺序仍 `assertEnabled → assertFormat → get`——`format=pdf&id=不存在` 必须报 FORMAT 而非 404 |
| K4 | JSON 同源 | ZIP 内 JSON 与 `format=json` 由**同一** `buildJsonNode(vo, exportedBy, exportedAt)` 产出：同一 ObjectMapper（Long→字符串、JavaTime 同形），字段集不含新键。禁止为 zip 另写一套序列化。导出路径禁止 `objectMapper.configure` / `registerModule`（容器 Bean 配置冻结后并发 `writeValueAsBytes` 才安全） |
| K5 | 一次取值、时间同形 | 一次 export 只取一次 `LocalDateTime.now()` 与一次 `resolveCreateBy()`。JSON 的 `exportedAt` / `exportedBy` 与 README「导出时间 / 导出人」同值。README 时间与生成人用该 ObjectMapper 对同一值的标量序列化文本，禁止另写 `DateTimeFormatter` |
| K6 | entry 命名单点 | 扁平、无目录、纯 ASCII（`packageNo` 形如 `CP-yyyyMMdd-序号`）。外层下载名与 ZIP entry 名只由 Exporter 的 `jsonFileName` / `zipFileName` 给出，Facade 使用返回值，禁止两处拼接。`ZipOutputStream(baos, StandardCharsets.UTF_8)`；README 内容 `getBytes(UTF_8)`。禁止中文文件名 |
| K7 | 响应头 | `Content-Type: application/octet-stream`；`Cache-Control: no-store`；`Content-Disposition: attachment; filename="{packageNo}.zip"`（`ContentDisposition.attachment().filename()` **不带 charset**）；映射**不写** `produces`（失败体仍 `R<>` JSON，前端按 Content-Type 前缀分流） |
| K8 | 流收尾 | 每次请求自己的 `ByteArrayOutputStream` + `ZipOutputStream` + `ObjectNode`。try-with-resources 结束（`close()` 内 finish）之后再 `baos.toByteArray()`。每 entry 先 `ZipEntry.setTimeLocal(exportedAt)` 再 `putNextEntry`（`xdostime == -1` 时 `putNextEntry` 会先写当前时间）。禁 `setTime(0)`、禁写死 `ZoneOffset.ofHours(8)`、禁「或系统默认时区」。**DOS 时间 2 秒粒度**：实测 `setTimeLocal(10:48:17)` 回读 `10:48:16`——README / JSON 的导出时间文本不受影响，但**禁止**拿 ZIP entry 时间与 `exportedAt` 做相等断言（只断言非 0 / 年份正确）。**时序错了会做出两个头**（实测）：`putNextEntry` 之后才 `setTimeLocal` → 本地头保留当前时间、中央目录写设定值（同一 entry 本地头 `2026-09-22T11:03:50` vs 中央目录 `2000-01-01T00:00`），不同解压工具显示不同时间。故顺序**必须**先 set 再 put |
| K9 | 只读零 Mapper | Exporter 只吃 VO + ObjectMapper + 方法入参（`historyPerLot` / `holdCap` / `alarmCap` 由 Facade 传入），**零** Mapper 字段 / **零** `com.mes.*.mapper` / **零** Facade / **零** Assembler 依赖。`ObjectMapper` 字面量允许。`com.mes.complaint` 包零 tx_log / genealogy / Hold / Alarm Mapper（A2 + 验收 8 延续） |
| K10 | 大包留痕不加码 | `toJsonBytes` 得到字节后，长度 > 10MB → `WARN`（packageNo + bytes + memberCount）；**不**拒绝、**不**新增错误码、**不**加信号量（成员 ≤200 由 A8 兜底）。10MB 为类内常量，验收不改这个常量。N = JSON 字节长度。**字节数组侧峰值**：JSON 路径 ≈ **2N**——`writeValueAsBytes` 内部走 `ByteArrayBuilder`（javap 实测：`toByteArray()` 会 `newarray` + 逐块拷贝），即缓冲 N + 返回数组 N；ZIP 路径 ≈ **3N**（再叠 zip 缓冲与 `baos.toByteArray()` 的拷贝）。**并发预算按 3N 统一取**，勿按 1N 估；另加 VO + ObjectNode（节点树活到写盘之后） |
| K11 | 前端不猜格式 | 文件名从响应头 `filename=` 取（`downloadFile` 已去引号）；按钮文案「下载 ZIP」，禁「eDHR / 良率包」 |
| K12 | 失败面单点翻译 | `get()` 与 format 校验在 try **外**。try 只包住 Exporter 调用：`BusinessException` 原样抛出（FORMAT / NOT_FOUND / DISABLED 不被盖掉）；其它异常 → 一处 `WARN`（`packageNo` + `e`）+ `BusinessException("追溯包导出失败")`。Exporter 内部不再包一层同样的业务异常 |
| K13 | 与 contain 交错 | 读已提交，导出与 contain 不加互斥。封面原样印 `status`（含 `CONTAINING`）。固定一行：`CONTAINING 为遏制进行中，不是结案快照`。禁止为导出去锁包行 |
| K14 | 封面口径 | 原因码、备注：空则 `-`；`\r` / `\n` 换成空格，保持一行。标签 `影响面已截断:` 只表示影响面 `truncated`。`成员数:` 取 `vo.getMemberCount()`。另起三行，值都由 Facade 传入、Exporter 不写死：`每 Lot 履历上限:` = `historyPerLot()`；`每 Lot Hold 各状态上限:` = `holdCap()`；`每 Lot 未关闭告警上限:` = `alarmCap()`。空块说明只管「无数据或装配失败」，不管有数据时的截断。固定一行：`空履历 / 空 Hold / genealogy 空表示无数据或装配失败（见服务端 WARN）`。生成人、导出人只印 id 字符串，不查用户表。口径句仍为「本包为客诉调查证据，非 eDHR / Device History，不含良率、OEE 数据」 |
| K15 | 上限单一来源 | `history-per-lot` **只**绑定在 `ComplaintPackageAssembler`。FacadeImpl **禁止**新增同 key 的 `@Value`。`historyPerLot()` 返回生效值 `historyPerLot < 1 ? 100 : historyPerLot`，`loadHistories` 改为调用它，删掉方法内第二份钳制。`holdCap()` / `alarmCap()` 返回现有常量 `HOLD_CAP` / `ALARM_CAP`（20）。FacadeImpl 把这三个返回值传入 Exporter |

**决策：**

| # | 决策 | 理由 |
|---|------|------|
| D1 | 封面取 `README.txt`，不做 PDF | §6.4 P1 合同原文即 README；PDF 需引依赖，违背「零新依赖」，后置 |
| D2 | entry 扁平（不带 `complaint-package/` 目录前缀） | 交付包单层，避免客户解压路径与重名困扰 |
| D3 | 不加 `manifest.json` | README 与 JSON 已自描述；多一份清单 = 第二真相 |
| D4 | Controller 只加 `no-store` | `downloadFile` 用 `fetch` 发 GET；证据包禁止被缓存。组包仍只在 Exporter |
| D5 | `ZipEntry.setTimeLocal` | `setTime(epoch)` 会再按 JVM 默认时区折回 DOS 时间；写死 +8 与「或系统默认时区」会做出两个时钟 |
| D6 | 文件名只在 Exporter | Facade 与 Exporter 各拼一次会漂 |
| D7 | 履历上限走 Assembler 访问器 | `ComplaintPackageAssembler` 已注入 FacadeImpl 且已绑定该 key；再加一份 `@Value` = 两个默认值可漂。访问器返回生效值，`loadHistories` 与封面共用（K15） |
| D8 | README 成员数取 `vo.getMemberCount()` | `assemble` 的成员来自 member 表（生成时落库，不随他处 Split 漂）；`ComplaintPackageVO` 无包头 `member_count` 字段，取 VO 唯一无歧义 |
| D9 | Hold / 告警上限走同一访问器 | 现网 `HOLD_CAP` / `ALARM_CAP` 已是 20。封面另写 20 会和常量分叉。Exporter 只收 int（K9 / K14） |

## 2. 接口清单

| 方法 | 路径 | 权限码 | 说明 |
|------|------|--------|------|
| GET | `/complaint-packages/{id}/export?format=zip` | `complaint:view` | **新增**：ZIP 附件（`{packageNo}.json` + `README.txt`），`Cache-Control: no-store` |
| GET | `/complaint-packages/{id}/export?format=json` / 空 | `complaint:view` | 字节口径不变（CP-4）；同样补 `no-store` |

复用：无新增权限、无新增错误码（`COMPLAINT_PACKAGE_DISABLED` / `COMPLAINT_PACKAGE_NOT_FOUND` / `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED` 全称沿用）。

## 3. 表变更

**零 DDL、零种子、零配置新增。** `mes.complaint-package.*` 四个 key 已存在（`enabled` / `max-members` / `history-per-lot` / `contain-rescue-seconds`）；K10 的 10MB 用类内常量，外置留遗留。`history-per-lot` **不**在 FacadeImpl 重复绑定。`ComplaintPackageAssembler` 提供 `historyPerLot()`（生效值）、`holdCap()`、`alarmCap()`（K15），FacadeImpl 取这三个 int 作 README 入参。

## 4. 影响的 Facade 与模块

| 项 | 变更 |
|----|------|
| `ComplaintPackageExporter`（**新增** support Bean） | `jsonFileName(packageNo)` / `zipFileName(packageNo)` / `buildJsonNode(vo, exportedBy, exportedAt)` / `toJsonBytes(...)` / `toReadme(..., historyPerLot, holdCap, alarmCap)` / `toZipBytes(..., historyPerLot, holdCap, alarmCap)`。只注入 `ObjectMapper`，不注入 Assembler |
| `ComplaintPackageFacade` | `exportFile(Long id, String format)` 语义扩到 json/zip；**签名不变** |
| `ComplaintPackageFacadeImpl` | `assertFormatJson` → `assertFormat`（白名单 json/zip）；序列化逻辑搬入 Exporter；取 `assembler.historyPerLot()` / `holdCap()` / `alarmCap()` 传入 Exporter（K15）；JSON 分支行为逐字保持。全仓唯一 Exporter 调用方 |
| `ComplaintPackageAssembler` | `historyPerLot()` 返回生效值，`loadHistories` 改调它。`holdCap()` / `alarmCap()` 返回现有常量。查询口径不变 |
| `ComplaintPackageExportFile` | 复用（`fileName` / `content`），无改动 |
| `MesComplaintPackageController` | **仅** `export` 响应加 `CacheControl.noStore()` |
| 前端 `api/complaint.ts` | `exportComplaintPackageApi(id, fileName?, format: 'json' \| 'zip' = 'json')`（第三参默认 json，既有调用不变） |
| 前端 `ComplaintPackageDrawer.tsx` | footer built 态加「下载 ZIP」；`downloading` 改 `'json' \| 'zip' \| null` |
| 其它模块（Lot / Track / Hold / History / Alarm） | **零改动**；无新 Facade 方法；禁止注入 Exporter |

## 5. 实现步骤

a. `ComplaintPackageAssembler`（K15）：
   1. `historyPerLot()` 返回 `historyPerLot < 1 ? 100 : historyPerLot`；`loadHistories` 改为调用它，删掉方法内那份钳制
   2. `holdCap()` / `alarmCap()` 返回现有 `HOLD_CAP` / `ALARM_CAP`
b. 新增 `ComplaintPackageExporter`（`support`，`@Component` + `@Slf4j` + `@RequiredArgsConstructor`，只注入 `ObjectMapper`；`@Slf4j` 是 K10 WARN 的前置；禁止注入 Assembler）：
   1. `jsonFileName` / `zipFileName`：`packageNo + ".json"` / `packageNo + ".zip"`
   2. `buildJsonNode(vo, exportedBy, exportedAt)` → `ObjectNode`（CP-4 逻辑原样搬迁：`convertValue` + `set("exportedAt")` + `set("exportedBy")`）
   3. `toJsonBytes(...)` → `objectMapper.writeValueAsBytes`；长度 > 10MB → `WARN`（K10，日志含 packageNo + bytes + memberCount）。异常向上抛，此处不翻译成业务异常
   4. `toReadme(...)` → String。时间与人员取 ObjectMapper 对同一值的标量文本（K5）。原因码 / 备注单行化（K14）。`成员数` 取 `vo.getMemberCount()`。三个上限用入参，不写死。固定行序：
      `客诉追溯包` / `包号:` / `锚点批次:`（`lotNo (id)`）/ `方向:` / `深度:` / `成员数:` / `影响面已截断:`（是/否）/ `每 Lot 履历上限:` / `每 Lot Hold 各状态上限:` / `每 Lot 未关闭告警上限:` / `包状态:` / `CONTAINING 为遏制进行中，不是结案快照` / `原因码:` / `备注:` / `生成人:` / `生成时间:` / `导出人:` / `导出时间:` / `文件清单:`（两行缩进）/ 空块说明 / 口径句
   5. `toZipBytes(...)`：内部走同一 `jsonFileName` + `toJsonBytes` + `toReadme`。`ByteArrayOutputStream` → try-with-resources `ZipOutputStream(baos, UTF_8)` → 每个 entry 先 `setTimeLocal(exportedAt)` 再 `putNextEntry` → 块结束 → `baos.toByteArray()`
c. Facade `exportFile`：
   1. `assertEnabled()`
   2. `assertFormat(format)`（K3 归一 + 白名单）——归一结果必须在方法内可见（`normalized`），供 c.5 分支
   3. `LocalDateTime exportedAt = LocalDateTime.now()`（**K5 一次取值**）；`Long exportedBy = resolveCreateBy()`
   4. `ComplaintPackageVO vo = get(id)`（现查装配，口径不变；**在 try 外**）
   5. try 内按**归一值**分支（`"zip".equals(normalized)`，禁拿原参 `format` 判等，否则 `" ZIP "` 走错路）：`json` → `new ComplaintPackageExportFile(exporter.jsonFileName(vo.getPackageNo()), exporter.toJsonBytes(...))`；`zip` → `new ComplaintPackageExportFile(exporter.zipFileName(vo.getPackageNo()), exporter.toZipBytes(..., assembler.historyPerLot(), assembler.holdCap(), assembler.alarmCap()))`
   6. `catch (BusinessException)` 原样抛；其它 → WARN（`packageNo` + `e`）+ `BusinessException("追溯包导出失败")`
d. Controller `export`：`.cacheControl(CacheControl.noStore())`。produces、权限、入参、组包不动
e. 清掉 FacadeImpl 内已搬迁的私有序列化逻辑，**禁止**两份真相；`ERR_FORMAT` 常量留在 Facade（对外错误码稳定）
f. 前端 `api/complaint.ts`：拼 `?format=${format}`；补 JSDoc 说明 zip 含 README
g. 前端抽屉：`downloading` 状态改三态；footer 增「下载 ZIP」按钮，`disabled={packageId == null}`，`loading={downloading === 'zip'}`（JSON 钮同款）；失败 toast 统一「下载失败」
h. 文档收尾（同会话）：接口设计 §3 表格 ZIP 行 → `✅ CP-6`、切片表 CP-6 行 → `✅`（依赖列写 CP-4）、§6.4 把「P1：…」改写为已落地并补 ZIP 内文件清单契约（含 K14 封面句）、头部「状态」行去「CP-6 ZIP 后置」；`MES-History已完成功能.md` 加「7. 导出（CP-4 / CP-6）」并把 §6 CP-6 行 ⏳→✅、frontmatter `slices` 加 `CP-6`；`MES-实施进度与下一步.md` 三处 ZIP 后置 → 已落地；`docs/INDEX.md` 重建：`python .workbuddy/scripts/add_frontmatter.py --reindex`（AGENTS.md §4 指定命令）

顺序：a → b → c → d → e →（f / g 可并行）→ h 收尾。**禁止**：Controller 内 zip、给 export 加事务、为 zip 另写一套 JSON 序列化、导出路径改 ObjectMapper 配置、Exporter 再查库、Exporter 注入 Assembler。

## 6. 测试与验收

- 验收标准（可判真假）：
  1. `format=zip` / `ZIP` / ` zip `（含空白）→ 200、`Content-Type: application/octet-stream`、`Cache-Control` 含 `no-store`、`Content-Disposition: attachment; filename="CP-20260922-N.zip"`
  2. 解压**恰好 2 个** entry：`{packageNo}.json` + `README.txt`；无目录层级、无中文名；两 entry 时间非 0 且年份为当年（**不做**与 `exportedAt` 的秒级相等断言——DOS 时间 2 秒粒度，K8）；**本地头与中央目录时间一致**（`unzip -l` 读中央目录，另用只读本地头的工具/流式读法取一次，两者应相同）——`setTimeLocal` 写在 `putNextEntry` 之后时此处**必然不一致**（K8 / F14）
  3. 同源不靠两次 HTTP 对拍全文：`toZipBytes` 内部调用 `toJsonBytes` / `buildJsonNode`，json 分支也只调 `toJsonBytes`（代码阅读）。跨请求只比字段集合：两次响应 `jq -S 'keys'` 一致。履历正文允许因现查漂移而不同
  4. `README.txt` 含包号 / 锚点 / direction / depth / 成员数 / 影响面已截断 / 每 Lot 履历上限 / 每 Lot Hold 各状态上限 / 每 Lot 未关闭告警上限 / status / 生成人时间 / 导出人时间 / 文件清单 / 空块说明 / `CONTAINING` 非结案句 / 口径句；UTF-8 中文不乱码。导出时间字符串与 JSON 内 `exportedAt` 同形。备注中的换行已变成空格，该行不跨行
  5. `format=pdf` / `format=xml` → `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED`，`Content-Type: application/json`，body 为 `R<>`，**零 zip 字节产生**，文案不是「追溯包导出失败」
  6. `enabled=false` → `COMPLAINT_PACKAGE_DISABLED`；`id` 不存在 → `COMPLAINT_PACKAGE_NOT_FOUND`；`format=pdf&id=不存在` → **FORMAT 错**（K3 顺序）
  7. 空装配包（无成员履历 / 无 Hold / 无 Alarm）与 VOID 包仍可导出，entry 数仍 2；封面仍带空块说明
  8. `format=json` 零回归：根对象仍含 `exportedAt` / `exportedBy`，字段与 CP-4 一致；响应同样 `no-store`
  9. `server/pom.xml` diff 为空（零新依赖）。ZIP 只用 `java.util.zip`（`ZipOutputStream` / `ZipEntry`）。`@Slf4j`、`ByteArrayOutputStream`、`StandardCharsets`、`LocalDateTime`、Jackson、VO 允许
  10. Exporter 源码无 `com.mes.*.mapper`、无 Mapper 字段、无 `ComplaintPackageAssembler`。`ObjectMapper` 允许。`ComplaintPackageExporter` 的生产代码引用只出现在自身与 `ComplaintPackageFacadeImpl`
  11. 前端：两按钮 loading 互不串扰；分别落地 `CP-….json` / `CP-….zip`；失败有 toast
  12. `exportFile` 无 `@Transactional`；Exporter 内无二次 `get` / Mapper 调用
  13. K10 的 WARN 用代码阅读判定，**不改** 10MB 常量：`toJsonBytes` 比较的是 JSON 字节长度，日志字段为 packageNo + bytes + memberCount。`historyPerLot()` 是唯一钳制（`loadHistories` 内无第二份 `< 1 ? 100`）。`ComplaintPackageFacadeImpl` 内零 `@Value("${mes.complaint-package.history-per-lot`。`holdCap()` / `alarmCap()` 返回 `HOLD_CAP` / `ALARM_CAP`
- 验证方式：
  - 头与产物：`curl -sD - -o /tmp/cp.zip "$HOST/complaint-packages/{id}/export?format=zip" -H "Authorization: Bearer …"` → 看 `Cache-Control`；`unzip -l /tmp/cp.zip`；`unzip -p /tmp/cp.zip README.txt`
  - 字段集合：ZIP 内 JSON 与另一次 `format=json` 各做 `jq -S 'keys'`，集合一致即可；不对拍履历全文
  - 失败分流：`curl -s …?format=pdf` → 看 `Content-Type` 与 body `code`（FORMAT，非「追溯包导出失败」）；`format=zip` 且开关关 → 同为 JSON 错误体
  - 页面：抽屉两按钮各点一次，核对落地后缀与内容
  - **项目无 `src/test`**（Doc-4 后置）→ 禁写「单测」当手段，一律 curl + SQL + 页面 + 调用点阅读

## 7. 回滚方式

纯读路径，无 DDL / 无状态写：① 代码回滚即恢复 CP-4 口径（`format=zip` 回落 `FORMAT_UNSUPPORTED`，前端按钮随之回退，`no-store` 一并撤回）；② `mes.complaint-package.enabled=false` 整体下线导出入口。**无数据需要回退**，无需数据清理。

## 8. 遗留（本切片不做）

| 项 | 现状 | 触发条件 | 方案 |
|----|------|----------|------|
| PDF / HTML 封面 | `README.txt` 代封面 | 客户要求盖章版式或 PDF 交付件 | 评估 openhtmltopdf / 前端打印转 PDF（需引依赖，另开切片） |
| 流式导出 | 内存 `byte[]`（§14） | 成员 200 × 履历 100 时内存 / 网关超时 | Exporter 改边装边写 + `StreamingResponseBody`；只把已有 `byte[]` 塞进流式不算解决 |
| 导出留痕 | 仅 WARN 日志 | 审计要「谁何时导了哪包」 | 加 `mes_complaint_package_export` 表 + 查询 |
| contain 结果进导出 | 结果仅界面（CP-5 遗留同款） | 8D 附件需遏制记录 | export VO 增 `containResult` |
| 大包限流 / 分卷 | 单文件，10MB 仅 WARN；无信号量 | 超门户附件上限或并发把堆打满 | 分卷、外链，或有界并发（需新错误码，本切片不加） |
| 作废流程 | export 不拦 VOID | 真做作废 | 加作废 API，export 一并拦 |
| README 兼容老记事本 | UTF-8 无 BOM（F9） | 客户用旧版记事本打开乱码 | 评估加 BOM（会破坏「纯 UTF-8」一致性，需客户确认） |
| 装配降级进 JSON | 封面只有说明句，JSON 不加键 | 审计要求机器可读的失败位 | VO 增降级标记后再导出（另开切片，本切片 K4 禁止新键） |

## 审查记录（绑定实现，禁止回退）

| # | 约束 |
|---|------|
| C1 | format 校验先于 get，错误码不被 404 抢（K3） |
| C2 | JSON 与 ZIP 内 JSON 同源、同 ObjectMapper；`now()` 与 `resolveCreateBy()` 各一次；导出路径不改 ObjectMapper 配置（K4 / K5） |
| C3 | entry 扁平 ASCII + UTF-8；文件名只在 Exporter；`close()` 之后再 `toByteArray()`；先 `setTimeLocal(exportedAt)` 再 `putNextEntry`（K6 / K8） |
| C4 | Controller 只加 `no-store`；禁在 Controller / 前端拼包（K1 / K7 / D4） |
| C5 | 零 DDL / 零新错误码 / 零新配置 / 零新依赖；10MB 只 WARN，步骤里要有（K2 / K10） |
| C6 | 禁引 PDF 依赖；封面走 README.txt（D1） |
| C7 | Exporter 零 Mapper、零 Facade、零 Assembler、不读 yml；上限只走入参；只许 FacadeImpl 调用（K1 / K9） |
| C8 | VOID 不拦，保持 §6.4 P0 口径（作废后置） |
| C9 | `format=json` 行为逐字保持，只搬类不换形 |
| C10 | 失败翻译只在 Facade 的 try 内；try 不包住 `get` / format；`BusinessException` 原样抛（K12） |
| C11 | 压缩在 `get()` 之后；不加事务、不占 token、不加包行锁（K2 / K13） |
| C12 | 封面：备注单行、三个上限入参、空块说明、`CONTAINING` 非结案句；人员只印 id（K14） |
| C13 | 跨请求验收只比字段集合，不对拍履历全文 |
| C14 | 履历上限单一来源：`historyPerLot()` 返回生效值，`loadHistories` 改调它；FacadeImpl 零新增 `@Value`（K15 / D7） |
| C15 | README 成员数取 `vo.getMemberCount()`，不用包头快照心智（D8） |
| C16 | 分支判定用归一后的 `normalized`，禁用原参 `format`（K3 / 步骤 c.5） |
| F1 | **实测**：`ZipEntry.setTimeLocal` JDK 21 可用；DOS 时间 2 秒粒度（设 `10:48:17` → 读 `10:48:16`）→ 验收禁做秒级相等断言；但 `setTimeLocal` 仍优于 `setTime(epoch)`（后者按 JVM 默认时区折算，会出现第二个时钟） |
| F2 | **对码**：`JacksonConfig` 只定制 `Long` → `ToStringSerializer`；`LocalDateTime` 走 Spring Boot 默认 JavaTimeModule（ISO 文本）→ K5「README 文本 = JSON 文本」成立；附带事实：JSON 里 `exportedBy` 是**字符串**（Long 定制生效），README 印 id 字符串与之一致 |
| F3 | `ComplaintPackageAssembler` 现网已绑定 `history-per-lot` 且已注入 FacadeImpl → 改访问器取用，删掉原计划的第二处 `@Value`。三轮补正：访问器返回生效值，`loadHistories` 改调它（K15 / D7） |
| F4 | README「成员数」定标 `vo.getMemberCount()`：`assemble` 成员取自 member 表（生成时落库），不随他处 Split 漂；VO 无包头 `member_count` 字段（D8） |
| F5 | 内存峰值：字节数组侧 ZIP 约 3N（json 字节 + zip 缓冲 + `toByteArray` 拷贝）。三轮补正：VO 与 ObjectNode 在拷贝前仍活着；JSON 路径无 `toByteArray`。并发预算 = 容器线程 ×（VO + 节点树 + 3N）（K10） |
| F6 | Exporter 必须带 `@Slf4j`，否则 K10 的 WARN 无处落（步骤 a 已补） |
| F7 | Facade 分支必须用归一值（`" ZIP "` 是最易写错的路径），已在步骤 c.5 显式写死 |
| F8 | K10 的 WARN 用代码阅读判定（比较 JSON 长度，日志含 packageNo + bytes + memberCount），不改 10MB 常量（验收 13） |
| F9 | README 保持 UTF-8 **无 BOM**（与 JSON / 现网口径一致）；老 Windows 记事本兼容诉求进遗留，不在本切片加 BOM |
| F10 | `historyPerLot < 1` 时查询用 100。访问器若只返回字段，封面会印 0。`historyPerLot()` 返回生效值，`loadHistories` 去掉第二份钳制（K15） |
| F11 | `putNextEntry` 在 `xdostime == -1` 时先写当前时间。`setTimeLocal` 必须在 `putNextEntry` 之前（K8） |
| F12 | Hold 每状态 20、告警 20 是 Assembler 常量。封面另写数字会分叉。`holdCap()` / `alarmCap()` 返回常量，Facade 传入，Exporter 不依赖 Assembler（K9 / K14 / D9） |
| F13 | **实测（javap 反编译 jackson-databind 2.13.4.1）**：`writeValueAsBytes` 内部 `new ByteArrayBuilder` → `_writeValueAndClose` → `toByteArray()`（字节码为 `newarray` + 逐块拷贝）→ **JSON 路径字节侧 ≈ 2N**，不是 1N；原 K10「JSON 无 `toByteArray`」只对我方代码成立，对 Jackson 内部不成立。K10 已改为「JSON 2N / ZIP 3N，预算按 3N 取」 |
| F14 | **实测**：`setTimeLocal` 晚于 `putNextEntry` → 本地头写当前时间、中央目录写设定值，**同一 entry 两个头时间不一致**（`late.txt` 本地头 `2026-09-22T11:03:50` / 中央目录 `2000-01-01T00:00`；先 set 的 `early.txt` 两头一致）。K8 强制次序，验收 2 增加两头一致性检查 |
| F15 | **对码**（现网 `ComplaintPackageAssembler:171`）：`int cap = historyPerLot < 1 ? 100 : historyPerLot;` 确实存在，且下游是 `historyFacade.listByLots(keySet, cap)`（一次 IN + 窗口函数，cap 为唯一入口）→ K15「访问器返回生效值 + 删方法内钳制」成立，且访问器与查询必然同值；封面印的值 = 实际查询用的值 |

---

**批准记录**：**2026-09-22 用户批准**（经四轮审查：一轮 K12–K14 / D4–D6；二轮对码 + JDK 21 实测 K15 / D7 / D8 / F1–F9；三轮生效值钳制 + `setTimeLocal` 时序 + 堆峰值 + Hold/告警上限入参 D9 / F10–F12；四轮 K8 两头不一致 + 验收 2 两头一致性 + K10 字节侧 2N 修正 F13–F15）。D1–D9 决策、K1–K15 约束、C1–C16 审查项全量生效。批准后按 §5 顺序 a→h 实施；**实现偏离本 plan 时先改 plan 再改码**。

---

## 实施记录（2026-09-22 完成）

**变更文件（7）**

| 文件 | 变更 |
|------|------|
| `support/ComplaintPackageExporter.java`（新增） | JSON 节点 / README / ZIP 打包 / 文件名；只注入 `ObjectMapper`；先 `setTimeLocal` 再 `putNextEntry` |
| `support/ComplaintPackageAssembler.java` | 新增 `historyPerLot()`（生效值）/ `holdCap()` / `alarmCap()`；`loadHistories` 改调访问器并删除方法内钳制（K15） |
| `facade/impl/ComplaintPackageFacadeImpl.java` | `assertFormat` 归一白名单 json/zip；`exportFile` 按归一值分支；try 只包 Exporter、异常单点翻译；移除自带 `ObjectMapper` / `ObjectNode` 序列化 |
| `controller/MesComplaintPackageController.java` | `export` 加 `CacheControl.noStore()`（其余不动） |
| `web/src/api/complaint.ts` | `exportComplaintPackageApi(id, fileName?, format = 'json')` |
| `web/src/components/lot/ComplaintPackageDrawer.tsx` | `downloading: 'json' \| 'zip' \| null`；footer「下载 JSON」/「下载 ZIP」双按钮 |
| 文档 | 接口设计 §3/§6.4/§9/§14 + 头部状态；已完成功能（slices 加 CP-6、§7 导出、§6 CP-6 ✅）；进度文档三处；`docs/INDEX.md` 重建 |

**已验证（可复现）**

- 后端编译：`mvn -o -DskipTests compile` → EXIT=0（零新依赖，`pom.xml` diff 为空 = 验收 9）
- 前端：`npm run build`（`tsc -b` + `vite build`）→ 通过（验收 11 的编译面）
- 静态核对：Exporter 无 `com.mes.*.mapper` / Mapper 字段 / Assembler / Facade（验收 10）；`exportFile` 无 `@Transactional`（验收 12）；`history-per-lot` 的 `@Value` 全仓仅 Assembler 一处（验收 13）；Exporter 生产代码引用只出现在自身与 FacadeImpl
- **一次性探针实测（真 Spring Boot 容器 ObjectMapper + 真 Exporter）12 项全 PASS**：entry 恰 2 个且扁平 ASCII；**本地头 == 中央目录时间**（K8/F14）；ZIP 内 JSON 与 `toJsonBytes` **逐字节相同**（同源，比验收 3 的「代码阅读」更硬）；README 20 项行项齐（含上限三行 / 空块说明 / 口径句）；备注换行折成空格；**导出时间 / 生成时间与 JSON 同值同形**（K5）；`exportedBy` / `packageId` 为字符串（F2）
- 探针件：`.workbuddy/tmp/cp6-probe/`（未入库；`Cp6Probe.java` 复用方式见文件头）

**未执行（环境阻塞，待补）**

- 验收 1 / 5 / 6 / 7 / 11 的真机 `curl`：本机 MySQL（3306）在，但 dev Redis `192.168.187.128:6379` 不可达 → 服务起不来，且需登录态。Redis 恢复后按 §6 验证方式执行：`format=zip|ZIP| zip ` 头与文件名、`format=pdf` → `COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED`（JSON 错误体、零 zip 字节）、开关关 → `DISABLED`、`format=pdf&id=不存在` → FORMAT 而非 404、空装配包/VOID 包仍出 2 entry、页面两按钮落地后缀

**与 plan 的偏离**：无功能偏离。实现期加了一处防御（不在 K 约束内的加固）：`scalar()` 遇到非标量节点时回退 `node.toString()`，避免容器若被配置成 `WRITE_DATES_AS_TIMESTAMPS=true` 时封面时间/人员静默变 `-`；实测容器默认关闭该 feature，正常走标量分支（K5 仍成立：文本来自同一 `ObjectMapper`）。

### 验收补做（2026-09-22 15:24，Redis 恢复后，真机 HTTP）

前置：dev Redis `192.168.187.128:6379` 已可达、后端在 `127.0.0.1:8080` 运行、登录 `admin`（文档登记的 dev 账号）取 token。探针件：`.workbuddy/tmp/cp6-probe/http_acceptance.py`（可重跑，自登录取 token）。

**结果：26 项断言全 PASS**（4 个存量包的导出各出 2 entry）：

| 验收 | 结论 |
|------|------|
| 1 | `format=zip` / `ZIP` / `%20zip%20` 均 200 + `PK` 魔数；`Content-Type: application/octet-stream`；`Cache-Control: no-store`；`Content-Disposition: attachment; filename="CP-20260922-1.zip"` |
| 2 | 恰 2 个 entry（`CP-20260922-1.json` + `README.txt`，扁平 ASCII）；**本地头 == 中央目录时间**（15:24:36 两处一致）；entry 时间非 0 且 2026 年 |
| 3 | ZIP 内 JSON 与独立 `format=json` **除 `exportedAt`/`exportedBy` 外逐字段一致**（`diff=[]`）、keys 集合相同 |
| 4 | README 20 项行项齐（含三行上限 / 空块说明 / `CONTAINING` 非结案句 / 口径句）；导出时间字符串与 JSON `exportedAt` 同值同形；导出人为字符串 id |
| 5 | `format=pdf` → `application/json` + `{"code":500,"msg":"COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED: 不支持的导出格式"}`，零 zip 字节 |
| 6 | `format=pdf&id=不存在` → **FORMAT 而非 NOT_FOUND**（K3 顺序成立）；`id=不存在` → `COMPLAINT_PACKAGE_NOT_FOUND` |
| 7 | 存量 4 包（READY / CONTAINED / READY / CONTAINED，各 2 成员）全部 200 且恰 2 entry |
| 8 | `format=json` 与空 `format` 口径不变：`filename="….json"` + `no-store`，根含 `exportedAt`/`exportedBy` |

**仍未执行（无法在不改配置/不重启实例的前提下验证）**：验收 6 的「`enabled=false` → `COMPLAINT_PACKAGE_DISABLED`」需改 `application.yml` 并重启（会打断用户正在跑的实例）→ 留待下次启动时顺带验证，或由用户手测。验收 11 的页面部分已由用户实测（并修出一处前端缺陷，见下）。

### 缺陷与口径登记（2026-09-22）

| # | 项 | 结论 |
|---|----|------|
| G1 | **前端并发下载（用户实测发现并修复）** | 抽屉拆双按钮后缺少重入保护：同时点「下载 JSON」「下载 ZIP」会并发下载，且先完成者的 `finally` 会清掉后者的 loading；抽屉重开（`packageId` 变更）后旧请求的 `finally` 还会清掉新会话状态。修法：`downloadingRef`（同步重入闸）+ `downloadSeq`（会话序号，旧请求 `finally` 不再改状态）+ 任一在途则两按钮同时 disabled。已过 `tsc -b`。责任在 CP-6 前端实现（原 CP-4 单按钮不暴露此问题） |
| G2 | **错误码承载口径写偏（文档修正）** | 计划/接口设计原写「失败走全局异常，看 body `code`」。实测现网 `GlobalExceptionHandler` → `R.fail(e.getCode(), …)`，`BusinessException` 的 code 为 `500`，**业务码在 `msg` 前缀**（`"COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED: 不支持的导出格式"`）。已修正接口设计 §6.4 措辞；前端取 `msg` 展示，与其它模块一致 |
| G3 | 验收探针自身两处误判（非产品问题） | ① 本地头 DOS 时间秒字段是 `seconds/2`，未 ×2 直接与 python `date_time` 比对 → 误报不一致；② 业务码断言取了 `code` 而非 `msg` → 误报 5 项 FAIL。均已修正，重跑全 PASS |

**遗留（未做，可另开切片）**：`EVAL` 尚未启用（`docs/eval/` 仅有 README）——G1 这类「验收期发现的前端缺陷」是否开始走 EVAL 流程，待用户定。
