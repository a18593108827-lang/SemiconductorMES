# MES 配方（Recipe）功能文档

> 定位：工艺**参数版本**与 Step/Eqp **资格绑定**——回答「这站这机能不能用、用哪版配方」  
> 对齐：`docs/架构/半导MES架构设计.md` §5.6、§3.2；业界 RMS / SEMI E139（对象模型参考，不全量实现）  
> 一期状态：**最小集已落地（后端 + Admin 前端 + Dispatch/Track 钩子）**  
> 更新：2026-07-30  
> 查验：`MES-Recipe已完成功能.md`  
> 演进：内嵌可拆 → 独立 RMS（见 §9）

---

## 1. 目标（最小集）

- 维护 Recipe 主数据与版本（草稿 / 生效）
- 维护绑定：`Step(+eqpType) × Eqp` → 生效 RecipeVersion
- Dispatch 候选过滤：无资格不出机
- TrackIn 校验绑定；履历记 `recipeId` / `recipeVersion`
- 对外只暴露**解析 / 资格 / 版本查询**门面，禁止业务模块直查配方表

**产品一句话：** Recipe 回答「这批在这站上这台机，该用哪版参数」；不跟机台说话（Download 后置）。

---

## 2. 边界

```
Recipe    = 主数据 + 版本 + 绑定 + 解析门面（本期 MES 内嵌）
Route     = Step 定义；不内嵌配方 body
Equipment = 资源目录；资格绑定消费 eqpId / eqp_type
Dispatch  = 候选过滤调 RecipeFacade.assertQualified / listQualified
Track     = TrackIn 调 resolve + 写履历；不直连配方表
Adapter   = SECS 上下传 / 比对 ← 后置；未来可并入独立 RMS
RMS       = 独立配方系统（大厂常见）← 二期+ 可选拆出
```

**一期已做：**

| 能力 | 说明 |
|------|------|
| Recipe 主数据 | `recipe_code` 唯一；名称、启停 |
| 版本 | 草稿编辑 → 发布生效；同 recipe 仅一个 `active` |
| 绑定 | Step ×（Eqp 或 EqpType）→ RecipeVersion |
| 解析 | 上下文 → 生效版本；无绑定默认不拦 TrackIn |
| Dispatch 钩子 | `listQualifiedEqpIds` 过滤候选 |
| Track 钩子 | TrackIn `assertQualified` + resolve；`mes_tx_log` 记版本 |
| Admin 页 | `/app/recipe` 配方 Tab + 绑定 Tab |
| 配置 | `mes.recipe.require-binding`（默认 false） |

**一期不做（后置）：**

- SECS Download / Upload / Compare
- Chamber 级配方、子配方模块化
- APC 动态参数、DOE 实验覆盖
- 二进制 body 编辑器、黄金配方比对引擎
- 独立 RMS 部署

---

## 3. 角色与权限

| 权限码 | 用途 | 种子 |
|--------|------|------|
| `recipe:view` | 列表 / 详情 / 解析只读；菜单 | ✅ 246 |
| `recipe:edit` | 主数据 / 草稿版本 / 启停 | ✅ 247 |
| `recipe:publish` | 发布版本 | ✅ 248 |
| `recipe:bind` | 维护 Step-Eqp 绑定 | ✅ 249 |

角色：admin / process_eng 全量；supervisor 仅 `recipe:view`。  
DDL：`migrate_recipe.sql`。

---

## 4. 领域模型

### 4.1 对象

| 对象 | 说明 |
|------|------|
| Recipe | 逻辑配方（短名 / 编码）；跨机台身份 |
| RecipeVersion | 不可变发布物；草稿可改，生效后改须升版 |
| RecipeBinding | 解析上下文 → Version |
| RecipeBody（可选） | 参数 JSON 或 MinIO 对象键；一期可空，只留元数据 |

### 4.2 版本状态

| 状态 | 说明 |
|------|------|
| `draft` | 可编辑 |
| `active` | 唯一生效；绑定与解析只认它 |
| `obsolete` | 被升版或人工停用；履历仍可反查 |

发布：同 `recipe_id` 原 `active` → `obsolete`，新版 → `active`。

### 4.3 解析（Context Resolution）

输入：`stepId` + `eqpId`（必）+ 可选 `productId`（一期可忽略）

优先级（高 → 低）：

1. Step + 具体 `eqpId` 绑定  
2. Step + 该机 `eqp_type` 绑定  
3. 无绑定 → **默认不拦 TrackIn**；`mes.recipe.require-binding=true` 时强制解析成功  

输出：`{ recipeId, recipeCode, versionId, versionNo }` 或空。  
实现：`com.mes.recipe.facade.RecipeFacade` / `RecipeFacadeImpl`。

### 4.4 与 Route 快照

Route 发布**不**拷贝配方 body。Lot 放行快照只含 Route/Step；运行时按当前站 + 选机再解析。  
避免 Route 与配方目录强耦合（对齐 Applied「短名 + 运行时解析」思路）。

---

## 5. 门面契约（可拆分核心）

包：`com.mes.recipe`；对外唯一入口 `RecipeFacade`（或同名 ApplicationService）。

| 方法 | 消费方 | 说明 |
|------|--------|------|
| `resolve(stepId, eqpId)` | Track / 现场 | 解析生效版 |
| `assertQualified(stepId, eqpId)` | TrackIn / Dispatch | 无资格抛业务错（策略开时） |
| `listQualifiedEqpIds(stepId, eqpIds)` | Dispatch | 批量过滤 |
| `getVersion(versionId)` | 履历展示 | 只读 |

**禁止：** Track / Dispatch / Lot 直接注入 Recipe Mapper / 直 SQL `mes_recipe*`。

统一响应：`{ code, msg, data }`；雪花 ID 前端禁止 `Number(id)`。

---

## 6. 接口（已落地）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/recipes` | `recipe:view` | 分页 |
| GET | `/recipes/{id}` | `recipe:view` | 含 activeVersion 摘要 |
| POST | `/recipes` | `recipe:edit` | 新建主数据 |
| PUT | `/recipes/{id}` | `recipe:edit` | 改主数据 |
| PUT | `/recipes/{id}/enabled` | `recipe:edit` | 启停 |
| GET | `/recipes/{id}/versions` | `recipe:view` | 版本列表 |
| GET | `/recipes/versions/{vid}` | `recipe:view` | 版本详情 |
| POST | `/recipes/{id}/versions` | `recipe:edit` | 新建草稿 |
| PUT | `/recipes/versions/{vid}` | `recipe:edit` | 改草稿 |
| POST | `/recipes/versions/{vid}/publish` | `recipe:publish` | 发布 |
| GET | `/recipe-bindings` | `recipe:view` | 绑定列表 |
| GET | `/recipe-bindings/{id}` | `recipe:view` | 绑定详情 |
| POST | `/recipe-bindings` | `recipe:bind` | 新建绑定 |
| DELETE | `/recipe-bindings/{id}` | `recipe:bind` | 删除 |
| GET | `/recipes/resolve?stepId=&eqpId=` | `recipe:view` 或 `track:view` | 解析 |

---

## 7. 页面

| 入口 | 说明 |
|------|------|
| Admin `/app/recipe` | 配方 Tab（列表/详情/版本）+ 绑定 Tab |
| `web/src/api/recipe.ts` | 前端 API |
| Track / Dispatch | 消费 Facade；无独立配方编辑页 |

---

## 8. 验收要点（最小集）

1. 生效版本唯一；草稿不可被解析命中  
2. 有 Step+Eqp 绑定 → 他机无绑定不出 Dispatch 候选（过滤开时）  
3. TrackIn 成功履历含 `recipe_version_id`（有解析结果时）  
4. 业务模块零直表访问，只走 Facade  
5. 无 Adapter / 无 Download 也能闭环资格与追溯  

---

## 9. 架构演进：内嵌 → 独立 RMS

### 9.1 决策

| 阶段 | 形态 | 条件 |
|------|------|------|
| 一期 | MES 同库同进程，独立包 | Adapter 未落地 |
| 二期 | 同契约远程化 / 独立库 | SECS 上下传、配方体量大、团队拆分 |

**现在内嵌、以后独立 —— 方便的前提 = §5 门面 + 表前缀隔离 + 履历只存 ID。**

### 9.2 拆分清单

| 项 | 做法 |
|----|------|
| 代码 | `com.mes.recipe` 整包迁出为服务；Facade → HTTP/gRPC 客户端 |
| 表 | `mes_recipe*` 迁独立库；他模块禁止 FK 硬绑，只存 ID |
| 文件 | body 已在 MinIO → 桶策略跟服务走 |
| 权限 | `recipe:*` 可迁独立鉴权或继续走统一 Auth |
| 回滚 | 客户端切换特性开关：local / remote |

### 9.3 与业界 RMS 对齐点（后置能力挂点）

| 能力 | 挂在 |
|------|------|
| Download / Upload / Compare | Adapter + Recipe 扩展 API |
| 短名 vs 机台路径 | 解析层增加 directory 映射表 |
| 实验覆盖 | Binding 增加 Lot 级 override（勿改主绑定） |

---

## 10. 与已落地模块

| 模块 | 关系 |
|------|------|
| Dispatch | `listQualifiedEqpIds` 接入候选过滤 |
| Track | TrackIn `resolve` + 履历字段 |
| Equipment | 绑定按 eqpId / eqp_type |
| Route | Step 主键；不拷贝配方 |
| Hold | 无关 |
| Auth | `recipe:*` 已种子 |

---

## 11. 关联

- `MES-Recipe数据库设计.md`
- `MES-Recipe已完成功能.md`
- `docs/架构/半导MES架构设计.md`
- `docs/架构/MES-实施进度与下一步.md`
- Dispatch / Equipment / Track 功能文档
