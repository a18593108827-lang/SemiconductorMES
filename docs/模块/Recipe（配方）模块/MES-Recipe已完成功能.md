---
type: 已完成功能
module: Recipe
status: done
slices: []
aligns: []
updated: 2026-07-30
---

# MES 配方（Recipe）— 功能查验清单

> 更新：2026-07-30  
> 状态：**一期最小集已完成（含门面钩子 + Admin 前端）**  
> 需求依据：`MES-Recipe功能文档.md` · `MES-Recipe数据库设计.md`

图例：✅ 已完成 · ⏳ 未做 · — 不适用  

---

## 0. 完成度总览

| 能力 | 后端 | 前端 | 权限码 |
|------|------|------|--------|
| Recipe 主数据 CRUD / 启停 | ✅ `/recipes` | ✅ 列表 + 抽屉 | `recipe:view` / `recipe:edit` |
| 版本草稿 / 发布 | ✅ draft / publish | ✅ 详情抽屉版本区 | `recipe:edit` / `recipe:publish` |
| Step×Eqp 绑定 | ✅ `/recipe-bindings` | ✅ 绑定 Tab | `recipe:bind` |
| 解析门面 Facade | ✅ `RecipeFacade` | ✅ `GET /recipes/resolve` | `recipe:view` / `track:view` |
| Dispatch 资格过滤 | ✅ `listQualifiedEqpIds` | — | — |
| TrackIn 履历记版本 | ✅ resolve + `mes_tx_log` | — | — |
| 表 / 迁移 | ✅ `migrate_recipe.sql` | — | — |
| 权限种子 | ✅ 246–249 + 角色 | ✅ 菜单 `/app/recipe` | `recipe:*` |
| Download / Chamber / 独立 RMS | — 后置 | — | — |

---

## 1. 接口速查

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/recipes` | `recipe:view` | 分页 keyword / enabled |
| GET | `/recipes/{id}` | `recipe:view` | 含 activeVersion |
| POST | `/recipes` | `recipe:edit` | 新建 |
| PUT | `/recipes/{id}` | `recipe:edit` | 改名称/备注 |
| PUT | `/recipes/{id}/enabled` | `recipe:edit` | 启停 |
| GET | `/recipes/{id}/versions` | `recipe:view` | 版本列表 |
| GET | `/recipes/versions/{vid}` | `recipe:view` | 版本详情 |
| POST | `/recipes/{id}/versions` | `recipe:edit` | 新建草稿（可 fromVersionId） |
| PUT | `/recipes/versions/{vid}` | `recipe:edit` | 改草稿 body |
| POST | `/recipes/versions/{vid}/publish` | `recipe:publish` | 发布 |
| GET | `/recipes/resolve` | `recipe:view` OR `track:view` | 解析 |
| GET | `/recipe-bindings` | `recipe:view` | 绑定分页 |
| GET | `/recipe-bindings/{id}` | `recipe:view` | 绑定详情 |
| POST | `/recipe-bindings` | `recipe:bind` | 新建绑定 |
| DELETE | `/recipe-bindings/{id}` | `recipe:bind` | 删除 |

包：`com.mes.recipe`；跨模块只调 `RecipeFacade`。

配置：`mes.recipe.require-binding`（默认 `false`；`true` 时 TrackIn 强制资格）。

---

## 2. 前端入口

| 路径 | 说明 |
|------|------|
| `/app/recipe` | Admin：配方 Tab + 绑定 Tab |
| `web/src/api/recipe.ts` | 前端 API |
| `web/src/pages/RecipePage.tsx` | 页面 |

---

## 3. 权限种子

| id | 码 | 角色 |
|----|-----|------|
| 246 | `recipe:view` | admin / process_eng / supervisor |
| 247 | `recipe:edit` | admin / process_eng |
| 248 | `recipe:publish` | admin / process_eng |
| 249 | `recipe:bind` | admin / process_eng |

已有库执行：`server/src/main/resources/db/migrate_recipe.sql`。

---

## 4. 验收要点

- [x] 同 recipe 仅一个 `active`；草稿不可被解析命中  
- [x] Step 有绑定 → 无资格机不出 Dispatch 候选；无绑定则不滤  
- [x] TrackIn 履历含 `recipe_id` / `recipe_version_id`（有解析结果时）  
- [x] Track / Dispatch 只走 Facade  
- [x] Admin 可维护主数据 / 版本 / 绑定  
- [x] 无 SECS / Download 依赖  
- [ ] `require-binding=true` 现场回归（可选）  

---

## 5. 后续扩展

- [ ] SECS Download / Upload / Compare  
- [ ] Chamber 级配方  
- [ ] 机台目录 ↔ 短名映射  
- [ ] Lot 级实验覆盖  
- [ ] 拆独立 RMS（Facade → 远程）  

详见功能文档 §9。

---

## 6. 关联

- `MES-Recipe功能文档.md`  
- `MES-Recipe数据库设计.md`  
- Dispatch / Track / Equipment / Route  
- `docs/架构/MES-实施进度与下一步.md`
