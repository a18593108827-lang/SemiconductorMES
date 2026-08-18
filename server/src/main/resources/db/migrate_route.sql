-- Route 模块：表结构 + 演示种子 + 权限按钮
-- 已有库执行本脚本；新库也可执行（CREATE IF NOT EXISTS + ON DUPLICATE KEY）
-- 对应文档：docs/模块/Route（工艺路线）模块/MES-Route数据库设计.md

-- =========================
-- 表结构
-- =========================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序定义';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺路线';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺路线版本';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线版本步骤';

-- =========================
-- 种子：工序（对齐 RoutePage mock）
-- =========================
INSERT INTO mes_step (id, step_code, step_name, step_type, eqp_type, status, remark, create_time, update_time, deleted) VALUES
(5001, 'PHOTO-01', '光刻',         1, NULL, 1, NULL, NOW(), NOW(), 0),
(5002, 'ETCH-01',  '刻蚀一',       1, NULL, 1, NULL, NOW(), NOW(), 0),
(5003, 'ETCH-02',  '刻蚀二',       1, NULL, 1, NULL, NOW(), NOW(), 0),
(5004, 'CMP-01',   '化学机械抛光', 1, NULL, 1, NULL, NOW(), NOW(), 0),
(5005, 'DIFF-03',  '扩散',         1, NULL, 1, NULL, NOW(), NOW(), 0),
(5006, 'METRO-01', '量测',         2, NULL, 1, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  step_name = VALUES(step_name),
  step_type = VALUES(step_type),
  status = VALUES(status),
  update_time = NOW();

-- =========================
-- 种子：路线 + 生效 v1
-- =========================
INSERT INTO mes_route (id, route_code, route_name, product_code, status, remark, create_time, update_time, deleted) VALUES
(5100, 'WAFER-N7-MAIN', '晶圆主工艺', 'N7', 1, '演示路线', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  route_name = VALUES(route_name),
  product_code = VALUES(product_code),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO mes_route_version (id, route_id, version_no, status, published_at, published_by, remark, create_time, update_time, deleted) VALUES
(5101, 5100, 1, 'active', NOW(), 1, '初始发布', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  published_at = VALUES(published_at),
  update_time = NOW();

INSERT INTO mes_route_step (id, version_id, step_id, sort_no, next_sort_no, create_time, update_time, deleted) VALUES
(5201, 5101, 5001, 10, 20,   NOW(), NOW(), 0),
(5202, 5101, 5002, 20, 30,   NOW(), NOW(), 0),
(5203, 5101, 5003, 30, 40,   NOW(), NOW(), 0),
(5204, 5101, 5004, 40, 50,   NOW(), NOW(), 0),
(5205, 5101, 5005, 50, 60,   NOW(), NOW(), 0),
(5206, 5101, 5006, 60, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  step_id = VALUES(step_id),
  next_sort_no = VALUES(next_sort_no),
  update_time = NOW();

-- =========================
-- 权限：route:add / route:edit（菜单 route:list 已有 id=250）
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(251, 250, 3, 'route:add',  '路线新增', NULL, NULL, 1, 1, NOW(), NOW(), 0),
(252, 250, 3, 'route:edit', '路线编辑', NULL, NULL, 2, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

-- admin
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1114, 1, 251, NOW()),
(1115, 1, 252, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1309, 3, 251, NOW()),
(1310, 3, 252, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
