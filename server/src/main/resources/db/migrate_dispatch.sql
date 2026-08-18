-- Dispatch 模块：设备预约（候选/推荐无表）+ 权限
-- 已有库执行本脚本
-- 对应文档：docs/模块/Dispatch（派工）模块/MES-Dispatch数据库设计.md
-- 二期 mes_dispatch_rule 不在此脚本

-- =========================
-- 预约表 mes_dispatch_reserve
-- status 枚举：
--   active    = 生效中（未过期；占机；他 Lot 不可再约同机）
--   released  = 已释约（人工取消）
--   expired   = 已超时（超过 expire_time）
--   consumed  = 已消费（TrackIn 成功后终态）
-- deleted：0正常 1逻辑删
-- 并发约束：eqp_slot/lot_slot + version 乐观锁
-- 已建库补列见 migrate_dispatch_reserve_slot.sql / migrate_dispatch_reserve_version.sql
-- =========================
CREATE TABLE IF NOT EXISTS mes_dispatch_reserve (
    id               BIGINT       NOT NULL COMMENT '主键',
    lot_id           BIGINT       NOT NULL COMMENT '批次ID',
    lot_slot         BIGINT                COMMENT 'active 时=lot_id，否则 NULL',
    eqp_id           BIGINT       NOT NULL COMMENT '设备ID',
    eqp_slot         BIGINT                COMMENT 'active 时=eqp_id，否则 NULL',
    status           VARCHAR(16)  NOT NULL COMMENT '预约态: active生效中/released已释约/expired已超时/consumed已消费',
    expire_time      DATETIME     NOT NULL COMMENT '超时时刻',
    reserve_user_id  BIGINT                COMMENT '预约人',
    consume_tx_id    BIGINT                COMMENT '消费时关联 mes_tx_log.id（可选）',
    remark           VARCHAR(256)          COMMENT '备注',
    version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time      DATETIME              COMMENT '创建时间',
    update_time      DATETIME              COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reserve_eqp_slot (eqp_slot),
    UNIQUE KEY uk_reserve_lot_slot (lot_slot),
    KEY idx_reserve_lot_status (lot_id, status),
    KEY idx_reserve_eqp_status (eqp_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派工设备预约（可选；候选推荐无表）';

-- =========================
-- 权限（挂生产执行目录 200）
-- 244 dispatch:view    菜单（可选 Admin 页）
-- 245 dispatch:reserve 按钮 预约/释约
-- 候选 API 也可 track:view OR dispatch:view
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(244, 200, 2, 'dispatch:view',    '派工',     '/app/dispatch', 'clipboard-list', 145, 1, NOW(), NOW(), 0),
(245, 244, 3, 'dispatch:reserve', '设备预约', NULL,             NULL,            1,   1, NOW(), NOW(), 0)
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
(1124, 1, 244, NOW()),
(1125, 1, 245, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng：查看 + 预约
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1318, 3, 244, NOW()),
(1319, 3, 245, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：查看 + 预约
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1410, 4, 244, NOW()),
(1411, 4, 245, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
