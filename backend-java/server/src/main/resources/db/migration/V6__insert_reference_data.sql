-- UrbanSafe Priority Flyway migration V6
-- 仅写入系统运行所需的字典和版本数据，不写入演示小区或楼栋。

INSERT INTO core.role (
    role_code,
    role_name,
    description,
    permissions
)
VALUES
    ('ADMIN', '系统管理员', '管理系统配置、用户、角色和全部业务数据', '["*"]'::jsonb),
    ('GOVERNMENT_MANAGER', '住建部门管理人员', '查看区域风险、报告和更新优先级', '["community:read", "building:read", "risk:read", "report:read"]'::jsonb),
    ('COMMUNITY_MANAGER', '街道社区管理人员', '维护辖区档案并组织巡检', '["community:manage", "building:manage", "inspection:manage"]'::jsonb),
    ('PROPERTY_INSPECTOR', '物业巡检人员', '执行巡检并上传现场资料', '["inspection:execute", "asset:upload"]'::jsonb),
    ('EXPERT', '专业复核人员', '复核模型结果和风险证据', '["inference:review", "risk:review"]'::jsonb)
ON CONFLICT DO NOTHING;

INSERT INTO ai.model_registry (
    model_code,
    model_name,
    model_version,
    model_type,
    framework,
    source_type,
    license_name,
    input_spec,
    output_spec,
    model_config,
    status
)
VALUES (
    'building-defect-placeholder',
    '建筑表观病害识别占位模型',
    '0.1.0-placeholder',
    'OBJECT_DETECTION',
    'FastAPI Placeholder',
    'PLACEHOLDER',
    'PROJECT-INTERNAL',
    '{"contentTypes":["image/jpeg","image/png"],"maxFileSizeMb":20}'::jsonb,
    '{"fields":["defectType","confidence","bbox","severity","description"]}'::jsonb,
    '{"note":"仅用于业务链路联调，不代表真实模型能力","offline":true}'::jsonb,
    'REGISTERED'
)
ON CONFLICT DO NOTHING;

INSERT INTO core.rule_version (
    rule_type,
    version_code,
    rule_name,
    rule_content,
    checksum,
    status,
    activated_at
)
VALUES
    (
        'COMPLETENESS',
        'CMP-1.0.0',
        '第一版数据完整度规则',
        '{"archive":30,"recentInspection":25,"imageCoverage":20,"maintenance":10,"professionalInspection":15}'::jsonb,
        repeat('0', 64),
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        'RISK',
        'RISK-1.0.0',
        '第一版房屋安全风险筛查规则',
        '{"buildingAge":20,"structureType":15,"inspectionDefects":35,"history":10,"environment":10,"professionalEvidence":10}'::jsonb,
        repeat('1', 64),
        'ACTIVE',
        CURRENT_TIMESTAMP
    ),
    (
        'RENEWAL',
        'RNW-1.0.0',
        '第一版城市更新优先级规则',
        '{"safetyRisk":35,"populationImpact":20,"buildingAge":10,"publicValue":10,"complaints":10,"dataReliability":5,"urgency":10}'::jsonb,
        repeat('2', 64),
        'ACTIVE',
        CURRENT_TIMESTAMP
    )
ON CONFLICT DO NOTHING;
