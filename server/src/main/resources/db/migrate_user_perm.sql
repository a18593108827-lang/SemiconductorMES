-- 已有库升级（新库直接跑 schema.sql）
-- 按实际库状态注释已执行过的语句

ALTER TABLE sys_user
    ADD COLUMN must_change_pwd TINYINT NOT NULL DEFAULT 0 COMMENT '1强制下次改密' AFTER status,
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'local' COMMENT '账号来源 local/sso' AFTER must_change_pwd,
    ADD COLUMN external_id VARCHAR(128) NULL COMMENT '外部身份ID' AFTER source,
    ADD KEY idx_external (source, external_id);

-- 增加 user_code
ALTER TABLE sys_user
    ADD COLUMN user_code VARCHAR(64) NULL COMMENT '用户编码（登录）' AFTER id;

UPDATE sys_user
SET user_code = COALESCE(user_code, username)
WHERE user_code IS NULL OR user_code = '';

ALTER TABLE sys_user
    MODIFY COLUMN user_code VARCHAR(64) NOT NULL COMMENT '用户编码（登录）';

-- username -> user_name（姓名，可重复）
ALTER TABLE sys_user DROP INDEX uk_username;
ALTER TABLE sys_user CHANGE COLUMN username user_name VARCHAR(64) NOT NULL COMMENT '姓名';
ALTER TABLE sys_user ADD KEY idx_user_name (user_name);

ALTER TABLE sys_user ADD UNIQUE KEY uk_user_code (user_code);

-- 去掉无用昵称字段
ALTER TABLE sys_user DROP COLUMN nickname;
