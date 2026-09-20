---
type: 已完成功能
module: 权限用户
status: done
slices: []
aligns: []
updated: 2026-07-24
---

# MES 用户权限 — 已完成功能查验

> 更新：2026-07-24  
> 状态：**一期已完成**  
> 范围：认证、用户/角色/权限树、菜单权限、用户中心、强制改密、踢人、权限申请审批、前端 Toast/确认反馈

---

## 0. 完成度总览

| 能力 | 后端 | 前端 | 权限码 |
|------|------|------|--------|
| 登录 | ✅ `POST /auth/login` | ✅ 登录页 | 匿名 |
| 登出 | ✅ `POST /auth/logout` | ✅ | 登录 |
| 当前用户信息 | ✅ `GET /auth/info` | ✅ AuthContext | 登录 |
| 新增用户 | ✅ `POST /auth/register` | ✅ 用户页抽屉 | `user:add` |
| 用户分页列表 | ✅ `GET /system/users` | ✅ 用户页列表 | `user:list` |
| 编辑用户 | ✅ `PUT /system/users/{id}` | ✅ | `user:edit` |
| 用户启停 | ✅ `PUT /system/users/{id}/status` | ✅ | `user:edit` |
| 用户角色查询 | ✅ `GET /system/users/{id}/roles` | ✅ | `user:list` |
| 用户分配角色 | ✅ `PUT /system/users/{id}/roles` | ✅ | `user:assign-role` |
| 管理员重置密码 | ✅ `PUT /system/users/{id}/password/reset` | ✅ | `user:reset-pwd` |
| 角色分页列表 | ✅ `GET /system/roles` | ✅ 角色页 | `role:list` |
| 新增角色 | ✅ `POST /system/roles` | ✅ | `role:add` |
| 编辑角色 | ✅ `PUT /system/roles/{id}` | ✅ | `role:edit` |
| 删除角色 | ✅ `DELETE /system/roles/{id}` | ✅ | `role:edit` |
| 角色分配权限 | ✅ `PUT /system/roles/{id}/permissions` | ✅ | `role:assign-perm` |
| 权限树查询 | ✅ `GET /system/permissions/tree` | ✅ 权限树页 / 赋权抽屉 | `perm:list` |
| 权限新增 | ✅ `POST /system/permissions` | ✅ | `perm:add` |
| 权限编辑 | ✅ `PUT /system/permissions/{id}` | ✅ | `perm:edit` |
| 权限删除 | ✅ `DELETE /system/permissions/{id}` | ✅ | `perm:edit` |
| 本人改密 | ✅ `PUT /auth/password` | ✅ 账号设置 / 强制改密 | 登录本人 |
| 强制改密拦截 | ✅ MustChangePwdInterceptor | ✅ RequireAuth + 登录直跳 | — |
| 踢人 | ✅ `POST /system/users/{id}/kick` | ✅ 用户列表「踢下线」 | `user:kick` |
| 权限申请 / 审批 | ✅ `/system/perm-requests/*` | ✅ 我的申请 / 审批 | `perm:apply` / `perm:approve` |
| `/auth/info` roles/permissions/menus | ✅ | ✅ 侧栏/Tab | 登录 |
| 前端反馈（Toast / 确认框） | — | ✅ Toast + ConfirmDialog | — |

统一响应：`{ code, msg, data }`；成功 `code=200`。  
Token：`Authorization: Bearer {token}`（Sa-Token）。  
匿名仅放行：`/auth/login`。

---

## 1. 认证接口

### 1.1 登录 `POST /auth/login`（匿名）

请求：

```json
{ "userCode": "admin", "password": "123456" }
```

成功 `data`：

```json
{ "token": "...", "tokenName": "Authorization" }
```

规则：校验账号存在、密码 BCrypt、`status=1`；失败统一「用户编码或密码错误」或「账号已禁用」。

### 1.2 登出 `POST /auth/logout`（登录）

注销当前 Token。

### 1.3 当前用户 `GET /auth/info`（登录）

成功 `data`（示例）：

```json
{
  "id": "1",
  "userCode": "admin",
  "userName": "管理员",
  "mustChangePwd": 0,
  "roles": ["admin"],
  "permissions": ["user:list", "…"],
  "menus": []
}
```

> 含 roles / permissions / mustChangePwd / 过滤后 menus。

### 1.4 本人改密 `PUT /auth/password`（登录）

请求：

```json
{ "oldPassword": "旧密码", "newPassword": "新密码" }
```

规则：校验旧密、新密 ≥6、≠旧密 → 更新 BCrypt → `must_change_pwd=0` → 注销当前 Token。  
审计：模块「账号」/ 修改密码；参数中密码字段脱敏。

### 1.5 新增用户 `POST /auth/register`（登录 + `user:add`）

请求：

```json
{ "userCode": "E1001", "userName": "张三", "password": "123456" }
```

落库 `sys_user`：

| 字段 | 值 |
|------|----|
| status | 1 |
| must_change_pwd | 0 |
| source | local |
| password | BCrypt |
| 角色 | **不绑定** |

不自动登录。失败：401 / 403 / 编码已存在 / 参数校验。

---

## 2. 用户列表接口

### `GET /system/users`（登录 + `user:list`）

| 参数 | 说明 |
|------|------|
| keyword | 编码或姓名模糊（OR） |
| userCode | 编码模糊 |
| userName | 姓名模糊 |
| status | `1` / `0` |
| page | 默认 1 |
| size | 默认 10 |

成功 `data`：

```json
{
  "records": [
    {
      "id": 1,
      "userCode": "admin",
      "userName": "管理员",
      "status": 1,
      "mustChangePwd": 0,
      "roles": ["admin"],
      "createTime": "2026-07-21T10:00:00",
      "updateTime": "2026-07-21T10:00:00"
    }
  ],
  "total": 1,
  "current": 1,
  "size": 10
}
```

不含密码。

### `POST /system/users/{id}/kick`（登录 + `user:kick`）

强制注销该用户全部会话；账号状态不变，可再次登录。  
不能踢自己。记操作日志「踢人下线」。

---

## 2.1 权限申请 / 审批

| 接口 | 权限 |
|------|------|
| `GET /system/perm-requests/applyable-roles` | `perm:apply` |
| `POST /system/perm-requests` | `perm:apply` |
| `GET /system/perm-requests/mine` | `system:perm-apply` |
| `POST /system/perm-requests/{id}/cancel` | `perm:apply` |
| `GET /system/perm-requests/todo` | `perm:approve` |
| `GET /system/perm-requests/done` | `perm:approve` |
| `POST /system/perm-requests/{id}/approve` | `perm:approve` |
| `POST /system/perm-requests/{id}/reject` | `perm:approve` |

规则：申请目标角色（不可 admin）；同用户同角色仅一笔 pending；通过 CAS；通过后写 `sys_user_role` 并踢申请人；不能审自己。

前端：`MyRequestsPage` / `ApprovalsPage`。

---

## 2.2 角色 CRUD

| 接口 | 权限 |
|------|------|
| `GET /system/roles` | `role:list` |
| `POST /system/roles` | `role:add` |
| `PUT /system/roles/{id}` | `role:edit` |
| `DELETE /system/roles/{id}` | `role:edit` |

列表参数：`keyword` / `status` / `page` / `size`。  
新增 body：`{ roleCode, roleName, remark, status }`。  
编辑 body：`{ roleName, remark, status }`（编码不可改）。  
删除：逻辑删除；`admin` 不可删；仍有用户绑定时不可删。

前端：`web/src/pages/auth/RolesPage.tsx` + `listRolesApi` / `createRoleApi` / `updateRoleApi` / `deleteRoleApi`。  
「分配权限」：拉权限树 + 已选 ID，保存覆盖写 `sys_role_permission`。

---

## 2.2 角色分配权限

| 接口 | 权限 |
|------|------|
| `GET /system/permissions/tree` | `perm:list` |
| `GET /system/roles/{id}/permissions` | `role:list` |
| `PUT /system/roles/{id}/permissions` | `role:assign-perm` |

PUT body：`{ "permissionIds": [100, 110, 111] }`（覆盖式；空数组清空）。

### 权限树 CRUD

| 接口 | 权限 |
|------|------|
| `GET /system/permissions/tree` | `perm:list` |
| `POST /system/permissions` | `perm:add` |
| `PUT /system/permissions/{id}` | `perm:edit` |
| `DELETE /system/permissions/{id}` | `perm:edit` |

删除：有子节点不可删；会清理角色绑定；逻辑删除。

---

## 3. 权限校验说明

| 权限码 | 用途 | 种子位置 |
|--------|------|----------|
| `user:list` | 用户列表 | sys_permission id=111 |
| `user:add` | 新增用户 | sys_permission id=112 |

目前仅 `admin` 角色绑定上述权限。

校验链路：

```
user_id → sys_user_role → sys_role → sys_role_permission → sys_permission.perm_code
```

实现：`StpInterfaceImpl` + `@SaCheckPermission`。

查验 SQL：

```sql
-- 用户权限码
SELECT DISTINCT p.perm_code
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.deleted = 0
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 1 AND p.deleted = 0
WHERE u.user_code = 'admin';

-- admin 是否绑角色
SELECT ur.*
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
WHERE u.user_code = 'admin';
```

空库启动：`DataInitializer` 创建 `admin/123456` 并绑 `role_id=1`。  
若库中已有 admin 但无角色，需手工补 `sys_user_role`。

---

## 4. 前端对接

| 能力 | 文件 |
|------|------|
| HTTP / Token | `web/src/lib/http.ts`（前缀 `/api`） |
| Toast 全局提示 | `web/src/components/ui/Toast.tsx`（`useToast`） |
| 确认弹窗 | `web/src/components/ui/ConfirmDialog.tsx`（`useConfirm`） |
| 登录/登出/info/注册/改密 | `web/src/api/auth.ts` |
| 用户/角色/权限/申请 | `web/src/api/system.ts` |
| 登录页 | `web/src/pages/LoginPage.tsx` |
| 会话 / 强制改密守卫 | `web/src/auth/AuthContext.tsx`、`RequireAuth.tsx` |
| 管理壳 / 菜单 | `web/src/layouts/AdminShell.tsx` |
| 用户管理 | `web/src/pages/auth/UsersPage.tsx` |
| 角色 / 权限树 | `RolesPage.tsx` / `PermsPage.tsx` |
| 我的申请 / 审批 | `MyRequestsPage.tsx` / `ApprovalsPage.tsx` |
| 账号设置 / 强制改密 | `pages/account/AccountPage.tsx`、`ForcePasswordPage.tsx` |

用户页：列表、新增、编辑、启停、重置密码、踢人、分配角色均为真实接口。

### 4.1 反馈交互约定

| 场景 | 表现 |
|------|------|
| 接口失败 / 业务拒绝 | 右上角 Toast（错误，约 3s，可关） |
| 保存 / 审批 / 踢人等成功 | Toast 成功 |
| 表单字段校验（必填、长度等） | 留在表单 / 抽屉内红字 |
| 破坏性操作（删角色/权限、踢人、撤回申请） | `ConfirmDialog`，不再用 `window.confirm` |
| 列表加载失败 | Toast + 表内「加载失败 · 重试」 |
| 登录页失败 | 表单内联（不打断输入流） |

根节点：`main.tsx` 包裹 `ToastProvider` + `ConfirmProvider`。

---

## 5. 后端关键文件

| 文件 | 作用 |
|------|------|
| `AuthController` | login/logout/info/register |
| `AuthServiceImpl` | 认证与注册逻辑 |
| `SysUserController` | 用户分页 |
| `SysUserServiceImpl` | 列表查询 + 批量角色 |
| `SaTokenConfig` | 登录拦截，仅放行 `/auth/login` |
| `StpInterfaceImpl` | 权限/角色加载 |
| `SysPermissionMapper` | perm / role 查询 |
| `DataInitializer` | 初始化 admin |
| `GlobalExceptionHandler` | 401/403/校验异常 |
| `schema.sql` | 表结构 + 角色权限种子 |

---

## 6. 手工查验清单

### 6.1 登录

1. `admin/123456` 登录 → 有 token，可进管理端  
2. 错误密码 → 失败  
3. 无 Token 访问 `/auth/info` → 401  

### 6.2 新增用户

1. admin 新增未占用编码 → 200，库中有记录，可用新账号登录  
2. 重复编码 → 业务失败  
3. 无 `user:add` 账号调用 → 403  

### 6.3 用户列表

1. admin 打开用户页 → 显示库中真实用户  
2. 按状态 / 关键字筛选有效  
3. 无 `user:list` → 403  

### 6.4 curl

```bash
# 登录
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userCode":"admin","password":"123456"}'

# 列表（替换 TOKEN）
curl -s "http://localhost:8080/system/users?page=1&size=20" \
  -H "Authorization: Bearer TOKEN"

# 新增
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"userCode":"E9001","userName":"测试用户","password":"123456"}'
```

前端经 Vite 代理时路径加前缀 `/api`。

---

## 7. 已知限制（后续增强，非一期阻塞）

- [ ] 注册时指定角色、强制改密（当前注册不绑角色，`must_change_pwd=0`）
- [ ] 独立 `POST /system/users`（当前新增走 `/auth/register`）
- [ ] 现场台挂账号设置入口
- [ ] SSO / OA / 多级审批 / 数据权限（见功能文档「本期不做」）

---

## 8. 关联文档

- `docs/模块/MES-用户权限功能文档.md`（全量规划）
- `docs/模块/MES-用户权限数据库设计.md`
- `docs/模块/MES-用户中心设计.md`（账号设置 + 本人改密已完成）
- `docs/模块/MES-菜单权限方案.md`（生产执行 / 系统管理侧栏）
- `server/src/main/resources/db/schema.sql`
