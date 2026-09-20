-- Alarm-5：HOLD_LOT 策略原因码。已有库执行。
-- 现网四告警码（SPC_OOC / QTIME_* / PROCESS_TIME_*）保持 on_raise=NONE，禁止本脚本改成 HOLD_LOT。

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8010, 'ALARM_POLICY', '告警策略锁批', 'quality', 1, 'mes_alarm_code.on_raise=HOLD_LOT 时引用', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();
