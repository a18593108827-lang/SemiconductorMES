-- Lot Split：谱系表 + mes_lot 增量字段 + 权限 track:split
-- 对应：docs/模块/Lot（批次）模块/MES-LotSplit接口设计.md

ALTER TABLE mes_lot
    ADD COLUMN parent_lot_id BIGINT NULL COMMENT '直系父Lot' AFTER customer_lot,
    ADD COLUMN scrap_qty INT NOT NULL DEFAULT 0 COMMENT '累计报废数量' AFTER qty,
    ADD COLUMN hot_flag TINYINT NOT NULL DEFAULT 0 COMMENT 'Hot Lot 0/1' AFTER priority,
    ADD COLUMN merged_to_lot_id BIGINT NULL COMMENT '合批目标Lot' AFTER parent_lot_id;

CREATE TABLE IF NOT EXISTS mes_lot_genealogy (
    id             BIGINT       NOT NULL COMMENT '主键',
    txn_type       VARCHAR(16)  NOT NULL COMMENT 'split/merge',
    parent_lot_id  BIGINT       NOT NULL COMMENT 'Split=父; Merge=主Lot',
    child_lot_id   BIGINT       NOT NULL COMMENT 'Split=子; Merge=被吞源',
    qty            INT          NOT NULL COMMENT '本次转移数量',
    tx_id          BIGINT                COMMENT '关联 mes_tx_log.id',
    reason_code    VARCHAR(64)           COMMENT '原因码',
    create_by      BIGINT                COMMENT '操作人',
    create_time    DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_gene_parent (parent_lot_id),
    KEY idx_gene_child (child_lot_id),
    KEY idx_gene_tx (tx_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次分合批谱系';

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(298, 290, 3, 'track:split', '分批', NULL, NULL, 8, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1133, 1, 298, NOW()),
(1327, 3, 298, NOW()),
(1416, 4, 298, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
