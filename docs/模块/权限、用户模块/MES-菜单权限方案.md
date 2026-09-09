# MES 菜单权限方案

> 状态：已落地种子 + `/auth/info` 菜单树 + 侧栏/Tab 按权限显隐 + 账号设置入口  
> 更新：2026-09-09（增补「复盘 / 报表」规划，落地随 Report Rep-4）

---

## 1. 结构

```
生产执行（目录，perm_code=NULL）
 ├─ 看板 / 批次 / 在制 / 设备 / 路线 / 锁批 / 报警 / 追溯 / 现场台
 ├─ …（派工 / 配方 / 量测 / 趋势等既有项）
 └─ 按钮：Track In/Out、发起锁批、解锁

复盘（目录，perm_code=NULL 或 review）← Report 一期新增
 └─ 报表 report:view → /app/report
    （禁止挂到「生产执行」下）

系统管理（目录，perm_code=system）
 ├─ 用户管理 system:user → /app/auth/users
 ├─ 角色管理 system:role → /app/auth/roles
 ├─ 权限管理 system:permission → /app/auth/perms
 ├─ 权限申请 system:perm-apply → /app/auth/requests
 └─ 权限审批 system:perm-approve → /app/auth/approvals
```

## 2. 显隐规则

| 层级 | 规则 |
|------|------|
| 目录 | 有任意可见子菜单才返回/显示 |
| 菜单 | 用户 permissions 含该 `perm_code` |
| 侧栏「系统管理」 | 分组标题可省；合并入口 `/app/auth` |
| Auth Tab | 有对应 `system:*` 菜单码才显示 |
| 现场台 | 归入「生产执行」分组内展示 |
| 账号设置 | 顶栏下拉 → `/app/account`（见用户中心设计） |

## 3. 接口

`GET /auth/info` 增加：

- `roles` / `permissions` / `mustChangePwd`
- `menus`：已过滤的目录+菜单树

## 4. 数据脚本

- 新库：`schema.sql` 已含生产执行种子  
- 已有库：执行 `migrate_ops_menu.sql`

## 5. 前端

- `AdminShell`：按 `user.menus` 渲染  
- `AuthLayout`：按 `hasPermission('system:…')` 过滤 Tab  
