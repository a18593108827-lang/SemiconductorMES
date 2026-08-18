-- Lot Merge：权限 track:merge
-- 对应：docs/模块/Lot（批次）模块/MES-LotMerge接口设计.md
-- 谱系表 / merged_to_lot_id / status=merged 已随 migrate_lot_split.sql

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(299, 290, 3, 'track:merge', '合批', NULL, NULL, 9, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1134, 1, 299, NOW()),
(1328, 3, 299, NOW()),
(1417, 4, 299, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
