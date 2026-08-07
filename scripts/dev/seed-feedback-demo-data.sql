-- 公众反馈闭环演示数据。依赖 seed-demo-data.sql 已创建的演示小区、楼栋和账号。
BEGIN;

WITH feedback_data(
    id, report_code, community_code, building_code, reporter_name,
    contact_phone, feedback_channel, report_type, description, status,
    urgency, location_text, handling_summary, tracking_secret_hash,
    submitted_at, handled_at
) AS (
    VALUES
        (
            '71000000-0000-4000-8000-000000000001'::uuid,
            'DEMO-FEEDBACK-WEB-001', 'DEMO-COMMUNITY-001', 'A-03', '王女士',
            '13800002001', 'WEB', 'WALL_CRACK',
            '首层外墙窗角裂缝近期有延伸迹象，希望安排现场核查。',
            'PROCESSING', 'HIGH', '3 栋首层西侧外墙',
            '社区已受理并转交物业巡检人员现场复查。',
            '9a63d71d89e2fdfc46f69b7402076f854bba0b3cf91134e9da3b470cf8b6488b',
            TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '2 days', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '1 day'
        ),
        (
            '71000000-0000-4000-8000-000000000002'::uuid,
            'DEMO-FEEDBACK-PHONE-001', 'DEMO-COMMUNITY-002', 'B-02', '李先生',
            '13800002002', 'PHONE', 'WATER_LEAKAGE',
            '居民来电反映连续降雨后顶层公共走廊出现渗水。',
            'ACCEPTED', 'NORMAL', '2 栋顶层公共走廊',
            '电话反馈已完成代录，等待物业安排检查。',
            '697873e3721e08b6a593558b7c42c2ea24fe575d102714b2bf5a761063bb1b57',
            TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '1 day', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '20 hours'
        ),
        (
            '71000000-0000-4000-8000-000000000003'::uuid,
            'DEMO-FEEDBACK-SMS-001', 'DEMO-COMMUNITY-001', 'A-01', '匿名反馈人',
            '13800002003', 'SMS', 'FIRE_ACCESS',
            '短信反映消防通道夜间经常被车辆占用。',
            'RESOLVED', 'URGENT', '1 栋北侧消防通道',
            '物业已设置禁停标识并安排夜间巡查。',
            'f38a61b1c994cef55d019def69426c83bf8e68d35e9e7b363e8ecba119482781',
            TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '5 days', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '4 days'
        )
)
INSERT INTO core.resident_report (
    id, report_code, community_id, building_id, reporter_name,
    contact_phone, contact_consent, feedback_channel, recorded_by,
    report_type, description, status, urgency, location_text,
    handling_summary, tracking_secret_hash, submitted_at, handled_at, updated_by
)
SELECT
    data.id, data.report_code, communities.id, buildings.id, data.reporter_name,
    data.contact_phone, TRUE, data.feedback_channel,
    CASE WHEN data.feedback_channel='WEB' THEN NULL ELSE manager.id END,
    data.report_type, data.description, data.status, data.urgency,
    data.location_text, data.handling_summary, data.tracking_secret_hash,
    data.submitted_at, data.handled_at, manager.id
FROM feedback_data data
JOIN core.community communities
  ON communities.community_code=data.community_code AND communities.deleted_at IS NULL
LEFT JOIN core.building buildings
  ON buildings.community_id=communities.id
 AND buildings.building_code=data.building_code
 AND buildings.deleted_at IS NULL
JOIN core.user_account manager
  ON manager.username='demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (report_code) WHERE deleted_at IS NULL DO UPDATE SET
    community_id=EXCLUDED.community_id,
    building_id=EXCLUDED.building_id,
    reporter_name=EXCLUDED.reporter_name,
    contact_phone=EXCLUDED.contact_phone,
    contact_consent=EXCLUDED.contact_consent,
    feedback_channel=EXCLUDED.feedback_channel,
    recorded_by=EXCLUDED.recorded_by,
    report_type=EXCLUDED.report_type,
    description=EXCLUDED.description,
    status=EXCLUDED.status,
    urgency=EXCLUDED.urgency,
    location_text=EXCLUDED.location_text,
    handling_summary=EXCLUDED.handling_summary,
    tracking_secret_hash=EXCLUDED.tracking_secret_hash,
    submitted_at=EXCLUDED.submitted_at,
    handled_at=EXCLUDED.handled_at,
    updated_by=EXCLUDED.updated_by,
    updated_at=TIMESTAMPTZ '2026-07-25 12:00:00+08';

WITH event_data(
    id, report_code, event_type, from_status, to_status,
    message, visibility, actor_type, actor_username, created_at
) AS (
    VALUES
        ('72000000-0000-4000-8000-000000000001'::uuid, 'DEMO-FEEDBACK-WEB-001', 'CREATED', NULL, 'SUBMITTED', '网页反馈已提交。', 'PUBLIC', 'CITIZEN', NULL, TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '2 days'),
        ('72000000-0000-4000-8000-000000000002'::uuid, 'DEMO-FEEDBACK-WEB-001', 'STATUS_CHANGED', 'SUBMITTED', 'ACCEPTED', '社区已受理反馈。', 'PUBLIC', 'STAFF', 'demo_community', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '30 hours'),
        ('72000000-0000-4000-8000-000000000003'::uuid, 'DEMO-FEEDBACK-WEB-001', 'STATUS_CHANGED', 'ACCEPTED', 'PROCESSING', '已安排现场复查。', 'PUBLIC', 'STAFF', 'demo_community', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '1 day'),
        ('72000000-0000-4000-8000-000000000004'::uuid, 'DEMO-FEEDBACK-PHONE-001', 'CREATED', NULL, 'SUBMITTED', '工作人员已根据来电完成代录。', 'PUBLIC', 'STAFF', 'demo_community', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '1 day'),
        ('72000000-0000-4000-8000-000000000005'::uuid, 'DEMO-FEEDBACK-PHONE-001', 'STATUS_CHANGED', 'SUBMITTED', 'ACCEPTED', '反馈已受理，等待检查。', 'PUBLIC', 'STAFF', 'demo_community', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '20 hours'),
        ('72000000-0000-4000-8000-000000000006'::uuid, 'DEMO-FEEDBACK-SMS-001', 'CREATED', NULL, 'SUBMITTED', '工作人员已根据短信完成代录。', 'PUBLIC', 'STAFF', 'demo_community', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '5 days'),
        ('72000000-0000-4000-8000-000000000007'::uuid, 'DEMO-FEEDBACK-SMS-001', 'STATUS_CHANGED', 'PROCESSING', 'RESOLVED', '已设置禁停标识并安排巡查。', 'PUBLIC', 'STAFF', 'demo_community', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '4 days')
)
INSERT INTO core.resident_report_event (
    id, resident_report_id, event_type, from_status, to_status,
    message, visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT
    data.id, reports.id, data.event_type, data.from_status, data.to_status,
    data.message, data.visibility, data.actor_type, users.id,
    '{"demo":true}'::jsonb, data.created_at
FROM event_data data
JOIN core.resident_report reports
  ON reports.report_code=data.report_code AND reports.deleted_at IS NULL
LEFT JOIN core.user_account users
  ON users.username=data.actor_username AND users.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
    resident_report_id=EXCLUDED.resident_report_id,
    event_type=EXCLUDED.event_type,
    from_status=EXCLUDED.from_status,
    to_status=EXCLUDED.to_status,
    message=EXCLUDED.message,
    visibility=EXCLUDED.visibility,
    actor_type=EXCLUDED.actor_type,
    actor_user_id=EXCLUDED.actor_user_id,
    event_data=EXCLUDED.event_data,
    created_at=EXCLUDED.created_at;

COMMIT;

-- 公开查询演示凭证：
-- DEMO-FEEDBACK-WEB-001   / demo-track-001
-- DEMO-FEEDBACK-PHONE-001 / demo-track-002
-- DEMO-FEEDBACK-SMS-001   / demo-track-003
