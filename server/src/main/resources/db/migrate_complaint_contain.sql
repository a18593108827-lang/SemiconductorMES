-- CP-5：contain 占位 token + 客诉遏制原因码。已有库执行一次。

ALTER TABLE mes_complaint_package
  ADD COLUMN contain_token VARCHAR(36) NULL COMMENT 'contain占位所有权，结束CAS匹配后清空';

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8011, 'CUSTOMER_COMPLAINT', '客诉遏制', 'customer', 1, '追溯包contain默认原因', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();
