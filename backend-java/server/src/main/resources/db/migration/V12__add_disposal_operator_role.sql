-- 增加移动端问题处置人员角色。
-- 该迁移只登记角色和权限，不创建演示账号，也不改变现有用户角色绑定。
INSERT INTO core.role (
    role_code,
    role_name,
    description,
    permissions
)
VALUES (
    'DISPOSAL_OPERATOR',
    '问题处置人员',
    '接收分配的问题或整改任务，提交处理过程和整改证据',
    '["issue:read_assigned","issue:handle","rectification:submit","asset:upload"]'::jsonb
)
ON CONFLICT (role_code) WHERE deleted_at IS NULL DO UPDATE SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    permissions = EXCLUDED.permissions,
    updated_at = CURRENT_TIMESTAMP;
