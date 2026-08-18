-- Rework 回流：边表 + Lot 计数 + 权限
-- 已有库执行本脚本
-- 对应：docs/模块/Route（工艺路线）模块/MES-Rework接口设计.md

CREATE TABLE IF NOT EXISTS mes_route_edge (
    id                 BIGINT        NOT NULL COMMENT '主键',
    version_id         BIGINT        NOT NULL COMMENT '路线版本ID',
    from_sort_no       INT           NOT NULL COMMENT '触发站顺序号',
    to_sort_no         INT           NOT NULL COMMENT '目标站顺序号',
    edge_type          VARCHAR(16)   NOT NULL COMMENT 'normal/branch/rework/skip_allow',
    max_rework_count   INT                    COMMENT 'rework 次数上限',
    reason_codes       VARCHAR(256)           COMMENT '逗号分隔原因码；空=任意',
    sort_no            INT           NOT NULL DEFAULT 0 COMMENT '同站多边排序',
    create_time        DATETIME               COMMENT '创建时间',
    update_time        DATETIME               COMMENT '更新时间',
    deleted            TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ver_edge (version_id, from_sort_no, to_sort_no, edge_type),
    KEY idx_ver_from (version_id, from_sort_no, edge_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线版本边（含回流）';

SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'rework_counts'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_lot ADD COLUMN rework_counts VARCHAR(512) NULL COMMENT ''按触发站累计返工次数 JSON'' AFTER current_eqp_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_tx_log' AND COLUMN_NAME = 'ext_json'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_tx_log ADD COLUMN ext_json VARCHAR(512) NULL COMMENT ''事务扩展JSON'' AFTER remark',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 演示：METRO-01(60) → PHOTO-01(10)，上限 2
INSERT INTO mes_route_edge (id, version_id, from_sort_no, to_sort_no, edge_type, max_rework_count, reason_codes, sort_no, create_time, update_time, deleted)
VALUES (5301, 5101, 60, 10, 'rework', 2, 'CD_FAIL,OVERLAY_FAIL', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  max_rework_count = VALUES(max_rework_count),
  reason_codes = VALUES(reason_codes),
  update_time = NOW();

-- 权限 track:rework（挂现场台 290）
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(295, 290, 3, 'track:rework', '返工', NULL, NULL, 5, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1130, 1, 295, NOW()),
(1324, 3, 295, NOW()),
(1413, 4, 295, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
