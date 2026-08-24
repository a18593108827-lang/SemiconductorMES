-- History 调查台：设备反查索引
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_tx_log' AND INDEX_NAME = 'idx_tx_eqp_time'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_tx_log ADD KEY idx_tx_eqp_time (eqp_id, create_time)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
