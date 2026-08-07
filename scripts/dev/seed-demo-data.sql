-- UrbanSafe Priority 本地开发测试数据。
-- 仅通过 scripts/dev/seed-demo-data.sh 手工执行，不属于 Flyway 正式迁移。
-- 所有测试账号共享密码：UrbanSafe@123
-- BCrypt 哈希由开发工具生成，脚本重复执行时会重置 demo_* 账号密码。

BEGIN;

DO $$
BEGIN
    IF to_regclass('core.user_account') IS NULL
       OR to_regclass('core.role') IS NULL
       OR to_regclass('core.community') IS NULL
       OR to_regclass('core.building') IS NULL
       OR to_regclass('core.inspection_task') IS NULL THEN
        RAISE EXCEPTION '数据库基础表尚未完成迁移，请先启动后端并让 Flyway 执行完毕';
    END IF;
END $$;

-- 1. 保证六类开发角色存在。
INSERT INTO core.role (role_code, role_name, description, permissions)
VALUES
    ('ADMIN', '系统管理员', '管理系统配置、用户、角色和全部业务数据', '["*"]'::jsonb),
    ('GOVERNMENT_MANAGER', '住建部门管理人员', '查看区域风险、报告和更新优先级', '["community:read","building:read","risk:read","report:read"]'::jsonb),
    ('COMMUNITY_MANAGER', '街道社区管理人员', '维护辖区档案并组织巡检', '["community:manage","building:manage","inspection:manage"]'::jsonb),
    ('PROPERTY_INSPECTOR', '物业巡检人员', '执行巡检并上传现场资料', '["inspection:execute","asset:upload"]'::jsonb),
    ('EXPERT', '专业复核人员', '复核模型结果和风险证据', '["inference:review","risk:review"]'::jsonb),
    ('DISPOSAL_OPERATOR', '问题处置人员', '接收问题或整改任务并提交处理证据', '["issue:read_assigned","issue:handle","rectification:submit","asset:upload"]'::jsonb)
ON CONFLICT (role_code) WHERE deleted_at IS NULL DO UPDATE SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    permissions = EXCLUDED.permissions,
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08';

-- 2. 每个角色一个测试账号。密码统一为 UrbanSafe@123。
INSERT INTO core.user_account (
    username, password_hash, real_name, phone, email, organization_name,
    status, profile, remark
)
VALUES
    ('demo_admin', '$2y$10$7Cr8p7w1XQ0RZ.uIW1o1Y.SSucFOQTDYZG6Y/8ahQgoLCpdxELvNm', '演示系统管理员', '13800001001', 'demo_admin@urban-safe.local', '城安智序项目组', 'ACTIVE', '{"demo":true,"preferredClient":"CONSOLE"}'::jsonb, 'DEMO_DATA：系统管理员测试账号'),
    ('demo_government', '$2y$10$7Cr8p7w1XQ0RZ.uIW1o1Y.SSucFOQTDYZG6Y/8ahQgoLCpdxELvNm', '演示住建管理员', '13800001002', 'demo_government@urban-safe.local', '示范区住房和城乡建设局', 'ACTIVE', '{"demo":true,"preferredClient":"CONSOLE"}'::jsonb, 'DEMO_DATA：住建部门测试账号'),
    ('demo_community', '$2y$10$7Cr8p7w1XQ0RZ.uIW1o1Y.SSucFOQTDYZG6Y/8ahQgoLCpdxELvNm', '演示社区管理员', '13800001003', 'demo_community@urban-safe.local', '湘江示范街道', 'ACTIVE', '{"demo":true,"preferredClient":"CONSOLE"}'::jsonb, 'DEMO_DATA：社区管理测试账号'),
    ('demo_inspector', '$2y$10$7Cr8p7w1XQ0RZ.uIW1o1Y.SSucFOQTDYZG6Y/8ahQgoLCpdxELvNm', '演示巡检人员', '13800001004', 'demo_inspector@urban-safe.local', '安居物业服务中心', 'ACTIVE', '{"demo":true,"preferredClient":"MOBILE"}'::jsonb, 'DEMO_DATA：现场巡检测试账号'),
    ('demo_expert', '$2y$10$7Cr8p7w1XQ0RZ.uIW1o1Y.SSucFOQTDYZG6Y/8ahQgoLCpdxELvNm', '演示专业复核员', '13800001005', 'demo_expert@urban-safe.local', '城市房屋安全技术中心', 'ACTIVE', '{"demo":true,"preferredClient":"CONSOLE"}'::jsonb, 'DEMO_DATA：专业复核测试账号'),
    ('demo_disposer', '$2y$10$7Cr8p7w1XQ0RZ.uIW1o1Y.SSucFOQTDYZG6Y/8ahQgoLCpdxELvNm', '演示问题处置员', '13800001006', 'demo_disposer@urban-safe.local', '安居物业维修班组', 'ACTIVE', '{"demo":true,"preferredClient":"MOBILE"}'::jsonb, 'DEMO_DATA：问题处置测试账号')
ON CONFLICT (username) WHERE deleted_at IS NULL DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    real_name = EXCLUDED.real_name,
    phone = EXCLUDED.phone,
    email = EXCLUDED.email,
    organization_name = EXCLUDED.organization_name,
    status = 'ACTIVE',
    profile = EXCLUDED.profile,
    remark = EXCLUDED.remark,
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08';

WITH assignments(username, role_code) AS (
    VALUES
        ('demo_admin', 'ADMIN'),
        ('demo_government', 'GOVERNMENT_MANAGER'),
        ('demo_community', 'COMMUNITY_MANAGER'),
        ('demo_inspector', 'PROPERTY_INSPECTOR'),
        ('demo_expert', 'EXPERT'),
        ('demo_disposer', 'DISPOSAL_OPERATOR')
)
INSERT INTO core.user_role (user_id, role_id)
SELECT users.id, roles.id
FROM assignments
JOIN core.user_account users
  ON users.username = assignments.username AND users.deleted_at IS NULL
JOIN core.role roles
  ON roles.role_code = assignments.role_code AND roles.deleted_at IS NULL
ON CONFLICT (user_id, role_id) WHERE deleted_at IS NULL DO UPDATE SET
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08';

-- 3. 两个小区及五栋楼。
INSERT INTO core.community (
    community_code, community_name, administrative_region, address,
    construction_period, building_count, household_count, resident_count,
    archive_completeness_score, status, extra_attributes, remark
)
VALUES
    ('DEMO-COMMUNITY-001', '湘江示范社区', '湖南省株洲市天元区', '湘江大道示范段 88 号', '1990—2005', 3, 216, 528, 82.50, 'ACTIVE', '{"demo":true,"managementMode":"PROPERTY"}'::jsonb, 'DEMO_DATA：用于巡检和人工智能链路演示'),
    ('DEMO-COMMUNITY-002', '枫溪更新社区', '湖南省株洲市芦淞区', '枫溪街道更新路 16 号', '1975—1995', 2, 144, 361, 64.00, 'ACTIVE', '{"demo":true,"renewalCandidate":true}'::jsonb, 'DEMO_DATA：用于风险和更新优先级演示')
ON CONFLICT (community_code) WHERE deleted_at IS NULL DO UPDATE SET
    community_name = EXCLUDED.community_name,
    administrative_region = EXCLUDED.administrative_region,
    address = EXCLUDED.address,
    construction_period = EXCLUDED.construction_period,
    building_count = EXCLUDED.building_count,
    household_count = EXCLUDED.household_count,
    resident_count = EXCLUDED.resident_count,
    archive_completeness_score = EXCLUDED.archive_completeness_score,
    status = EXCLUDED.status,
    extra_attributes = EXCLUDED.extra_attributes,
    remark = EXCLUDED.remark,
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08';

WITH building_data(
    community_code, building_code, building_name, address, construction_year,
    structure_type, floor_count, building_area, household_count, resident_count,
    elderly_count, child_count, has_elevator, has_illegal_modification,
    has_ground_floor_business, archive_score, extra_attributes
) AS (
    VALUES
        ('DEMO-COMMUNITY-001', 'A-01', '湘江示范社区 1 栋', '湘江大道示范段 88 号 1 栋', 1998, 'BRICK_CONCRETE', 7, 6850.00, 56, 138, 22, 18, FALSE, FALSE, TRUE, 88.00, '{"demo":true,"riskHint":"MEDIUM"}'::jsonb),
        ('DEMO-COMMUNITY-001', 'A-02', '湘江示范社区 2 栋', '湘江大道示范段 88 号 2 栋', 2003, 'FRAME', 11, 11200.00, 88, 206, 31, 27, TRUE, FALSE, FALSE, 91.50, '{"demo":true,"riskHint":"LOW"}'::jsonb),
        ('DEMO-COMMUNITY-001', 'A-03', '湘江示范社区 3 栋', '湘江大道示范段 88 号 3 栋', 1994, 'BRICK_CONCRETE', 6, 6040.00, 72, 184, 29, 21, FALSE, TRUE, TRUE, 73.00, '{"demo":true,"riskHint":"HIGH"}'::jsonb),
        ('DEMO-COMMUNITY-002', 'B-01', '枫溪更新社区 1 栋', '枫溪街道更新路 16 号 1 栋', 1982, 'BRICK_CONCRETE', 6, 5320.00, 64, 162, 34, 16, FALSE, TRUE, TRUE, 58.00, '{"demo":true,"riskHint":"HIGH","renewalCandidate":true}'::jsonb),
        ('DEMO-COMMUNITY-002', 'B-02', '枫溪更新社区 2 栋', '枫溪街道更新路 16 号 2 栋', 1991, 'MASONRY', 7, 6410.00, 80, 199, 37, 25, FALSE, FALSE, FALSE, 68.00, '{"demo":true,"riskHint":"MEDIUM","renewalCandidate":true}'::jsonb)
)
INSERT INTO core.building (
    community_id, building_code, building_name, address, construction_year,
    structure_type, floor_count, building_area, household_count, resident_count,
    elderly_count, child_count, has_elevator, has_illegal_modification,
    has_ground_floor_business, archive_completeness_score, status,
    extra_attributes, remark
)
SELECT
    communities.id, data.building_code, data.building_name, data.address,
    data.construction_year, data.structure_type, data.floor_count,
    data.building_area, data.household_count, data.resident_count,
    data.elderly_count, data.child_count, data.has_elevator,
    data.has_illegal_modification, data.has_ground_floor_business,
    data.archive_score, 'ACTIVE', data.extra_attributes,
    'DEMO_DATA：本地开发测试楼栋'
FROM building_data data
JOIN core.community communities
  ON communities.community_code = data.community_code AND communities.deleted_at IS NULL
ON CONFLICT (community_id, building_code) WHERE deleted_at IS NULL DO UPDATE SET
    building_name = EXCLUDED.building_name,
    address = EXCLUDED.address,
    construction_year = EXCLUDED.construction_year,
    structure_type = EXCLUDED.structure_type,
    floor_count = EXCLUDED.floor_count,
    building_area = EXCLUDED.building_area,
    household_count = EXCLUDED.household_count,
    resident_count = EXCLUDED.resident_count,
    elderly_count = EXCLUDED.elderly_count,
    child_count = EXCLUDED.child_count,
    has_elevator = EXCLUDED.has_elevator,
    has_illegal_modification = EXCLUDED.has_illegal_modification,
    has_ground_floor_business = EXCLUDED.has_ground_floor_business,
    archive_completeness_score = EXCLUDED.archive_completeness_score,
    status = EXCLUDED.status,
    extra_attributes = EXCLUDED.extra_attributes,
    remark = EXCLUDED.remark,
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08';

-- 4. 小区地图位置，使用人工确认的 WGS84 测试点。
WITH location_data(community_code, longitude, latitude, formatted_address, quality_score) AS (
    VALUES
        ('DEMO-COMMUNITY-001', 113.133960, 27.827670, '湖南省株洲市天元区湘江大道示范段 88 号', 95.00),
        ('DEMO-COMMUNITY-002', 113.116500, 27.833000, '湖南省株洲市芦淞区枫溪街道更新路 16 号', 92.00)
)
INSERT INTO geo.community_location (
    community_id, centroid, formatted_address, source_provider,
    source_coordinate_system, match_level, quality_score, metadata
)
SELECT
    communities.id,
    ST_SetSRID(ST_MakePoint(data.longitude, data.latitude), 4326),
    data.formatted_address,
    'MANUAL',
    'WGS84',
    'DEMO_MANUAL_POINT',
    data.quality_score,
    '{"demo":true,"verifiedForDevelopment":true}'::jsonb
FROM location_data data
JOIN core.community communities
  ON communities.community_code = data.community_code AND communities.deleted_at IS NULL
ON CONFLICT (community_id) WHERE deleted_at IS NULL DO UPDATE SET
    centroid = EXCLUDED.centroid,
    formatted_address = EXCLUDED.formatted_address,
    source_provider = EXCLUDED.source_provider,
    source_coordinate_system = EXCLUDED.source_coordinate_system,
    match_level = EXCLUDED.match_level,
    quality_score = EXCLUDED.quality_score,
    metadata = EXCLUDED.metadata,
    collected_at = TIMESTAMPTZ '2026-07-25 12:00:00+08',
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08';

-- 5. 四种状态的巡检任务。
WITH task_data(
    task_code, community_code, building_code, title, description,
    inspection_type, planned_at, status, started_at, completed_at,
    cancelled_at, focus_parts, remark
) AS (
    VALUES
        ('DEMO-TASK-001', 'DEMO-COMMUNITY-001', 'A-01', '1 栋外立面例行巡检', '检查墙面裂缝、脱落和渗水痕迹', 'ROUTINE', TIMESTAMPTZ '2026-07-25 12:00:00+08' + INTERVAL '1 day', 'PENDING', NULL, NULL, NULL, '["EXTERIOR_WALL","BALCONY"]'::jsonb, 'DEMO_DATA：待执行任务'),
        ('DEMO-TASK-002', 'DEMO-COMMUNITY-001', 'A-03', '3 栋重点部位复查', '复查首层商业改造区域和楼梯间裂缝', 'SPECIAL', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '1 day', 'IN_PROGRESS', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '3 hours', NULL, NULL, '["GROUND_FLOOR","STAIRWELL"]'::jsonb, 'DEMO_DATA：进行中任务'),
        ('DEMO-TASK-003', 'DEMO-COMMUNITY-002', 'B-01', '更新候选楼栋完整巡检', '采集结构外观、公共区域和维护记录', 'COMPREHENSIVE', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '8 days', 'COMPLETED', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '8 days', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '7 days', NULL, '["EXTERIOR_WALL","CORRIDOR","ROOF"]'::jsonb, 'DEMO_DATA：已完成任务'),
        ('DEMO-TASK-004', 'DEMO-COMMUNITY-002', 'B-02', '雨后渗漏专项巡检', '原计划雨后检查，因天气原因取消', 'SPECIAL', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '2 days', 'CANCELLED', NULL, NULL, TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '2 days', '["ROOF","EXTERIOR_WALL"]'::jsonb, 'DEMO_DATA：已取消任务')
)
INSERT INTO core.inspection_task (
    task_code, building_id, inspection_type, planned_at, assigned_to,
    focus_parts, status, created_by, title, description,
    started_at, completed_at, cancelled_at, remark
)
SELECT
    data.task_code,
    buildings.id,
    data.inspection_type,
    data.planned_at,
    inspector.id,
    data.focus_parts,
    data.status,
    manager.id,
    data.title,
    data.description,
    data.started_at,
    data.completed_at,
    data.cancelled_at,
    data.remark
FROM task_data data
JOIN core.community communities
  ON communities.community_code = data.community_code AND communities.deleted_at IS NULL
JOIN core.building buildings
  ON buildings.community_id = communities.id
 AND buildings.building_code = data.building_code
 AND buildings.deleted_at IS NULL
JOIN core.user_account inspector
  ON inspector.username = 'demo_inspector' AND inspector.deleted_at IS NULL
JOIN core.user_account manager
  ON manager.username = 'demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (task_code) WHERE deleted_at IS NULL DO UPDATE SET
    building_id = EXCLUDED.building_id,
    inspection_type = EXCLUDED.inspection_type,
    planned_at = EXCLUDED.planned_at,
    assigned_to = EXCLUDED.assigned_to,
    focus_parts = EXCLUDED.focus_parts,
    status = EXCLUDED.status,
    created_by = EXCLUDED.created_by,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    started_at = EXCLUDED.started_at,
    completed_at = EXCLUDED.completed_at,
    cancelled_at = EXCLUDED.cancelled_at,
    remark = EXCLUDED.remark,
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08';

-- 6. 巡检记录。
WITH record_data(
    id, task_code, inspection_part, inspected_at, submitted_at, status,
    summary, form_data, issue_type, severity, rectification_suggestion,
    extra_data, remark
) AS (
    VALUES
        ('30000000-0000-4000-8000-000000000001'::uuid, 'DEMO-TASK-002', '首层外墙', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '2 hours', NULL, 'DRAFT', '发现窗角附近疑似斜向裂缝，等待补充近景图片。', '{"crackLengthCm":42,"surfaceCondition":"DAMP"}'::jsonb, 'CRACK', 'HIGH', '设置临时警示并安排专业人员复核。', '{"demo":true,"weather":"CLOUDY"}'::jsonb, 'DEMO_DATA：进行中记录'),
        ('30000000-0000-4000-8000-000000000002'::uuid, 'DEMO-TASK-003', '西侧外墙', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '8 days', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '8 days' + INTERVAL '2 hours', 'COMPLETED', '局部抹灰层空鼓和轻微脱落，无明显主体结构变形。', '{"hollowAreaSquareMeter":1.8,"fallingRisk":true}'::jsonb, 'SURFACE_FALLING', 'MEDIUM', '清除松动饰面并完成修补，设置防坠措施。', '{"demo":true,"reviewRequired":true}'::jsonb, 'DEMO_DATA：已完成记录'),
        ('30000000-0000-4000-8000-000000000003'::uuid, 'DEMO-TASK-003', '屋面及女儿墙', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '8 days' + INTERVAL '3 hours', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '8 days' + INTERVAL '4 hours', 'COMPLETED', '屋面排水口有杂物堆积，女儿墙表面存在轻微开裂。', '{"drainBlocked":true,"parapetCrack":"MINOR"}'::jsonb, 'WATER_LEAKAGE', 'LOW', '清理排水口并对女儿墙裂缝进行封闭处理。', '{"demo":true,"reviewRequired":false}'::jsonb, 'DEMO_DATA：已完成记录')
)
INSERT INTO core.inspection_record (
    id, inspection_task_id, building_id, inspector_id, inspection_part,
    inspected_at, submitted_at, status, summary, form_data, issue_type,
    severity, rectification_suggestion, extra_data, remark
)
SELECT
    data.id,
    tasks.id,
    tasks.building_id,
    inspector.id,
    data.inspection_part,
    data.inspected_at,
    data.submitted_at,
    data.status,
    data.summary,
    data.form_data,
    data.issue_type,
    data.severity,
    data.rectification_suggestion,
    data.extra_data,
    data.remark
FROM record_data data
JOIN core.inspection_task tasks
  ON tasks.task_code = data.task_code AND tasks.deleted_at IS NULL
JOIN core.user_account inspector
  ON inspector.username = 'demo_inspector' AND inspector.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
    inspection_task_id = EXCLUDED.inspection_task_id,
    building_id = EXCLUDED.building_id,
    inspector_id = EXCLUDED.inspector_id,
    inspection_part = EXCLUDED.inspection_part,
    inspected_at = EXCLUDED.inspected_at,
    submitted_at = EXCLUDED.submitted_at,
    status = EXCLUDED.status,
    summary = EXCLUDED.summary,
    form_data = EXCLUDED.form_data,
    issue_type = EXCLUDED.issue_type,
    severity = EXCLUDED.severity,
    rectification_suggestion = EXCLUDED.rectification_suggestion,
    extra_data = EXCLUDED.extra_data,
    remark = EXCLUDED.remark,
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08',
    deleted_at = NULL;

-- 7. 楼栋证据和居民上报。
WITH evidence_data(
    id, community_code, building_code, evidence_type, title, description,
    occurred_at, source, reliability_level, evidence_data
) AS (
    VALUES
        ('40000000-0000-4000-8000-000000000001'::uuid, 'DEMO-COMMUNITY-001', 'A-03', 'MAINTENANCE_RECORD', '2025 年外墙维修记录', '完成局部抹灰修补，但未覆盖首层商业改造区域。', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '300 days', '安居物业维修档案', 'OFFICIAL_RECORD', '{"demo":true,"documentNo":"WX-2025-031"}'::jsonb),
        ('40000000-0000-4000-8000-000000000002'::uuid, 'DEMO-COMMUNITY-002', 'B-01', 'PROFESSIONAL_INSPECTION', '结构安全初步排查记录', '建议对局部墙体开裂和违规改造开展专项检测。', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '60 days', '城市房屋安全技术中心', 'PROFESSIONAL_CONFIRMED', '{"demo":true,"recommendation":"SPECIAL_TEST"}'::jsonb),
        ('40000000-0000-4000-8000-000000000003'::uuid, 'DEMO-COMMUNITY-002', 'B-02', 'HISTORICAL_COMPLAINT', '雨季顶层渗漏投诉汇总', '近两年共收到 4 次顶层渗漏相关投诉。', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '120 days', '社区问题台账', 'OFFICIAL_RECORD', '{"demo":true,"complaintCount":4}'::jsonb)
)
INSERT INTO core.building_evidence (
    id, building_id, evidence_type, title, description, occurred_at,
    source, reliability_level, evidence_data, created_by
)
SELECT
    data.id, buildings.id, data.evidence_type, data.title, data.description,
    data.occurred_at, data.source, data.reliability_level,
    data.evidence_data, manager.id
FROM evidence_data data
JOIN core.community communities
  ON communities.community_code = data.community_code AND communities.deleted_at IS NULL
JOIN core.building buildings
  ON buildings.community_id = communities.id
 AND buildings.building_code = data.building_code
 AND buildings.deleted_at IS NULL
JOIN core.user_account manager
  ON manager.username = 'demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
    building_id = EXCLUDED.building_id,
    evidence_type = EXCLUDED.evidence_type,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    occurred_at = EXCLUDED.occurred_at,
    source = EXCLUDED.source,
    reliability_level = EXCLUDED.reliability_level,
    evidence_data = EXCLUDED.evidence_data,
    created_by = EXCLUDED.created_by,
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08',
    deleted_at = NULL;

WITH report_data(
    report_code, community_code, building_code, report_type, description,
    status, urgency, evidence, submitted_at
) AS (
    VALUES
        ('DEMO-REPORT-001', 'DEMO-COMMUNITY-001', 'A-03', 'WALL_CRACK', '居民反映首层外墙窗角裂缝近期有所延伸。', 'SUBMITTED', 'HIGH', '[{"type":"TEXT","note":"等待现场补充图片"}]'::jsonb, TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '2 days'),
        ('DEMO-REPORT-002', 'DEMO-COMMUNITY-002', 'B-02', 'WATER_LEAKAGE', '顶层住户反映连续降雨后屋面出现渗漏。', 'PROCESSING', 'NORMAL', '[{"type":"TEXT","note":"物业已安排初查"}]'::jsonb, TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '5 days')
)
INSERT INTO core.resident_report (
    report_code, community_id, building_id, reporter_user_id, report_type,
    description, status, urgency, evidence, submitted_at
)
SELECT
    data.report_code,
    communities.id,
    buildings.id,
    inspector.id,
    data.report_type,
    data.description,
    data.status,
    data.urgency,
    data.evidence,
    data.submitted_at
FROM report_data data
JOIN core.community communities
  ON communities.community_code = data.community_code AND communities.deleted_at IS NULL
JOIN core.building buildings
  ON buildings.community_id = communities.id
 AND buildings.building_code = data.building_code
 AND buildings.deleted_at IS NULL
JOIN core.user_account inspector
  ON inspector.username = 'demo_inspector' AND inspector.deleted_at IS NULL
ON CONFLICT (report_code) WHERE deleted_at IS NULL DO UPDATE SET
    community_id = EXCLUDED.community_id,
    building_id = EXCLUDED.building_id,
    reporter_user_id = EXCLUDED.reporter_user_id,
    report_type = EXCLUDED.report_type,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    urgency = EXCLUDED.urgency,
    evidence = EXCLUDED.evidence,
    submitted_at = EXCLUDED.submitted_at,
    updated_at = TIMESTAMPTZ '2026-07-25 12:00:00+08';

COMMIT;
