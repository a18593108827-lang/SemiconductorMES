-- Queue Time：边时间窗 + Lot 开窗运行态 + Hold 原因码
-- 对应：docs/模块/Route（工艺路线）模块/MES-QueueTime接口设计.md

SET @db := DATABASE();

-- mes_route_edge.max_queue_min
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_edge' AND COLUMN_NAME = 'max_queue_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_edge ADD COLUMN max_queue_min INT NULL COMMENT ''QueueTime上限分钟'' AFTER max_rework_count',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_route_edge.min_queue_min
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_edge' AND COLUMN_NAME = 'min_queue_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_edge ADD COLUMN min_queue_min INT NULL COMMENT ''QueueTime下限预留'' AFTER max_queue_min',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_route_edge.on_violate
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_edge' AND COLUMN_NAME = 'on_violate'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_edge ADD COLUMN on_violate VARCHAR(16) NULL COMMENT ''HOLD|ALARM|HOLD_ALARM'' AFTER min_queue_min',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_lot.qtime_from_sort
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'qtime_from_sort'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_lot ADD COLUMN qtime_from_sort INT NULL COMMENT ''QueueTime开窗触发站'' AFTER off_flow_counts',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'qtime_to_sort'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_lot ADD COLUMN qtime_to_sort INT NULL COMMENT ''QueueTime目标站'' AFTER qtime_from_sort',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'qtime_started_at'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_lot ADD COLUMN qtime_started_at DATETIME(3) NULL COMMENT ''QueueTime开窗时刻'' AFTER qtime_to_sort',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'qtime_max_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_lot ADD COLUMN qtime_max_min INT NULL COMMENT ''QueueTime开窗固化上限分钟'' AFTER qtime_started_at',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'qtime_on_violate'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_lot ADD COLUMN qtime_on_violate VARCHAR(16) NULL COMMENT ''QueueTime开窗固化策略'' AFTER qtime_max_min',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8007, 'QTIME_EXCEED', 'Queue Time超时', 'quality', 1, '站间等待超限', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();
