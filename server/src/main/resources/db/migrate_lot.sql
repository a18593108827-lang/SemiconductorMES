-- Lot 模块：表结构 + 演示种子 + 权限按钮
-- 已有库执行本脚本；依赖 Route（mes_route / mes_route_version 演示种子 5100/5101）
-- 对应文档：docs/模块/Lot（批次）模块/MES-Lot数据库设计.md

-- =========================
-- 表结构
-- =========================
CREATE TABLE IF NOT EXISTS mes_lot (
    id                BIGINT        NOT NULL COMMENT '主键',
    lot_no            VARCHAR(64)   NOT NULL COMMENT '批次号',
    product_code      VARCHAR(64)            COMMENT '产品编码',
    qty               INT           NOT NULL DEFAULT 0 COMMENT '数量',
    priority          INT           NOT NULL DEFAULT 50 COMMENT '优先级1-100，越大越急，默认50',
    customer_lot      VARCHAR(64)            COMMENT '客户Lot',
    route_id          BIGINT                 COMMENT '路线ID',
    route_version_id  BIGINT                 COMMENT '放行快照版本ID，Release后锁定',
    status            VARCHAR(32)   NOT NULL DEFAULT 'created' COMMENT 'created/released/completed/scrapped',
    remark            VARCHAR(512)           COMMENT '备注',
    version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by         BIGINT                 COMMENT '创建人',
    create_time       DATETIME               COMMENT '创建时间',
    update_by         BIGINT                 COMMENT '更新人',
    update_time       DATETIME               COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot_no (lot_no),
    KEY idx_lot_status (status),
    KEY idx_lot_product (product_code),
    KEY idx_lot_route (route_id),
    KEY idx_lot_route_ver (route_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次';

CREATE TABLE IF NOT EXISTS mes_lot_no_seq (
    seq_day  CHAR(8) NOT NULL COMMENT 'yyyyMMdd',
    next_no  INT     NOT NULL COMMENT '当日已分配流水（当前最大值）',
    PRIMARY KEY (seq_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次号按日流水';

-- 已建表时补默认/注释（可重复执行）
ALTER TABLE mes_lot
  MODIFY COLUMN priority INT NOT NULL DEFAULT 50 COMMENT '优先级1-100，越大越急，默认50';

-- =========================
-- 种子：演示 Lot（依赖 WAFER-N7-MAIN v1 = 5100/5101）
-- =========================
INSERT INTO mes_lot (
  id, lot_no, product_code, qty, priority, customer_lot,
  route_id, route_version_id, status, remark, version,
  create_by, create_time, update_by, update_time, deleted
) VALUES
(6001, 'LOT-N7-DEMO-01', 'N7', 25, 50, NULL,
 5100, NULL, 'created', '未放行演示', 0,
 1, NOW(), 1, NOW(), 0),
(6002, 'LOT-N7-DEMO-02', 'N7', 25, 80, 'CUST-001',
 5100, 5101, 'released', '已放行演示（绑 active v1）', 0,
 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE
  product_code = VALUES(product_code),
  qty = VALUES(qty),
  priority = VALUES(priority),
  customer_lot = VALUES(customer_lot),
  route_id = VALUES(route_id),
  route_version_id = VALUES(route_version_id),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();

-- =========================
-- 权限：菜单 lot:list 已有 id=220；补按钮
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(221, 220, 3, 'lot:add',     '批次新增', NULL, NULL, 1, 1, NOW(), NOW(), 0),
(222, 220, 3, 'lot:edit',    '批次编辑', NULL, NULL, 2, 1, NOW(), NOW(), 0),
(223, 220, 3, 'lot:release', '批次放行', NULL, NULL, 3, 1, NOW(), NOW(), 0)
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
(1116, 1, 221, NOW()),
(1117, 1, 222, NOW()),
(1118, 1, 223, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1311, 3, 221, NOW()),
(1312, 3, 222, NOW()),
(1313, 3, 223, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
