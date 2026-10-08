-- TD-1 Test：Bin 字典 / 测试记录 / Bin 汇总 / 提交守卫 / 号段 + 演示种子 + 管理端菜单
-- 已有库执行本脚本。2026-10-08 探针 sys_permission MAX(id)=332，本脚本用 340-343。
-- 对应：docs/方案/MES-封测测试数据与Bin回流方案.md §3.1-§3.3、§3.2.1

CREATE TABLE IF NOT EXISTS mes_bin_def (
    id                BIGINT        NOT NULL COMMENT '主键',
    bin_scope         VARCHAR(16)   NOT NULL COMMENT 'GLOBAL/PRODUCT/PROGRAM/PROGRAM_VERSION',
    product_code      VARCHAR(64)   NOT NULL COMMENT '适用产品，不适用填空串',
    program_name      VARCHAR(64)   NOT NULL COMMENT '适用测试程序，不适用填空串',
    program_version   VARCHAR(32)   NOT NULL COMMENT '适用程序版本，不适用填空串',
    bin_type          VARCHAR(8)    NOT NULL COMMENT 'HARD/SOFT',
    bin_code          VARCHAR(32)   NOT NULL COMMENT 'Bin号',
    bin_name          VARCHAR(64)   NOT NULL COMMENT '名称',
    failure_mode      VARCHAR(128)           COMMENT '失效模式',
    is_shippable      TINYINT       NOT NULL DEFAULT 0 COMMENT '按规格可出货 0/1',
    status            TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    remark            VARCHAR(256)           COMMENT '备注',
    create_time       DATETIME               COMMENT '创建时间',
    update_time       DATETIME               COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bin_def (product_code, program_name, program_version, bin_type, bin_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bin字典';

CREATE TABLE IF NOT EXISTS mes_test_record (
    id                BIGINT        NOT NULL COMMENT '主键',
    record_no         VARCHAR(32)   NOT NULL COMMENT '业务号 TR-yyyyMMdd-序号',
    lot_id            BIGINT        NOT NULL COMMENT '所属批',
    lot_no            VARCHAR(64)            COMMENT '批次号快照',
    test_stage        VARCHAR(8)    NOT NULL COMMENT 'CP/FT/OTHER',
    program_name      VARCHAR(64)   NOT NULL COMMENT '测试程序名',
    program_version   VARCHAR(32)   NOT NULL COMMENT '程序版本',
    eqp_id            BIGINT                 COMMENT '测试设备，外协或手工可空',
    eqp_code          VARCHAR(64)            COMMENT '设备编码快照',
    test_time         DATETIME      NOT NULL COMMENT '测试完成时间',
    total_qty         INT           NOT NULL COMMENT '本次测试颗数',
    source_type       VARCHAR(16)   NOT NULL COMMENT 'FILE/API/MANUAL',
    source_ref        VARCHAR(256)           COMMENT '文件引用或外部标识',
    remark            VARCHAR(512)           COMMENT '备注',
    create_by         BIGINT                 COMMENT '创建人',
    create_time       DATETIME               COMMENT '创建时间',
    update_time       DATETIME               COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_no (record_no),
    KEY idx_test_record_lot (lot_id),
    KEY idx_test_record_lot_time (lot_id, test_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试记录头';

CREATE TABLE IF NOT EXISTS mes_test_bin_summary (
    id                BIGINT        NOT NULL COMMENT '主键',
    record_id         BIGINT        NOT NULL COMMENT '所属测试记录',
    bin_type          VARCHAR(8)    NOT NULL COMMENT 'HARD/SOFT',
    bin_code          VARCHAR(32)   NOT NULL COMMENT 'Bin号',
    bin_name          VARCHAR(64)            COMMENT '名称快照',
    bin_qty           INT           NOT NULL COMMENT '颗数',
    is_shippable      TINYINT       NOT NULL COMMENT '可出货性快照',
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_bin_sum (record_id, bin_type, bin_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试Bin汇总';

CREATE TABLE IF NOT EXISTS mes_test_submit_guard (
    id                BIGINT        NOT NULL COMMENT '主键',
    lot_id            BIGINT        NOT NULL COMMENT '所属批',
    eqp_key           BIGINT        NOT NULL COMMENT '设备id，无设备写0',
    program_name      VARCHAR(64)   NOT NULL COMMENT '测试程序名',
    program_version   VARCHAR(32)   NOT NULL COMMENT '程序版本',
    test_time         DATETIME      NOT NULL COMMENT '测试完成时间',
    total_qty         INT           NOT NULL COMMENT '本次测试颗数',
    window_bucket     BIGINT        NOT NULL COMMENT '插入当时的10分钟桶',
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_submit_guard (lot_id, eqp_key, program_name, program_version, test_time, total_qty, window_bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试提交守卫';

CREATE TABLE IF NOT EXISTS mes_test_record_no_seq (
    seq_day  CHAR(8) NOT NULL COMMENT 'yyyyMMdd',
    next_no  INT     NOT NULL COMMENT '当日已分配流水',
    PRIMARY KEY (seq_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试记录号按日流水';

INSERT INTO mes_bin_def (
  id, bin_scope, product_code, program_name, program_version, bin_type, bin_code, bin_name,
  failure_mode, is_shippable, status, version, remark, create_time, update_time, deleted
) VALUES
(7201, 'GLOBAL', '', '', '', 'HARD', '1', '良品（最高档）', NULL, 1, 1, 0, '演示档', NOW(), NOW(), 0),
(7202, 'GLOBAL', '', '', '', 'HARD', '2', '参数次档',     NULL, 0, 1, 0, '演示档', NOW(), NOW(), 0),
(7203, 'GLOBAL', '', '', '', 'HARD', '3', '失效',         NULL, 0, 1, 0, '演示档', NOW(), NOW(), 0),
(7204, 'GLOBAL', '', '', '', 'HARD', '4', '失效',         NULL, 0, 1, 0, '演示档', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  bin_name = VALUES(bin_name),
  is_shippable = VALUES(is_shippable),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();

-- 挂生产执行目录 200。现场 operator 不授。
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(340, 200, 2, 'test:view',     '测试数据', '/app/test', 'clipboard', 170, 1, NOW(), NOW(), 0),
(341, 340, 3, 'test:create',   '测试登记', NULL,        NULL,          1, 1, NOW(), NOW(), 0),
(342, 340, 3, 'test:void',     '测试作废', NULL,        NULL,          2, 1, NOW(), NOW(), 0),
(343, 340, 3, 'test:edit-bin', 'Bin维护',  NULL,        NULL,          3, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  path = VALUES(path),
  icon = VALUES(icon),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

-- admin
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1154, 1, 340, NOW()),
(1155, 1, 341, NOW()),
(1156, 1, 342, NOW()),
(1157, 1, 343, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1349, 3, 340, NOW()),
(1350, 3, 341, NOW()),
(1351, 3, 342, NOW()),
(1352, 3, 343, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：只读
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1433, 4, 340, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
