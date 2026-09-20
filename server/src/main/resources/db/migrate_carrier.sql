-- Car-1：载具台账 + 绑定表 + Lot.carrier_id + 权限 + 配置（业务绑解见 Car-3）
-- 已有库执行一次；列已存在时 ALTER 会报错，可忽略该句。

-- =========================
-- mes_carrier
-- =========================
CREATE TABLE IF NOT EXISTS mes_carrier (
    id               BIGINT         NOT NULL COMMENT '主键',
    carrier_code     VARCHAR(64)    NOT NULL COMMENT '载具编码（扫码）',
    carrier_type     VARCHAR(32)    NOT NULL DEFAULT 'FOUP' COMMENT '类型',
    capacity         INT            NOT NULL DEFAULT 25 COMMENT '槽位数',
    status           VARCHAR(32)    NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/IN_USE/QUARANTINE/SCRAPPED',
    clean_status     VARCHAR(32)             DEFAULT 'UNKNOWN' COMMENT 'CLEAN/DIRTY/UNKNOWN',
    location_type    VARCHAR(32)             DEFAULT 'NONE' COMMENT 'NONE/STOCKER/PORT/OHB/MANUAL',
    location_ref     VARCHAR(128)            COMMENT '位置引用',
    remark           VARCHAR(512)            COMMENT '备注',
    version          INT            NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by        BIGINT                  COMMENT '创建人',
    create_time      DATETIME                COMMENT '创建时间',
    update_by        BIGINT                  COMMENT '更新人',
    update_time      DATETIME                COMMENT '更新时间',
    deleted          TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_carrier_code (carrier_code),
    KEY idx_carrier_status (status),
    KEY idx_carrier_location (location_type, location_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='载具台账';

-- =========================
-- mes_carrier_binding（当前绑定；解绑物理删）
-- =========================
CREATE TABLE IF NOT EXISTS mes_carrier_binding (
    id               BIGINT         NOT NULL COMMENT '主键',
    carrier_id       BIGINT         NOT NULL COMMENT '载具ID',
    lot_id           BIGINT         NOT NULL COMMENT '批次ID',
    bind_time        DATETIME       NOT NULL COMMENT '绑定时间',
    bind_by          BIGINT                  COMMENT '绑定人',
    create_time      DATETIME                COMMENT '创建时间',
    update_time      DATETIME                COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_binding_lot (lot_id),
    UNIQUE KEY uk_binding_carrier (carrier_id),
    KEY idx_binding_bind_time (bind_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='载具当前绑定';

-- =========================
-- mes_lot.carrier_id
-- =========================
ALTER TABLE mes_lot
    ADD COLUMN carrier_id BIGINT NULL COMMENT '当前载具ID' AFTER current_eqp_id;

ALTER TABLE mes_lot
    ADD KEY idx_lot_carrier (carrier_id);

-- =========================
-- 权限：320 菜单 view；321 edit；322 bind（菜单页 Car-5 再用 path）
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(320, 200, 2, 'carrier:view', '载具', '/app/carrier', 'box', 155, 1, NOW(), NOW(), 0),
(321, 320, 3, 'carrier:edit', '载具编辑', NULL, NULL, 1, 1, NOW(), NOW(), 0),
(322, 320, 3, 'carrier:bind', '载具绑解', NULL, NULL, 2, 1, NOW(), NOW(), 0)
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
(1148, 1, 320, NOW()),
(1149, 1, 321, NOW()),
(1150, 1, 322, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1343, 3, 320, NOW()),
(1344, 3, 321, NOW()),
(1345, 3, 322, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：看 + 绑解
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1428, 4, 320, NOW()),
(1429, 4, 322, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- operator：看 + 绑解
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1211, 2, 320, NOW()),
(1212, 2, 322, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
