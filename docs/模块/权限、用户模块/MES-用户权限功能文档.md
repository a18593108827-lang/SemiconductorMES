# MES 用户权限功能文档

> 方案：本地账号管理 + MES 内权限审批  
> 技术：Sa-Token / RBAC  
> 预留：SSO / OA（本期不做）  
> 一期状态：**已落地**（查验见 `MES-用户权限已完成功能.md`）  
> 更新：2026-07-24

---

## 1. 目标

- 本地维护用户、角色、权限
- 接口与菜单按权限码控制
- 用户可发起权限申请，管理员在 MES 内审批通过后自动赋权
- 为后续 SSO / OA 预留字段，不改核心模型

---

## 2. 角色与职责

| 角色 | 说明 |
|------|------|
| 超级管理员 | 用户/角色/权限全量管理；可直接赋权；可审批 |
| 审批人 | 处理权限申请（默认：拥有 `perm:approve` 的用户） |
| 普通用户 | 登录、查看本人信息、发起/撤回权限申请 |

预设业务角色（种子数据，可改）：

| 角色编码 | 名称 | 典型权限 |
|----------|------|----------|
| `admin` | 超级管理员 | `*` |
| `operator` | 现场操作员 | Track In/Out、扫码相关 |
| `process_eng` | 工艺工程师 | Route/Recipe 查看与配置 |
| `supervisor` | 班组长 | WIP 看板、Hold 查看、审批申请 |

---

## 3. 功能清单

### 3.1 认证

| 功能 | 说明 | 权限 |
|------|------|------|
| 注册 | 用户编码 + 姓名 + 密码；校验编码唯一；写入本地账号，不自动登录 | `user:add` |
| 登录 | 用户编码 + 密码；校验禁用态；发 Token | 匿名 |
| 登出 | 注销当前会话 | 登录 |
| 当前用户信息 | 返回 id/用户编码/姓名/角色/权限码列表 | 登录 |
| 修改本人密码 | 旧密 + 新密 | 登录 |
| 踢人下线 | 管理员强制下线指定用户 | `user:kick` |

### 3.2 用户管理（管理员）

| 功能 | 说明 | 权限 |
|------|------|------|
| 用户分页查询 | 编码/姓名/状态筛选 | `user:list` |
| 新增用户 | 用户编码、姓名、初始密码、状态、角色 | `user:add` |
| 编辑用户 | 改姓名、状态、角色（编码默认不可改；不改密码走重置） | `user:edit` |
| 禁用/启用 | 禁用后无法登录；踢掉现有会话 | `user:edit` |
| 重置密码 | 设临时密码，可选强制下次改密 | `user:reset-pwd` |
| 分配角色 | 覆盖式绑定角色列表 | `user:assign-role` |

规则：
- `user_code`（用户编码）全局唯一，用于登录；`user_name`（姓名）可重复
- 初始密码由管理员设定；建议首次登录强制改密（`must_change_pwd=1`）
- 不可删除最后一个 `admin` 角色用户
- 逻辑删除；释放策略：删除后 `user_code` 加 `_del_{id}` 后缀防冲突

### 3.3 角色管理

| 功能 | 说明 | 权限 |
|------|------|------|
| 角色列表 | 编码/名称/状态 | `role:list` |
| 新增/编辑角色 | 编码唯一；维护名称、备注、状态 | `role:add` / `role:edit` |
| 分配权限 | 勾选权限树，覆盖式保存 | `role:assign-perm` |
| 禁用角色 | 禁用后该角色权限不再生效 | `role:edit` |

### 3.4 权限/菜单管理

| 功能 | 说明 | 权限 |
|------|------|------|
| 权限树查询 | 目录/菜单/按钮（接口）树 | `perm:list` |
| 新增/编辑权限 | 维护权限码、类型、路由、排序 | `perm:add` / `perm:edit` |

权限类型：
- `1` 目录
- `2` 菜单（前端路由）
- `3` 按钮/接口（权限码，如 `track:track-in`）

权限码约定：`{模块}:{动作}`，示例：
- `user:list` / `user:add` / `user:edit`
- `role:list` / `role:assign-perm`
- `perm:approve` / `perm:apply`
- `track:track-in` / `track:track-out`
- `hold:release` / `lot:scrap`

前端：登录后拉权限码，控制菜单显隐与按钮；后端注解二次校验，不以 UI 为准。

反馈约定（已落地）：
- 接口失败 / 成功 → 全局 Toast
- 表单校验 → 字段旁或抽屉内
- 破坏性操作 → ConfirmDialog（非原生 confirm）
- 列表加载失败 → Toast + 表内重试

详见 `MES-用户权限已完成功能.md` §4.1。

### 3.5 权限申请（MES 内审批）

| 功能 | 说明 | 权限 |
|------|------|------|
| 发起申请 | 选择目标角色（或权限包），填原因 | `perm:apply` |
| 我的申请 | 查看本人申请单与状态 | 登录 |
| 撤回申请 | 仅 `pending` 可撤回 | 申请人本人 |
| 待我审批 | 审批人列表 | `perm:approve` |
| 通过 | 填写意见；自动给申请人绑定申请的角色 | `perm:approve` |
| 驳回 | 填写意见；不赋权 | `perm:approve` |

申请单状态：

```
draft(可选) → pending → approved
                      ↘ rejected
                      ↘ cancelled（撤回）
```

规则：
- 一期申请粒度：**申请角色**（不是零散权限码），降低审批复杂度
- 同一用户对同一角色仅允许一笔 `pending`
- 通过时若用户已有该角色 → 幂等成功，记日志
- 审批通过后写操作审计；可选踢用户重新登录以刷新权限（或服务端下次请求重载权限）
- 超级管理员可直接在用户管理赋角色，不强制走申请
- 预留字段 `oa_instance_id`，后期可改「提交到 OA」，入口不变

### 3.6 审计

| 功能 | 说明 |
|------|------|
| 操作日志 | 复用 `sys_oper_log`：登录、改密、赋权、审批等 |
| 申请流转日志 | 申请单状态变更记录（提交/通过/驳回/撤回） |

---

## 4. 页面入口（管理端）

| 菜单 | 路径建议 | 权限 |
|------|----------|------|
| 用户管理 | `/system/user` | `user:list` |
| 角色管理 | `/system/role` | `role:list` |
| 权限管理 | `/system/permission` | `perm:list` |
| 权限申请 | `/system/perm-apply` | `perm:apply` |
| 权限审批 | `/system/perm-approve` | `perm:approve` |

现场台：仅登录 + 按权限显示操作按钮，不开放用户管理。

---

## 5. 接口清单

> 状态：✅ 已实现 · ⏳ 未实现

### 认证
- ✅ `POST /auth/register` → `{ userCode, userName, password }`，需 `user:add`
- ✅ `POST /auth/login`
- ✅ `POST /auth/logout`
- ✅ `GET  /auth/info` → id / userCode / userName / roles / permissions / mustChangePwd / menus
- ✅ `PUT  /auth/password`（本人改密）

### 用户
- ✅ `GET    /system/users` → keyword / status / page / size，需 `user:list`
- ✅ `PUT    /system/users/{id}` → `{ userName }`，需 `user:edit`
- ✅ `PUT    /system/users/{id}/status` → `{ status }`，需 `user:edit`（禁用踢下线）
- ✅ `GET    /system/users/{id}/roles` → 需 `user:list`
- ✅ `PUT    /system/users/{id}/roles` → `{ roleIds }` 覆盖，需 `user:assign-role`
- ✅ `PUT    /system/users/{id}/password/reset` → `{ password, mustChangePwd }`，需 `user:reset-pwd`
- ⏳ `POST   /system/users`（当前新增走 `/auth/register`）
- ✅ `POST   /system/users/{id}/kick`（`user:kick`）

### 角色 / 权限
- ✅ `GET    /system/roles` → keyword / status / page / size，需 `role:list`
- ✅ `POST   /system/roles` → 需 `role:add`
- ✅ `PUT    /system/roles/{id}` → 需 `role:edit`（编码不可改）
- ✅ `DELETE /system/roles/{id}` → 需 `role:edit`（admin 角色不可删；有用户不可删）
- ✅ `GET    /system/roles/{id}/permissions` → 需 `role:list`
- ✅ `PUT    /system/roles/{id}/permissions` → `{ permissionIds }` 覆盖保存，需 `role:assign-perm`
- ✅ `GET    /system/permissions/tree` → 需 `perm:list`
- ✅ `POST   /system/permissions` → 需 `perm:add`
- ✅ `PUT    /system/permissions/{id}` → 需 `perm:edit`
- ✅ `DELETE /system/permissions/{id}` → 需 `perm:edit`（有子节点不可删）

### 申请
- ✅ `POST /system/perm-requests`
- ✅ `GET  /system/perm-requests/mine`
- ✅ `POST /system/perm-requests/{id}/cancel`
- ✅ `GET  /system/perm-requests/todo`
- ✅ `GET  /system/perm-requests/done`
- ✅ `POST /system/perm-requests/{id}/approve`
- ✅ `POST /system/perm-requests/{id}/reject`
- ✅ `GET  /system/perm-requests/applyable-roles`

已完成能力的查验说明见：`docs/模块/MES-用户权限已完成功能.md`。

---

## 6. 关键业务流程

### 6.1 管理员注册账号

```
具备 user:add 的用户（如 admin）登录 → POST /auth/register
→ 校验编码唯一 → BCrypt 加密写入 sys_user（status=1, source=local）
```

### 6.2 管理员建号

```
管理员新增用户 → 设初始密码 + 角色 → 用户登录
→ must_change_pwd=1 则强制改密 → 进入系统
```

### 6.3 用户申请权限

```
用户发起申请（选角色 + 原因）
→ 状态 pending
→ 审批人通过 → 写入 sys_user_role → approved
→ 审批人驳回 → rejected（可再次申请）
→ 申请人撤回 → cancelled
```

### 6.4 鉴权

```
请求进入 → Sa-Token 校验登录
→ @SaCheckPermission 校验权限码
→ 权限来自：用户→角色→权限（实时查或登录缓存，改权后需刷新）
```

---

## 7. 非功能

- 密码：BCrypt；禁止明文日志
- Token：`Authorization: Bearer {token}`；超时按现有 Sa-Token 配置
- 权限变更后：下次请求重载，或审批通过后 `StpUtil.logout(userId)` 强制重登
- 敏感操作（赋角色、重置密码、审批通过）必须记 `sys_oper_log`
- 并发：审批通过用乐观锁/状态 CAS（仅 `pending` → `approved`）

---

## 8. 本期不做

- SSO / LDAP / 企业微信登录
- OA 审批流对接
- 数据权限（按车间/产线行级隔离）——后续可加 `sys_user_data_scope`
- 多级审批链（一期单级：任一审批人可通过/驳回）

---

## 9. 验收标准

1. 无 Token 访问业务接口返回未登录
2. 无权限码访问受控接口返回无权限
3. 管理员可完成：建用户 → 分角色 → 角色分权限 → 用户按权限看到菜单/按钮
4. 用户可申请角色；审批通过后权限立即（或重登后）生效
5. 禁用用户无法登录；重置密码后可用新密码登录
6. 申请/审批/赋权均有审计记录
