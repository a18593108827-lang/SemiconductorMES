-- Lot Bonus：权限 track:bonus
-- 对应：docs/模块/Lot（批次）模块/MES-LotBonus接口设计.md

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(301, 290, 3, 'track:bonus', '数量调整', NULL, NULL, 11, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1136, 1, 301, NOW()),
(1330, 3, 301, NOW()),
(1419, 4, 301, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
