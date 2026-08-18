-- WIP 模块：在制投影表 mes_wip_lot
-- 已有库执行本脚本；依赖 mes_lot（Track 运行态字段）
-- 对应文档：docs/模块/WIP（在制）模块/MES-WIP数据库设计.md

-- =========================
-- 在制投影（只读查询源；由 Track 同事务维护）
-- status 枚举（仅在制子集）：
--   wait       = 待加工（在站待 TrackIn）
--   processing = 加工中（已 TrackIn）
--   held       = 锁批（Hold 二期；一期可写入预留）
-- 离开在制（completed / scrapped / created）不落本表，完工时 DELETE
-- =========================
CREATE TABLE IF NOT EXISTS mes_wip_lot (
    lot_id            BIGINT        NOT NULL COMMENT '批次ID，同mes_lot.id',
    lot_no            VARCHAR(64)   NOT NULL COMMENT '批次号冗余',
    product_code      VARCHAR(64)            COMMENT '产品编码',
    qty               INT           NOT NULL DEFAULT 0 COMMENT '数量',
    priority          INT           NOT NULL DEFAULT 50 COMMENT '优先级1-100，越大越急',
    hot_flag          TINYINT       NOT NULL DEFAULT 0 COMMENT 'Hot Lot 0/1',
    customer_lot      VARCHAR(64)            COMMENT '客户Lot',
    status            VARCHAR(32)   NOT NULL COMMENT '在制状态: wait待加工/processing加工中/held锁批',
    current_sort_no   INT                    COMMENT '当前站顺序号（快照内）',
    current_step_id   BIGINT                 COMMENT '当前工序ID',
    current_eqp_id    BIGINT                 COMMENT '当前设备ID（TrackIn后可空）',
    route_id          BIGINT                 COMMENT '路线ID',
    route_version_id  BIGINT                 COMMENT '放行快照版本ID',
    update_time       DATETIME      NOT NULL COMMENT '投影更新时间',
    PRIMARY KEY (lot_id),
    UNIQUE KEY uk_wip_lot_no (lot_no),
    KEY idx_wip_status_sort (status, current_sort_no),
    KEY idx_wip_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在制投影（WIP只读；Track同事务同步）';

-- 从已在制 Lot 回填（可重复执行）
INSERT INTO mes_wip_lot (
  lot_id, lot_no, product_code, qty, priority, hot_flag, customer_lot,
  status, current_sort_no, current_step_id, current_eqp_id,
  route_id, route_version_id, update_time
)
SELECT
  id, lot_no, product_code, qty, priority, IFNULL(hot_flag, 0), customer_lot,
  status, current_sort_no, current_step_id, current_eqp_id,
  route_id, route_version_id, IFNULL(update_time, NOW())
FROM mes_lot
WHERE deleted = 0
  AND status IN ('wait', 'processing', 'held')
ON DUPLICATE KEY UPDATE
  lot_no = VALUES(lot_no),
  product_code = VALUES(product_code),
  qty = VALUES(qty),
  priority = VALUES(priority),
  hot_flag = VALUES(hot_flag),
  customer_lot = VALUES(customer_lot),
  status = VALUES(status),
  current_sort_no = VALUES(current_sort_no),
  current_step_id = VALUES(current_step_id),
  current_eqp_id = VALUES(current_eqp_id),
  route_id = VALUES(route_id),
  route_version_id = VALUES(route_version_id),
  update_time = VALUES(update_time);

-- 离开在制的行清理（幂等）
DELETE w
FROM mes_wip_lot w
LEFT JOIN mes_lot l ON l.id = w.lot_id AND l.deleted = 0
WHERE l.id IS NULL
   OR l.status NOT IN ('wait', 'processing', 'held');
