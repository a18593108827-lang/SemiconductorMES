-- Process Time：步骤加工时长上下限 + Lot 开计时
-- 对应：docs/模块/Track（执行引擎）模块/MES-ProcessTime接口设计.md

SET @db := DATABASE();

-- mes_step.min_process_min
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_step' AND COLUMN_NAME = 'min_process_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_step ADD COLUMN min_process_min INT NULL COMMENT ''站内加工下限分钟'' AFTER max_queue_min',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_step.max_process_min
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_step' AND COLUMN_NAME = 'max_process_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_step ADD COLUMN max_process_min INT NULL COMMENT ''站内加工上限分钟'' AFTER min_process_min',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_route_step.min_process_min
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_step' AND COLUMN_NAME = 'min_process_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_step ADD COLUMN min_process_min INT NULL COMMENT ''快照站内加工下限分钟'' AFTER max_queue_min',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_route_step.max_process_min
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_step' AND COLUMN_NAME = 'max_process_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_step ADD COLUMN max_process_min INT NULL COMMENT ''快照站内加工上限分钟'' AFTER min_process_min',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_lot.process_started_at
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_lot' AND COLUMN_NAME = 'process_started_at'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_lot ADD COLUMN process_started_at DATETIME(3) NULL COMMENT ''ProcessTime开计时时刻'' AFTER qtime_on_violate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8008, 'PROCESS_TIME_EXCEED', 'Process Time超时', 'quality', 1, '站内加工超上限，出站后锁批', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();
