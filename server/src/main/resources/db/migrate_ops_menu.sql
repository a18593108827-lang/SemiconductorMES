-- 生产执行菜单种子 + 系统管理 path 对齐前端
-- 可重复执行（ON DUPLICATE KEY UPDATE）

-- 系统管理菜单 path 对齐
UPDATE sys_permission SET path = '/app/auth/users', update_time = NOW() WHERE id = 110;
UPDATE sys_permission SET path = '/app/auth/roles', update_time = NOW() WHERE id = 120;
UPDATE sys_permission SET path = '/app/auth/perms', update_time = NOW() WHERE id = 130;
UPDATE sys_permission SET path = '/app/auth/requests', update_time = NOW() WHERE id = 140;
UPDATE sys_permission SET path = '/app/auth/approvals', update_time = NOW() WHERE id = 150;

-- 生产执行目录与菜单
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(200, 0,   1, NULL,              '生产执行', NULL,              'factory',          10, 1, NOW(), NOW(), 0),
(210, 200, 2, 'dashboard:view',  '看板',     '/app/dashboard',  'layout-dashboard', 11, 1, NOW(), NOW(), 0),
(220, 200, 2, 'lot:list',        '批次',     '/app/lots',       'package',          12, 1, NOW(), NOW(), 0),
(230, 200, 2, 'wip:list',        '在制',     '/app/wip',        'boxes',            13, 1, NOW(), NOW(), 0),
(240, 200, 2, 'eqp:list',        '设备',     '/app/equipment',  'factory',          14, 1, NOW(), NOW(), 0),
(250, 200, 2, 'route:list',      '路线',     '/app/route',      'map',              15, 1, NOW(), NOW(), 0),
(260, 200, 2, 'hold:list',       '锁批',     '/app/hold',       'pause-circle',     16, 1, NOW(), NOW(), 0),
(261, 260, 3, 'hold:create',     '发起锁批', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(262, 260, 3, 'hold:release',    '解锁',     NULL,              NULL,               2,  1, NOW(), NOW(), 0),
(270, 200, 2, 'alarm:list',      '报警',     '/app/alarm',      'bell',             17, 1, NOW(), NOW(), 0),
(280, 200, 2, 'history:list',    '追溯',     '/app/history',    'history',          18, 1, NOW(), NOW(), 0),
(290, 200, 2, 'track:view',      '现场台',   '/track',          'lock',             19, 1, NOW(), NOW(), 0),
(291, 290, 3, 'track:track-in',  'Track In', NULL,              NULL,               1,  1, NOW(), NOW(), 0),
(292, 290, 3, 'track:track-out', 'Track Out',NULL,              NULL,               2,  1, NOW(), NOW(), 0)
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

-- admin 绑定生产执行
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1100, 1, 200, NOW()),
(1101, 1, 210, NOW()),
(1102, 1, 220, NOW()),
(1103, 1, 230, NOW()),
(1104, 1, 240, NOW()),
(1105, 1, 250, NOW()),
(1106, 1, 260, NOW()),
(1107, 1, 261, NOW()),
(1108, 1, 262, NOW()),
(1109, 1, 270, NOW()),
(1110, 1, 280, NOW()),
(1111, 1, 290, NOW()),
(1112, 1, 291, NOW()),
(1113, 1, 292, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- operator：看板/在制/现场台
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1200, 2, 200, NOW()),
(1201, 2, 210, NOW()),
(1202, 2, 230, NOW()),
(1203, 2, 290, NOW()),
(1204, 2, 291, NOW()),
(1205, 2, 292, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng：工艺相关
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1300, 3, 200, NOW()),
(1301, 3, 210, NOW()),
(1302, 3, 220, NOW()),
(1303, 3, 230, NOW()),
(1304, 3, 240, NOW()),
(1305, 3, 250, NOW()),
(1306, 3, 260, NOW()),
(1307, 3, 270, NOW()),
(1308, 3, 280, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor：看板/在制/锁批/报警 + 系统审批菜单
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1400, 4, 200, NOW()),
(1401, 4, 210, NOW()),
(1402, 4, 230, NOW()),
(1403, 4, 260, NOW()),
(1404, 4, 261, NOW()),
(1405, 4, 262, NOW()),
(1406, 4, 270, NOW()),
(1407, 4, 100, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
