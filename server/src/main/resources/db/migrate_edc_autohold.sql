-- EDC P1：OOS 采集后可选 Auto-Hold
-- 已有库执行本脚本

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8009, 'EDC_OOS', '量测超规', 'quality', 1, '采集OOS后锁批，解锁后须重采合格才能完工', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();
