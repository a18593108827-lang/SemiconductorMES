-- Alarm-1：码表 + 实例表 + 权限对齐 + 种子码。已有库执行。
-- 去重：OPEN 同 dedupe_key 仅一行，由应用层保证（Alarm-2）；此处建辅助索引。

-- =========================
-- 告警码表 mes_alarm_code
-- =========================
CREATE TABLE IF NOT EXISTS mes_alarm_code (
    code               VARCHAR(64)    NOT NULL COMMENT '告警码 PK',
    name               VARCHAR(128)   NOT NULL COMMENT '展示名',
    level              VARCHAR(16)    NOT NULL COMMENT 'CRITICAL/WARNING/INFO',
    on_raise           VARCHAR(16)    NOT NULL DEFAULT 'NONE' COMMENT 'NONE/HOLD_LOT；一期仅 NONE',
    hold_reason_code   VARCHAR(32)             COMMENT 'on_raise=HOLD_LOT 时用',
    enabled            TINYINT        NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark             VARCHAR(256)            COMMENT '备注',
    create_time        DATETIME                COMMENT '创建时间',
    update_time        DATETIME                COMMENT '更新时间',
    deleted            TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警码表';

INSERT INTO mes_alarm_code (code, name, level, on_raise, hold_reason_code, enabled, remark, create_time, update_time, deleted) VALUES
('SPC_OOC',                 'SPC失控',           'WARNING', 'NONE', NULL, 1, '控制限判异', NOW(), NOW(), 0),
('QTIME_EXCEED',            'Queue Time超时',    'WARNING', 'NONE', NULL, 1, 'Hold 由 QTime 自挂', NOW(), NOW(), 0),
('QTIME_OPEN_FAIL',         'Queue Time开窗失败', 'WARNING', 'NONE', NULL, 1, NULL, NOW(), NOW(), 0),
('PROCESS_TIME_VIOLATION',  'Process Time违规',  'WARNING', 'NONE', NULL, 1, '超 max Hold 由 ProcessTime 自挂', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  level = VALUES(level),
  on_raise = VALUES(on_raise),
  hold_reason_code = VALUES(hold_reason_code),
  enabled = VALUES(enabled),
  remark = VALUES(remark),
  update_time = NOW();

-- =========================
-- 告警实例 mes_alarm
-- =========================
CREATE TABLE IF NOT EXISTS mes_alarm (
    id                 BIGINT         NOT NULL COMMENT '主键',
    code               VARCHAR(64)    NOT NULL COMMENT '告警码',
    level              VARCHAR(16)    NOT NULL COMMENT '触发时级别快照',
    status             VARCHAR(16)    NOT NULL COMMENT 'OPEN/ACK/CLEARED',
    message            VARCHAR(512)            COMMENT '消息',
    entity_type        VARCHAR(16)    NOT NULL DEFAULT 'NONE' COMMENT 'LOT/EQP/CHART/NONE',
    entity_id          BIGINT         NOT NULL DEFAULT 0 COMMENT '实体ID；无则 0',
    dedupe_key         VARCHAR(128)   NOT NULL COMMENT 'code|type|id',
    payload_json       TEXT                    COMMENT 'payload JSON',
    raise_count        INT            NOT NULL DEFAULT 1 COMMENT '同 OPEN 累加',
    first_raise_at     DATETIME       NOT NULL COMMENT '首次',
    last_raise_at      DATETIME       NOT NULL COMMENT '最近',
    ack_by             BIGINT                  COMMENT '确认人',
    ack_at             DATETIME                COMMENT '确认时间',
    ack_remark         VARCHAR(256)            COMMENT '确认备注',
    clear_by           BIGINT                  COMMENT '关闭人',
    clear_at           DATETIME                COMMENT '关闭时间',
    clear_remark       VARCHAR(256)            COMMENT '关闭备注',
    create_time        DATETIME                COMMENT '创建时间',
    update_time        DATETIME                COMMENT '更新时间',
    deleted            TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除；关闭走 CLEARED',
    PRIMARY KEY (id),
    KEY idx_alarm_dedupe_status (dedupe_key, status),
    KEY idx_alarm_status_level_time (status, level, last_raise_at),
    KEY idx_alarm_entity (entity_type, entity_id, last_raise_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警实例';

-- =========================
-- 权限：270 alarm:list → alarm:view；子权限 ack/clear/edit
-- =========================
UPDATE sys_permission
SET perm_code = 'alarm:view', perm_name = '报警', update_time = NOW()
WHERE id = 270 AND (perm_code = 'alarm:list' OR perm_code = 'alarm:view');

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(271, 270, 3, 'alarm:ack',   '报警确认', NULL, NULL, 1, 1, NOW(), NOW(), 0),
(272, 270, 3, 'alarm:clear', '报警关闭', NULL, NULL, 2, 1, NOW(), NOW(), 0),
(273, 270, 3, 'alarm:edit',  '报警码维护', NULL, NULL, 3, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

-- 确保菜单行存在且为 view（已有库可能缺 270）
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(270, 200, 2, 'alarm:view', '报警', '/app/alarm', 'bell', 17, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = 'alarm:view',
  perm_name = VALUES(perm_name),
  path = VALUES(path),
  icon = VALUES(icon),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

-- admin
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1109, 1, 270, NOW()),
(1144, 1, 271, NOW()),
(1145, 1, 272, NOW()),
(1146, 1, 273, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1307, 3, 270, NOW()),
(1339, 3, 271, NOW()),
(1340, 3, 272, NOW()),
(1341, 3, 273, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：看 + 确认 + 关闭
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1406, 4, 270, NOW()),
(1424, 4, 271, NOW()),
(1425, 4, 272, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
