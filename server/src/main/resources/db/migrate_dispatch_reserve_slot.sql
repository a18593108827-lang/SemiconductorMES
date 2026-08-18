-- Dispatch 预约并发：active 唯一坑位（eqp_slot / lot_slot）
-- 已有库执行本脚本（在 migrate_dispatch.sql 之后）
-- 规则：status=active 时 slot=对应 id；非 active 时 slot=NULL（UNIQUE 允许多 NULL）

-- 1) 加列
ALTER TABLE mes_dispatch_reserve
    ADD COLUMN eqp_slot BIGINT NULL COMMENT 'active 时=eqp_id，否则 NULL；UNIQUE 防同机双约' AFTER eqp_id,
    ADD COLUMN lot_slot BIGINT NULL COMMENT 'active 时=lot_id，否则 NULL；UNIQUE 防同批双约' AFTER lot_id;

-- 2) 清理同机多条 active（只留 id 最大的一条）
UPDATE mes_dispatch_reserve r
    INNER JOIN (
        SELECT eqp_id, MAX(id) AS keep_id
        FROM mes_dispatch_reserve
        WHERE status = 'active' AND deleted = 0
        GROUP BY eqp_id
        HAVING COUNT(*) > 1
    ) d ON r.eqp_id = d.eqp_id
        AND r.status = 'active'
        AND r.deleted = 0
        AND r.id <> d.keep_id
SET r.status = 'released',
    r.remark = '并发脏数据清理-同机多约',
    r.update_time = NOW();

-- 3) 清理同批多条 active（只留 id 最大的一条）
UPDATE mes_dispatch_reserve r
    INNER JOIN (
        SELECT lot_id, MAX(id) AS keep_id
        FROM mes_dispatch_reserve
        WHERE status = 'active' AND deleted = 0
        GROUP BY lot_id
        HAVING COUNT(*) > 1
    ) d ON r.lot_id = d.lot_id
        AND r.status = 'active'
        AND r.deleted = 0
        AND r.id <> d.keep_id
SET r.status = 'released',
    r.remark = '并发脏数据清理-同批多约',
    r.update_time = NOW();

-- 4) 回填：仅 active 占坑
UPDATE mes_dispatch_reserve
SET eqp_slot = eqp_id,
    lot_slot = lot_id
WHERE status = 'active' AND deleted = 0;

-- 5) 唯一索引
ALTER TABLE mes_dispatch_reserve
    ADD UNIQUE KEY uk_reserve_eqp_slot (eqp_slot),
    ADD UNIQUE KEY uk_reserve_lot_slot (lot_slot);
