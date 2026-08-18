# MES 工艺路线（Route）功能文档

> 定位：工艺「定义」——有序工序清单与版本；执行由 Track 负责  
> 对齐：`docs/架构/半导MES架构设计.md` §5.3、`docs/业务清单/MES-半导体业务清单.md` §2  
> 一期状态：**前后端已落地**  
> 更新：2026-07-24

---

## 1. 目标

- 维护可复用工序（Step）与工艺路线（Route）主数据
- 路线带版本：草稿 → 发布生效 → 归档；生效版不可原地改
- Lot 放行时绑定 Route **版本快照**，在途不受新版影响
- 为 Track 提供「当前步 → 合法下一站」校验依据
- 一期只做**线性主路径**；复杂分支 / 并行 / Recipe 绑定后置

---

## 2. 角色与职责

| 角色 | 说明 |
|------|------|
| 工艺工程师（`process_eng`） | 维护 Step / Route、编辑步骤、发布版本 |
| 超级管理员 | 全量配置 |
| 现场操作 / 班组长 | 只读查看生效路线（过站用 Track，不改 Route） |

权限码：

| 权限码 | 用途 |
|--------|------|
| `route:list` | 路线 / 工序查询 |
| `route:add` | 新建路线 / 工序 / 升版草稿 |
| `route:edit` | 编辑草稿步骤、发布、归档 |
| `route:publish` | 发布生效（可与 edit 合并，一期用 `route:edit`） |

一期实现：`route:list` / `route:add` / `route:edit` 即可。

---

## 3. 概念说明

| 概念 | 说明 |
|------|------|
| Step（工序） | 可复用工序定义：编码、名称、类型（加工/量测等） |
| Route（路线） | 某产品（或产品族）的工艺流程壳，含多个版本 |
| RouteVersion | 路线的一个版本：草稿 / 生效 / 归档 |
| RouteStep | 版本内有序节点：引用 Step + 序号 + 下一站 |

业界别名：Process Flow / Traveler / Run Card —— 含义相同：有序步骤清单。

边界：

```
Route  = 工艺「定义」（能走哪些站、顺序）
Track  = 工艺「执行」（当前走到哪）
WIP    = 在制「投影」（只读，不另存路线真相）
```

**禁止**：WIP / Lot 各自维护一套可编辑工序顺序，与 Route 版本冲突。

---

## 4. 一期功能清单（MVP）

### 4.1 工序（Step）

| 功能 | 说明 | 权限 |
|------|------|------|
| 工序列表 | 编码 / 名称 / 类型 / 状态 | `route:list` |
| 新增工序 | 编码唯一；名称；类型 | `route:add` |
| 编辑工序 | 改名称、类型、状态；编码不可改 | `route:edit` |
| 禁用工序 | 禁用后不可再挂到新草稿；已挂历史版本保留 | `route:edit` |

工序类型（一期枚举）：

| 值 | 含义 |
|----|------|
| `1` | 加工 |
| `2` | 量测 |
| `3` | 其它 |

### 4.2 路线与版本

| 功能 | 说明 | 权限 |
|------|------|------|
| 路线列表 | 编码 / 名称 / 当前生效版本号 / 状态 | `route:list` |
| 新建路线 | 生成 Route + 首个草稿版本 `v1` | `route:add` |
| 版本列表 | 某路线下全部版本及状态 | `route:list` |
| 编辑草稿步骤 | 增删改有序 RouteStep（仅 `draft`） | `route:edit` |
| 发布 | 草稿 → 生效；原生效版 → 归档 | `route:edit` |
| 升版 | 基于某版本复制为新草稿（版本号 +1） | `route:add` |
| 路线详情 | 含步骤序列，供 UI / Track | `route:list` |

版本状态：

```
draft（草稿） → active（生效） → archived（归档）
```

规则：
- 同一 Route **最多一个** `active` 版本
- 同一 Route **最多一个** `draft` 版本（升版时若已有草稿则拒绝）
- 仅 `draft` 可改步骤；`active` / `archived` 只读
- 发布时校验：至少 1 个步骤；序号连续；`next` 指向合法后续或「结束」
- 删除：一期不做物理删；草稿可「作废」为归档且无 Lot 引用时可清（可选，非阻塞）

### 4.3 与 Lot / Track 的约定（接口契约）

| 场景 | 行为 |
|------|------|
| Lot 创建 | 可选预填目标 Route；未放行前可改 |
| Lot Release | **必须**绑定当时 `active` 的 `route_version_id`（快照）；之后不可改路线版本 |
| Track Move / TrackOut | 按快照中的 RouteStep 算下一站；禁止跳站（Skip 二期） |
| 查询在制 | WIP 展示当前 Step，数据来自 Track 投影，不改 Route |

Release 绑版本已在 Lot / Track 落地（`POST /track/release`，`POST /lots/{id}/release` 兼容）。  
Track 过站按 `route_version_id` 快照校验已落地。Lot 文档：`docs/模块/Lot（批次）模块/MES-Lot功能文档.md`；进度：`docs/架构/MES-实施进度与下一步.md`。

---

## 5. 一期不做

| 项 | 说明 |
|----|------|
| 复杂分支 / 并行站 | 仅线性主路径 |
| Rework 回流图 | 二期：回流站 + 次数上限 |
| Skip 规则配置 | 二期 + 强权限 |
| Step ↔ Recipe / Reticle | 属 Recipe / 光罩模块 |
| 设备组强校验 | 可预留 `eqp_type` 字段，一期 Track 可放宽 |
| Future Hold / Queue Time | P1：Future Hold P0 ✅；Queue Time ✅ 到期自动 Hold（见 `MES-QueueTime接口设计.md`） |
| 产品主数据完整 CRUD | 一期 Route 可用 `product_code` 字符串关联，Product 模块后补 |
| 图形化拖拽编辑器 | 一期表格维护即可（对齐现有 `RoutePage`） |

---

## 6. 页面入口

| 菜单 | 路径 | 权限 |
|------|------|------|
| 工艺路线 | `/app/route`（现有壳） | `route:list` |

页面能力（一期）：
- 路线列表 + 新建
- 进入某路线：版本 Tab（草稿/生效/历史）
- 草稿：表格维护步骤（工序、名称只读自 Step、下一站、排序）
- 发布 / 基于当前升版
- 生效/归档：只读

现场台：不开放 Route 配置。

---

## 7. 接口清单（MVP）

> 统一响应：`{ code, msg, data }`；需登录 + 权限注解  
> 路径前缀：后端 `/route/...` 或 `/system/routes`（实现时与包名统一）；前端经 `/api`

### 工序

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/routes/steps` | `route:list` | 分页：keyword / status / page / size |
| POST | `/routes/steps` | `route:add` | `{ stepCode, stepName, stepType }` |
| PUT | `/routes/steps/{id}` | `route:edit` | `{ stepName, stepType, status }` |

### 路线

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/routes` | `route:list` | 分页：keyword / page / size |
| POST | `/routes` | `route:add` | `{ routeCode, routeName, productCode?, remark? }` → 含 draft v1 |
| GET | `/routes/{id}` | `route:list` | 路线头 + 当前生效版本摘要 |
| GET | `/routes/{id}/versions` | `route:list` | 版本列表 |
| GET | `/routes/versions/{versionId}` | `route:list` | 版本详情 + 有序 steps（**Track 主依赖**） |
| PUT | `/routes/versions/{versionId}/steps` | `route:edit` | 覆盖保存草稿步骤列表 |
| POST | `/routes/versions/{versionId}/publish` | `route:edit` | 发布 |
| POST | `/routes/{id}/versions` | `route:add` | 升版：`{ fromVersionId }` → 新 draft |

保存步骤 body 示例：

```json
{
  "steps": [
    { "stepId": "1001", "sortNo": 10, "nextSortNo": 20 },
    { "stepId": "1002", "sortNo": 20, "nextSortNo": null }
  ]
}
```

`nextSortNo = null` 表示结束。

---

## 8. 关键业务流程

### 8.1 配置并发布一条路线

```
工艺工程师 → 新建工序（如 PHOTO-01）
           → 新建路线 WAFER-N7-MAIN（自动 draft v1）
           → 编辑步骤顺序
           → 发布 → v1=active
```

### 8.2 工艺变更（升版）

```
基于 v1 升版 → draft v2（复制步骤）
编辑 v2 → 发布
→ v2=active，v1=archived
在途 Lot 仍走各自快照的 versionId（多为 v1）
新 Release 的 Lot 绑 v2
```

### 8.3 Track 读路线（已落地）

```
TrackOut → 读 Lot.route_version_id
        → 查 mes_route_step（同版本 next_sort_no）
        → 按当前 RouteStep 取下一站
        → 非法则拒绝事务
```

---

## 9. 数据与审计约定

- 主键雪花 `BIGINT`；逻辑删除；时间 `DATETIME`
- 表设计见：`docs/模块/Route（工艺路线）模块/MES-Route数据库设计.md`
- 操作审计：发布 / 升版写入 `sys_oper_log`（模块 `Route`）
- 种子：可预置演示路线 `WAFER-N7-MAIN`（对齐前端 mock 工序名）

---

## 10. 验收要点（一期）

1. 新建路线自动带 draft v1；未发布不可被 Lot Release 选用  
2. 发布后步骤只读；再改必须升版  
3. 同路线仅一个 active  
4. `GET /routes/versions/{id}` 返回有序步骤，可供 Track 使用  
5. 无 `route:edit` 不能发布；无 `route:list` 不能进菜单  

---

## 11. 关联文档

- `docs/模块/Route（工艺路线）模块/MES-Route数据库设计.md`
- `docs/模块/Route（工艺路线）模块/MES-Route已完成功能.md`（查验清单）
- `docs/架构/半导MES架构设计.md`
- `docs/业务清单/MES-半导体业务清单.md`
- `docs/模块/权限、用户模块/MES-菜单权限方案.md`（侧栏「工艺路线」）
- Lot：`docs/模块/Lot（批次）模块/`（含 Release 绑版本）  
- Track：`docs/模块/Track（执行引擎）模块/MES-Track功能文档.md`（过站按快照校验）
