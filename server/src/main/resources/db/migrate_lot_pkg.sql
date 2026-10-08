-- TD-1 Lot 扩展：Strip 条级 + 客户 Lot 映射
-- 已有库执行本脚本。
-- 对应：docs/方案/MES-封测测试数据与Bin回流方案.md §3.4-§3.5

CREATE TABLE IF NOT EXISTS mes_lot_strip (
    id           BIGINT       NOT NULL COMMENT '主键',
    lot_id       BIGINT       NOT NULL COMMENT '所属批',
    strip_no     VARCHAR(64)  NOT NULL COMMENT '条号',
    seq_no       INT                   COMMENT '批内序号',
    die_qty      INT                   COMMENT '本条颗数',
    bin_code     VARCHAR(32)           COMMENT '该条最终判定档，TD-1可空',
    status       VARCHAR(16)           COMMENT '条级状态，TD-1仅登记',
    remark       VARCHAR(256)          COMMENT '备注',
    create_by    BIGINT                COMMENT '创建人',
    create_time  DATETIME              COMMENT '创建时间',
    update_time  DATETIME              COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot_strip (lot_id, strip_no),
    KEY idx_lot_strip_lot (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Strip条级';

CREATE TABLE IF NOT EXISTS mes_lot_customer_map (
    id                BIGINT       NOT NULL COMMENT '主键',
    lot_id            BIGINT       NOT NULL COMMENT '内部批',
    lot_no            VARCHAR(64)           COMMENT '批次号快照',
    map_type          VARCHAR(16)  NOT NULL COMMENT 'INBOUND/OUTBOUND',
    external_lot_no   VARCHAR(64)  NOT NULL COMMENT '外部批号',
    external_source   VARCHAR(64)           COMMENT '供应商或客户编码',
    customer_code     VARCHAR(64)           COMMENT '客户编码',
    qty               INT                   COMMENT '映射数量',
    remark            VARCHAR(256)          COMMENT '备注',
    create_by         BIGINT                COMMENT '创建人',
    create_time       DATETIME              COMMENT '创建时间',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot_customer_map (lot_id, map_type, external_lot_no),
    KEY idx_customer_map_ext (external_lot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户Lot映射';
