-- Hold 模块：原因码 + 锁批记录
-- 已有库执行本脚本
-- 对应文档：docs/模块/Hold（锁批）模块/MES-Hold数据库设计.md

-- =========================
-- 原因码 mes_hold_reason
-- category 预留：quality质量 / eng工程 / customer客户 / other其它
-- status：1启用 0停用
-- =========================
CREATE TABLE IF NOT EXISTS mes_hold_reason (
    id            BIGINT       NOT NULL COMMENT '主键',
    reason_code   VARCHAR(32)  NOT NULL COMMENT '原因编码（唯一）',
    reason_name   VARCHAR(64)  NOT NULL COMMENT '原因名称',
    category      VARCHAR(32)           COMMENT '分类: quality质量/eng工程/customer客户/other其它',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0停用',
    remark        VARCHAR(256)          COMMENT '备注',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hold_reason_code (reason_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锁批原因码';

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8001, 'Q_PENDING',  '待检/待判',     'quality',  1, '质量等待',   NOW(), NOW(), 0),
(8002, 'Q_ABNORMAL', '量测/良率异常', 'quality',  1, '质量调查',   NOW(), NOW(), 0),
(8003, 'E_REVIEW',   '工程评审',     'eng',      1, '工程',       NOW(), NOW(), 0),
(8004, 'M_MATERIAL', '缺料/物料问题', 'other',    1, '制造',       NOW(), NOW(), 0),
(8005, 'C_REQUEST',  '客户要求',     'customer', 1, '客规',       NOW(), NOW(), 0),
(8006, 'OTHER',      '其它',         'other',    1, '须填备注',   NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();

-- =========================
-- 锁批记录 mes_hold
-- status 枚举：
--   active   = 生效中（Track 须拦截）
--   released = 已解锁（历史只读）
-- prev_status：Hold 前 Lot.status（wait/processing）
-- 一期应用层约束：同 lot 最多一条 active；表不建唯一索引以便二期多层 Hold
-- =========================
CREATE TABLE IF NOT EXISTS mes_hold (
    id                 BIGINT       NOT NULL COMMENT '主键',
    lot_id             BIGINT       NOT NULL COMMENT '批次ID',
    lot_no             VARCHAR(64)           COMMENT '批次号冗余',
    reason_id          BIGINT       NOT NULL COMMENT '原因码ID',
    reason_code        VARCHAR(32)  NOT NULL COMMENT '原因编码冗余',
    status             VARCHAR(16)  NOT NULL COMMENT '状态: active生效中/released已解锁',
    prev_status        VARCHAR(32)           COMMENT 'Hold前Lot状态(wait/processing)',
    remark             VARCHAR(512)          COMMENT '上锁备注',
    release_remark     VARCHAR(512)          COMMENT '解锁备注',
    hold_user_id       BIGINT                COMMENT '上锁人',
    hold_user_name     VARCHAR(64)           COMMENT '上锁人名称冗余',
    hold_time          DATETIME     NOT NULL COMMENT '上锁时间',
    release_user_id    BIGINT                COMMENT '解锁人',
    release_user_name  VARCHAR(64)           COMMENT '解锁人名称冗余',
    release_time       DATETIME              COMMENT '解锁时间',
    create_time        DATETIME              COMMENT '创建时间',
    update_time        DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_hold_lot_status (lot_id, status),
    KEY idx_hold_status_time (status, hold_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锁批记录（最小集；一期同Lot最多一条active）';
