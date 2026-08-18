-- Equipment 模块：设备主数据 + 权限按钮
-- 已有库执行本脚本
-- 对应文档：docs/模块/Equipment（设备）模块/MES-Equipment数据库设计.md

-- =========================
-- 设备主表 mes_eqp
-- status 枚举：
--   idle     = 空闲（可 TrackIn）
--   running  = 加工中（可 TrackIn）
--   down     = 故障
--   pm       = 保养
--   eng      = 工程
--   offline  = 离线
-- enabled：1启用 0停用
-- =========================
CREATE TABLE IF NOT EXISTS mes_eqp (
    id            BIGINT       NOT NULL COMMENT '主键',
    eqp_code      VARCHAR(64)  NOT NULL COMMENT '设备编码（唯一）',
    eqp_name      VARCHAR(128) NOT NULL COMMENT '设备名称',
    eqp_type      VARCHAR(64)           COMMENT '设备类型（对齐 mes_step.eqp_type）',
    area          VARCHAR(64)           COMMENT '区域/Bay',
    status        VARCHAR(16)  NOT NULL DEFAULT 'idle' COMMENT '业务态: idle/running/down/pm/eng/offline',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark        VARCHAR(256)          COMMENT '备注',
    version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by     BIGINT                COMMENT '创建人',
    update_by     BIGINT                COMMENT '更新人',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eqp_code (eqp_code),
    KEY idx_eqp_status (status, enabled),
    KEY idx_eqp_type (eqp_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备主数据（最小集）';

-- 已有表补乐观锁列
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_eqp' AND COLUMN_NAME = 'version'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_eqp ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁'' AFTER remark',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO mes_eqp (
  id, eqp_code, eqp_name, eqp_type, area, status, enabled, remark, version,
  create_time, update_time, deleted
) VALUES
(9001, 'EQP-ETCH-A1',  '刻蚀机 A1', 'ETCH',  '刻蚀', 'idle', 1, NULL, 0, NOW(), NOW(), 0),
(9002, 'EQP-ETCH-A2',  '刻蚀机 A2', 'ETCH',  '刻蚀', 'idle', 1, NULL, 0, NOW(), NOW(), 0),
(9003, 'EQP-CMP-B2',   '抛光机 B2', 'CMP',   'CMP',  'idle', 1, NULL, 0, NOW(), NOW(), 0),
(9004, 'EQP-PHOTO-E1', '光刻机 E1', 'PHOTO', '光刻', 'pm',   1, '保养中', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  eqp_name = VALUES(eqp_name),
  eqp_type = VALUES(eqp_type),
  area = VALUES(area),
  status = VALUES(status),
  enabled = VALUES(enabled),
  remark = VALUES(remark),
  update_time = NOW();

-- =========================
-- 权限按钮（菜单 eqp:list=240 已有）
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(241, 240, 3, 'eqp:add',    '设备新增', NULL, NULL, 1, 1, NOW(), NOW(), 0),
(242, 240, 3, 'eqp:edit',   '设备编辑', NULL, NULL, 2, 1, NOW(), NOW(), 0),
(243, 240, 3, 'eqp:status', '设备改态', NULL, NULL, 3, 1, NOW(), NOW(), 0)
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
(1121, 1, 241, NOW()),
(1122, 1, 242, NOW()),
(1123, 1, 243, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng：主数据 + 改态
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1315, 3, 241, NOW()),
(1316, 3, 242, NOW()),
(1317, 3, 243, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：改态
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1408, 4, 240, NOW()),
(1409, 4, 243, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
