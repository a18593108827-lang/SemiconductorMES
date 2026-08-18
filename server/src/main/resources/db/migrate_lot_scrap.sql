-- Lot Scrap：权限 track:scrap
-- 对应：docs/模块/Lot（批次）模块/MES-LotScrap接口设计.md
-- scrap_qty 字段已随 migrate_lot_split.sql

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(300, 290, 3, 'track:scrap', '报废', NULL, NULL, 10, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1135, 1, 300, NOW()),
(1329, 3, 300, NOW()),
(1418, 4, 300, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
