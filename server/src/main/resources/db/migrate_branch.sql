-- 条件/可选分支：mes_route_edge.condition_code + 唯一键含 condition
-- 已有库执行本脚本
-- 对应：docs/模块/Route（工艺路线）模块/MES-Branch接口设计.md

SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_edge' AND COLUMN_NAME = 'condition_code'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_edge ADD COLUMN condition_code VARCHAR(64) NULL COMMENT ''branch 条件码'' AFTER reason_codes',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 允许同站同目标不同 condition 的 branch（PASS/FAIL 可同指一站）
SET @idx := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_edge' AND INDEX_NAME = 'uk_ver_edge'
);
SET @sql := IF(@idx > 0, 'ALTER TABLE mes_route_edge DROP INDEX uk_ver_edge', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx2 := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_edge' AND INDEX_NAME = 'uk_ver_edge_cond'
);
SET @sql := IF(@idx2 = 0,
  'ALTER TABLE mes_route_edge ADD UNIQUE KEY uk_ver_edge_cond (version_id, from_sort_no, to_sort_no, edge_type, condition_code)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
