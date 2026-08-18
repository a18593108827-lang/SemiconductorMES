# MES 用户权限数据库设计

> 对应功能文档：`docs/模块/MES-用户权限功能文档.md`  
> 约定：主键雪花 `BIGINT`；时间 `DATETIME`；逻辑删除 `deleted`；字符集 `utf8mb4`

---

## 1. ER 关系

```
sys_user ──< sys_user_role >── sys_role ──< sys_role_permission >── sys_permission
                │
                └── 审批通过时写入

sys_user ──< sys_perm_request（申请角色）──> sys_role
                │
                └──< sys_perm_request_log（流转日志）
```

说明：
- 授权主路径：用户 → 角色 → 权限（RBAC）
- 申请单只绑「目标角色」，通过后写 `sys_user_role`
- `sys_user` 在现有表上扩展字段，不重建

---

## 2. 表设计

### 2.1 sys_user（扩展现有表）

现有字段保留，新增如下：

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| user_code | VARCHAR(64) | N | | 用户编码，唯一，用于登录 |
| user_name | VARCHAR(64) | N | | 姓名，可重复 |
| password | VARCHAR(128) | N | | BCrypt |
| status | TINYINT | N | 1 | 1正常 0禁用 |
| must_change_pwd | TINYINT | N | 0 | 1强制改密 |
| source | VARCHAR(16) | N | local | 账号来源 local/sso（预留） |
| external_id | VARCHAR(128) | Y | | 外部身份 ID（预留 SSO） |
| create_time | DATETIME | Y | | |
| update_time | DATETIME | Y | | |
| deleted | TINYINT | N | 0 | 逻辑删除 |

索引：
- `uk_user_code (user_code)`
- `idx_user_name (user_name)`
- `idx_external (source, external_id)`

说明：登录用 `user_code`；`user_name` 为姓名可重名；SSO 用 `external_id`。

```sql
-- 见 db/migrate_user_perm.sql
```

---

### 2.2 sys_role

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| role_code | VARCHAR(64) | N | | 角色编码，唯一 |
| role_name | VARCHAR(64) | N | | 角色名称 |
| remark | VARCHAR(255) | Y | | 备注 |
| status | TINYINT | N | 1 | 1正常 0禁用 |
| create_time | DATETIME | Y | | |
| update_time | DATETIME | Y | | |
| deleted | TINYINT | N | 0 | |

```sql
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL COMMENT '主键',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色编码',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    remark      VARCHAR(255)          COMMENT '备注',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME              COMMENT '创建时间',
    update_time DATETIME              COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';
```

---

### 2.3 sys_permission

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| parent_id | BIGINT | N | 0 | 父节点，根为 0 |
| perm_type | TINYINT | N | | 1目录 2菜单 3按钮/接口 |
| perm_code | VARCHAR(128) | Y | | 权限码；按钮必填 |
| perm_name | VARCHAR(64) | N | | 名称 |
| path | VARCHAR(255) | Y | | 前端路由（菜单） |
| icon | VARCHAR(64) | Y | | 图标 |
| sort_no | INT | N | 0 | 排序，小在前 |
| status | TINYINT | N | 1 | 1正常 0禁用 |
| create_time | DATETIME | Y | | |
| update_time | DATETIME | Y | | |
| deleted | TINYINT | N | 0 | |

```sql
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT       NOT NULL COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父ID',
    perm_type   TINYINT      NOT NULL COMMENT '1目录 2菜单 3按钮',
    perm_code   VARCHAR(128)          COMMENT '权限码',
    perm_name   VARCHAR(64)  NOT NULL COMMENT '名称',
    path        VARCHAR(255)          COMMENT '路由',
    icon        VARCHAR(64)           COMMENT '图标',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    create_time DATETIME              COMMENT '创建时间',
    update_time DATETIME              COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_parent (parent_id),
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限/菜单';
```

说明：`perm_code` 对目录可为空；MySQL 唯一索引允许多个 NULL。若要用空串，改为非空时用占位编码。

---

### 2.4 sys_user_role

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| user_id | BIGINT | N | | 用户 ID |
| role_id | BIGINT | N | | 角色 ID |
| create_time | DATETIME | Y | | |

```sql
CREATE TABLE IF NOT EXISTS sys_user_role (
    id          BIGINT   NOT NULL COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    role_id     BIGINT   NOT NULL COMMENT '角色ID',
    create_time DATETIME          COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色';
```

---

### 2.5 sys_role_permission

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| role_id | BIGINT | N | | 角色 ID |
| permission_id | BIGINT | N | | 权限 ID |
| create_time | DATETIME | Y | | |

```sql
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT   NOT NULL COMMENT '主键',
    role_id       BIGINT   NOT NULL COMMENT '角色ID',
    permission_id BIGINT   NOT NULL COMMENT '权限ID',
    create_time   DATETIME          COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限';
```

---

### 2.6 sys_perm_request（权限申请单）

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| request_no | VARCHAR(32) | N | | 单号，唯一 |
| applicant_id | BIGINT | N | | 申请人用户 ID |
| role_id | BIGINT | N | | 申请的目标角色 |
| reason | VARCHAR(512) | N | | 申请原因 |
| status | VARCHAR(16) | N | pending | pending/approved/rejected/cancelled |
| approver_id | BIGINT | Y | | 实际审批人 |
| approve_opinion | VARCHAR(512) | Y | | 审批意见 |
| approve_time | DATETIME | Y | | 审批时间 |
| oa_instance_id | VARCHAR(64) | Y | | 预留 OA 实例号 |
| create_time | DATETIME | Y | | |
| update_time | DATETIME | Y | | |
| deleted | TINYINT | N | 0 | |

```sql
CREATE TABLE IF NOT EXISTS sys_perm_request (
    id              BIGINT       NOT NULL COMMENT '主键',
    request_no      VARCHAR(32)  NOT NULL COMMENT '申请单号',
    applicant_id    BIGINT       NOT NULL COMMENT '申请人',
    role_id         BIGINT       NOT NULL COMMENT '目标角色',
    reason          VARCHAR(512) NOT NULL COMMENT '申请原因',
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/cancelled',
    approver_id     BIGINT                COMMENT '审批人',
    approve_opinion VARCHAR(512)          COMMENT '审批意见',
    approve_time    DATETIME              COMMENT '审批时间',
    oa_instance_id  VARCHAR(64)           COMMENT '预留OA实例ID',
    create_time     DATETIME              COMMENT '创建时间',
    update_time     DATETIME              COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_applicant (applicant_id),
    KEY idx_status (status),
    KEY idx_applicant_role_status (applicant_id, role_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限申请单';
```

业务约束（代码层）：同一 `applicant_id + role_id` 仅允许一笔 `status=pending`。

单号建议：`PR` + `yyyyMMdd` + 流水，或雪花转短码。

---

### 2.7 sys_perm_request_log（申请流转日志）

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| request_id | BIGINT | N | | 申请单 ID |
| from_status | VARCHAR(16) | Y | | 原状态 |
| to_status | VARCHAR(16) | N | | 新状态 |
| operator_id | BIGINT | N | | 操作人 |
| opinion | VARCHAR(512) | Y | | 意见 |
| create_time | DATETIME | Y | | |

```sql
CREATE TABLE IF NOT EXISTS sys_perm_request_log (
    id           BIGINT       NOT NULL COMMENT '主键',
    request_id   BIGINT       NOT NULL COMMENT '申请单ID',
    from_status  VARCHAR(16)           COMMENT '原状态',
    to_status    VARCHAR(16)  NOT NULL COMMENT '新状态',
    operator_id  BIGINT       NOT NULL COMMENT '操作人',
    opinion      VARCHAR(512)          COMMENT '意见',
    create_time  DATETIME              COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限申请流转日志';
```

---

### 2.8 sys_oper_log

沿用现有表，无需改结构。用户/权限相关操作写入 `module=系统` 或 `认证`。

---

## 3. 种子数据（建议）

### 角色
| role_code | role_name |
|-----------|-----------|
| admin | 超级管理员 |
| operator | 现场操作员 |
| process_eng | 工艺工程师 |
| supervisor | 班组长 |

### 权限（节选）
| perm_code | perm_name | perm_type |
|-----------|-----------|-----------|
| system | 系统管理 | 1 目录 |
| system:user | 用户管理 | 2 菜单 |
| user:list / user:add / user:edit / user:reset-pwd / user:assign-role / user:kick | 用户按钮 | 3 |
| system:role | 角色管理 | 2 |
| role:list / role:add / role:edit / role:assign-perm | 角色按钮 | 3 |
| system:permission | 权限管理 | 2 |
| perm:list / perm:add / perm:edit | 权限按钮 | 3 |
| system:perm-apply | 权限申请 | 2 |
| perm:apply | 发起申请 | 3 |
| system:perm-approve | 权限审批 | 2 |
| perm:approve | 审批 | 3 |

`admin` 角色绑定全部权限；`admin` 用户绑定 `admin` 角色。

---

## 4. 查询要点

### 用户权限码集合
```sql
SELECT DISTINCT p.perm_code
FROM sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 1
WHERE ur.user_id = ?
  AND p.perm_code IS NOT NULL
  AND p.perm_code <> '';
```

### 审批通过（事务内）
1. `UPDATE sys_perm_request SET status='approved', ... WHERE id=? AND status='pending'`（影响行=1 才继续）
2. `INSERT IGNORE sys_user_role (user_id, role_id, ...)`
3. `INSERT sys_perm_request_log`
4. 写 `sys_oper_log`；可选踢下线

---

## 5. 预留扩展

| 场景 | 用法 |
|------|------|
| SSO | `sys_user.source='sso'` + `external_id` |
| OA 审批 | 填 `oa_instance_id`；状态仍由回调更新本表 |
| 数据权限 | 新增 `sys_user_data_scope`（本期不做） |
| 多级审批 | 新增审批节点表（本期单级，不建） |

---

## 6. 表清单汇总

| 表名 | 动作 |
|------|------|
| sys_user | ALTER 扩展 3 字段 |
| sys_role | 新建 |
| sys_permission | 新建 |
| sys_user_role | 新建 |
| sys_role_permission | 新建 |
| sys_perm_request | 新建 |
| sys_perm_request_log | 新建 |
| sys_oper_log | 沿用 |
