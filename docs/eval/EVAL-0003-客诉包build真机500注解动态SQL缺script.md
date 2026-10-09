---
type: eval
module: Test
status: done
slices: [TD-1]
aligns: [TD-1-plan.md, MES-封测测试数据与Bin回流方案.md]
updated: 2026-10-09
---

# EVAL-0003 客诉包 build 真机 500：注解动态 SQL 缺 `<script>`

> **性质：验收期缺陷**（TD-1 步骤 f 之后、R9 真机验收阶段发现；未上线）。
> 发现途径：**R9 真机 curl 验收**——这正是单测盲区的兜底（见「为何漏测」）。

## 1. 现象

`POST /complaint-packages`（锚定已登记测试记录的批次）返回 `code=500 / 系统异常`。
同一路径在步骤 d 之前的验收中从未打过——三块装配（`testSummaryByLot` 等）是 TD-1 新增，`TestFacade.listRecordsByLots` 首次进入客诉包调用链。

## 2. 根因

`com/mes/test/mapper/MesTestRecordMapper.java` 的 `selectRecentByLots` 使用注解 `@Select` 写动态 SQL（含 `<foreach>`），**但缺 `<script>` 包裹**。MyBatis 注解方式下，动态标签必须以 `<script>` 开头才被解析为动态 SQL，否则整体当纯文本 → 生成非法 SQL → `BadSqlGrammarException` → `GlobalExceptionHandler` 兜底为「系统异常」。

对照：同切片的 `MesLotStripMapper.selectByLots` / `MesLotCustomerMapMapper.selectByLots` 均正确带 `<script>`（且 `<=` 转义为 `&lt;=`），唯 `MesTestRecordMapper` 漏掉。

修复：补 `<script>` 包裹 + `rn <= #{limit}` 转义为 `rn &lt;= #{limit}`（与另两个 mapper 同款）。修复后 8082 第二实例真机验证：build 200、GET 三键齐全、ZIP 导出 README 四行上限 PASS。

## 3. 为何漏测（根因分析）

| 环节 | 为什么没拦住 |
|------|--------------|
| 零依赖单测（f 步骤 11 例） | `MesTestRecordServiceImplTest` **mock 了 mapper**——V6 用例验证的是「DuplicateKey 异常转换」等 service 逻辑，`selectRecentByLots` 的真 SQL 从未执行 |
| R8 反向验证 | 改坏的是 service 层校验逻辑（对账 / 守卫调用），仍在 mock 边界内 |
| d 步骤 ComplaintPackage*Test | Assembler 同样 mock 依赖，装配 SQL 不真跑 |
| **R9 真机验收** | **唯一跑到真 SQL 的环节 → 抓住**（这正是 plan 验收方式里「真机 curl + SQL 复核」存在的意义） |

## 4. 防复发回流点（本 EVAL 的闭环物）

1. **`AGENTS.md` §5 常见错误新增**：「注解 `@Select/@Insert` 写动态 SQL（`<if>/<foreach>/<where>`）必须 `<script>` 包裹；`<=` 转义 `&lt;=`」——AI 会话开工即可见（已落）。
2. **回归口径**：涉及**新写 mapper SQL** 的切片，验收必须包含至少一次真机/真容器调用（mock 单测不算 SQL 验收）——已写入 `TD-1-plan.md` R9 验收轮。
3. 测试基建后置项（不阻塞本条闭环）：`src/test` 增加一条**真 SQL 冒烟**路径（H2 或 @MybatisTest）属于 Doc-4 回归网增强，见 §12 遗留。

## 5. 遗留

- 真机验收仍未覆盖：权限 403（无低权账号凭据）、拆批 D18 真机场景（需 split 数据）——登记于 plan R9，后续补打。
- `AGENTS.md` §5 本次新增一条（回流动作），无其他代码外改动。
