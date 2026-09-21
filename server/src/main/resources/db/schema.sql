-- =========================
-- ????
-- =========================
CREATE TABLE IF NOT EXISTS sys_user (
    id               BIGINT       NOT NULL COMMENT '??',
    user_code        VARCHAR(64)  NOT NULL COMMENT '????????',
    user_name        VARCHAR(64)  NOT NULL COMMENT '??',
    password         VARCHAR(128) NOT NULL COMMENT '??',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '?? 1?? 0??',
    must_change_pwd  TINYINT      NOT NULL DEFAULT 0 COMMENT '1??????',
    source           VARCHAR(16)  NOT NULL DEFAULT 'local' COMMENT '???? local/sso',
    external_id      VARCHAR(128)          COMMENT '????ID',
    create_time      DATETIME              COMMENT '????',
    update_time      DATETIME              COMMENT '????',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_code (user_code),
    KEY idx_user_name (user_name),
    KEY idx_external (source, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????';

-- =========================
-- ??????
-- =========================
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id             BIGINT        NOT NULL COMMENT '??',
    user_id        BIGINT                 COMMENT '???ID',
    username       VARCHAR(64)            COMMENT '?????',
    module         VARCHAR(64)            COMMENT '??',
    action         VARCHAR(64)            COMMENT '??',
    lot_no         VARCHAR(64)            COMMENT '???',
    request_uri    VARCHAR(255)           COMMENT '??URI',
    request_method VARCHAR(16)            COMMENT '????',
    request_param  TEXT                   COMMENT '??????',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '?? 1?? 0??',
    error_msg      VARCHAR(512)           COMMENT '????',
    ip             VARCHAR(64)            COMMENT 'IP',
    cost_time      BIGINT                 COMMENT '??ms',
    create_time    DATETIME               COMMENT '????',
    PRIMARY KEY (id),
    KEY idx_create_time (create_time),
    KEY idx_username (username),
    KEY idx_lot_no (lot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??????';

-- =========================
-- ??
-- =========================
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL COMMENT '??',
    role_code   VARCHAR(64)  NOT NULL COMMENT '????',
    role_name   VARCHAR(64)  NOT NULL COMMENT '????',
    remark      VARCHAR(255)          COMMENT '??',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1?? 0??',
    create_time DATETIME              COMMENT '????',
    update_time DATETIME              COMMENT '????',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??';

-- =========================
-- ??/??
-- =========================
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT       NOT NULL COMMENT '??',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '?ID',
    perm_type   TINYINT      NOT NULL COMMENT '1?? 2?? 3??',
    perm_code   VARCHAR(128)          COMMENT '???',
    perm_name   VARCHAR(64)  NOT NULL COMMENT '??',
    path        VARCHAR(255)          COMMENT '??',
    icon        VARCHAR(64)           COMMENT '??',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '??',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1?? 0??',
    create_time DATETIME              COMMENT '????',
    update_time DATETIME              COMMENT '????',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '????',
    PRIMARY KEY (id),
    KEY idx_parent (parent_id),
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??/??';

-- =========================
-- ????
-- =========================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id          BIGINT   NOT NULL COMMENT '??',
    user_id     BIGINT   NOT NULL COMMENT '??ID',
    role_id     BIGINT   NOT NULL COMMENT '??ID',
    create_time DATETIME          COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????';

-- =========================
-- ????
-- =========================
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT   NOT NULL COMMENT '??',
    role_id       BIGINT   NOT NULL COMMENT '??ID',
    permission_id BIGINT   NOT NULL COMMENT '??ID',
    create_time   DATETIME          COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????';

-- =========================
-- ?????
-- =========================
CREATE TABLE IF NOT EXISTS sys_perm_request (
    id              BIGINT       NOT NULL COMMENT '??',
    request_no      VARCHAR(32)  NOT NULL COMMENT '????',
    applicant_id    BIGINT       NOT NULL COMMENT '???',
    role_id         BIGINT       NOT NULL COMMENT '????',
    reason          VARCHAR(512) NOT NULL COMMENT '????',
    status          VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/cancelled',
    approver_id     BIGINT                COMMENT '???',
    approve_opinion VARCHAR(512)          COMMENT '????',
    approve_time    DATETIME              COMMENT '????',
    oa_instance_id  VARCHAR(64)           COMMENT '??OA??ID',
    create_time     DATETIME              COMMENT '????',
    update_time     DATETIME              COMMENT '????',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_applicant (applicant_id),
    KEY idx_status (status),
    KEY idx_applicant_role_status (applicant_id, role_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='?????';

-- =========================
-- ????????
-- =========================
CREATE TABLE IF NOT EXISTS sys_perm_request_log (
    id           BIGINT       NOT NULL COMMENT '??',
    request_id   BIGINT       NOT NULL COMMENT '???ID',
    from_status  VARCHAR(16)           COMMENT '???',
    to_status    VARCHAR(16)  NOT NULL COMMENT '???',
    operator_id  BIGINT       NOT NULL COMMENT '???',
    opinion      VARCHAR(512)          COMMENT '??',
    create_time  DATETIME              COMMENT '????',
    PRIMARY KEY (id),
    KEY idx_request_id (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????????';

-- =========================
-- ?????
-- =========================
INSERT INTO sys_role (id, role_code, role_name, remark, status, create_time, update_time, deleted) VALUES
(1, 'admin',       '?????', '????', 1, NOW(), NOW(), 0),
(2, 'operator',    '?????', '?? Track ??', 1, NOW(), NOW(), 0),
(3, 'process_eng', '?????', 'Route/Recipe ??', 1, NOW(), NOW(), 0),
(4, 'supervisor',  '???',     '???????', 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- =========================
-- ??????
-- perm_type: 1?? 2?? 3??
-- =========================
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
-- ????
(200, 0,   1, NULL,              '????', NULL,              'factory',          10, 1, NOW(), NOW(), 0),
(210, 200, 2, 'dashboard:view',  '??',     '/app/dashboard',  'layout-dashboard', 11, 1, NOW(), NOW(), 0),
(220, 200, 2, 'lot:list',        '??',     '/app/lots',       'package',          12, 1, NOW(), NOW(), 0),
(221, 220, 3, 'lot:add',         '????', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(222, 220, 3, 'lot:edit',        '????', NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(223, 220, 3, 'lot:release',     '????', NULL,              NULL,               3,  1, NOW(), NOW(), 0),
(230, 200, 2, 'wip:list',        '??',     '/app/wip',        'boxes',            13, 1, NOW(), NOW(), 0),
(240, 200, 2, 'eqp:list',        '??',     '/app/equipment',  'factory',          14, 1, NOW(), NOW(), 0),
(241, 240, 3, 'eqp:add',         '????', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(242, 240, 3, 'eqp:edit',        '????', NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(243, 240, 3, 'eqp:status',      '????', NULL,              NULL,               3,  1, NOW(), NOW(), 0),
(244, 200, 2, 'dispatch:view',   '??',     '/app/dispatch',  'clipboard-list',  145,1, NOW(), NOW(), 0),
(245, 244, 3, 'dispatch:reserve','????', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(246, 200, 2, 'recipe:view',     '??',     '/app/recipe',    'flask-conical',   150,1, NOW(), NOW(), 0),
(247, 246, 3, 'recipe:edit',     '????', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(248, 246, 3, 'recipe:publish',  '????', NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(249, 246, 3, 'recipe:bind',     '????', NULL,              NULL,               3,  1, NOW(), NOW(), 0),
(250, 200, 2, 'route:list',      '??',     '/app/route',      'map',              15, 1, NOW(), NOW(), 0),
(251, 250, 3, 'route:add',       '????', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(252, 250, 3, 'route:edit',      '????', NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(253, 200, 2, 'edc:view',        '??',     '/app/edc',        'ruler',           160,1, NOW(), NOW(), 0),
(254, 253, 3, 'edc:edit',        '????', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(255, 253, 3, 'edc:publish',     '????', NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(256, 253, 3, 'edc:collect',     '????', NULL,              NULL,               3,  1, NOW(), NOW(), 0),
(257, 200, 2, 'spc:view',        '趋势',     '/app/spc',        'activity',         165,1, NOW(), NOW(), 0),
(258, 257, 3, 'spc:edit',        '趋势编辑', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(260, 200, 2, 'hold:list',       '??',     '/app/hold',       'pause-circle',     16, 1, NOW(), NOW(), 0),
(261, 260, 3, 'hold:create',     '????', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(262, 260, 3, 'hold:release',    '??',     NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(270, 200, 2, 'alarm:view',      '报警',     '/app/alarm',      'bell',             17, 1, NOW(), NOW(), 0),
(271, 270, 3, 'alarm:ack',       '报警确认', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(272, 270, 3, 'alarm:clear',     '报警关闭', NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(273, 270, 3, 'alarm:edit',      '报警码维护', NULL,            NULL,               3,  1, NOW(), NOW(), 0),
(280, 200, 2, 'history:list',    '??',     '/app/history',    'history',          18, 1, NOW(), NOW(), 0),
(290, 200, 2, 'track:view',      '???',   '/track',          'lock',             19, 1, NOW(), NOW(), 0),
(291, 290, 3, 'track:track-in',  'Track In', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(292, 290, 3, 'track:track-out', 'Track Out',NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(293, 290, 3, 'track:release',   '??',     NULL,              NULL,               3,  1, NOW(), NOW(), 0),
(294, 290, 3, 'track:move',      '独立移站', NULL,            NULL,               4,  1, NOW(), NOW(), 0),
(295, 290, 3, 'track:rework',    '返工',   NULL,              NULL,               5,  1, NOW(), NOW(), 0),
(296, 290, 3, 'track:skip',      '跳站',   NULL,              NULL,               6,  1, NOW(), NOW(), 0),
(297, 290, 3, 'track:off-flow',   '临时离线', NULL,            NULL,               7,  1, NOW(), NOW(), 0),
(298, 290, 3, 'track:split',      '分批',   NULL,              NULL,               8,  1, NOW(), NOW(), 0),
(299, 290, 3, 'track:merge',      '合批',   NULL,              NULL,               9,  1, NOW(), NOW(), 0),
(300, 290, 3, 'track:scrap',      '报废',   NULL,              NULL,              10,  1, NOW(), NOW(), 0),
(301, 290, 3, 'track:bonus',      '数量调整', NULL,            NULL,              11,  1, NOW(), NOW(), 0),
(302, 290, 3, 'track:abort',      '加工中止', NULL,            NULL,              12,  1, NOW(), NOW(), 0),
(310, 0,   1, NULL,                 '复盘',   NULL,              'bar-chart-2',     50, 1, NOW(), NOW(), 0),
(311, 310, 2, 'report:view',        '报表',   '/app/report',     'bar-chart-2',     10, 1, NOW(), NOW(), 0),
(320, 200, 2, 'carrier:view',       '载具',   '/app/carrier',    'box',            155, 1, NOW(), NOW(), 0),
(321, 320, 3, 'carrier:edit',       '载具编辑', NULL,            NULL,               1, 1, NOW(), NOW(), 0),
(322, 320, 3, 'carrier:bind',       '载具绑解', NULL,            NULL,               2, 1, NOW(), NOW(), 0),
(330, 280, 3, 'complaint:view',     '追溯包查看', NULL,          NULL,               1, 1, NOW(), NOW(), 0),
(331, 280, 3, 'complaint:build',    '追溯包生成', NULL,          NULL,               2, 1, NOW(), NOW(), 0),
(332, 280, 3, 'complaint:contain',  '追溯包遏制', NULL,          NULL,               3, 1, NOW(), NOW(), 0),
-- ????
(100, 0,   1, 'system',              '????', NULL,                   'settings', 100, 1, NOW(), NOW(), 0),
(110, 100, 2, 'system:user',         '????', '/app/auth/users',      NULL,       10,  1, NOW(), NOW(), 0),
(111, 110, 3, 'user:list',           '????', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(112, 110, 3, 'user:add',            '????', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(113, 110, 3, 'user:edit',           '????', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(114, 110, 3, 'user:reset-pwd',      '????', NULL,                   NULL,       4,   1, NOW(), NOW(), 0),
(115, 110, 3, 'user:assign-role',    '????', NULL,                   NULL,       5,   1, NOW(), NOW(), 0),
(116, 110, 3, 'user:kick',           '????', NULL,                   NULL,       6,   1, NOW(), NOW(), 0),
(120, 100, 2, 'system:role',         '????', '/app/auth/roles',      NULL,       20,  1, NOW(), NOW(), 0),
(121, 120, 3, 'role:list',           '????', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(122, 120, 3, 'role:add',            '????', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(123, 120, 3, 'role:edit',           '????', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(124, 120, 3, 'role:assign-perm',    '????', NULL,                   NULL,       4,   1, NOW(), NOW(), 0),
(130, 100, 2, 'system:permission',   '????', '/app/auth/perms',      NULL,       30,  1, NOW(), NOW(), 0),
(131, 130, 3, 'perm:list',           '????', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(132, 130, 3, 'perm:add',            '????', NULL,                   NULL,       2,   1, NOW(), NOW(), 0),
(133, 130, 3, 'perm:edit',           '????', NULL,                   NULL,       3,   1, NOW(), NOW(), 0),
(140, 100, 2, 'system:perm-apply',   '????', '/app/auth/requests',   NULL,       40,  1, NOW(), NOW(), 0),
(141, 140, 3, 'perm:apply',          '????', NULL,                   NULL,       1,   1, NOW(), NOW(), 0),
(150, 100, 2, 'system:perm-approve', '????', '/app/auth/approvals',  NULL,       50,  1, NOW(), NOW(), 0),
(151, 150, 3, 'perm:approve',        '????', NULL,                   NULL,       1,   1, NOW(), NOW(), 0)
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

-- =========================
-- ???admin ??????
-- =========================
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1001, 1, 100, NOW()),
(1002, 1, 110, NOW()),
(1003, 1, 111, NOW()),
(1004, 1, 112, NOW()),
(1005, 1, 113, NOW()),
(1006, 1, 114, NOW()),
(1007, 1, 115, NOW()),
(1008, 1, 116, NOW()),
(1009, 1, 120, NOW()),
(1010, 1, 121, NOW()),
(1011, 1, 122, NOW()),
(1012, 1, 123, NOW()),
(1013, 1, 124, NOW()),
(1014, 1, 130, NOW()),
(1015, 1, 131, NOW()),
(1016, 1, 132, NOW()),
(1017, 1, 133, NOW()),
(1018, 1, 140, NOW()),
(1019, 1, 141, NOW()),
(1020, 1, 150, NOW()),
(1021, 1, 151, NOW()),
(1100, 1, 200, NOW()),
(1101, 1, 210, NOW()),
(1102, 1, 220, NOW()),
(1116, 1, 221, NOW()),
(1117, 1, 222, NOW()),
(1118, 1, 223, NOW()),
(1103, 1, 230, NOW()),
(1104, 1, 240, NOW()),
(1121, 1, 241, NOW()),
(1122, 1, 242, NOW()),
(1123, 1, 243, NOW()),
(1124, 1, 244, NOW()),
(1125, 1, 245, NOW()),
(1126, 1, 246, NOW()),
(1127, 1, 247, NOW()),
(1128, 1, 248, NOW()),
(1129, 1, 249, NOW()),
(1105, 1, 250, NOW()),
(1114, 1, 251, NOW()),
(1115, 1, 252, NOW()),
(1106, 1, 260, NOW()),
(1107, 1, 261, NOW()),
(1108, 1, 262, NOW()),
(1109, 1, 270, NOW()),
(1110, 1, 280, NOW()),
(1111, 1, 290, NOW()),
(1112, 1, 291, NOW()),
(1113, 1, 292, NOW()),
(1119, 1, 293, NOW()),
(1120, 1, 294, NOW()),
(1130, 1, 295, NOW()),
(1131, 1, 296, NOW()),
(1132, 1, 297, NOW()),
(1133, 1, 298, NOW()),
(1134, 1, 299, NOW()),
(1135, 1, 300, NOW()),
(1136, 1, 301, NOW()),
(1137, 1, 302, NOW()),
(1138, 1, 253, NOW()),
(1139, 1, 254, NOW()),
(1140, 1, 255, NOW()),
(1141, 1, 256, NOW()),
(1142, 1, 257, NOW()),
(1143, 1, 258, NOW()),
(1144, 1, 271, NOW()),
(1145, 1, 272, NOW()),
(1146, 1, 273, NOW()),
(1147, 1, 311, NOW()),
(1148, 1, 320, NOW()),
(1149, 1, 321, NOW()),
(1150, 1, 322, NOW()),
(1151, 1, 330, NOW()),
(1152, 1, 331, NOW()),
(1153, 1, 332, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor????? + ????
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(2001, 4, 140, NOW()),
(2002, 4, 141, NOW()),
(2003, 4, 150, NOW()),
(2004, 4, 151, NOW()),
(1400, 4, 200, NOW()),
(1401, 4, 210, NOW()),
(1402, 4, 230, NOW()),
(1403, 4, 260, NOW()),
(1404, 4, 261, NOW()),
(1405, 4, 262, NOW()),
(1406, 4, 270, NOW()),
(1424, 4, 271, NOW()),
(1425, 4, 272, NOW()),
(1407, 4, 100, NOW()),
(1408, 4, 240, NOW()),
(1409, 4, 243, NOW()),
(1410, 4, 244, NOW()),
(1411, 4, 245, NOW()),
(1412, 4, 246, NOW()),
(1413, 4, 295, NOW()),
(1414, 4, 296, NOW()),
(1415, 4, 297, NOW()),
(1416, 4, 298, NOW()),
(1417, 4, 299, NOW()),
(1418, 4, 300, NOW()),
(1419, 4, 301, NOW()),
(1420, 4, 302, NOW()),
(1421, 4, 294, NOW()),
(1422, 4, 253, NOW()),
(1423, 4, 257, NOW()),
(1424, 4, 271, NOW()),
(1425, 4, 272, NOW()),
(1427, 4, 311, NOW()),
(1428, 4, 320, NOW()),
(1429, 4, 322, NOW()),
(1430, 4, 330, NOW()),
(1431, 4, 331, NOW()),
(1432, 4, 332, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- operator??? + ??
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(3001, 2, 140, NOW()),
(3002, 2, 141, NOW()),
(1200, 2, 200, NOW()),
(1201, 2, 210, NOW()),
(1202, 2, 230, NOW()),
(1203, 2, 290, NOW()),
(1204, 2, 291, NOW()),
(1205, 2, 292, NOW()),
(1206, 2, 293, NOW()),
(1207, 2, 294, NOW()),
(1208, 2, 302, NOW()),
(1209, 2, 253, NOW()),
(1210, 2, 256, NOW()),
(1211, 2, 320, NOW()),
(1212, 2, 322, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng??? + ????
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(3003, 3, 140, NOW()),
(3004, 3, 141, NOW()),
(1300, 3, 200, NOW()),
(1301, 3, 210, NOW()),
(1302, 3, 220, NOW()),
(1311, 3, 221, NOW()),
(1312, 3, 222, NOW()),
(1313, 3, 223, NOW()),
(1303, 3, 230, NOW()),
(1304, 3, 240, NOW()),
(1315, 3, 241, NOW()),
(1316, 3, 242, NOW()),
(1317, 3, 243, NOW()),
(1318, 3, 244, NOW()),
(1319, 3, 245, NOW()),
(1320, 3, 246, NOW()),
(1321, 3, 247, NOW()),
(1322, 3, 248, NOW()),
(1323, 3, 249, NOW()),
(1324, 3, 295, NOW()),
(1325, 3, 296, NOW()),
(1326, 3, 297, NOW()),
(1327, 3, 298, NOW()),
(1328, 3, 299, NOW()),
(1329, 3, 300, NOW()),
(1330, 3, 301, NOW()),
(1331, 3, 302, NOW()),
(1332, 3, 294, NOW()),
(1333, 3, 253, NOW()),
(1334, 3, 254, NOW()),
(1335, 3, 255, NOW()),
(1336, 3, 256, NOW()),
(1337, 3, 257, NOW()),
(1338, 3, 258, NOW()),
(1339, 3, 271, NOW()),
(1340, 3, 272, NOW()),
(1341, 3, 273, NOW()),
(1342, 3, 311, NOW()),
(1343, 3, 320, NOW()),
(1344, 3, 321, NOW()),
(1345, 3, 322, NOW()),
(1346, 3, 330, NOW()),
(1347, 3, 331, NOW()),
(1348, 3, 332, NOW()),
(1305, 3, 250, NOW()),
(1309, 3, 251, NOW()),
(1310, 3, 252, NOW()),
(1314, 3, 293, NOW()),
(1306, 3, 260, NOW()),
(1307, 3, 270, NOW()),
(1308, 3, 280, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- =========================
-- Route ?????? migrate_route.sql?
-- =========================
CREATE TABLE IF NOT EXISTS mes_step (
    id          BIGINT        NOT NULL COMMENT '??',
    step_code   VARCHAR(64)   NOT NULL COMMENT '????',
    step_name   VARCHAR(128)  NOT NULL COMMENT '????',
    step_type   TINYINT       NOT NULL DEFAULT 1 COMMENT '1?? 2?? 3??',
    eqp_type    VARCHAR(64)            COMMENT '??????',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1?? 0??',
    remark      VARCHAR(255)           COMMENT '??',
    create_time DATETIME               COMMENT '????',
    update_time DATETIME               COMMENT '????',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_step_code (step_code),
    KEY idx_step_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????';

CREATE TABLE IF NOT EXISTS mes_route (
    id           BIGINT        NOT NULL COMMENT '??',
    route_code   VARCHAR(64)   NOT NULL COMMENT '????',
    route_name   VARCHAR(128)  NOT NULL COMMENT '????',
    product_code VARCHAR(64)            COMMENT '????',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1?? 0??',
    remark       VARCHAR(255)           COMMENT '??',
    create_time  DATETIME               COMMENT '????',
    update_time  DATETIME               COMMENT '????',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_code (route_code),
    KEY idx_route_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????';

CREATE TABLE IF NOT EXISTS mes_route_version (
    id            BIGINT       NOT NULL COMMENT '??',
    route_id      BIGINT       NOT NULL COMMENT '??ID',
    version_no    INT          NOT NULL COMMENT '???',
    status        VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/active/archived',
    published_at  DATETIME              COMMENT '????',
    published_by  BIGINT                COMMENT '???',
    remark        VARCHAR(255)          COMMENT '??',
    create_time   DATETIME              COMMENT '????',
    update_time   DATETIME              COMMENT '????',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_ver (route_id, version_no),
    KEY idx_route_ver_status (route_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??????';

CREATE TABLE IF NOT EXISTS mes_route_step (
    id            BIGINT   NOT NULL COMMENT '??',
    version_id    BIGINT   NOT NULL COMMENT '??ID',
    step_id       BIGINT   NOT NULL COMMENT '??ID',
    sort_no       INT      NOT NULL COMMENT '???',
    next_sort_no  INT               COMMENT '??????????',
    create_time   DATETIME          COMMENT '????',
    update_time   DATETIME          COMMENT '????',
    deleted       TINYINT  NOT NULL DEFAULT 0 COMMENT '????',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ver_sort (version_id, sort_no),
    KEY idx_ver_step (version_id, step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??????';

INSERT INTO mes_step (id, step_code, step_name, step_type, eqp_type, status, remark, create_time, update_time, deleted) VALUES
(5001, 'PHOTO-01', '??',         1, NULL, 1, NULL, NOW(), NOW(), 0),
(5002, 'ETCH-01',  '???',       1, NULL, 1, NULL, NOW(), NOW(), 0),
(5003, 'ETCH-02',  '???',       1, NULL, 1, NULL, NOW(), NOW(), 0),
(5004, 'CMP-01',   '??????', 1, NULL, 1, NULL, NOW(), NOW(), 0),
(5005, 'DIFF-03',  '??',         1, NULL, 1, NULL, NOW(), NOW(), 0),
(5006, 'METRO-01', '??',         2, NULL, 1, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE step_name = VALUES(step_name), update_time = NOW();

INSERT INTO mes_route (id, route_code, route_name, product_code, status, remark, create_time, update_time, deleted) VALUES
(5100, 'WAFER-N7-MAIN', '?????', 'N7', 1, '????', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE route_name = VALUES(route_name), update_time = NOW();

INSERT INTO mes_route_version (id, route_id, version_no, status, published_at, published_by, remark, create_time, update_time, deleted) VALUES
(5101, 5100, 1, 'active', NOW(), 1, '????', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE status = VALUES(status), update_time = NOW();

INSERT INTO mes_route_step (id, version_id, step_id, sort_no, next_sort_no, create_time, update_time, deleted) VALUES
(5201, 5101, 5001, 10, 20,   NOW(), NOW(), 0),
(5202, 5101, 5002, 20, 30,   NOW(), NOW(), 0),
(5203, 5101, 5003, 30, 40,   NOW(), NOW(), 0),
(5204, 5101, 5004, 40, 50,   NOW(), NOW(), 0),
(5205, 5101, 5005, 50, 60,   NOW(), NOW(), 0),
(5206, 5101, 5006, 60, NULL, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE step_id = VALUES(step_id), next_sort_no = VALUES(next_sort_no), update_time = NOW();

CREATE TABLE IF NOT EXISTS mes_route_edge (
    id                 BIGINT        NOT NULL COMMENT '主键',
    version_id         BIGINT        NOT NULL COMMENT '路线版本ID',
    from_sort_no       INT           NOT NULL COMMENT '触发站顺序号',
    to_sort_no         INT           NOT NULL COMMENT '目标站顺序号',
    edge_type          VARCHAR(16)   NOT NULL COMMENT 'normal/branch/rework/skip_allow/off_flow/time_link',
    max_rework_count   INT                    COMMENT 'rework次数上限',
    max_queue_min      INT                    COMMENT 'QueueTime上限分钟',
    min_queue_min      INT                    COMMENT 'QueueTime下限预留',
    on_violate         VARCHAR(16)            COMMENT 'HOLD|ALARM|HOLD_ALARM',
    reason_codes       VARCHAR(256)           COMMENT '逗号分隔原因码',
    condition_code     VARCHAR(64)            COMMENT 'branch条件码',
    sort_no            INT           NOT NULL DEFAULT 0 COMMENT '同站多边排序',
    create_time        DATETIME               COMMENT '创建时间',
    update_time        DATETIME               COMMENT '更新时间',
    deleted            TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ver_edge_cond (version_id, from_sort_no, to_sort_no, edge_type, condition_code),
    KEY idx_ver_from (version_id, from_sort_no, edge_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线版本边';

INSERT INTO mes_route_edge (id, version_id, from_sort_no, to_sort_no, edge_type, max_rework_count, reason_codes, condition_code, sort_no, create_time, update_time, deleted)
VALUES (5301, 5101, 60, 10, 'rework', 2, 'CD_FAIL,OVERLAY_FAIL', NULL, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE max_rework_count = VALUES(max_rework_count), update_time = NOW();

INSERT INTO mes_route_edge (id, version_id, from_sort_no, to_sort_no, edge_type, max_rework_count, reason_codes, condition_code, sort_no, create_time, update_time, deleted) VALUES
(5320, 5101, 10, 20, 'normal', NULL, NULL, NULL, 0, NOW(), NOW(), 0),
(5321, 5101, 20, 30, 'normal', NULL, NULL, NULL, 1, NOW(), NOW(), 0),
(5322, 5101, 30, 40, 'normal', NULL, NULL, NULL, 2, NOW(), NOW(), 0),
(5323, 5101, 40, 50, 'normal', NULL, NULL, NULL, 3, NOW(), NOW(), 0),
(5324, 5101, 50, 60, 'normal', NULL, NULL, NULL, 4, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE to_sort_no = VALUES(to_sort_no), update_time = NOW();

-- =========================
-- Lot ?????? migrate_lot.sql?
-- =========================
CREATE TABLE IF NOT EXISTS mes_lot (
    id                BIGINT        NOT NULL COMMENT '??',
    lot_no            VARCHAR(64)   NOT NULL COMMENT '???',
    product_code      VARCHAR(64)            COMMENT '????',
    qty               INT           NOT NULL DEFAULT 0 COMMENT '??',
    scrap_qty         INT           NOT NULL DEFAULT 0 COMMENT '累计报废数量',
    priority          INT           NOT NULL DEFAULT 50 COMMENT '???1-100????????50',
    hot_flag          TINYINT       NOT NULL DEFAULT 0 COMMENT 'Hot Lot 0/1',
    customer_lot      VARCHAR(64)            COMMENT '客户Lot',
    parent_lot_id     BIGINT                 COMMENT '直系父Lot',
    merged_to_lot_id  BIGINT                 COMMENT '合批目标Lot',
    route_id          BIGINT                 COMMENT '路线ID',
    route_version_id  BIGINT                 COMMENT '放行快照版本ID，Release后锁定',
    current_sort_no   INT                    COMMENT '当前站顺序号',
    current_step_id   BIGINT                 COMMENT '当前工序ID',
    current_eqp_id    BIGINT                 COMMENT '当前设备ID',
    carrier_id        BIGINT                 COMMENT '当前载具ID',
    rework_counts     VARCHAR(512)           COMMENT '按触发站累计返工次数JSON',
    off_flow          TINYINT       NOT NULL DEFAULT 0 COMMENT '是否在Off-Flow中 0/1',
    off_flow_anchor_sort INT                 COMMENT 'Off-Flow锚点站序',
    off_flow_anchor_step_id BIGINT           COMMENT 'Off-Flow锚点工序',
    off_flow_anchor_eqp_id BIGINT            COMMENT 'Off-Flow锚点机台',
    off_flow_anchor_status VARCHAR(32)       COMMENT 'Off-Flow锚点状态 wait/processing',
    off_flow_counts   VARCHAR(512)           COMMENT '按触发站累计Off-Flow次数JSON',
    qtime_from_sort   INT                    COMMENT 'QueueTime开窗触发站',
    qtime_to_sort     INT                    COMMENT 'QueueTime目标站',
    qtime_started_at  DATETIME(3)            COMMENT 'QueueTime开窗时刻',
    qtime_max_min     INT                    COMMENT 'QueueTime开窗固化上限分钟',
    qtime_on_violate  VARCHAR(16)            COMMENT 'QueueTime开窗固化策略',
    process_started_at DATETIME(3)           COMMENT 'ProcessTime开计时时刻',
    status            VARCHAR(32)   NOT NULL DEFAULT 'created' COMMENT '状态',
    remark            VARCHAR(512)           COMMENT '备注',
    version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_by         BIGINT                 COMMENT '创建人',
    create_time       DATETIME               COMMENT '创建时间',
    update_by         BIGINT                 COMMENT '更新人',
    update_time       DATETIME               COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lot_no (lot_no),
    KEY idx_lot_status (status),
    KEY idx_lot_product (product_code),
    KEY idx_lot_route (route_id),
    KEY idx_lot_route_ver (route_version_id),
    KEY idx_lot_parent (parent_lot_id),
    KEY idx_lot_current_step (current_step_id),
    KEY idx_lot_carrier (carrier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次';

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

CREATE TABLE IF NOT EXISTS mes_lot_no_seq (
    seq_day  CHAR(8) NOT NULL COMMENT 'yyyyMMdd',
    next_no  INT     NOT NULL COMMENT '??????????????',
    PRIMARY KEY (seq_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='???????';

CREATE TABLE IF NOT EXISTS mes_tx_log (
    id                BIGINT        NOT NULL COMMENT '??',
    lot_id            BIGINT        NOT NULL COMMENT '??ID',
    lot_no            VARCHAR(64)            COMMENT '?????',
    tx_type           VARCHAR(32)   NOT NULL COMMENT '????: RELEASE??/MOVE??/TRACK_IN??/TRACK_OUT??/HOLD??/RELEASE_HOLD??/SKIP??/REWORK??',
    from_status       VARCHAR(32)            COMMENT '?????(?mes_lot.status??)',
    to_status         VARCHAR(32)            COMMENT '?????(?mes_lot.status??)',
    from_sort_no      INT                    COMMENT '?????',
    to_sort_no        INT                    COMMENT '?????',
    step_id           BIGINT                 COMMENT '????',
    eqp_id            BIGINT                 COMMENT '????',
    recipe_id         BIGINT                 COMMENT '??ID',
    recipe_version_id BIGINT                 COMMENT '??????ID',
    route_version_id  BIGINT                 COMMENT '??????',
    remark            VARCHAR(512)           COMMENT '??',
    ext_json          VARCHAR(512)           COMMENT '事务扩展JSON',
    oper_user_id      BIGINT                 COMMENT '???',
    oper_user_name    VARCHAR(64)            COMMENT '???????',
    create_time       DATETIME      NOT NULL COMMENT '????',
    PRIMARY KEY (id),
    KEY idx_tx_lot_time (lot_id, create_time),
    KEY idx_tx_type_time (tx_type, create_time),
    KEY idx_tx_eqp_time (eqp_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Track?????????';

-- =========================
-- WIP ??????? migrate_wip.sql?
-- status: wait??? / processing??? / held??
-- =========================
CREATE TABLE IF NOT EXISTS mes_wip_lot (
    lot_id            BIGINT        NOT NULL COMMENT '??ID??mes_lot.id',
    lot_no            VARCHAR(64)   NOT NULL COMMENT '?????',
    product_code      VARCHAR(64)            COMMENT '????',
    qty               INT           NOT NULL DEFAULT 0 COMMENT '??',
    priority          INT           NOT NULL DEFAULT 50 COMMENT '???1-100?????',
    hot_flag          TINYINT       NOT NULL DEFAULT 0 COMMENT 'Hot Lot 0/1',
    customer_lot      VARCHAR(64)            COMMENT '??Lot',
    status            VARCHAR(32)   NOT NULL COMMENT '????: wait???/processing???/held??',
    current_sort_no   INT                    COMMENT '???????????',
    current_step_id   BIGINT                 COMMENT '????ID',
    current_eqp_id    BIGINT                 COMMENT '????ID?TrackIn????',
    route_id          BIGINT                 COMMENT '??ID',
    route_version_id  BIGINT                 COMMENT '??????ID',
    update_time       DATETIME      NOT NULL COMMENT '??????',
    PRIMARY KEY (lot_id),
    UNIQUE KEY uk_wip_lot_no (lot_no),
    KEY idx_wip_status_sort (status, current_sort_no),
    KEY idx_wip_product (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='?????WIP???Track??????';

INSERT INTO mes_lot (
  id, lot_no, product_code, qty, priority, customer_lot,
  route_id, route_version_id, current_sort_no, current_step_id, current_eqp_id,
  status, remark, version,
  create_by, create_time, update_by, update_time, deleted
) VALUES
(6001, 'LOT-N7-DEMO-01', 'N7', 25, 50, NULL, 5100, NULL, NULL, NULL, NULL, 'created', '?????', 0, 1, NOW(), 1, NOW(), 0),
(6002, 'LOT-N7-DEMO-02', 'N7', 25, 80, 'CUST-001', 5100, 5101, 10, 5001, NULL, 'wait', '???????? wait?', 0, 1, NOW(), 1, NOW(), 0)
ON DUPLICATE KEY UPDATE
  product_code = VALUES(product_code),
  qty = VALUES(qty),
  status = VALUES(status),
  route_version_id = VALUES(route_version_id),
  current_sort_no = VALUES(current_sort_no),
  current_step_id = VALUES(current_step_id),
  update_time = NOW();

INSERT INTO mes_tx_log (
  id, lot_id, lot_no, tx_type,
  from_status, to_status, from_sort_no, to_sort_no,
  step_id, eqp_id, route_version_id, remark,
  oper_user_id, oper_user_name, create_time
) VALUES
(7001, 6002, 'LOT-N7-DEMO-02', 'RELEASE',
 'created', 'wait', NULL, 10,
 5001, NULL, 5101, '???????',
 1, 'admin', NOW())
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

INSERT INTO mes_wip_lot (
  lot_id, lot_no, product_code, qty, priority, customer_lot,
  status, current_sort_no, current_step_id, current_eqp_id,
  route_id, route_version_id, update_time
) VALUES
(6002, 'LOT-N7-DEMO-02', 'N7', 25, 80, 'CUST-001',
 'wait', 10, 5001, NULL,
 5100, 5101, NOW())
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  current_sort_no = VALUES(current_sort_no),
  current_step_id = VALUES(current_step_id),
  update_time = VALUES(update_time);

-- =========================
-- Hold ????? migrate_hold.sql?
-- mes_hold.status: active??? / released???
-- mes_hold_reason.category: quality/eng/customer/other
-- =========================
CREATE TABLE IF NOT EXISTS mes_hold_reason (
    id            BIGINT       NOT NULL COMMENT '??',
    reason_code   VARCHAR(32)  NOT NULL COMMENT '????????',
    reason_name   VARCHAR(64)  NOT NULL COMMENT '????',
    category      VARCHAR(32)           COMMENT '??: quality??/eng??/customer??/other??',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '??: 1?? 0??',
    remark        VARCHAR(256)          COMMENT '??',
    create_time   DATETIME              COMMENT '????',
    update_time   DATETIME              COMMENT '????',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '????: 0? 1?',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hold_reason_code (reason_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='?????';

INSERT INTO mes_hold_reason (
  id, reason_code, reason_name, category, status, remark, create_time, update_time, deleted
) VALUES
(8001, 'Q_PENDING',  '??/??',     'quality',  1, '????',   NOW(), NOW(), 0),
(8002, 'Q_ABNORMAL', '??/????', 'quality',  1, '????',   NOW(), NOW(), 0),
(8003, 'E_REVIEW',   '????',     'eng',      1, '??',       NOW(), NOW(), 0),
(8004, 'M_MATERIAL', '??/????', 'other',    1, '??',       NOW(), NOW(), 0),
(8005, 'C_REQUEST',  '????',     'customer', 1, '??',       NOW(), NOW(), 0),
(8006, 'OTHER',      '??',         'other',    1, '????',   NOW(), NOW(), 0),
(8007, 'QTIME_EXCEED', 'Queue Time超时', 'quality', 1, '站间等待超限', NOW(), NOW(), 0),
(8008, 'PROCESS_TIME_EXCEED', 'Process Time超时', 'quality', 1, '站内加工超上限，出站后锁批', NOW(), NOW(), 0),
(8009, 'EDC_OOS', '量测超规', 'quality', 1, '采集OOS后锁批，解锁后须重采合格才能完工', NOW(), NOW(), 0),
(8010, 'ALARM_POLICY', '告警策略锁批', 'quality', 1, 'mes_alarm_code.on_raise=HOLD_LOT 时引用', NOW(), NOW(), 0),
(8011, 'CUSTOMER_COMPLAINT', '客诉遏制', 'customer', 1, '追溯包contain默认原因', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  reason_name = VALUES(reason_name),
  category = VALUES(category),
  status = VALUES(status),
  remark = VALUES(remark),
  update_time = NOW();

CREATE TABLE IF NOT EXISTS mes_hold (
    id                 BIGINT       NOT NULL COMMENT '??',
    lot_id             BIGINT       NOT NULL COMMENT '??ID',
    lot_no             VARCHAR(64)           COMMENT '?????',
    reason_id          BIGINT       NOT NULL COMMENT '???ID',
    reason_code        VARCHAR(32)  NOT NULL COMMENT '??????',
    status             VARCHAR(16)  NOT NULL COMMENT '??: active???/released???',
    prev_status        VARCHAR(32)           COMMENT 'Hold?Lot??(wait/processing)',
    remark             VARCHAR(512)          COMMENT '????',
    release_remark     VARCHAR(512)          COMMENT '????',
    hold_user_id       BIGINT                COMMENT '???',
    hold_user_name     VARCHAR(64)           COMMENT '???????',
    hold_time          DATETIME     NOT NULL COMMENT '????',
    release_user_id    BIGINT                COMMENT '???',
    release_user_name  VARCHAR(64)           COMMENT '???????',
    release_time       DATETIME              COMMENT '????',
    create_time        DATETIME              COMMENT '????',
    update_time        DATETIME              COMMENT '????',
    PRIMARY KEY (id),
    KEY idx_hold_lot_status (lot_id, status),
    KEY idx_hold_status_time (status, hold_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锁批记录（最小集；一期同Lot最多一条active）';

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

-- =========================
-- Equipment
-- mes_eqp.status: idle/running/down/pm/eng/offline
-- =========================
CREATE TABLE IF NOT EXISTS mes_eqp (
    id            BIGINT       NOT NULL COMMENT '??',
    eqp_code      VARCHAR(64)  NOT NULL COMMENT '????????',
    eqp_name      VARCHAR(128) NOT NULL COMMENT '????',
    eqp_type      VARCHAR(64)           COMMENT '??????? mes_step.eqp_type?',
    area          VARCHAR(64)           COMMENT '??/Bay',
    status        VARCHAR(16)  NOT NULL DEFAULT 'idle' COMMENT '???: idle/running/down/pm/eng/offline',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1?? 0??',
    remark        VARCHAR(256)          COMMENT '??',
    version       INT          NOT NULL DEFAULT 0 COMMENT '???',
    create_by     BIGINT                COMMENT '???',
    update_by     BIGINT                COMMENT '???',
    create_time   DATETIME              COMMENT '????',
    update_time   DATETIME              COMMENT '????',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '????: 0? 1?',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eqp_code (eqp_code),
    KEY idx_eqp_status (status, enabled),
    KEY idx_eqp_type (eqp_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??????????';

INSERT INTO mes_eqp (
  id, eqp_code, eqp_name, eqp_type, area, status, enabled, remark, version,
  create_time, update_time, deleted
) VALUES
(9001, 'EQP-ETCH-A1',  '??? A1', 'ETCH',  '??', 'idle', 1, NULL, 0, NOW(), NOW(), 0),
(9002, 'EQP-ETCH-A2',  '??? A2', 'ETCH',  '??', 'idle', 1, NULL, 0, NOW(), NOW(), 0),
(9003, 'EQP-CMP-B2',   '??? B2', 'CMP',   'CMP',  'idle', 1, NULL, 0, NOW(), NOW(), 0),
(9004, 'EQP-PHOTO-E1', '??? E1', 'PHOTO', '??', 'pm',   1, '???', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  eqp_name = VALUES(eqp_name),
  eqp_type = VALUES(eqp_type),
  area = VALUES(area),
  status = VALUES(status),
  enabled = VALUES(enabled),
  remark = VALUES(remark),
  update_time = NOW();

-- =========================
-- Dispatch ????? migrate_dispatch.sql?
-- mes_dispatch_reserve.status: active/released/expired/consumed
-- =========================
CREATE TABLE IF NOT EXISTS mes_dispatch_reserve (
    id               BIGINT       NOT NULL COMMENT '??',
    lot_id           BIGINT       NOT NULL COMMENT '??ID',
    lot_slot         BIGINT                COMMENT 'active?=lot_id, else NULL',
    eqp_id           BIGINT       NOT NULL COMMENT '??ID',
    eqp_slot         BIGINT                COMMENT 'active?=eqp_id, else NULL',
    status           VARCHAR(16)  NOT NULL COMMENT '???: active???/released???/expired???/consumed???',
    expire_time      DATETIME     NOT NULL COMMENT '????',
    reserve_user_id  BIGINT                COMMENT '???',
    consume_tx_id    BIGINT                COMMENT '????? mes_tx_log.id????',
    remark           VARCHAR(256)          COMMENT '??',
    version          INT          NOT NULL DEFAULT 0 COMMENT 'optimistic lock',
    create_time      DATETIME              COMMENT '????',
    update_time      DATETIME              COMMENT '????',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '????: 0? 1?',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reserve_eqp_slot (eqp_slot),
    UNIQUE KEY uk_reserve_lot_slot (lot_slot),
    KEY idx_reserve_lot_status (lot_id, status),
    KEY idx_reserve_eqp_status (eqp_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='?????????????????';

-- =========================
-- Recipe (see migrate_recipe.sql)
-- mes_recipe_version.status: draft/active/obsolete
-- =========================
CREATE TABLE IF NOT EXISTS mes_recipe (
    id            BIGINT       NOT NULL COMMENT 'PK',
    recipe_code   VARCHAR(64)  NOT NULL COMMENT 'recipe code',
    recipe_name   VARCHAR(128) NOT NULL COMMENT 'recipe name',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 enabled',
    remark        VARCHAR(256)          COMMENT 'remark',
    version       INT          NOT NULL DEFAULT 0 COMMENT 'optimistic lock',
    create_by     BIGINT                COMMENT 'create by',
    update_by     BIGINT                COMMENT 'update by',
    create_time   DATETIME              COMMENT 'create time',
    update_time   DATETIME              COMMENT 'update time',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_code (recipe_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='recipe master';

CREATE TABLE IF NOT EXISTS mes_recipe_version (
    id               BIGINT       NOT NULL COMMENT 'PK',
    recipe_id        BIGINT       NOT NULL COMMENT 'recipe id',
    version_no       INT          NOT NULL COMMENT 'version no',
    status           VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/active/obsolete',
    body_json        TEXT                  COMMENT 'params json',
    body_object_key  VARCHAR(256)          COMMENT 'minio key',
    remark           VARCHAR(256)          COMMENT 'remark',
    published_at     DATETIME              COMMENT 'published at',
    create_by        BIGINT                COMMENT 'create by',
    update_by        BIGINT                COMMENT 'update by',
    create_time      DATETIME              COMMENT 'create time',
    update_time      DATETIME              COMMENT 'update time',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_ver (recipe_id, version_no),
    KEY idx_recipe_ver_status (recipe_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='recipe version';

CREATE TABLE IF NOT EXISTS mes_recipe_binding (
    id                  BIGINT       NOT NULL COMMENT 'PK',
    step_id             BIGINT       NOT NULL COMMENT 'step id',
    eqp_id              BIGINT                COMMENT 'eqp id',
    eqp_type            VARCHAR(64)           COMMENT 'eqp type',
    recipe_id           BIGINT       NOT NULL COMMENT 'recipe id',
    recipe_version_id   BIGINT                COMMENT 'version id or follow active',
    enabled             TINYINT      NOT NULL DEFAULT 1 COMMENT '1 enabled',
    create_by           BIGINT                COMMENT 'create by',
    update_by           BIGINT                COMMENT 'update by',
    create_time         DATETIME              COMMENT 'create time',
    update_time         DATETIME              COMMENT 'update time',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    KEY idx_bind_step (step_id, enabled),
    KEY idx_bind_eqp (eqp_id),
    KEY idx_bind_type (step_id, eqp_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='recipe step-eqp binding';

-- =========================
-- EDC (see migrate_edc.sql)
-- =========================
CREATE TABLE IF NOT EXISTS mes_edc_param (
    id            BIGINT       NOT NULL COMMENT 'PK',
    param_code    VARCHAR(64)  NOT NULL COMMENT 'param code',
    param_name    VARCHAR(128) NOT NULL COMMENT 'param name',
    unit          VARCHAR(32)           COMMENT 'unit',
    value_type    VARCHAR(16)  NOT NULL DEFAULT 'NUMBER' COMMENT 'NUMBER',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 enabled',
    remark        VARCHAR(256)          COMMENT 'remark',
    version       INT          NOT NULL DEFAULT 0 COMMENT 'optimistic lock',
    create_by     BIGINT                COMMENT 'create by',
    update_by     BIGINT                COMMENT 'update by',
    create_time   DATETIME              COMMENT 'create time',
    update_time   DATETIME              COMMENT 'update time',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edc_param_code (param_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='edc param';

CREATE TABLE IF NOT EXISTS mes_edc_spec (
    id            BIGINT         NOT NULL COMMENT 'PK',
    param_id      BIGINT         NOT NULL COMMENT 'param id',
    product_code  VARCHAR(64)    NOT NULL DEFAULT '' COMMENT 'product; empty=all',
    version_no    INT            NOT NULL COMMENT 'version no',
    status        VARCHAR(16)    NOT NULL DEFAULT 'draft' COMMENT 'draft/active/obsolete',
    usl           DECIMAL(20,8)           COMMENT 'usl',
    lsl           DECIMAL(20,8)           COMMENT 'lsl',
    target        DECIMAL(20,8)           COMMENT 'target',
    remark        VARCHAR(256)            COMMENT 'remark',
    published_at  DATETIME                COMMENT 'published at',
    version       INT            NOT NULL DEFAULT 0 COMMENT 'optimistic lock',
    create_by     BIGINT                  COMMENT 'create by',
    update_by     BIGINT                  COMMENT 'update by',
    create_time   DATETIME                COMMENT 'create time',
    update_time   DATETIME                COMMENT 'update time',
    deleted       TINYINT        NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edc_spec_ver (param_id, product_code, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='edc spec';

CREATE TABLE IF NOT EXISTS mes_edc_plan (
    id            BIGINT       NOT NULL COMMENT 'PK',
    step_id       BIGINT       NOT NULL COMMENT 'step id',
    required      TINYINT      NOT NULL DEFAULT 0 COMMENT '1 trackout gate',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 enabled',
    remark        VARCHAR(256)          COMMENT 'remark',
    version       INT          NOT NULL DEFAULT 0 COMMENT 'optimistic lock',
    create_by     BIGINT                COMMENT 'create by',
    update_by     BIGINT                COMMENT 'update by',
    create_time   DATETIME              COMMENT 'create time',
    update_time   DATETIME              COMMENT 'update time',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edc_plan_step (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='edc plan';

CREATE TABLE IF NOT EXISTS mes_edc_plan_item (
    id            BIGINT       NOT NULL COMMENT 'PK',
    plan_id       BIGINT       NOT NULL COMMENT 'plan id',
    param_id      BIGINT       NOT NULL COMMENT 'param id',
    spec_id       BIGINT                COMMENT 'spec or follow active',
    sort_no       INT          NOT NULL DEFAULT 0 COMMENT 'sort',
    mandatory     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 mandatory',
    create_time   DATETIME              COMMENT 'create time',
    update_time   DATETIME              COMMENT 'update time',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    UNIQUE KEY uk_edc_plan_param (plan_id, param_id),
    KEY idx_edc_plan_item (plan_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='edc plan item';

CREATE TABLE IF NOT EXISTS mes_edc_collection (
    id                 BIGINT       NOT NULL COMMENT 'PK',
    lot_id             BIGINT       NOT NULL COMMENT 'lot id',
    lot_no             VARCHAR(64)           COMMENT 'lot no',
    route_version_id   BIGINT       NOT NULL COMMENT 'route version',
    sort_no            INT          NOT NULL COMMENT 'sort no',
    step_id            BIGINT       NOT NULL COMMENT 'step id',
    track_in_tx_id     BIGINT       NOT NULL COMMENT 'track in tx',
    plan_id            BIGINT       NOT NULL COMMENT 'plan id',
    result             VARCHAR(16)  NOT NULL COMMENT 'PASS/FAIL',
    source             VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO',
    eqp_id             BIGINT                COMMENT 'eqp id',
    remark             VARCHAR(256)          COMMENT 'remark',
    collected_by       BIGINT                COMMENT 'collected by',
    collected_at       DATETIME     NOT NULL COMMENT 'collected at',
    create_time        DATETIME              COMMENT 'create time',
    update_time        DATETIME              COMMENT 'update time',
    deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    KEY idx_edc_col_visit (lot_id, track_in_tx_id, collected_at),
    KEY idx_edc_col_lot_step (lot_id, step_id, collected_at),
    KEY idx_edc_col_step_time (step_id, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='edc collection';

CREATE TABLE IF NOT EXISTS mes_edc_collection_item (
    id              BIGINT         NOT NULL COMMENT 'PK',
    collection_id   BIGINT         NOT NULL COMMENT 'collection id',
    param_id        BIGINT         NOT NULL COMMENT 'param id',
    spec_id         BIGINT                  COMMENT 'spec used',
    usl_snap        DECIMAL(20,8)           COMMENT 'usl snap',
    lsl_snap        DECIMAL(20,8)           COMMENT 'lsl snap',
    value_num       DECIMAL(20,8)  NOT NULL COMMENT 'value',
    item_result     VARCHAR(16)    NOT NULL COMMENT 'PASS/OOS',
    create_time     DATETIME                COMMENT 'create time',
    deleted         TINYINT        NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    KEY idx_edc_col_item (collection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='edc collection item';

CREATE TABLE IF NOT EXISTS mes_spc_chart (
    id            BIGINT         NOT NULL COMMENT 'PK',
    param_id      BIGINT         NOT NULL COMMENT 'param id',
    step_id       BIGINT         NOT NULL COMMENT 'step id',
    eqp_id        BIGINT         NOT NULL DEFAULT 0 COMMENT 'eqp; 0=all eqp at step',
    chart_type    VARCHAR(16)    NOT NULL DEFAULT 'IMR' COMMENT 'IMR',
    limit_mode    VARCHAR(16)    NOT NULL COMMENT 'MANUAL/LEARNING',
    learning_n    INT            NOT NULL DEFAULT 25 COMMENT 'learning sample n',
    ucl           DECIMAL(20,8)           COMMENT 'ucl',
    cl            DECIMAL(20,8)           COMMENT 'cl',
    lcl           DECIMAL(20,8)           COMMENT 'lcl',
    run_n         INT            NOT NULL DEFAULT 7 COMMENT 'run rule n; 0=off',
    enabled       TINYINT        NOT NULL DEFAULT 1 COMMENT '1 enabled',
    version       INT            NOT NULL DEFAULT 0 COMMENT 'optimistic lock',
    create_by     BIGINT                  COMMENT 'create by',
    update_by     BIGINT                  COMMENT 'update by',
    create_time   DATETIME                COMMENT 'create time',
    update_time   DATETIME                COMMENT 'update time',
    deleted       TINYINT        NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    UNIQUE KEY uk_spc_chart_ctx (param_id, step_id, eqp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='spc chart';

CREATE TABLE IF NOT EXISTS mes_spc_eval (
    id                   BIGINT         NOT NULL COMMENT 'PK',
    chart_id             BIGINT         NOT NULL COMMENT 'chart id',
    collection_item_id   BIGINT         NOT NULL COMMENT 'edc item id',
    ooc                  TINYINT        NOT NULL COMMENT '0/1',
    rule_code            VARCHAR(16)    NOT NULL COMMENT 'WE1/RUN',
    ucl_snap             DECIMAL(20,8)           COMMENT 'ucl snap',
    cl_snap              DECIMAL(20,8)           COMMENT 'cl snap',
    lcl_snap             DECIMAL(20,8)           COMMENT 'lcl snap',
    create_time          DATETIME                COMMENT 'create time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_spc_eval_item (chart_id, collection_item_id),
    KEY idx_spc_eval_chart_time (chart_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='spc eval';

CREATE TABLE IF NOT EXISTS mes_alarm_code (
    code               VARCHAR(64)    NOT NULL COMMENT 'alarm code PK',
    name               VARCHAR(128)   NOT NULL COMMENT 'display name',
    level              VARCHAR(16)    NOT NULL COMMENT 'CRITICAL/WARNING/INFO',
    on_raise           VARCHAR(16)    NOT NULL DEFAULT 'NONE' COMMENT 'NONE/HOLD_LOT',
    hold_reason_code   VARCHAR(32)             COMMENT 'hold reason when HOLD_LOT',
    enabled            TINYINT        NOT NULL DEFAULT 1 COMMENT '1 enabled',
    remark             VARCHAR(256)            COMMENT 'remark',
    create_time        DATETIME                COMMENT 'create time',
    update_time        DATETIME                COMMENT 'update time',
    deleted            TINYINT        NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='alarm code';

INSERT INTO mes_alarm_code (code, name, level, on_raise, hold_reason_code, enabled, remark, create_time, update_time, deleted) VALUES
('SPC_OOC',                 'SPC失控',           'WARNING', 'NONE', NULL, 1, '控制限判异', NOW(), NOW(), 0),
('QTIME_EXCEED',            'Queue Time超时',    'WARNING', 'NONE', NULL, 1, 'Hold 由 QTime 自挂', NOW(), NOW(), 0),
('QTIME_OPEN_FAIL',         'Queue Time开窗失败', 'WARNING', 'NONE', NULL, 1, NULL, NOW(), NOW(), 0),
('PROCESS_TIME_VIOLATION',  'Process Time违规',  'WARNING', 'NONE', NULL, 1, '超 max Hold 由 ProcessTime 自挂', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  level = VALUES(level),
  on_raise = VALUES(on_raise),
  enabled = VALUES(enabled),
  remark = VALUES(remark),
  update_time = NOW();

CREATE TABLE IF NOT EXISTS mes_alarm (
    id                 BIGINT         NOT NULL COMMENT 'PK',
    code               VARCHAR(64)    NOT NULL COMMENT 'alarm code',
    level              VARCHAR(16)    NOT NULL COMMENT 'level snap',
    status             VARCHAR(16)    NOT NULL COMMENT 'OPEN/ACK/CLEARED',
    message            VARCHAR(512)            COMMENT 'message',
    entity_type        VARCHAR(16)    NOT NULL DEFAULT 'NONE' COMMENT 'LOT/EQP/CHART/NONE',
    entity_id          BIGINT         NOT NULL DEFAULT 0 COMMENT 'entity id; 0 if none',
    dedupe_key         VARCHAR(128)   NOT NULL COMMENT 'code|type|id',
    payload_json       TEXT                    COMMENT 'payload JSON',
    raise_count        INT            NOT NULL DEFAULT 1 COMMENT 'bump on OPEN',
    first_raise_at     DATETIME       NOT NULL COMMENT 'first raise',
    last_raise_at      DATETIME       NOT NULL COMMENT 'last raise',
    ack_by             BIGINT                  COMMENT 'ack user',
    ack_at             DATETIME                COMMENT 'ack time',
    ack_remark         VARCHAR(256)            COMMENT 'ack remark',
    clear_by           BIGINT                  COMMENT 'clear user',
    clear_at           DATETIME                COMMENT 'clear time',
    clear_remark       VARCHAR(256)            COMMENT 'clear remark',
    create_time        DATETIME                COMMENT 'create time',
    update_time        DATETIME                COMMENT 'update time',
    deleted            TINYINT        NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    KEY idx_alarm_dedupe_status (dedupe_key, status),
    KEY idx_alarm_status_level_time (status, level, last_raise_at),
    KEY idx_alarm_entity (entity_type, entity_id, last_raise_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='alarm instance';

CREATE TABLE IF NOT EXISTS mes_carrier (
    id               BIGINT         NOT NULL COMMENT 'PK',
    carrier_code     VARCHAR(64)    NOT NULL COMMENT 'carrier code',
    carrier_type     VARCHAR(32)    NOT NULL DEFAULT 'FOUP' COMMENT 'type',
    capacity         INT            NOT NULL DEFAULT 25 COMMENT 'slot capacity',
    status           VARCHAR(32)    NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/IN_USE/QUARANTINE/SCRAPPED',
    clean_status     VARCHAR(32)             DEFAULT 'UNKNOWN' COMMENT 'CLEAN/DIRTY/UNKNOWN',
    location_type    VARCHAR(32)             DEFAULT 'NONE' COMMENT 'NONE/STOCKER/PORT/OHB/MANUAL',
    location_ref     VARCHAR(128)            COMMENT 'location ref',
    remark           VARCHAR(512)            COMMENT 'remark',
    version          INT            NOT NULL DEFAULT 0 COMMENT 'optimistic lock',
    create_by        BIGINT                  COMMENT 'create by',
    create_time      DATETIME                COMMENT 'create time',
    update_by        BIGINT                  COMMENT 'update by',
    update_time      DATETIME                COMMENT 'update time',
    deleted          TINYINT        NOT NULL DEFAULT 0 COMMENT 'soft delete',
    PRIMARY KEY (id),
    UNIQUE KEY uk_carrier_code (carrier_code),
    KEY idx_carrier_status (status),
    KEY idx_carrier_location (location_type, location_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='carrier master';

CREATE TABLE IF NOT EXISTS mes_carrier_binding (
    id               BIGINT         NOT NULL COMMENT 'PK',
    carrier_id       BIGINT         NOT NULL COMMENT 'carrier id',
    lot_id           BIGINT         NOT NULL COMMENT 'lot id',
    bind_time        DATETIME       NOT NULL COMMENT 'bind time',
    bind_by          BIGINT                  COMMENT 'bind by',
    create_time      DATETIME                COMMENT 'create time',
    update_time      DATETIME                COMMENT 'update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_binding_lot (lot_id),
    UNIQUE KEY uk_binding_carrier (carrier_id),
    KEY idx_binding_bind_time (bind_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='carrier current binding';

CREATE TABLE IF NOT EXISTS mes_complaint_package (
    id               BIGINT         NOT NULL COMMENT 'PK=packageId',
    package_no       VARCHAR(64)    NOT NULL COMMENT 'CP-yyyyMMdd-seq',
    anchor_lot_id    BIGINT         NOT NULL COMMENT 'anchor lot',
    anchor_lot_no    VARCHAR(64)    NOT NULL COMMENT 'anchor lot no snapshot',
    direction        VARCHAR(16)    NOT NULL COMMENT 'up/down/both',
    depth            INT            NOT NULL COMMENT 'expand depth',
    member_count     INT            NOT NULL DEFAULT 0 COMMENT 'member count',
    truncated        TINYINT        NOT NULL DEFAULT 0 COMMENT 'truncated 0/1',
    reason_code      VARCHAR(64)             COMMENT 'reason code',
    remark           VARCHAR(512)            COMMENT 'remark',
    status           VARCHAR(32)    NOT NULL DEFAULT 'READY' COMMENT 'READY/CONTAINING/CONTAINED/VOID',
    create_by        BIGINT                  COMMENT 'create by',
    create_time      DATETIME                COMMENT 'create time',
    contain_by       BIGINT                  COMMENT 'first contain by',
    contain_time     DATETIME                COMMENT 'first contain time',
    contain_token    VARCHAR(36)             COMMENT 'contain owner token',
    update_time      DATETIME                COMMENT 'update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_complaint_package_no (package_no),
    KEY idx_complaint_pkg_anchor (anchor_lot_id),
    KEY idx_complaint_pkg_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='complaint trace package header';

CREATE TABLE IF NOT EXISTS mes_complaint_package_member (
    id                 BIGINT         NOT NULL COMMENT 'PK',
    package_id         BIGINT         NOT NULL COMMENT 'package id',
    lot_id             BIGINT         NOT NULL COMMENT 'member lot',
    lot_no             VARCHAR(64)    NOT NULL COMMENT 'member lot no',
    relation           VARCHAR(32)    NOT NULL COMMENT 'ANCHOR/ANCESTOR/DESCENDANT',
    depth_from_anchor  INT            NOT NULL DEFAULT 0 COMMENT 'depth from anchor',
    qty_snapshot       INT                     COMMENT 'qty at build',
    status_snapshot    VARCHAR(32)             COMMENT 'status at build',
    create_time        DATETIME                COMMENT 'create time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_complaint_pkg_lot (package_id, lot_id),
    KEY idx_complaint_member_lot (lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='complaint package members';
