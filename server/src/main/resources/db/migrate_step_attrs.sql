-- Step 站属性：mes_step 预留 + mes_route_step 快照
-- 对应：docs/模块/Route（工艺路线）模块/MES-Step站属性接口设计.md

SET @db := DATABASE();

-- mes_step.allow_skip
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_step' AND COLUMN_NAME = 'allow_skip'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_step ADD COLUMN allow_skip TINYINT NULL COMMENT ''1允许Skip 0禁止 空=跟随全局'' AFTER eqp_type',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_step.max_queue_min
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_step' AND COLUMN_NAME = 'max_queue_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_step ADD COLUMN max_queue_min INT NULL COMMENT ''站间最大等待分钟 预留'' AFTER allow_skip',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_route_step.eqp_type
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_step' AND COLUMN_NAME = 'eqp_type'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_step ADD COLUMN eqp_type VARCHAR(64) NULL COMMENT ''快照设备类型'' AFTER next_sort_no',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_route_step.step_type
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_step' AND COLUMN_NAME = 'step_type'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_step ADD COLUMN step_type TINYINT NULL COMMENT ''快照工序类型'' AFTER eqp_type',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_route_step.allow_skip
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_step' AND COLUMN_NAME = 'allow_skip'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_step ADD COLUMN allow_skip TINYINT NULL COMMENT ''快照Skip许可'' AFTER step_type',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mes_route_step.max_queue_min
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_route_step' AND COLUMN_NAME = 'max_queue_min'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_route_step ADD COLUMN max_queue_min INT NULL COMMENT ''快照QueueTime'' AFTER allow_skip',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
