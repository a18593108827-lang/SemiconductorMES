-- Temporary Off-Flow：Lot 锚点字段 + 权限 track:off-flow
-- 对应：docs/模块/Route（工艺路线）模块/MES-OffFlow接口设计.md

ALTER TABLE mes_lot
    ADD COLUMN off_flow TINYINT NOT NULL DEFAULT 0 COMMENT '是否在Off-Flow中 0/1' AFTER rework_counts,
    ADD COLUMN off_flow_anchor_sort INT NULL COMMENT 'Off-Flow锚点站序' AFTER off_flow,
    ADD COLUMN off_flow_anchor_step_id BIGINT NULL COMMENT 'Off-Flow锚点工序' AFTER off_flow_anchor_sort,
    ADD COLUMN off_flow_anchor_eqp_id BIGINT NULL COMMENT 'Off-Flow锚点机台' AFTER off_flow_anchor_step_id,
    ADD COLUMN off_flow_anchor_status VARCHAR(32) NULL COMMENT 'Off-Flow锚点状态 wait/processing' AFTER off_flow_anchor_eqp_id;

INSERT INTO sys_permission (id, parent_id, perm_type, perm_code, perm_name, path, icon, sort_no, status, create_time, update_time, deleted) VALUES
(297, 290, 3, 'track:off-flow', '临时离线', NULL, NULL, 7, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  perm_code = VALUES(perm_code),
  perm_name = VALUES(perm_name),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id, create_time) VALUES
(1132, 1, 297, NOW()),
(1326, 3, 297, NOW()),
(1415, 4, 297, NOW())
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
