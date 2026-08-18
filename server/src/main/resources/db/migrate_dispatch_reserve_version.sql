-- Dispatch 预约乐观锁 version
-- 已有库在 migrate_dispatch_reserve_slot.sql 之后执行

ALTER TABLE mes_dispatch_reserve
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER remark;
