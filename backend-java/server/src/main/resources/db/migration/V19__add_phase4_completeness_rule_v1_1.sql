-- UrbanSafe Priority Flyway migration V19
-- 第四阶段：数据完整度规则 V1.1，将巡检窗口、图片阈值和证据有效期统一规则化。

UPDATE core.completeness_assessment ca
SET status = 'STALE',
    stale_reason = 'RULE_CHANGED:COMPLETENESS-V1.1'
WHERE ca.status = 'CURRENT'
  AND EXISTS (
      SELECT 1
      FROM core.rule_version rv
      WHERE rv.id = ca.rule_version_id
        AND rv.rule_type = 'COMPLETENESS'
        AND rv.version_code <> 'COMPLETENESS-V1.1'
  );

UPDATE core.rule_version
SET status = 'RETIRED',
    updated_at = CURRENT_TIMESTAMP
WHERE rule_type = 'COMPLETENESS'
  AND status = 'ACTIVE'
  AND deleted_at IS NULL;

INSERT INTO core.rule_version (
    id, rule_type, version_code, rule_name, rule_content, checksum,
    status, activated_at, created_at, updated_at
) VALUES (
    '41000000-0000-0000-0000-000000000004',
    'COMPLETENESS',
    'COMPLETENESS-V1.1',
    '数据完整度评分规则 V1.1',
    $rule$
{
  "schemaVersion": "1.1",
  "scoreDirection": "HIGHER_IS_MORE_COMPLETE",
  "precision": 2,
  "engineVersion": "phase4-rule-engine-v1",
  "dimensions": [
    {"code": "BASIC_ARCHIVE", "label": "基础档案", "weight": "0.35"},
    {"code": "RECENT_INSPECTION", "label": "近期巡检", "weight": "0.25"},
    {"code": "IMAGE_COVERAGE", "label": "图片覆盖", "weight": "0.15"},
    {"code": "MAINTENANCE_RECORD", "label": "维修资料", "weight": "0.10"},
    {"code": "PROFESSIONAL_INSPECTION", "label": "专业检测", "weight": "0.15"}
  ],
  "fieldWeights": {
    "constructionYear": 20,
    "structureType": 20,
    "floorCount": 15,
    "buildingArea": 10,
    "householdCount": 10,
    "residentCount": 10,
    "address": 5,
    "geometry": 10
  },
  "inspectionRecency": [
    {"maxDays": 180, "score": 100},
    {"maxDays": 365, "score": 80},
    {"maxDays": 730, "score": 50},
    {"fallbackScore": 20}
  ],
  "imageCoverage": [
    {"minImages": 0, "minParts": 0, "score": 0},
    {"minImages": 1, "minParts": 1, "score": 40},
    {"minImages": 2, "minParts": 2, "score": 70},
    {"minImages": 4, "minParts": 3, "score": 100}
  ],
  "evidenceCoverage": {
    "verifiedRecent": 100,
    "verifiedOld": 80,
    "unverified": 40,
    "missing": 0
  },
  "evidenceRecency": {
    "maintenanceYears": 5,
    "professionalInspectionYears": 5
  },
  "levels": [
    {"code": "INSUFFICIENT", "min": "0", "maxExclusive": "50"},
    {"code": "LIMITED", "min": "50", "maxExclusive": "70"},
    {"code": "GOOD", "min": "70", "maxExclusive": "85"},
    {"code": "EXCELLENT", "min": "85", "maxInclusive": "100"}
  ],
  "metadata": {
    "purpose": "比赛第二版数据完整度评分，规则化巡检窗口、图片阈值和证据有效期",
    "disclaimer": "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。"
  }
}$rule$::jsonb,
    'cc528dc0e116ebbcde636db751834be4173d13a31911648c176de841751b4a2a',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
