-- Future Hold：预约锁批
-- 已有库执行本脚本
-- 对应文档：docs/模块/Hold（锁批）模块/MES-FutureHold接口设计.md

CREATE TABLE IF NOT EXISTS mes_future_hold (
    id                 BIGINT       NOT NULL COMMENT '主键',
    lot_id             BIGINT       NOT NULL COMMENT '批次ID',
    lot_no             VARCHAR(64)           COMMENT '批次号冗余',
    route_version_id   BIGINT       NOT NULL COMMENT '放行快照版本',
    target_sort_no     INT          NOT NULL COMMENT '目标站序',
    timing             VARCHAR(8)   NOT NULL COMMENT 'PRE进站前/POST出站后',
    reason_id          BIGINT       NOT NULL COMMENT '原因码ID',
    reason_code        VARCHAR(32)  NOT NULL COMMENT '原因编码冗余',
    status             VARCHAR(16)  NOT NULL COMMENT 'pending/activated/cancelled',
    hold_id            BIGINT                COMMENT '激活后 mes_hold.id',
    remark             VARCHAR(512)          COMMENT '预约备注',
    owner_user_id      BIGINT                COMMENT '主责人',
    owner_user_name    VARCHAR(64)           COMMENT '主责人名称',
    create_user_id     BIGINT                COMMENT '创建人',
    create_user_name   VARCHAR(64)           COMMENT '创建人名称',
    create_time        DATETIME     NOT NULL COMMENT '创建时间',
    activate_time      DATETIME              COMMENT '激活时间',
    cancel_user_id     BIGINT                COMMENT '取消人',
    cancel_user_name   VARCHAR(64)           COMMENT '取消人名称',
    cancel_time        DATETIME              COMMENT '取消时间',
    cancel_remark      VARCHAR(512)          COMMENT '取消备注',
    update_time        DATETIME              COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    KEY idx_fh_lot_status (lot_id, status),
    KEY idx_fh_activate (lot_id, route_version_id, target_sort_no, timing, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约锁批 Future Hold';
