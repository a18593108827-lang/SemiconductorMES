-- Skip 跳站：权限 track:skip
-- 对应：docs/模块/Route（工艺路线）模块/MES-Skip接口设计.md

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(296, 290, 3, 'track:skip', '跳站', NULL, NULL, 6, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1131, 1, 296, NOW()),
(1325, 3, 296, NOW()),
(1414, 4, 296, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
