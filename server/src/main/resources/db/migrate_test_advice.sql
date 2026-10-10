-- TD-2：不良 Bin 处置建议联动
-- 已有库执行本脚本。2026-10-10 探针：sys_permission MAX(id)=343 → 344–348；sys_role MAX(id)=4 → 5(quality)；
-- sys_role_permission 小 id MAX=3004 → 本脚本用 3101–3114。
-- 对应：docs/方案/MES-TD2规格-不良Bin处置建议联动.md · docs/模块/测试数据（Test）模块/TD-2-plan.md §1/§3/§5.1

-- =========================
-- mes_test_advice_rule 阈值规则
-- =========================
CREATE TABLE IF NOT EXISTS mes_test_advice_rule (
    id                 BIGINT         NOT NULL COMMENT '主键',
    product_code       VARCHAR(64)    NOT NULL COMMENT '适用产品，不适用填空串',
    program_name       VARCHAR(64)    NOT NULL COMMENT '适用测试程序，不适用填空串',
    program_version    VARCHAR(32)    NOT NULL COMMENT '适用程序版本，不适用填空串',
    bin_type           VARCHAR(8)     NOT NULL COMMENT 'P0仅HARD',
    bin_code           VARCHAR(32)    NOT NULL COMMENT '规则盯的bin',
    max_ratio          DECIMAL(7,6)   NOT NULL COMMENT '占比上限0~1，判定 ratio>max_ratio',
    suggested_action   VARCHAR(16)    NOT NULL COMMENT 'SUGGEST_HOLD/SUGGEST_REWORK/SUGGEST_RETEST',
    default_reason_code VARCHAR(32)            COMMENT '确认HOLD预填原因码，取自mes_hold_reason',
    enabled            TINYINT        NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark             VARCHAR(255)            COMMENT '备注',
    version            INT            NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_time        DATETIME                COMMENT '创建时间',
    update_time        DATETIME                COMMENT '更新时间',
    deleted            TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_advice_rule (product_code, program_name, program_version, bin_type, bin_code),
    KEY idx_advice_rule_enabled (enabled, bin_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试处置建议阈值规则';

-- =========================
-- mes_test_advice 建议单主表（无软删）
-- =========================
CREATE TABLE IF NOT EXISTS mes_test_advice (
    id                 BIGINT         NOT NULL COMMENT '主键',
    record_id          BIGINT         NOT NULL COMMENT '测试记录，1:1',
    lot_id             BIGINT         NOT NULL COMMENT '批ID快照',
    lot_no             VARCHAR(64)             COMMENT '批号快照',
    product_code       VARCHAR(64)             COMMENT '产品快照',
    program_name       VARCHAR(64)             COMMENT '程序名快照',
    program_version    VARCHAR(32)             COMMENT '程序版本快照',
    total_qty          INT            NOT NULL COMMENT '触发时总量快照',
    version            INT            NOT NULL DEFAULT 0 COMMENT '拍板CAS乐观锁',
    status             VARCHAR(16)    NOT NULL COMMENT 'PENDING/CONFIRMED/RELEASED/IGNORED',
    action_taken       VARCHAR(16)             COMMENT 'HOLD/REWORK/RETEST/TO_SCRAP',
    hold_id            BIGINT                  COMMENT '确认HOLD回写',
    exec_note          VARCHAR(255)            COMMENT 'REWORK/RETEST/TO_SCRAP执行快照',
    confirm_remark     VARCHAR(255)            COMMENT '确认备注',
    release_reason     VARCHAR(32)             COMMENT '放行原因枚举',
    release_remark     VARCHAR(255)            COMMENT '放行说明',
    ignore_reason      VARCHAR(32)             COMMENT 'MISJUDGE/DUPLICATE/VOIDED_RECORD',
    ignore_remark      VARCHAR(255)            COMMENT '忽略说明',
    record_voided      TINYINT        NOT NULL DEFAULT 0 COMMENT '源记录已作废打标',
    confirmed_by       BIGINT                  COMMENT '确认人',
    confirmed_at       DATETIME                COMMENT '确认时间',
    released_by        BIGINT                  COMMENT '放行人',
    released_at        DATETIME                COMMENT '放行时间',
    ignored_by         BIGINT                  COMMENT '忽略人',
    ignored_at         DATETIME                COMMENT '忽略时间',
    create_time        DATETIME                COMMENT '创建时间',
    update_time        DATETIME                COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_advice_record (record_id),
    KEY idx_test_advice_status (status),
    KEY idx_test_advice_lot (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试处置建议单';

-- =========================
-- mes_test_advice_item 明细（无软删，全部HARD行快照）
-- =========================
CREATE TABLE IF NOT EXISTS mes_test_advice_item (
    id                 BIGINT         NOT NULL COMMENT '主键',
    advice_id          BIGINT         NOT NULL COMMENT '建议单',
    bin_id             BIGINT         NOT NULL COMMENT '字典bin id；无字典写0（UK不含NULL）',
    bin_type           VARCHAR(8)     NOT NULL COMMENT 'HARD/SOFT',
    bin_code           VARCHAR(32)    NOT NULL COMMENT 'Bin号',
    bin_name           VARCHAR(64)             COMMENT '名称快照',
    qty                INT            NOT NULL COMMENT '数量',
    ratio              DECIMAL(7,6)   NOT NULL COMMENT 'qty/total_qty',
    rule_id            BIGINT                  COMMENT '命中规则id，未命中空',
    rule_scope         VARCHAR(32)             COMMENT '命中scope快照',
    rule_max_ratio     DECIMAL(7,6)            COMMENT '命中上限快照',
    is_hit             TINYINT        NOT NULL DEFAULT 0 COMMENT '1命中 0对照',
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_advice_item (advice_id, bin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试处置建议明细';

-- =========================
-- mes_test_rule_log 规则变更审计（只追加、无软删）
-- =========================
CREATE TABLE IF NOT EXISTS mes_test_rule_log (
    id                 BIGINT         NOT NULL COMMENT '主键',
    rule_id            BIGINT         NOT NULL COMMENT '规则id',
    action             VARCHAR(16)    NOT NULL COMMENT 'CREATE/UPDATE/DELETE',
    before_json        TEXT                    COMMENT '变更前JSON',
    after_json         TEXT                    COMMENT '变更后JSON',
    op_by              BIGINT                  COMMENT '操作人',
    op_at              DATETIME       NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_test_rule_log_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试阈值规则变更审计';

-- =========================
-- Hold 原因码：测试Bin超限（默认预填）
-- =========================
INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8012, 'TEST_BIN_EXCEED', '测试Bin超限', 'quality', 1, '建议单确认HOLD默认原因', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();

-- =========================
-- 角色 quality（C7）
-- =========================
INSERT INTO sys_role (id, role_code, role_name, remark, status, create_time, update_time, deleted) VALUES
(5, 'quality', '质量工程师', 'TD-2 C7 拍板主体', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  remark = VALUES(remark),
  status = VALUES(status),
  update_time = NOW();

-- =========================
-- 权限 344–348（按钮，挂 test:view=340）
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(344, 340, 3, 'test:advice-view',    '处置建议查看', NULL, NULL, 4, 1, NOW(), NOW(), 0),
(345, 340, 3, 'test:advice-confirm', '处置建议确认', NULL, NULL, 5, 1, NOW(), NOW(), 0),
(346, 340, 3, 'test:advice-release', '处置建议放行', NULL, NULL, 6, 1, NOW(), NOW(), 0),
(347, 340, 3, 'test:advice-ignore',  '处置建议忽略', NULL, NULL, 7, 1, NOW(), NOW(), 0),
(348, 340, 3, 'test:edit-rule',      '阈值规则维护', NULL, NULL, 8, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

-- =========================
-- 角色绑定（plan §1；3101–3114）
-- admin(1): 344–348；quality(5): 344–348；process_eng(3): 344+348；operator(2)/supervisor(4): 344
-- =========================
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
-- admin
(3101, 1, 344, NOW()),
(3102, 1, 345, NOW()),
(3103, 1, 346, NOW()),
(3104, 1, 347, NOW()),
(3105, 1, 348, NOW()),
-- quality
(3106, 5, 344, NOW()),
(3107, 5, 345, NOW()),
(3108, 5, 346, NOW()),
(3109, 5, 347, NOW()),
(3110, 5, 348, NOW()),
-- process_eng：view + edit-rule
(3111, 3, 344, NOW()),
(3112, 3, 348, NOW()),
-- operator / supervisor：只读 view
(3113, 2, 344, NOW()),
(3114, 4, 344, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), permission_id = VALUES(permission_id);

-- =========================
-- C10 试点规则：GLOBAL HARD bin3 上限 2%，默认 HOLD + TEST_BIN_EXCEED
-- =========================
INSERT INTO mes_test_advice_rule (
  id, product_code, program_name, program_version, bin_type, bin_code, max_ratio,
  suggested_action, default_reason_code, enabled, remark, version, create_time, update_time, deleted
) VALUES
(7301, '', '', '', 'HARD', '3', 0.020000, 'SUGGEST_HOLD', 'TEST_BIN_EXCEED', 1,
 'TD-2 C10 试点规则：HARD bin3 >2% 举牌', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  max_ratio = VALUES(max_ratio),
  suggested_action = VALUES(suggested_action),
  default_reason_code = VALUES(default_reason_code),
  enabled = VALUES(enabled),
  remark = VALUES(remark),
  update_time = NOW();
