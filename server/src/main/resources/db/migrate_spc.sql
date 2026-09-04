-- SPC：EDC 序列索引 + 图表 + 判异记录 + 权限。已有库执行。

-- SPC-1：EDC 序列查询索引
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(1) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'mes_edc_collection' AND INDEX_NAME = 'idx_edc_col_step_time'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE mes_edc_collection ADD KEY idx_edc_col_step_time (step_id, collected_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =========================
-- 图主数据 mes_spc_chart
-- eqp_id 空存 0；不存点值
-- =========================
CREATE TABLE IF NOT EXISTS mes_spc_chart (
    id            BIGINT         NOT NULL COMMENT '主键',
    param_id      BIGINT         NOT NULL COMMENT '特性ID',
    step_id       BIGINT         NOT NULL COMMENT '工序ID',
    eqp_id        BIGINT         NOT NULL DEFAULT 0 COMMENT '机台ID；0=该站全部机',
    chart_type    VARCHAR(16)    NOT NULL DEFAULT 'IMR' COMMENT '一期固定 IMR',
    limit_mode    VARCHAR(16)    NOT NULL COMMENT 'MANUAL/LEARNING',
    learning_n    INT            NOT NULL DEFAULT 25 COMMENT '学习样本数',
    ucl           DECIMAL(20,8)           COMMENT '控制上限',
    cl            DECIMAL(20,8)           COMMENT '中心线',
    lcl           DECIMAL(20,8)           COMMENT '控制下限',
    run_n         INT            NOT NULL DEFAULT 7 COMMENT '连跑同侧点数；0=关',
    enabled       TINYINT        NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    version       INT            NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by     BIGINT                  COMMENT '创建人',
    update_by     BIGINT                  COMMENT '更新人',
    create_time   DATETIME                COMMENT '创建时间',
    update_time   DATETIME                COMMENT '更新时间',
    deleted       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_spc_chart_ctx (param_id, step_id, eqp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPC图定义';

-- =========================
-- 判异记录 mes_spc_eval
-- 不存点值；同点同图不重复判
-- =========================
CREATE TABLE IF NOT EXISTS mes_spc_eval (
    id                   BIGINT         NOT NULL COMMENT '主键',
    chart_id             BIGINT         NOT NULL COMMENT '图ID',
    collection_item_id   BIGINT         NOT NULL COMMENT 'EDC采集项ID，不硬外键',
    ooc                  TINYINT        NOT NULL COMMENT '0正常 1失控',
    rule_code            VARCHAR(16)    NOT NULL COMMENT 'WE1/RUN',
    ucl_snap             DECIMAL(20,8)           COMMENT '当时UCL',
    cl_snap              DECIMAL(20,8)           COMMENT '当时CL',
    lcl_snap             DECIMAL(20,8)           COMMENT '当时LCL',
    create_time          DATETIME                COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_spc_eval_item (chart_id, collection_item_id),
    KEY idx_spc_eval_chart_time (chart_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPC判异记录';

-- =========================
-- 权限（挂生产执行目录 200）
-- 257 spc:view  菜单 /app/spc
-- 258 spc:edit  改图/改限
-- 现场 operator 不给
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(257, 200, 2, 'spc:view', '趋势',     '/app/spc', 'activity', 165, 1, NOW(), NOW(), 0),
(258, 257, 3, 'spc:edit', '趋势编辑', NULL,       NULL,         1,  1, NOW(), NOW(), 0)
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
(1142, 1, 257, NOW()),
(1143, 1, 258, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng：全部
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1337, 3, 257, NOW()),
(1338, 3, 258, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：只读
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1423, 4, 257, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
