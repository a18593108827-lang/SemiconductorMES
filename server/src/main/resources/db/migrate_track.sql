-- Track 模块：Lot 运行态字段 + 事务履历 + 权限按钮
-- 已有库执行本脚本；依赖 mes_lot
-- 对应文档：docs/模块/Track（执行引擎）模块/MES-Track数据库设计.md

-- =========================
-- mes_lot 运行态字段（可重复执行）
-- =========================
SET @db := DATABASE();

SET @exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'current_sort_no'
);
SET @sql := IF(@exist = 0,
  'ALTER TABLE mes_lot ADD COLUMN current_sort_no INT NULL COMMENT ''当前站顺序号（快照内）'' AFTER route_version_id',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'current_step_id'
);
SET @sql := IF(@exist = 0,
  'ALTER TABLE mes_lot ADD COLUMN current_step_id BIGINT NULL COMMENT ''当前工序ID'' AFTER current_sort_no',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'current_eqp_id'
);
SET @sql := IF(@exist = 0,
  'ALTER TABLE mes_lot ADD COLUMN current_eqp_id BIGINT NULL COMMENT ''当前设备ID（TrackIn后）'' AFTER current_step_id',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE mes_lot
  MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'created'
    COMMENT '状态: created已创建/released已放行/wait等待加工/processing加工中/held锁批/completed已完工/scrapped已报废';

-- 已放行演示 Lot：进入首站 wait（PHOTO-01 sort=10）
UPDATE mes_lot
SET status = 'wait',
    current_sort_no = 10,
    current_step_id = 5001,
    current_eqp_id = NULL,
    update_time = NOW()
WHERE id = 6002 AND route_version_id IS NOT NULL;

-- =========================
-- 事务履历（只追加）
-- =========================
CREATE TABLE IF NOT EXISTS mes_tx_log (
    id                BIGINT        NOT NULL COMMENT '主键',
    lot_id            BIGINT        NOT NULL COMMENT '批次ID',
    lot_no            VARCHAR(64)            COMMENT '批次号冗余',
    tx_type           VARCHAR(32)   NOT NULL COMMENT '事务类型: RELEASE放行/MOVE移站/TRACK_IN开工/TRACK_OUT完工/HOLD锁批/RELEASE_HOLD解锁/SKIP跳站/REWORK返工',
    from_status       VARCHAR(32)            COMMENT '变更前状态(同mes_lot.status枚举)',
    to_status         VARCHAR(32)            COMMENT '变更后状态(同mes_lot.status枚举)',
    from_sort_no      INT                    COMMENT '变更前站序',
    to_sort_no        INT                    COMMENT '变更后站序',
    step_id           BIGINT                 COMMENT '相关工序',
    eqp_id            BIGINT                 COMMENT '相关设备',
    route_version_id  BIGINT                 COMMENT '路线版本快照',
    remark            VARCHAR(512)           COMMENT '备注',
    oper_user_id      BIGINT                 COMMENT '操作人',
    oper_user_name    VARCHAR(64)            COMMENT '操作人名称冗余',
    create_time       DATETIME      NOT NULL COMMENT '事务时间',
    PRIMARY KEY (id),
    KEY idx_tx_lot_time (lot_id, create_time),
    KEY idx_tx_type_time (tx_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Track事务履历（只追加）';

-- 演示：6002 放行履历
INSERT INTO mes_tx_log (
  id, lot_id, lot_no, tx_type,
  from_status, to_status, from_sort_no, to_sort_no,
  step_id, eqp_id, route_version_id, remark,
  oper_user_id, oper_user_name, create_time
) VALUES
(7001, 6002, 'LOT-N7-DEMO-02', 'RELEASE',
 'created', 'wait', NULL, 10,
 5001, NULL, 5101, '演示放行进首站',
 1, 'admin', NOW())
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- =========================
-- 权限：补 track:release / track:move（290/291/292 已有）
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(293, 290, 3, 'track:release', '放行', NULL, NULL, 3, 1, NOW(), NOW(), 0),
(294, 290, 3, 'track:move',    '移站', NULL, NULL, 4, 1, NOW(), NOW(), 0)
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
(1119, 1, 293, NOW()),
(1120, 1, 294, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- operator
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1206, 2, 293, NOW()),
(1207, 2, 294, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng：放行可见（移站可选）
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1314, 3, 293, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
