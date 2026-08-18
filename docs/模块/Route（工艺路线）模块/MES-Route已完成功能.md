# MES 工艺路线（Route）— 功能查验清单

> 更新：2026-07-30  
> 状态：**一期前后端已落地**；Lot Release 绑版本 ✅；Track 过站按快照校验 ✅  
> 范围：工序、路线/版本、草稿步骤编辑、发布/升版、权限种子、前端路线页、Track 查询契约  
> 需求依据：`MES-Route功能文档.md` · `MES-Route数据库设计.md`  
> 二期清单：`MES-Route二期功能清单.md`

图例：✅ 已完成 · ⏳ 未做 · — 不适用

---

## 0. 完成度总览

| 能力 | 后端 | 前端 | 权限码 |
|------|------|------|--------|
| 工序列表 | ✅ `GET /routes/steps` | ✅ 工序库抽屉 | `route:list` |
| 新增工序 | ✅ `POST /routes/steps` | ✅ | `route:add` |
| 编辑工序 | ✅ `PUT /routes/steps/{id}` | ✅ | `route:edit` |
| 路线分页列表 | ✅ `GET /routes` | ✅ RoutePage | `route:list` |
| 新建路线（含 draft v1） | ✅ `POST /routes` | ✅ | `route:add` |
| 路线详情 | ✅ `GET /routes/{id}` | ✅ 维护抽屉 | `route:list` |
| 版本列表 | ✅ `GET /routes/{id}/versions` | ✅ 版本切换 | `route:list` |
| 版本详情 + 有序步骤 | ✅ `GET /routes/versions/{versionId}` | ✅ | `route:list` |
| 覆盖保存草稿步骤 | ✅ `PUT /routes/versions/{versionId}/steps` | ✅ | `route:edit` |
| 发布版本 | ✅ `POST /routes/versions/{versionId}/publish` | ✅ | `route:edit` |
| 升版（复制为新 draft） | ✅ `POST /routes/{id}/versions` | ✅ | `route:add` |
| 表结构 mes_step/route/version/step | ✅ `migrate_route.sql` / `schema.sql` | — | — |
| 权限码 + 菜单种子 | ✅ `route:list/add/edit` | ✅ 侧栏 `/app/route` | 见下 |
| 演示种子 WAFER-N7-MAIN | ✅ | ✅ 列表可见 | — |
| Lot Release 绑版本快照 | ✅ Lot `POST /lots/{id}/release` | ✅ 放行 | `lot:release` |
| Track 按版本校验下一站 | ✅ TrackOut 只认 `route_version_id` + `next_sort_no` | ✅ 现场台 | — |

统一响应：`{ code, msg, data }`；成功 `code=200`。  
Token：`Authorization: Bearer {token}`（Sa-Token）。

---

## 1. 工序接口

### 1.1 列表 `GET /routes/steps`（登录 + `route:list`）

查询：`keyword` / `status` / `page` / `size`

成功 `data`：分页 records，字段含 `id, stepCode, stepName, stepType, status, eqpType?`

### 1.2 新增 `POST /routes/steps`（登录 + `route:add`）

```json
{ "stepCode": "PHOTO-01", "stepName": "光刻", "stepType": 1 }
```

规则：`step_code` 唯一；`step_type`：1加工 / 2量测 / 3其它。

### 1.3 编辑 `PUT /routes/steps/{id}`（登录 + `route:edit`）

```json
{ "stepName": "光刻", "stepType": 1, "status": 1 }
```

规则：编码不可改；禁用后不可挂到**新**草稿步骤。

---

## 2. 路线 / 版本接口

### 2.1 列表 `GET /routes`（登录 + `route:list`）

查询：`keyword` / `page` / `size`  
返回：路线头 + 当前 `active` 版本号（无则 null）。

### 2.2 新建 `POST /routes`（登录 + `route:add`）

```json
{
  "routeCode": "WAFER-N7-MAIN",
  "routeName": "晶圆主工艺",
  "productCode": "N7",
  "remark": ""
}
```

落库：`mes_route` + `mes_route_version`（`version_no=1`, `status=draft`），步骤为空。

### 2.3 详情 `GET /routes/{id}`（登录 + `route:list`）

路线头 + 当前生效版本摘要（versionId / versionNo / status）。

### 2.4 版本列表 `GET /routes/{id}/versions`（登录 + `route:list`）

该路线全部版本：`versionNo, status, publishedAt, publishedBy`。

### 2.5 版本详情 `GET /routes/versions/{versionId}`（登录 + `route:list`）

**Track 主依赖。** 返回有序 steps：

```json
{
  "id": "...",
  "routeId": "...",
  "versionNo": 1,
  "status": "active",
  "steps": [
    {
      "id": "...",
      "stepId": "...",
      "stepCode": "PHOTO-01",
      "stepName": "光刻",
      "sortNo": 10,
      "nextSortNo": 20
    }
  ]
}
```

`nextSortNo = null` 表示结束。

### 2.6 保存草稿步骤 `PUT /routes/versions/{versionId}/steps`（登录 + `route:edit`）

```json
{
  "steps": [
    { "stepId": "1001", "sortNo": 10, "nextSortNo": 20 },
    { "stepId": "1002", "sortNo": 20, "nextSortNo": null }
  ]
}
```

规则：仅 `status=draft`；覆盖式保存；步骤引用的工序须 `status=1`。

### 2.7 发布 `POST /routes/versions/{versionId}/publish`（登录 + `route:edit`）

规则：
- 仅 draft 可发布
- 至少 1 步；`sort_no` 唯一；`next_sort_no` 指向存在序号或 null
- 同路线原 `active` → `archived`；本版本 → `active`
- 记 `published_at` / `published_by`；写操作审计（模块 Route）

### 2.8 升版 `POST /routes/{id}/versions`（登录 + `route:add`）

```json
{ "fromVersionId": "..." }
```

复制源版本步骤 → 新 draft，`version_no = max+1`。  
规则：同路线已存在 `draft` 时拒绝（「已有草稿版本，请先发布或继续编辑现有草稿」）。

---

## 3. 业务规则查验（实现后勾选）

| # | 规则 | 状态 |
|---|------|------|
| R1 | 同路线最多一个 `active` | ✅ |
| R1b | 同路线最多一个 `draft`（升版拦截） | ✅ |
| R2 | 仅 draft 可改步骤；active/archived 只读 | ✅ |
| R3 | 发布后不可原地改，变更必须升版 | ✅ |
| R4 | Lot Release 绑当时 active 的 `route_version_id`（Lot 模块） | ✅ |
| R5 | 在途 Lot 不受新发布版本影响（靠快照，Track 执行时生效） | ✅ |
| R6 | Route 不写 Lot 当前站；状态真相在 Track | ✅（约定） |
| R7 | 无权限返回 403 | ✅ |

---

## 4. 前端对接

| 能力 | 文件 | 状态 |
|------|------|------|
| 路线页 | `web/src/pages/RoutePage.tsx` | ✅ |
| Route API | `web/src/api/route.ts` | ✅ |
| 版本状态 Pill | `StatusPill.RouteVersionPill` | ✅ |
| 侧栏菜单 `/app/route` | 权限树 + AdminShell | ✅ |
| Toast / Confirm | 复用现有 | ✅ |

页面一期能力：
- ✅ 路线列表 + 新建
- ✅ 版本切换（草稿 / 生效 / 归档）
- ✅ 草稿表格维护步骤 + 保存（线性自动下一站）
- ✅ 发布 / 升版
- ✅ 生效/归档只读
- ✅ 工序库抽屉

---

## 5. 后端关键文件

| 文件 / 包 | 作用 | 状态 |
|-----------|------|------|
| `com.mes.route.controller` | MesStepController / MesRouteController | ✅ |
| `com.mes.route.service.impl` | MesStepServiceImpl / MesRouteServiceImpl | ✅ |
| `com.mes.route.entity` | MesStep / MesRoute / MesRouteVersion / MesRouteStep | ✅ |
| `migrate_route.sql` / `schema.sql` | DDL + 种子 | ✅ |
| 权限种子 `route:list/add/edit` | sys_permission + 角色绑定 | ✅ |
| DataInitializer / 演示数据 | WAFER-N7-MAIN | ✅ SQL 种子 |

---

## 6. 手工查验清单（实现后执行）

### 6.1 工序

1. 新增未占用编码 → 200，列表可见  
2. 重复编码 → 业务失败  
3. 无 `route:add` → 403  

### 6.2 路线发布

1. 新建路线 → 自动 draft v1、步骤空  
2. 保存线性步骤 → 再发布 → 该版本 active  
3. 同路线再发另一 draft → 原 active 变 archived  
4. 改 active 步骤 → 应拒绝  

### 6.3 升版

1. 基于 active 升版 → 新 draft，步骤内容一致  
2. 已有 draft 时再升版 → 业务失败  
3. 编辑 draft 后发布 → 新版 active，旧版 archived  

### 6.4 查询契约

1. `GET /routes/versions/{activeId}` 步骤顺序与库一致  
2. 无 Token → 401；无 `route:list` → 403  

### 6.5 curl 示例（落地后替换 TOKEN / ID）

```bash
# 工序列表
curl -s "http://localhost:8080/routes/steps?page=1&size=20" \
  -H "Authorization: Bearer TOKEN"

# 新建路线
curl -s -X POST http://localhost:8080/routes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"routeCode":"WAFER-N7-MAIN","routeName":"晶圆主工艺"}'

# 版本详情
curl -s "http://localhost:8080/routes/versions/VERSION_ID" \
  -H "Authorization: Bearer TOKEN"

# 发布
curl -s -X POST "http://localhost:8080/routes/versions/VERSION_ID/publish" \
  -H "Authorization: Bearer TOKEN"
```

前端经 Vite 代理时路径加前缀 `/api`。

---

## 7. 一期已闭环 / 二期待做

### 7.1 一期已闭环（原「已知限制」中已落地项）

- [x] Lot Release 绑 `route_version_id`（Lot 模块）
- [x] Track 过站按快照校验下一站（功能文档 §4.3）

### 7.2 二期待做（详见 `MES-Route二期功能清单.md`）

| 优先级 | 项 | 状态 |
|--------|----|------|
| P0 | Rework 回流 + 次数上限 | ✅ 已落地（`MES-Rework接口设计.md` / `migrate_rework.sql`） |
| P0 | 条件/可选分支（边表） | ✅ 已落地（`MES-Branch接口设计.md` / `migrate_branch.sql`） |
| P0 | Step `eqp_type` 强校验 | ✅ 已落地（`MES-Step站属性接口设计.md` / `migrate_step_attrs.sql`） |
| P1 | Skip 规则白名单 | ✅ 已落地（`MES-Skip接口设计.md` / `migrate_skip.sql`） |
| P1 | Temporary Off-Flow | ✅ 已落地（`MES-OffFlow接口设计.md` / `migrate_off_flow.sql` / `migrate_off_flow_count.sql`） |
| P1 | Future Hold（Hold 主责） | ✅ P0 闭环（`MES-FutureHold接口设计.md`）；FH-4/FH-5 后置不做 |
| P1 | Queue Time | ✅ 后端+现场台倒计时+Route边配置；到期自动 Hold+清窗（见 `MES-QueueTime接口设计.md`） |
| P2 | 层级子流程 / 并行站 / 图形编辑器 | ⏳ |
| 后置 | Product 主数据 CRUD（现 `product_code` 字符串） | ⏳ |
| 不做 | Recipe / Reticle 进 Route 快照（继续 Facade 运行时解析） | — |

切片顺序：边表 → Rework → 分支 → eqp_type → Skip → Off-Flow → Future Hold（✅ P0）→ Queue Time → 子流程/画布。

---

## 8. 关联文档

- `docs/模块/Route（工艺路线）模块/MES-Route功能文档.md`
- `docs/模块/Route（工艺路线）模块/MES-Route数据库设计.md`
- `docs/模块/Route（工艺路线）模块/MES-Route二期功能清单.md`
- `docs/模块/Route（工艺路线）模块/MES-Rework接口设计.md`
- `docs/模块/Route（工艺路线）模块/MES-OffFlow接口设计.md`
- `docs/架构/半导MES架构设计.md` §5.3
- `docs/业务清单/MES-半导体业务清单.md` §2
- 对照写法：`docs/模块/权限、用户模块/MES-用户权限已完成功能.md`
