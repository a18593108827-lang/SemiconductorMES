-- Hot Lot：WIP 投影同步 hot_flag，待派排序消费
-- 对齐：MES-LotHot接口设计.md HT-3/HT-4
-- mes_lot.hot_flag 已存在

ALTER TABLE mes_wip_lot
    ADD COLUMN hot_flag TINYINT NOT NULL DEFAULT 0 COMMENT 'Hot Lot 0/1' AFTER priority;

UPDATE mes_wip_lot w
INNER JOIN mes_lot l ON l.id = w.lot_id AND l.deleted = 0
SET w.hot_flag = IFNULL(l.hot_flag, 0),
    w.priority = l.priority;
