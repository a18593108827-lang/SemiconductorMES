---
type: 数据库设计
module: Route
status: done
slices: []
aligns: []
updated: 2026-09-20
---

# MES 工艺路线（Route）数据库设计

> 对应功能文档：`docs/模块/Route（工艺路线）模块/MES-Route功能文档.md`  
> 查验清单：`docs/模块/Route（工艺路线）模块/MES-Route已完成功能.md`  
> 约定：主键雪花 `BIGINT`；时间 `DATETIME`；逻辑删除 `deleted`；字符集 `utf8mb4`

---

## 1. ER 关系

```
mes_step
    ↑（多对多语义：版本内引用）
mes_route ──< mes_route_version ──< mes_route_step
                                      │
                                      └── step_id → mes_step

Lot（已落地）── route_version_id → mes_route_version   （放行快照）
```

说明：
- `mes_route` 为路线壳；可执行内容在 `mes_route_version`
- `mes_route_step` 只挂在版本上；发布后行数据只读
- 一期 `product_code` 挂在路线头，不强制 Product 表

---

## 2. 表设计

### 2.1 mes_step（工序）

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| step_code | VARCHAR(64) | N | | 工序编码，唯一 |
| step_name | VARCHAR(128) | N | | 名称 |
| step_type | TINYINT | N | 1 | 1加工 2量测 3其它 |
| eqp_type | VARCHAR(64) | Y | | 设备类型（预留，一期可空） |
| status | TINYINT | N | 1 | 1正常 0禁用 |
| remark | VARCHAR(255) | Y | | |
| create_time | DATETIME | Y | | |
| update_time | DATETIME | Y | | |
| deleted | TINYINT | N | 0 | |

索引：
- `uk_step_code (step_code)`
- `idx_step_status (status)`

```sql
CREATE TABLE IF NOT EXISTS mes_step (
    id          BIGINT        NOT NULL COMMENT '主键',
    step_code   VARCHAR(64)   NOT NULL COMMENT '工序编码',
    step_name   VARCHAR(128)  NOT NULL COMMENT '工序名称',
    step_type   TINYINT       NOT NULL DEFAULT 1 COMMENT '1加工 2量测 3其它',
    eqp_type    VARCHAR(64)            COMMENT '设备类型预留',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    remark      VARCHAR(255)           COMMENT '备注',
    create_time DATETIME               COMMENT '创建时间',
    update_time DATETIME               COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_step_code (step_code),
    KEY idx_step_status (status)
) COMMENT='工序定义';
```

---

### 2.2 mes_route（路线）

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| route_code | VARCHAR(64) | N | | 路线编码，唯一 |
| route_name | VARCHAR(128) | N | | 名称 |
| product_code | VARCHAR(64) | Y | | 关联产品编码（一期字符串） |
| status | TINYINT | N | 1 | 1正常 0停用（停用后不可新 Release） |
| remark | VARCHAR(255) | Y | | |
| create_time | DATETIME | Y | | |
| update_time | DATETIME | Y | | |
| deleted | TINYINT | N | 0 | |

索引：
- `uk_route_code (route_code)`
- `idx_route_product (product_code)`

```sql
CREATE TABLE IF NOT EXISTS mes_route (
    id           BIGINT        NOT NULL COMMENT '主键',
    route_code   VARCHAR(64)   NOT NULL COMMENT '路线编码',
    route_name   VARCHAR(128)  NOT NULL COMMENT '路线名称',
    product_code VARCHAR(64)            COMMENT '产品编码',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    remark       VARCHAR(255)           COMMENT '备注',
    create_time  DATETIME               COMMENT '创建时间',
    update_time  DATETIME               COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_code (route_code),
    KEY idx_route_product (product_code)
) COMMENT='工艺路线';
```

---

### 2.3 mes_route_version（路线版本）

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| route_id | BIGINT | N | | 路线 ID |
| version_no | INT | N | | 版本号，自 1 递增 |
| status | VARCHAR(16) | N | draft | draft / active / archived |
| published_at | DATETIME | Y | | 发布时间 |
| published_by | BIGINT | Y | | 发布人 user_id |
| remark | VARCHAR(255) | Y | | |
| create_time | DATETIME | Y | | |
| update_time | DATETIME | Y | | |
| deleted | TINYINT | N | 0 | |

索引：
- `uk_route_ver (route_id, version_no)`
- `idx_route_ver_status (route_id, status)`

约束（应用层）：
- 同一 `route_id` 至多一条 `status=active`

```sql
CREATE TABLE IF NOT EXISTS mes_route_version (
    id            BIGINT       NOT NULL COMMENT '主键',
    route_id      BIGINT       NOT NULL COMMENT '路线ID',
    version_no    INT          NOT NULL COMMENT '版本号',
    status        VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/active/archived',
    published_at  DATETIME              COMMENT '发布时间',
    published_by  BIGINT                COMMENT '发布人',
    remark        VARCHAR(255)          COMMENT '备注',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_ver (route_id, version_no),
    KEY idx_route_ver_status (route_id, status)
) COMMENT='工艺路线版本';
```

---

### 2.4 mes_route_step（版本内步骤）

| 字段 | 类型 | 空 | 默认 | 说明 |
|------|------|----|------|------|
| id | BIGINT | N | | 主键 |
| version_id | BIGINT | N | | 版本 ID |
| step_id | BIGINT | N | | 工序 ID |
| sort_no | INT | N | | 顺序号（建议 10/20/30） |
| next_sort_no | INT | Y | | 下一站 sort_no；空=结束 |
| create_time | DATETIME | Y | | |
| update_time | DATETIME | Y | | |
| deleted | TINYINT | N | 0 | |

索引：
- `uk_ver_sort (version_id, sort_no)`
- `idx_ver_step (version_id, step_id)`

说明：一期线性路径用 `next_sort_no` 足够；二期分支可另表，不必改主语义。

```sql
CREATE TABLE IF NOT EXISTS mes_route_step (
    id            BIGINT   NOT NULL COMMENT '主键',
    version_id    BIGINT   NOT NULL COMMENT '版本ID',
    step_id       BIGINT   NOT NULL COMMENT '工序ID',
    sort_no       INT      NOT NULL COMMENT '顺序号',
    next_sort_no  INT               COMMENT '下一站顺序号，空结束',
    create_time   DATETIME          COMMENT '创建时间',
    update_time   DATETIME          COMMENT '更新时间',
    deleted       TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ver_sort (version_id, sort_no),
    KEY idx_ver_step (version_id, step_id)
) COMMENT='路线版本步骤';
```

---

## 3. Lot 侧字段（已落地，见 Lot 库表文档）

| 字段 | 类型 | 说明 |
|------|------|------|
| route_id | BIGINT | 路线（冗余，便于查询） |
| route_version_id | BIGINT | **放行快照**，Track 只认这个 |

未 Release：`route_version_id` 可空；Release 后锁定。  
详表：`docs/模块/Lot（批次）模块/MES-Lot数据库设计.md`。

---

## 4. 种子数据建议

对齐前端 mock（`RoutePage`）：

| step_code | step_name |
|-----------|-----------|
| PHOTO-01 | 光刻 |
| ETCH-01 | 刻蚀一 |
| ETCH-02 | 刻蚀二 |
| CMP-01 | 化学机械抛光 |
| DIFF-03 | 扩散 |
| METRO-01 | 量测 |

路线：`WAFER-N7-MAIN` / 晶圆主工艺，发布为 `active` v1。

---

## 5. 权限种子（sys_permission）

挂在「生产执行」菜单 `route:list`（id=250）下：

| id | perm_code | 名称 |
|----|-----------|------|
| 250 | `route:list` | 路线（菜单，已有） |
| 251 | `route:add` | 路线新增 |
| 252 | `route:edit` | 路线编辑/发布 |

`admin` / `process_eng` 绑定 250–252。

SQL 脚本：
- 已有库：`server/src/main/resources/db/migrate_route.sql`
- 新库：已并入 `schema.sql`

---

## 6. 包结构建议

```
com.mes.route
  ├── api          # Controller
  ├── application  # Service
  ├── domain       # 实体 / 枚举
  └── infrastructure  # Mapper
```

与现有 `com.mes.auth` / `com.mes.system` 并列，符合架构「单体模块化」。
