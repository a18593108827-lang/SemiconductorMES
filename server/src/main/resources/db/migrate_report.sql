-- Report：复盘目录 + 报表菜单（Rep-4）；API 权限码仍为 report:view

-- 复盘目录（生产执行与系统管理之间）
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(310, 0, 1, NULL, '复盘', NULL, 'bar-chart-2', 50, 1, NOW(), NOW(), 0)
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

-- 报表菜单（由 Rep-1 的按钮权限升级为侧栏菜单）
INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(311, 310, 2, 'report:view', '报表', '/app/report', 'bar-chart-2', 10, 1, NOW(), NOW(), 0)
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
(1147, 1, 311, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- process_eng
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1342, 3, 311, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- supervisor
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1427, 4, 311, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
