-- Track Abort：权限 track:abort
-- 对应：docs/模块/Track（执行引擎）模块/MES-TrackAbort接口设计.md

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(302, 290, 3, 'track:abort', '加工中止', NULL, NULL, 12, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1137, 1, 302, NOW()),
(1208, 2, 302, NOW()),
(1331, 3, 302, NOW()),
(1420, 4, 302, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
