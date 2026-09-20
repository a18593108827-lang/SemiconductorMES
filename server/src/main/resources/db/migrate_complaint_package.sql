-- CP-1：客诉追溯包表 + 权限 + 配置（业务见 CP-2+）
-- 已有库执行一次。

-- =========================
-- mes_complaint_package
-- =========================
CREATE TABLE IF NOT EXISTS mes_complaint_package (
    id               BIGINT         NOT NULL COMMENT '主键=packageId',
    package_no       VARCHAR(64)    NOT NULL COMMENT '业务号 CP-yyyyMMdd-序号',
    anchor_lot_id    BIGINT         NOT NULL COMMENT '锚点Lot',
    anchor_lot_no    VARCHAR(64)    NOT NULL COMMENT '锚点Lot号快照',
    direction        VARCHAR(16)    NOT NULL COMMENT 'up/down/both',
    depth            INT            NOT NULL COMMENT '展开深度',
    member_count     INT            NOT NULL DEFAULT 0 COMMENT '成员数快照',
    truncated        TINYINT        NOT NULL DEFAULT 0 COMMENT '是否截断 0/1',
    reason_code      VARCHAR(64)             COMMENT '调查原因码',
    remark           VARCHAR(512)            COMMENT '备注',
    status           VARCHAR(32)    NOT NULL DEFAULT 'READY' COMMENT 'READY/CONTAINING/CONTAINED/VOID',
    create_by        BIGINT                  COMMENT '创建人',
    create_time      DATETIME                COMMENT '创建时间',
    contain_by       BIGINT                  COMMENT '首次遏制人',
    contain_time     DATETIME                COMMENT '首次遏制时间',
    update_time      DATETIME                COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_complaint_package_no (package_no),
    KEY idx_complaint_pkg_anchor (anchor_lot_id),
    KEY idx_complaint_pkg_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客诉追溯包头';

-- =========================
-- mes_complaint_package_member
-- =========================
CREATE TABLE IF NOT EXISTS mes_complaint_package_member (
    id                 BIGINT         NOT NULL COMMENT '主键',
    package_id         BIGINT         NOT NULL COMMENT '包ID',
    lot_id             BIGINT         NOT NULL COMMENT '成员Lot',
    lot_no             VARCHAR(64)    NOT NULL COMMENT '成员Lot号快照',
    relation           VARCHAR(32)    NOT NULL COMMENT 'ANCHOR/ANCESTOR/DESCENDANT',
    depth_from_anchor  INT            NOT NULL DEFAULT 0 COMMENT '相对锚点深度',
    qty_snapshot       INT                     COMMENT '生成时qty',
    status_snapshot    VARCHAR(32)             COMMENT '生成时status',
    create_time        DATETIME                COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_complaint_pkg_lot (package_id, lot_id),
    KEY idx_complaint_member_lot (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客诉追溯包成员';

-- =========================
-- 权限：挂在历史(280)下；330 view / 331 build / 332 contain
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(330, 280, 3, 'complaint:view',    '追溯包查看', NULL, NULL, 1, 1, NOW(), NOW(), 0),
(331, 280, 3, 'complaint:build',   '追溯包生成', NULL, NULL, 2, 1, NOW(), NOW(), 0),
(332, 280, 3, 'complaint:contain', '追溯包遏制', NULL, NULL, 3, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  path = VALUES(path),
  icon = VALUES(icon),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

-- admin
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1151, 1, 330, NOW()),
(1152, 1, 331, NOW()),
(1153, 1, 332, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1346, 3, 330, NOW()),
(1347, 3, 331, NOW()),
(1348, 3, 332, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：看 + 生成 + 遏制
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1430, 4, 330, NOW()),
(1431, 4, 331, NOW()),
(1432, 4, 332, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
