-- UrbanSafe Priority Flyway migration V24
-- 修复早期本地数据库中 bootstrap 管理员真实姓名的 UTF-8 误解码值。

UPDATE core.user_account
SET real_name = '开发管理员',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND deleted_at IS NULL
  AND real_name = convert_from(convert_to('开发管理员', 'UTF8'), 'LATIN1');
