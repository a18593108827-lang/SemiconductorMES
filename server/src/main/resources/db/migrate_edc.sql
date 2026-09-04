-- EDC 模块：特性 / 规格 / 站计划 / 采集 + 权限
-- 已有库执行本脚本
-- 对应文档：docs/模块/EDC（量测）模块/MES-EDC数据库设计.md

-- =========================
-- 特性 mes_edc_param
-- =========================
CREATE TABLE IF NOT EXISTS mes_edc_param (
    id            BIGINT       NOT NULL COMMENT '主键',
    param_code    VARCHAR(64)  NOT NULL COMMENT '特性编码',
    param_name    VARCHAR(128) NOT NULL COMMENT '特性名称',
    unit          VARCHAR(32)           COMMENT '单位',
    value_type    VARCHAR(16)  NOT NULL DEFAULT 'NUMBER' COMMENT '一期固定 NUMBER',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark        VARCHAR(256)          COMMENT '备注',
    version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by     BIGINT                COMMENT '创建人',
    update_by     BIGINT                COMMENT '更新人',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edc_param_code (param_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EDC特性';

-- =========================
-- 规格 mes_edc_spec
-- status: draft / active / obsolete
-- product_code 空串=全产品默认；同 (param_id, product_code) 至多一条 active（应用层）
-- =========================
CREATE TABLE IF NOT EXISTS mes_edc_spec (
    id            BIGINT         NOT NULL COMMENT '主键',
    param_id      BIGINT         NOT NULL COMMENT '特性ID',
    product_code  VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '产品编码；空=全产品默认',
    version_no    INT            NOT NULL COMMENT '版本号（同 param+product 递增）',
    status        VARCHAR(16)    NOT NULL DEFAULT 'draft' COMMENT 'draft/active/obsolete',
    usl           DECIMAL(20,8)           COMMENT '上限',
    lsl           DECIMAL(20,8)           COMMENT '下限',
    target        DECIMAL(20,8)           COMMENT '目标（SPC预留）',
    remark        VARCHAR(256)            COMMENT '备注',
    published_at  DATETIME                COMMENT '发布时间',
    version       INT            NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by     BIGINT                  COMMENT '创建人',
    update_by     BIGINT                  COMMENT '更新人',
    create_time   DATETIME                COMMENT '创建时间',
    update_time   DATETIME                COMMENT '更新时间',
    deleted       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edc_spec_ver (param_id, product_code, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EDC规格';

-- =========================
-- 站计划 mes_edc_plan
-- 一 step 一计划；逻辑引用 mes_step，不建硬 FK
-- =========================
CREATE TABLE IF NOT EXISTS mes_edc_plan (
    id            BIGINT       NOT NULL COMMENT '主键',
    step_id       BIGINT       NOT NULL COMMENT '工序ID',
    required      TINYINT      NOT NULL DEFAULT 0 COMMENT '1=TrackOut门禁启用',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark        VARCHAR(256)          COMMENT '备注',
    version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by     BIGINT                COMMENT '创建人',
    update_by     BIGINT                COMMENT '更新人',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edc_plan_step (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EDC站计划';

-- =========================
-- 计划项 mes_edc_plan_item
-- spec_id 空=解析该 param（+lot.product）当前 active Spec
-- =========================
CREATE TABLE IF NOT EXISTS mes_edc_plan_item (
    id            BIGINT       NOT NULL COMMENT '主键',
    plan_id       BIGINT       NOT NULL COMMENT '计划ID',
    param_id      BIGINT       NOT NULL COMMENT '特性ID',
    spec_id       BIGINT                COMMENT '指定规格；空=跟active',
    sort_no       INT          NOT NULL DEFAULT 0 COMMENT '录入顺序',
    mandatory     TINYINT      NOT NULL DEFAULT 1 COMMENT '1必采',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edc_plan_param (plan_id, param_id),
    KEY idx_edc_plan_item (plan_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EDC计划项';

-- =========================
-- 采集头 mes_edc_collection
-- result: PASS / FAIL；source: MANUAL / AUTO
-- track_in_tx_id = 本趟访问（mes_tx_log.id）
-- =========================
CREATE TABLE IF NOT EXISTS mes_edc_collection (
    id                 BIGINT       NOT NULL COMMENT '主键',
    lot_id             BIGINT       NOT NULL COMMENT '批次ID',
    lot_no             VARCHAR(64)           COMMENT '批次号冗余',
    route_version_id   BIGINT       NOT NULL COMMENT '在途路线版本',
    sort_no            INT          NOT NULL COMMENT '站序',
    step_id            BIGINT       NOT NULL COMMENT '工序ID',
    track_in_tx_id     BIGINT       NOT NULL COMMENT '本趟TrackIn履历ID',
    plan_id            BIGINT       NOT NULL COMMENT '提交时计划ID',
    result             VARCHAR(16)  NOT NULL COMMENT 'PASS/FAIL',
    source             VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO',
    eqp_id             BIGINT                COMMENT '量测机',
    remark             VARCHAR(256)          COMMENT '备注',
    collected_by       BIGINT                COMMENT '采集人',
    collected_at       DATETIME     NOT NULL COMMENT '采集时间',
    create_time        DATETIME              COMMENT '创建时间',
    update_time        DATETIME              COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    KEY idx_edc_col_visit (lot_id, track_in_tx_id, collected_at),
    KEY idx_edc_col_lot_step (lot_id, step_id, collected_at),
    KEY idx_edc_col_step_time (step_id, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EDC采集头';

-- =========================
-- 采集点 mes_edc_collection_item
-- item_result: PASS / OOS
-- =========================
CREATE TABLE IF NOT EXISTS mes_edc_collection_item (
    id              BIGINT         NOT NULL COMMENT '主键',
    collection_id   BIGINT         NOT NULL COMMENT '采集头ID',
    param_id        BIGINT         NOT NULL COMMENT '特性ID',
    spec_id         BIGINT                  COMMENT '判定所用规格',
    usl_snap        DECIMAL(20,8)           COMMENT '上限快照',
    lsl_snap        DECIMAL(20,8)           COMMENT '下限快照',
    value_num       DECIMAL(20,8)  NOT NULL COMMENT '量测值',
    item_result     VARCHAR(16)    NOT NULL COMMENT 'PASS/OOS',
    create_time     DATETIME                COMMENT '创建时间',
    deleted         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    KEY idx_edc_col_item (collection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EDC采集点';

-- =========================
-- 权限（挂生产执行目录 200）
-- 253 edc:view     菜单
-- 254 edc:edit     Param/Plan/Spec草稿
-- 255 edc:publish  Spec发布
-- 256 edc:collect  提交采集
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(253, 200, 2, 'edc:view',    '量测',     '/app/edc', 'ruler', 160, 1, NOW(), NOW(), 0),
(254, 253, 3, 'edc:edit',    '量测编辑', NULL,       NULL,      1,  1, NOW(), NOW(), 0),
(255, 253, 3, 'edc:publish', '规格发布', NULL,       NULL,      2,  1, NOW(), NOW(), 0),
(256, 253, 3, 'edc:collect', '量测采集', NULL,       NULL,      3,  1, NOW(), NOW(), 0)
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
(1138, 1, 253, NOW()),
(1139, 1, 254, NOW()),
(1140, 1, 255, NOW()),
(1141, 1, 256, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng：全部
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1333, 3, 253, NOW()),
(1334, 3, 254, NOW()),
(1335, 3, 255, NOW()),
(1336, 3, 256, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：只读
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1422, 4, 253, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- operator：view + collect
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1209, 2, 253, NOW()),
(1210, 2, 256, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 已建库补乐观锁列（新建表已含 version，可重复执行）
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_edc_spec' AND COLUMN_NAME = 'version'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_edc_spec ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁'' AFTER published_at',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
