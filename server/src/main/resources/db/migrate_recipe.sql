-- Recipe 模块：主数据 / 版本 / 绑定 + 履历扩展 + 权限
-- 已有库执行本脚本
-- 对应文档：docs/模块/Recipe（配方）模块/MES-Recipe数据库设计.md

-- =========================
-- 主表 mes_recipe
-- =========================
CREATE TABLE IF NOT EXISTS mes_recipe (
    id            BIGINT       NOT NULL COMMENT '主键',
    recipe_code   VARCHAR(64)  NOT NULL COMMENT '配方编码（短名，唯一）',
    recipe_name   VARCHAR(128) NOT NULL COMMENT '配方名称',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark        VARCHAR(256)          COMMENT '备注',
    version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by     BIGINT                COMMENT '创建人',
    update_by     BIGINT                COMMENT '更新人',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_code (recipe_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配方主数据';

-- =========================
-- 版本表 mes_recipe_version
-- status: draft草稿 / active生效 / obsolete停用
-- 同 recipe_id 至多一条 active（应用层保证）
-- =========================
CREATE TABLE IF NOT EXISTS mes_recipe_version (
    id               BIGINT       NOT NULL COMMENT '主键',
    recipe_id        BIGINT       NOT NULL COMMENT '配方ID',
    version_no       INT          NOT NULL COMMENT '版本号（同配方内递增）',
    status           VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/active/obsolete',
    body_json        TEXT                  COMMENT '参数JSON（一期可选）',
    body_object_key  VARCHAR(256)          COMMENT 'MinIO对象键',
    remark           VARCHAR(256)          COMMENT '备注',
    published_at     DATETIME              COMMENT '发布时间',
    create_by        BIGINT                COMMENT '创建人',
    update_by        BIGINT                COMMENT '更新人',
    create_time      DATETIME              COMMENT '创建时间',
    update_time      DATETIME              COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_ver (recipe_id, version_no),
    KEY idx_recipe_ver_status (recipe_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配方版本';

-- =========================
-- 绑定表 mes_recipe_binding
-- eqp_id 与 eqp_type 不可同时空；解析优先 eqp_id
-- recipe_version_id 空 = 跟随 recipe 当前 active
-- =========================
CREATE TABLE IF NOT EXISTS mes_recipe_binding (
    id                  BIGINT       NOT NULL COMMENT '主键',
    step_id             BIGINT       NOT NULL COMMENT '工序ID',
    eqp_id              BIGINT                COMMENT '具体设备ID',
    eqp_type            VARCHAR(64)           COMMENT '设备类型绑定',
    recipe_id           BIGINT       NOT NULL COMMENT '配方ID',
    recipe_version_id   BIGINT                COMMENT '指定版本；空=跟active',
    enabled             TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    create_by           BIGINT                COMMENT '创建人',
    update_by           BIGINT                COMMENT '更新人',
    create_time         DATETIME              COMMENT '创建时间',
    update_time         DATETIME              COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    KEY idx_bind_step (step_id, enabled),
    KEY idx_bind_eqp (eqp_id),
    KEY idx_bind_type (step_id, eqp_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配方Step×Eqp绑定';

-- =========================
-- mes_tx_log 履历扩展列
-- =========================
SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_tx_log' AND COLUMN_NAME = 'recipe_id'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_tx_log ADD COLUMN recipe_id BIGINT NULL COMMENT ''配方ID'' AFTER eqp_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(1) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_tx_log' AND COLUMN_NAME = 'recipe_version_id'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_tx_log ADD COLUMN recipe_version_id BIGINT NULL COMMENT ''配方版本ID（追溯锚点）'' AFTER recipe_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =========================
-- 权限（挂生产执行目录 200）
-- 246 recipe:view     菜单
-- 247 recipe:edit     主数据/草稿
-- 248 recipe:publish  发布
-- 249 recipe:bind     绑定
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(246, 200, 2, 'recipe:view',    '配方',     '/app/recipe', 'flask-conical', 150, 1, NOW(), NOW(), 0),
(247, 246, 3, 'recipe:edit',    '配方编辑', NULL,          NULL,            1,   1, NOW(), NOW(), 0),
(248, 246, 3, 'recipe:publish', '配方发布', NULL,          NULL,            2,   1, NOW(), NOW(), 0),
(249, 246, 3, 'recipe:bind',    '配方绑定', NULL,          NULL,            3,   1, NOW(), NOW(), 0)
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
(1126, 1, 246, NOW()),
(1127, 1, 247, NOW()),
(1128, 1, 248, NOW()),
(1129, 1, 249, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng：全部
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1320, 3, 246, NOW()),
(1321, 3, 247, NOW()),
(1322, 3, 248, NOW()),
(1323, 3, 249, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：只读
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1412, 4, 246, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
