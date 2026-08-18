-- Track Move：权限 track:move（种子 294 已有则只改正文；补齐现场角色）
-- 对应：docs/模块/Track（执行引擎）模块/MES-TrackMove接口设计.md

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(294, 290, 3, 'track:move', '独立移站', NULL, NULL, 4, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1120, 1, 294, NOW()),
(1207, 2, 294, NOW()),
(1332, 3, 294, NOW()),
(1421, 4, 294, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
