#!/usr/bin/env python3
from __future__ import annotations

import os
from pathlib import Path

SEED = int(os.environ.get("SHOWCASE_SEED", "20260810"))
HISTORY_MONTHS = max(3, min(60, int(os.environ.get("SHOWCASE_HISTORY_MONTHS", "24"))))
DENSITY = os.environ.get("SHOWCASE_DATA_DENSITY", "rich").strip().lower()
PROFILE_MODE = os.environ.get("SHOWCASE_DIVERSITY_PROFILE", "balanced").strip().lower()
SQL_FILE = Path(os.environ["SHOWCASE_DIVERSITY_SQL_FILE"])

DENSITY_CONFIG = {
    "light": {"task_max": 1, "record_max": 1, "report_mod": 7, "evidence_mod": 5},
    "balanced": {"task_max": 3, "record_max": 2, "report_mod": 5, "evidence_mod": 3},
    "rich": {"task_max": 5, "record_max": 4, "report_mod": 3, "evidence_mod": 2},
}
if DENSITY not in DENSITY_CONFIG:
    raise SystemExit("SHOWCASE_DATA_DENSITY 仅支持 light / balanced / rich")

cfg = DENSITY_CONFIG[DENSITY]
seed_shift = SEED % 6
history_days = HISTORY_MONTHS * 30

sql = f"""
BEGIN;

-- 场景增强层只修改/生成业务数据，不修改高德真实空间点位和已确认边界。
-- 六类画像通过真实小区稳定排序 + 固定种子轮换，保证可重复生成。
WITH ranked_communities AS (
    SELECT c.id,
           c.community_code,
           row_number() OVER (ORDER BY c.administrative_region, c.community_name, c.community_code) AS rn
    FROM core.community c
    WHERE c.deleted_at IS NULL
      AND c.community_code LIKE 'SHOWCASE-WH-%'
), profiled AS (
    SELECT id,
           CASE ((rn - 1 + {seed_shift}) % 6)
             WHEN 0 THEN 'OLD_WORK_UNIT'
             WHEN 1 THEN 'MATURE_COMMODITY'
             WHEN 2 THEN 'HIGH_RISE'
             WHEN 3 THEN 'NEW_LARGE_COMMUNITY'
             WHEN 4 THEN 'MIXED_USE'
             ELSE 'OLD_CITY_FOCUS'
           END AS profile
    FROM ranked_communities
)
UPDATE core.community c
SET extra_attributes = COALESCE(c.extra_attributes, '{{}}'::jsonb)
    || jsonb_build_object(
        'showcaseGenerated', true,
        'scenarioProfile', p.profile,
        'diversityProfile', '{PROFILE_MODE}',
        'historyMonths', {HISTORY_MONTHS},
        'dataDensity', '{DENSITY}',
        'scenarioVersion', 2
    ),
    updated_at = CURRENT_TIMESTAMP
FROM profiled p
WHERE c.id = p.id;

-- 建筑属性按社区画像形成相关性，而不是独立均匀随机。
WITH ranked_communities AS (
    SELECT c.id AS community_id,
           CASE ((row_number() OVER (ORDER BY c.administrative_region, c.community_name, c.community_code) - 1 + {seed_shift}) % 6)
             WHEN 0 THEN 'OLD_WORK_UNIT'
             WHEN 1 THEN 'MATURE_COMMODITY'
             WHEN 2 THEN 'HIGH_RISE'
             WHEN 3 THEN 'NEW_LARGE_COMMUNITY'
             WHEN 4 THEN 'MIXED_USE'
             ELSE 'OLD_CITY_FOCUS'
           END AS profile
    FROM core.community c
    WHERE c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
), ranked_buildings AS (
    SELECT b.id,
           b.community_id,
           rc.profile,
           row_number() OVER (PARTITION BY b.community_id ORDER BY b.building_code, b.id) AS brn
    FROM core.building b
    JOIN ranked_communities rc ON rc.community_id = b.community_id
    WHERE b.deleted_at IS NULL
), derived AS (
    SELECT rb.*,
           CASE rb.profile
             WHEN 'OLD_WORK_UNIT' THEN 1978 + ((rb.brn * 3 + {SEED}) % 18)::int
             WHEN 'MATURE_COMMODITY' THEN 1996 + ((rb.brn * 5 + {SEED}) % 13)::int
             WHEN 'HIGH_RISE' THEN 2006 + ((rb.brn * 3 + {SEED}) % 13)::int
             WHEN 'NEW_LARGE_COMMUNITY' THEN 2016 + ((rb.brn * 2 + {SEED}) % 9)::int
             WHEN 'MIXED_USE' THEN 1994 + ((rb.brn * 4 + {SEED}) % 22)::int
             ELSE 1968 + ((rb.brn * 5 + {SEED}) % 25)::int
           END AS construction_year,
           CASE rb.profile
             WHEN 'OLD_WORK_UNIT' THEN 5 + ((rb.brn + {SEED}) % 4)::int
             WHEN 'MATURE_COMMODITY' THEN 6 + ((rb.brn * 2 + {SEED}) % 13)::int
             WHEN 'HIGH_RISE' THEN 18 + ((rb.brn * 3 + {SEED}) % 16)::int
             WHEN 'NEW_LARGE_COMMUNITY' THEN 18 + ((rb.brn * 5 + {SEED}) % 17)::int
             WHEN 'MIXED_USE' THEN 8 + ((rb.brn * 4 + {SEED}) % 18)::int
             ELSE 4 + ((rb.brn + {SEED}) % 6)::int
           END AS floor_count,
           CASE rb.profile
             WHEN 'OLD_WORK_UNIT' THEN CASE WHEN rb.brn % 3 = 0 THEN 'MASONRY' ELSE 'BRICK_CONCRETE' END
             WHEN 'MATURE_COMMODITY' THEN CASE WHEN rb.brn % 3 = 0 THEN 'FRAME' ELSE 'BRICK_CONCRETE' END
             WHEN 'HIGH_RISE' THEN CASE WHEN rb.brn % 4 = 0 THEN 'FRAME' ELSE 'FRAME_SHEAR' END
             WHEN 'NEW_LARGE_COMMUNITY' THEN CASE WHEN rb.brn % 5 = 0 THEN 'FRAME' ELSE 'FRAME_SHEAR' END
             WHEN 'MIXED_USE' THEN CASE WHEN rb.brn % 2 = 0 THEN 'FRAME' ELSE 'FRAME_SHEAR' END
             ELSE CASE WHEN rb.brn % 4 = 0 THEN 'BRICK_CONCRETE' ELSE 'MASONRY' END
           END AS structure_type,
           CASE rb.profile
             WHEN 'OLD_WORK_UNIT' THEN 48 + ((rb.brn * 7 + {SEED}) % 28)
             WHEN 'MATURE_COMMODITY' THEN 68 + ((rb.brn * 5 + {SEED}) % 27)
             WHEN 'HIGH_RISE' THEN 72 + ((rb.brn * 7 + {SEED}) % 25)
             WHEN 'NEW_LARGE_COMMUNITY' THEN 86 + ((rb.brn * 3 + {SEED}) % 13)
             WHEN 'MIXED_USE' THEN 62 + ((rb.brn * 6 + {SEED}) % 31)
             ELSE 42 + ((rb.brn * 9 + {SEED}) % 31)
           END::numeric(5,2) AS archive_score
    FROM ranked_buildings rb
), calculated AS (
    SELECT d.*,
           greatest(18, (d.floor_count * (5 + ((d.brn + {SEED}) % 5)))::int) AS household_count
    FROM derived d
), final_values AS (
    SELECT c.*,
           (c.household_count * (2.05 + ((c.brn + {SEED}) % 7) * 0.11))::int AS resident_count
    FROM calculated c
)
UPDATE core.building b
SET construction_year = f.construction_year,
    structure_type = f.structure_type,
    floor_count = f.floor_count,
    household_count = f.household_count,
    resident_count = f.resident_count,
    building_area = round((f.household_count * (68 + ((f.brn * 7 + {SEED}) % 48)))::numeric, 2),
    elderly_count = CASE f.profile
        WHEN 'OLD_CITY_FOCUS' THEN (f.resident_count * (0.24 + (f.brn % 5) * 0.015))::int
        WHEN 'OLD_WORK_UNIT' THEN (f.resident_count * (0.18 + (f.brn % 4) * 0.015))::int
        ELSE (f.resident_count * (0.09 + (f.brn % 5) * 0.012))::int
    END,
    child_count = CASE f.profile
        WHEN 'NEW_LARGE_COMMUNITY' THEN (f.resident_count * (0.16 + (f.brn % 4) * 0.012))::int
        WHEN 'HIGH_RISE' THEN (f.resident_count * (0.13 + (f.brn % 4) * 0.01))::int
        ELSE (f.resident_count * (0.07 + (f.brn % 4) * 0.01))::int
    END,
    has_elevator = CASE
        WHEN f.profile IN ('HIGH_RISE','NEW_LARGE_COMMUNITY') THEN true
        WHEN f.profile IN ('OLD_WORK_UNIT','OLD_CITY_FOCUS') THEN f.floor_count >= 9 AND f.brn % 4 = 0
        ELSE f.floor_count >= 9 OR f.brn % 3 = 0
    END,
    has_illegal_modification = CASE
        WHEN f.profile = 'OLD_CITY_FOCUS' THEN f.brn % 3 <> 0
        WHEN f.profile = 'OLD_WORK_UNIT' THEN f.brn % 3 = 0
        WHEN f.profile = 'MIXED_USE' THEN f.brn % 4 = 0
        WHEN f.profile = 'NEW_LARGE_COMMUNITY' THEN f.brn % 12 = 0
        ELSE f.brn % 8 = 0
    END,
    has_ground_floor_business = CASE
        WHEN f.profile = 'MIXED_USE' THEN f.brn % 4 <> 0
        WHEN f.profile = 'OLD_CITY_FOCUS' THEN f.brn % 3 = 0
        ELSE f.brn % 6 = 0
    END,
    archive_completeness_score = f.archive_score,
    extra_attributes = COALESCE(b.extra_attributes, '{{}}'::jsonb)
        || jsonb_build_object(
            'scenarioProfile', f.profile,
            'businessAttributesSynthetic', true,
            'diversityProfile', '{PROFILE_MODE}',
            'dataDensity', '{DENSITY}',
            'historyMonths', {HISTORY_MONTHS}
        ),
    updated_at = CURRENT_TIMESTAMP
FROM final_values f
WHERE b.id = f.id;

-- 为不同社区画像生成 0~{cfg['task_max']} 条历史巡检任务，覆盖最近 {HISTORY_MONTHS} 个月。
WITH community_profiles AS (
    SELECT c.id AS community_id,
           CASE ((row_number() OVER (ORDER BY c.administrative_region, c.community_name, c.community_code) - 1 + {seed_shift}) % 6)
             WHEN 0 THEN 'OLD_WORK_UNIT'
             WHEN 1 THEN 'MATURE_COMMODITY'
             WHEN 2 THEN 'HIGH_RISE'
             WHEN 3 THEN 'NEW_LARGE_COMMUNITY'
             WHEN 4 THEN 'MIXED_USE'
             ELSE 'OLD_CITY_FOCUS'
           END AS profile
    FROM core.community c
    WHERE c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
), buildings AS (
    SELECT b.id AS building_id, b.building_code, b.building_name, cp.profile,
           row_number() OVER (PARTITION BY b.community_id ORDER BY b.building_code, b.id) AS brn
    FROM core.building b
    JOIN community_profiles cp ON cp.community_id=b.community_id
    WHERE b.deleted_at IS NULL
), expanded AS (
    SELECT b.*, gs AS seq,
           ('SHOWCASE-HIST-' || b.building_code || '-' || gs)::varchar(64) AS task_code,
           (1 + ((b.brn * 37 + gs * 53 + {SEED}) % {history_days}))::int AS days_ago,
           CASE ((b.brn + gs + {SEED}) % 10)
             WHEN 0 THEN 'CANCELLED'
             WHEN 1 THEN 'PENDING'
             WHEN 2 THEN 'IN_PROGRESS'
             ELSE 'COMPLETED'
           END AS task_status
    FROM buildings b
    CROSS JOIN LATERAL generate_series(
        1,
        CASE
          WHEN b.profile='OLD_CITY_FOCUS' THEN LEAST({cfg['task_max']}, 2 + (b.brn % GREATEST(1,{cfg['task_max']})))
          WHEN b.profile='OLD_WORK_UNIT' THEN LEAST({cfg['task_max']}, 1 + (b.brn % GREATEST(1,{cfg['task_max']})))
          WHEN b.profile='MIXED_USE' THEN LEAST({cfg['task_max']}, 1 + ((b.brn + 1) % GREATEST(1,{cfg['task_max']})))
          WHEN b.profile='NEW_LARGE_COMMUNITY' THEN CASE WHEN b.brn % 4=0 THEN 1 ELSE 0 END
          ELSE LEAST({cfg['task_max']}, (b.brn + {SEED}) % ({cfg['task_max']} + 1))
        END
    ) gs
)
INSERT INTO core.inspection_task (
    task_code, building_id, inspection_type, planned_at, assigned_to, focus_parts,
    status, created_by, title, description, started_at, completed_at, cancelled_at, remark
)
SELECT e.task_code, e.building_id,
       CASE (e.seq + e.brn) % 3 WHEN 0 THEN 'ROUTINE' WHEN 1 THEN 'SPECIAL' ELSE 'COMPREHENSIVE' END,
       CURRENT_TIMESTAMP - make_interval(days => e.days_ago),
       inspector.id,
       to_jsonb(ARRAY[CASE (e.seq + e.brn) % 6
         WHEN 0 THEN '外立面' WHEN 1 THEN '楼梯间' WHEN 2 THEN '屋面'
         WHEN 3 THEN '地下空间' WHEN 4 THEN '阳台及外窗' ELSE '首层公共区域' END]),
       e.task_status,
       manager.id,
       e.building_name || ' 历史巡检',
       CASE e.profile
         WHEN 'OLD_CITY_FOCUS' THEN '重点关注老城建筑，结合投诉、维修和结构表观异常开展复查。'
         WHEN 'OLD_WORK_UNIT' THEN '结合老旧住宅维护记录开展常规安全巡查。'
         WHEN 'MIXED_USE' THEN '重点关注底商、公共区域和人员密集使用条件。'
         WHEN 'NEW_LARGE_COMMUNITY' THEN '开展新建住宅质量与渗漏类问题跟踪。'
         ELSE '开展建筑公共部位、外观和维护情况检查。' END,
       CASE WHEN e.task_status IN ('IN_PROGRESS','COMPLETED') THEN CURRENT_TIMESTAMP - make_interval(days => greatest(1,e.days_ago-1)) ELSE NULL END,
       CASE WHEN e.task_status='COMPLETED' THEN CURRENT_TIMESTAMP - make_interval(days => greatest(0,e.days_ago-2)) ELSE NULL END,
       CASE WHEN e.task_status='CANCELLED' THEN CURRENT_TIMESTAMP - make_interval(days => e.days_ago) ELSE NULL END,
       '城市样例历史巡检'
FROM expanded e
JOIN core.user_account inspector ON inspector.username='demo_inspector' AND inspector.deleted_at IS NULL
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (task_code) WHERE deleted_at IS NULL DO UPDATE SET
    status=EXCLUDED.status, planned_at=EXCLUDED.planned_at, started_at=EXCLUDED.started_at,
    completed_at=EXCLUDED.completed_at, cancelled_at=EXCLUDED.cancelled_at,
    focus_parts=EXCLUDED.focus_parts, title=EXCLUDED.title, description=EXCLUDED.description,
    updated_at=CURRENT_TIMESTAMP;

-- 每条已开始任务生成 1~{cfg['record_max']} 条现场记录，形成同栋楼多问题组合。
WITH tasks AS (
    SELECT t.id AS task_id, t.task_code, t.building_id, t.status,
           row_number() OVER (ORDER BY t.task_code) AS rn
    FROM core.inspection_task t
    WHERE t.deleted_at IS NULL AND t.task_code LIKE 'SHOWCASE-HIST-%'
      AND t.status IN ('IN_PROGRESS','COMPLETED')
), expanded AS (
    SELECT t.*, gs AS seq,
           (substr(md5(t.task_code || ':record:' || gs::text),1,8) || '-' ||
            substr(md5(t.task_code || ':record:' || gs::text),9,4) || '-' ||
            substr(md5(t.task_code || ':record:' || gs::text),13,4) || '-' ||
            substr(md5(t.task_code || ':record:' || gs::text),17,4) || '-' ||
            substr(md5(t.task_code || ':record:' || gs::text),21,12))::uuid AS record_id
    FROM tasks t
    CROSS JOIN LATERAL generate_series(1, CASE WHEN t.status='COMPLETED' THEN 1 + (t.rn % {cfg['record_max']}) ELSE 1 END) gs
)
INSERT INTO core.inspection_record (
    id, inspection_task_id, building_id, inspector_id, inspection_part,
    inspected_at, submitted_at, status, summary, form_data, issue_type,
    severity, rectification_suggestion, extra_data, remark
)
SELECT e.record_id, e.task_id, e.building_id, inspector.id,
       CASE (e.rn + e.seq) % 6 WHEN 0 THEN '外立面' WHEN 1 THEN '楼梯间' WHEN 2 THEN '屋面'
            WHEN 3 THEN '地下空间' WHEN 4 THEN '阳台及外窗' ELSE '首层公共区域' END,
       CURRENT_TIMESTAMP - make_interval(days => 1 + ((e.rn * 17 + e.seq * 11 + {SEED}) % {history_days})),
       CASE WHEN e.status='COMPLETED' THEN CURRENT_TIMESTAMP - make_interval(days => ((e.rn * 17 + e.seq * 11 + {SEED}) % {history_days})) ELSE NULL END,
       CASE WHEN e.status='COMPLETED' THEN 'COMPLETED' ELSE 'DRAFT' END,
       CASE (e.rn + e.seq + {SEED}) % 5
         WHEN 0 THEN '发现局部裂缝，需要记录宽度变化并安排后续复查。'
         WHEN 1 THEN '存在雨后渗漏和潮湿痕迹，建议排查防水节点。'
         WHEN 2 THEN '局部饰面空鼓或脱落风险，需要设置警示并安排维修。'
         WHEN 3 THEN '发现轻微变形或构件异常，建议提交专业人员复核。'
         ELSE '整体状况基本稳定，存在轻微老化问题，纳入常规维护。' END,
       jsonb_build_object('showcaseGenerated',true,'weather',CASE e.rn%4 WHEN 0 THEN 'SUNNY' WHEN 1 THEN 'CLOUDY' WHEN 2 THEN 'RAIN_AFTER' ELSE 'OVERCAST' END,'photoCount',1 + (e.rn+e.seq)%7),
       CASE (e.rn + e.seq + {SEED}) % 5 WHEN 0 THEN 'CRACK' WHEN 1 THEN 'WATER_LEAKAGE' WHEN 2 THEN 'SURFACE_FALLING' WHEN 3 THEN 'DEFORMATION' ELSE 'OTHER' END,
       CASE WHEN (e.rn + e.seq + {SEED}) % 13=0 THEN 'HIGH' WHEN (e.rn + e.seq) % 3=0 THEN 'MEDIUM' ELSE 'LOW' END,
       '根据风险程度安排维修、复查、专业检测或持续观察。',
       jsonb_build_object('showcaseGenerated',true,'historyMonths',{HISTORY_MONTHS},'requiresReview',((e.rn+e.seq+{SEED})%13=0)),
       '城市样例历史现场记录'
FROM expanded e
JOIN core.user_account inspector ON inspector.username='demo_inspector' AND inspector.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
    status=EXCLUDED.status, summary=EXCLUDED.summary, form_data=EXCLUDED.form_data,
    issue_type=EXCLUDED.issue_type, severity=EXCLUDED.severity,
    rectification_suggestion=EXCLUDED.rectification_suggestion, extra_data=EXCLUDED.extra_data,
    updated_at=CURRENT_TIMESTAMP, deleted_at=NULL;

-- 丰富证据类型，制造官方记录、专业确认与待核实记录并存的情况。
WITH targets AS (
    SELECT b.id AS building_id, b.building_code,
           row_number() OVER (ORDER BY c.community_code,b.building_code) AS rn
    FROM core.building b
    JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
    WHERE b.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
), selected AS (
    SELECT t.*,
           (substr(md5(t.building_code || ':diversity:evidence'),1,8) || '-' ||
            substr(md5(t.building_code || ':diversity:evidence'),9,4) || '-' ||
            substr(md5(t.building_code || ':diversity:evidence'),13,4) || '-' ||
            substr(md5(t.building_code || ':diversity:evidence'),17,4) || '-' ||
            substr(md5(t.building_code || ':diversity:evidence'),21,12))::uuid AS evidence_id
    FROM targets t WHERE t.rn % {cfg['evidence_mod']} <> 0
)
INSERT INTO core.building_evidence (
    id, building_id, evidence_type, title, description, occurred_at,
    source, reliability_level, evidence_data, created_by
)
SELECT s.evidence_id, s.building_id,
       CASE s.rn%6 WHEN 0 THEN 'MAINTENANCE_RECORD' WHEN 1 THEN 'PROFESSIONAL_INSPECTION'
            WHEN 2 THEN 'HISTORICAL_COMPLAINT' WHEN 3 THEN 'PUBLIC_VALUE'
            WHEN 4 THEN 'ENVIRONMENT_RISK' ELSE 'OTHER' END,
       CASE s.rn%5 WHEN 0 THEN '维修完成后的跟踪复查' WHEN 1 THEN '专业检查补充记录'
            WHEN 2 THEN '居民历史投诉核查记录' WHEN 3 THEN '社区治理重点关注记录'
            ELSE '建筑安全档案补充材料' END,
       '用于形成多来源、多可靠度、跨时间的建筑安全治理证据链。',
       CURRENT_TIMESTAMP - make_interval(days => 10 + ((s.rn*29 + {SEED}) % {history_days})),
       CASE s.rn%4 WHEN 0 THEN '专业检测单位' WHEN 1 THEN '社区治理台账' WHEN 2 THEN '居民反馈归档' ELSE '物业维护记录' END,
       CASE s.rn%5 WHEN 0 THEN 'PROFESSIONAL_CONFIRMED' WHEN 1 THEN 'OFFICIAL_RECORD' ELSE 'UNVERIFIED' END,
       jsonb_build_object('showcaseGenerated',true,'diversityProfile','{PROFILE_MODE}','score',20 + ((s.rn*17+{SEED})%79)),
       manager.id
FROM selected s
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
    evidence_type=EXCLUDED.evidence_type, title=EXCLUDED.title, description=EXCLUDED.description,
    occurred_at=EXCLUDED.occurred_at, source=EXCLUDED.source,
    reliability_level=EXCLUDED.reliability_level, evidence_data=EXCLUDED.evidence_data,
    updated_at=CURRENT_TIMESTAMP, deleted_at=NULL;

-- 居民反馈形成普通、重复投诉、高紧迫度和已闭环等不同状态。
WITH targets AS (
    SELECT b.id AS building_id, b.building_code, c.id AS community_id,
           row_number() OVER (ORDER BY c.community_code,b.building_code) AS rn
    FROM core.building b
    JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
    WHERE b.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
), selected AS (
    SELECT * FROM targets WHERE rn % {cfg['report_mod']} = 1 OR rn % 17 = 0
)
INSERT INTO core.resident_report (
    report_code, community_id, building_id, reporter_user_id, report_type,
    description, status, urgency, evidence, submitted_at, handled_at
)
SELECT ('SHOWCASE-DIV-REPORT-' || s.building_code || '-' || ((s.rn%3)+1))::varchar(64),
       s.community_id, s.building_id, reporter.id,
       CASE s.rn%5 WHEN 0 THEN 'WALL_CRACK' WHEN 1 THEN 'WATER_LEAKAGE' WHEN 2 THEN 'SURFACE_FALLING' WHEN 3 THEN 'DEFORMATION' ELSE 'OTHER' END,
       CASE s.rn%5
         WHEN 0 THEN '居民反映公共区域裂缝近期有发展趋势，希望尽快安排复查。'
         WHEN 1 THEN '居民反映雨后持续渗漏，已多次向物业反馈。'
         WHEN 2 THEN '居民反映外墙或饰面存在松动、脱落隐患。'
         WHEN 3 THEN '居民反映门窗或构件出现异常变形，建议现场核查。'
         ELSE '居民提交建筑公共部位安全问题线索，等待社区核查。' END,
       CASE s.rn%7 WHEN 0 THEN 'SUBMITTED' WHEN 1 THEN 'ACCEPTED' WHEN 2 THEN 'PROCESSING'
            WHEN 3 THEN 'NEED_MORE_INFO' WHEN 4 THEN 'RESOLVED' ELSE 'CLOSED' END,
       CASE WHEN s.rn%17=0 THEN 'URGENT' WHEN s.rn%5=0 THEN 'HIGH' WHEN s.rn%3=0 THEN 'LOW' ELSE 'NORMAL' END,
       jsonb_build_array(jsonb_build_object('type','TEXT','note',CASE WHEN s.rn%17=0 THEN '重复投诉，已升级社区重点跟踪' ELSE '居民现场描述与回访记录' END)),
       CURRENT_TIMESTAMP - make_interval(days => 1 + ((s.rn*19 + {SEED}) % {history_days})),
       CASE WHEN s.rn%7 IN (4,5,6) THEN CURRENT_TIMESTAMP - make_interval(days => ((s.rn*11 + {SEED}) % greatest(2,{history_days//2}))) ELSE NULL END
FROM selected s
JOIN core.user_account reporter ON reporter.username='demo_inspector' AND reporter.deleted_at IS NULL
ON CONFLICT (report_code) WHERE deleted_at IS NULL DO UPDATE SET
    report_type=EXCLUDED.report_type, description=EXCLUDED.description,
    status=EXCLUDED.status, urgency=EXCLUDED.urgency, evidence=EXCLUDED.evidence,
    submitted_at=EXCLUDED.submitted_at, handled_at=EXCLUDED.handled_at,
    updated_at=CURRENT_TIMESTAMP;

COMMIT;
"""

SQL_FILE.write_text(sql.strip() + "\n", encoding="utf-8")
print(
    f"场景增强 SQL 已生成：density={DENSITY}, profile={PROFILE_MODE}, "
    f"historyMonths={HISTORY_MONTHS}, taskMax={cfg['task_max']}",
    flush=True,
)
