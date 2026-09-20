---
type: 架构
module: 权限用户
status: done
slices: []
aligns: []
updated: 2026-07-24
---

# MES 用户中心（账号设置）设计

> 状态：**已完成**（账号设置 + 强制改密前后端 + API 兜底）  
> 关联：`MES-用户权限已完成功能.md`、`MES-菜单权限方案.md`  
> 更新：2026-07-24

---

## 1. 定位

用户中心是 **登录用户管自己** 的入口，与「系统管理 → 用户管理」分离：

| | 用户中心 | 用户管理 |
|--|----------|----------|
| 视角 | 本人 | 管理员管别人 |
| 典型能力 | 看资料、自己改密 | 建号、启停、重置别人密码、分角色 |
| 权限 | 登录即可（改本人） | `user:*` 等管理权限 |

入口：管理端顶栏用户区下拉 →「账号设置」/「退出登录」。现场台一期不挂入口（强制改密仍走专用页）。

---

## 2. 一期范围（MVP）

1. **基本资料（只读）**：用户编码、姓名、角色列表  
2. **安全 - 修改密码**：旧密码 + 新密码 + 确认（含强度提示）  
3. **强制改密拦截**：`mustChangePwd === 1` 时仅放行 `/app/account/password` + 登出  

### 一期不做

- 页内「返回」键（壳内靠侧栏/顶栏导航；强制改密页禁止回业务）  
- 改头像 / 邮箱 / 手机 / MFA / 设备列表 / 主题  
- SSO 改密跳转（有 `source=sso` 时再隐藏改密入口）

---

## 3. 页面与路由

| 路径 | 说明 | 状态 |
|------|------|------|
| `/app/account` | 账号设置（AdminShell 内：身份条 + 改密表单） | ✅ |
| `/app/account/password` | 强制改密（无侧栏） | ✅ |

交互：

```
顶栏用户区 → 下拉
  ├─ 账号设置 → /app/account
  └─ 退出登录
```

强制改密：

```
登录成功 → mustChangePwd=1
  → 登录页直接跳转 /app/account/password（RequireAuth 兜底）
  → 改密成功 → 清标记并注销 → 登录页
```

前端文件：

- `web/src/pages/account/AccountPage.tsx`
- `web/src/pages/account/ForcePasswordPage.tsx`
- `web/src/auth/RequireAuth.tsx`（强制改密拦截）
- `web/src/layouts/AdminShell.tsx`（顶栏下拉）

---

## 4. 接口

### 4.1 当前用户信息（已完成）

`GET /auth/info` 已返回：

```json
{
  "id": "…",
  "userCode": "admin",
  "userName": "管理员",
  "roles": ["admin"],
  "permissions": ["user:list", "…"],
  "mustChangePwd": 0,
  "menus": [ ]
}
```

`mustChangePwd`：`0` / `1`（整数）。

### 4.2 修改本人密码（已完成）

`PUT /auth/password`（需登录，**无特殊权限码**）

请求：

```json
{
  "oldPassword": "旧密码",
  "newPassword": "新密码"
}
```

规则：

- 校验旧密码；新密码 ≥ 6 位；新密码 ≠ 旧密码  
- 成功：BCrypt 更新、`must_change_pwd=0`、**注销当前会话**  
- 审计：模块「账号」动作「修改密码」；日志脱敏 `oldPassword` / `newPassword`  

前端：成功后清 Token 并跳转登录页。

### 4.3 与管理员重置

| | 本人改密 | 管理员重置 |
|--|----------|------------|
| 接口 | `PUT /auth/password` | `PUT /system/users/{id}/password/reset` |
| 旧密码 | 要 | 不要 |
| 权限 | 登录本人 | `user:reset-pwd` |
| 强制改密 | 成功后清 0 | 默认置 1 |

---

## 5. UI 约定

- 账号页：身份条（首字母块 + 工号 mono + 角色 chips）+ 改密区；错误/成功带图标与 `role=alert` / `status`  
- 密码强度：色条 +「较弱/一般/较好」文案（色不独担）  
- 强制改密页：与登录页同系深色网格底；仅「修改并继续」与「退出登录」  
- 动效：GSAP ≤200ms 轻位移；`prefers-reduced-motion` 跳过  

---

## 6. 后端

| 项 | 说明 |
|----|------|
| `PUT /auth/password` | `AuthController` + `ChangePasswordDTO` |
| Service | 校验旧密 → 更新 → 清 `mustChangePwd` → `StpUtil.logout()` |
| 审计 | `@OperLog(module = "账号", action = "修改密码")` |

---

## 7. 验收标准

| # | 项 | 现状 |
|---|----|------|
| 1 | 顶栏进账号设置，见编码/姓名/角色 | ✅ |
| 2 | 改密表单校验与反馈 | ✅ |
| 3 | `mustChangePwd=1` 只能进强制改密页 | ✅ |
| 4 | 真实改密写库并清标记 | ✅ |
| 5 | 操作日志无明文密码 | ✅ |
| 6 | 无 Token 调改密 → 401 | ✅（Sa-Token 拦截） |
| 7 | mustChangePwd=1 时其它 API 403 | ✅ MustChangePwdInterceptor |

---

## 8. 可选后续

1. 现场台顶栏挂账号入口  
2. 密码策略加强（复杂度）  

---

## 9. 强制改密后端兜底

`MustChangePwdInterceptor`：`must_change_pwd=1` 时仅放行：

- `GET /auth/info`
- `PUT /auth/password`
- `POST /auth/logout`

其余接口返回 `403`，文案「请先修改密码」。

---

## 10. 关联已有能力

- 管理员重置密码：已实现  
- `/auth/info`：roles / permissions / mustChangePwd / menus  
- 用户中心 + 本人改密 + 前后端强制改密拦截：已完成  
