-- UrbanSafe Priority Flyway migration V14
-- 第四阶段：风险评分、判断置信度与城市更新优先级。
-- 规则摘要按 UTF-8、Unicode NFC、对象键字典序、无多余空白的规范化 JSON 计算 SHA-256。

ALTER TABLE core.risk_assessment
    RENAME COLUMN safety_score TO risk_score;

ALTER TABLE core.completeness_assessment
    ADD COLUMN calculation_batch_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN engine_version VARCHAR(64) NOT NULL DEFAULT 'phase4-rule-engine-v1',
    ADD COLUMN trigger_type VARCHAR(32) NOT NULL DEFAULT 'DATA_CHANGE',
    ADD COLUMN triggered_by UUID REFERENCES core.user_account(id),
    ADD COLUMN stale_reason VARCHAR(255),
    ADD COLUMN dimension_scores JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE core.risk_assessment
    ADD COLUMN calculation_batch_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN engine_version VARCHAR(64) NOT NULL DEFAULT 'phase4-rule-engine-v1',
    ADD COLUMN trigger_type VARCHAR(32) NOT NULL DEFAULT 'DATA_CHANGE',
    ADD COLUMN triggered_by UUID REFERENCES core.user_account(id),
    ADD COLUMN stale_reason VARCHAR(255),
    ADD COLUMN evidence_reliability_score NUMERIC(5, 2) NOT NULL DEFAULT 0
        CHECK (evidence_reliability_score BETWEEN 0 AND 100);

ALTER TABLE core.renewal_priority
    ADD COLUMN calculation_batch_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN engine_version VARCHAR(64) NOT NULL DEFAULT 'phase4-rule-engine-v1',
    ADD COLUMN trigger_type VARCHAR(32) NOT NULL DEFAULT 'DATA_CHANGE',
    ADD COLUMN triggered_by UUID REFERENCES core.user_account(id),
    ADD COLUMN stale_reason VARCHAR(255),
    ADD COLUMN ranking_scope_key VARCHAR(160) NOT NULL DEFAULT 'ALL';

ALTER TABLE core.completeness_assessment
    ADD CONSTRAINT ck_completeness_trigger_type
    CHECK (trigger_type IN ('MANUAL', 'BATCH', 'DATA_CHANGE', 'RULE_CHANGE', 'DEMO_SEED'));

ALTER TABLE core.risk_assessment
    ADD CONSTRAINT ck_risk_trigger_type
    CHECK (trigger_type IN ('MANUAL', 'BATCH', 'DATA_CHANGE', 'RULE_CHANGE', 'DEMO_SEED'));

ALTER TABLE core.renewal_priority
    ADD CONSTRAINT ck_renewal_trigger_type
    CHECK (trigger_type IN ('MANUAL', 'BATCH', 'DATA_CHANGE', 'RULE_CHANGE', 'DEMO_SEED'));

CREATE UNIQUE INDEX uk_rule_version_type_code
    ON core.rule_version(rule_type, version_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_rule_version_single_active
    ON core.rule_version(rule_type)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uk_completeness_current_building
    ON core.completeness_assessment(building_id)
    WHERE status = 'CURRENT';

CREATE UNIQUE INDEX uk_risk_current_building
    ON core.risk_assessment(building_id)
    WHERE status = 'CURRENT';

CREATE UNIQUE INDEX uk_renewal_current_scope_building
    ON core.renewal_priority(building_id, ranking_scope_key)
    WHERE status = 'CURRENT';

CREATE INDEX idx_completeness_building_assessed
    ON core.completeness_assessment(building_id, assessed_at DESC);

CREATE INDEX idx_completeness_current_score
    ON core.completeness_assessment(completeness_score DESC, building_id)
    WHERE status = 'CURRENT';

CREATE INDEX idx_risk_building_assessed
    ON core.risk_assessment(building_id, assessed_at DESC);

CREATE INDEX idx_risk_level_current
    ON core.risk_assessment(risk_level, risk_score DESC, confidence_score DESC)
    WHERE status = 'CURRENT';

CREATE INDEX idx_renewal_scope_ranking
    ON core.renewal_priority(ranking_scope_key, ranking, priority_score DESC)
    WHERE status = 'CURRENT';

CREATE INDEX idx_assessment_idempotency
    ON core.risk_assessment(building_id, input_checksum, rule_version_id, engine_version, assessed_at DESC);

CREATE OR REPLACE FUNCTION core.prevent_active_rule_content_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'ACTIVE'
       AND (
           NEW.rule_type IS DISTINCT FROM OLD.rule_type
           OR NEW.version_code IS DISTINCT FROM OLD.version_code
           OR NEW.rule_name IS DISTINCT FROM OLD.rule_name
           OR NEW.rule_content IS DISTINCT FROM OLD.rule_content
           OR NEW.checksum IS DISTINCT FROM OLD.checksum
       ) THEN
        RAISE EXCEPTION 'ACTIVE rule content is immutable; create a new DRAFT version';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_rule_version_active_immutable
BEFORE UPDATE ON core.rule_version
FOR EACH ROW
EXECUTE FUNCTION core.prevent_active_rule_content_update();

-- 第四阶段基线启用前，旧的同类型 ACTIVE 规则退役；历史评分仍保留其 rule_version_id。
UPDATE core.rule_version
SET status = 'RETIRED',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE'
  AND rule_type IN ('COMPLETENESS', 'RISK', 'RENEWAL')
  AND deleted_at IS NULL;

INSERT INTO core.rule_version (
    id, rule_type, version_code, rule_name, rule_content, checksum,
    status, activated_at, created_at, updated_at
) VALUES
(
    '41000000-0000-0000-0000-000000000001',
    'COMPLETENESS',
    'COMPLETENESS-V1',
    '数据完整度评分规则 V1',
    $rule$
{
  "schemaVersion": "1.0",
  "scoreDirection": "HIGHER_IS_MORE_COMPLETE",
  "precision": 2,
  "engineVersion": "phase4-rule-engine-v1",
  "dimensions": [
    {
      "code": "BASIC_ARCHIVE",
      "label": "基础档案",
      "weight": "0.35"
    },
    {
      "code": "RECENT_INSPECTION",
      "label": "近期巡检",
      "weight": "0.25"
    },
    {
      "code": "IMAGE_COVERAGE",
      "label": "图片覆盖",
      "weight": "0.15"
    },
    {
      "code": "MAINTENANCE_RECORD",
      "label": "维修资料",
      "weight": "0.10"
    },
    {
      "code": "PROFESSIONAL_INSPECTION",
      "label": "专业检测",
      "weight": "0.15"
    }
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
    {
      "maxDays": 180,
      "score": 100
    },
    {
      "maxDays": 365,
      "score": 80
    },
    {
      "maxDays": 730,
      "score": 50
    },
    {
      "maxDays": 999999,
      "score": 20
    }
  ],
  "imageCoverage": [
    {
      "minImages": 4,
      "minParts": 3,
      "score": 100
    },
    {
      "minImages": 2,
      "minParts": 2,
      "score": 70
    },
    {
      "minImages": 1,
      "minParts": 1,
      "score": 40
    },
    {
      "minImages": 0,
      "minParts": 0,
      "score": 0
    }
  ],
  "evidenceCoverage": {
    "verifiedRecent": 100,
    "verifiedOld": 80,
    "unverified": 40,
    "missing": 0,
    "recentYears": 5
  },
  "levels": [
    {
      "code": "INSUFFICIENT",
      "min": "0",
      "maxExclusive": "50"
    },
    {
      "code": "LIMITED",
      "min": "50",
      "maxExclusive": "70"
    },
    {
      "code": "GOOD",
      "min": "70",
      "maxExclusive": "85"
    },
    {
      "code": "EXCELLENT",
      "min": "85",
      "maxInclusive": "100"
    }
  ],
  "metadata": {
    "purpose": "比赛第一版数据完整度评分",
    "disclaimer": "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。"
  }
}$rule$::jsonb,
    'e0938f1cbcd38b78ba4471c9c37c70d3d88acf60685e6ad7b9866d616ff7fcdc',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '41000000-0000-0000-0000-000000000002',
    'RISK',
    'RISK-V1',
    '安全风险筛查评分规则 V1',
    $rule$
{
  "schemaVersion": "1.0",
  "scoreDirection": "HIGHER_IS_RISKIER",
  "precision": 2,
  "engineVersion": "phase4-rule-engine-v1",
  "dimensions": [
    {
      "code": "BUILDING_BASE",
      "label": "楼龄与结构基础",
      "weight": "0.20"
    },
    {
      "code": "INSPECTION_DEFECT",
      "label": "人工巡检病害",
      "weight": "0.30"
    },
    {
      "code": "PROFESSIONAL_HISTORY",
      "label": "专业和历史证据",
      "weight": "0.20"
    },
    {
      "code": "SPATIAL_ENVIRONMENT",
      "label": "空间与环境风险",
      "weight": "0.10"
    },
    {
      "code": "RESIDENT_FEEDBACK",
      "label": "公众反馈",
      "weight": "0.10"
    },
    {
      "code": "REVIEWED_AI",
      "label": "经复核人工智能证据",
      "weight": "0.10"
    }
  ],
  "baseRisk": {
    "ageWeight": "0.60",
    "structureWeight": "0.40",
    "illegalModificationBonus": 25,
    "groundFloorBusinessBonus": 10
  },
  "ageScores": [
    {
      "maxAge": 20,
      "score": 10
    },
    {
      "maxAge": 30,
      "score": 25
    },
    {
      "maxAge": 40,
      "score": 45
    },
    {
      "maxAge": 50,
      "score": 65
    },
    {
      "maxAge": 70,
      "score": 80
    },
    {
      "maxAge": 999,
      "score": 95
    },
    {
      "missing": true,
      "score": 50
    }
  ],
  "structureScores": {
    "SHEAR_WALL": 35,
    "FRAME": 45,
    "FRAME_SHEAR_WALL": 40,
    "BRICK_CONCRETE": 70,
    "MASONRY": 80,
    "WOOD_SIMPLE": 90,
    "UNKNOWN": 50
  },
  "severityScores": {
    "NONE": 0,
    "MINOR": 25,
    "MODERATE": 50,
    "SEVERE": 75,
    "CRITICAL": 100
  },
  "inspectionAggregation": {
    "maxSeverityWeight": "0.70",
    "multiPartWeight": "0.20",
    "persistenceWeight": "0.10"
  },
  "reliability": {
    "PROFESSIONAL_VERIFIED": "1.00",
    "MAINTENANCE_VERIFIED": "0.85",
    "INSPECTION_COMPLETED": "0.80",
    "BUSINESS_REVIEWED": "0.70",
    "BUSINESS_UNVERIFIED": "0.40",
    "PUBLIC_REPORT": "0.35",
    "AI_REVIEWED_REAL": "0.70",
    "AI_UNREVIEWED_REAL": "0",
    "AI_MOCK": "0"
  },
  "feedback": {
    "windowDays": 365,
    "sameTypeThirtyDayCap": 3,
    "scoreMultiplier": "20",
    "urgencyWeights": {
      "LOW": "0.5",
      "NORMAL": "1.0",
      "HIGH": "1.5",
      "URGENT": "2.0"
    },
    "statusWeights": {
      "SUBMITTED": "1.0",
      "ACCEPTED": "1.0",
      "PROCESSING": "1.0",
      "NEED_MORE_INFO": "0.8",
      "RESOLVED": "0.3",
      "CLOSED": "0.1",
      "REJECTED": "0",
      "CANCELLED": "0"
    },
    "timeDecay": [
      {
        "maxDays": 30,
        "factor": "1.0"
      },
      {
        "maxDays": 90,
        "factor": "0.7"
      },
      {
        "maxDays": 180,
        "factor": "0.4"
      },
      {
        "maxDays": 365,
        "factor": "0.2"
      }
    ]
  },
  "confidence": {
    "completenessWeight": "0.70",
    "evidenceReliabilityWeight": "0.30",
    "lowThreshold": "60"
  },
  "levels": [
    {
      "code": "LOW",
      "min": "0",
      "maxExclusive": "25"
    },
    {
      "code": "MEDIUM",
      "min": "25",
      "maxExclusive": "50"
    },
    {
      "code": "HIGH",
      "min": "50",
      "maxExclusive": "75"
    },
    {
      "code": "VERY_HIGH",
      "min": "75",
      "maxInclusive": "100"
    }
  ],
  "recommendations": {
    "manualReviewRiskThreshold": "50",
    "lowConfidenceThreshold": "60",
    "professionalInspectionRiskThreshold": "75",
    "severeInspectionRequiresProfessional": true
  },
  "metadata": {
    "purpose": "比赛第一版安全风险筛查评分",
    "disclaimer": "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。"
  }
}$rule$::jsonb,
    '47e2b83e321f954c8de2739e5edee4666a533ff57259ba8d31a3da9c9a2da7e9',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '41000000-0000-0000-0000-000000000003',
    'RENEWAL',
    'RENEWAL-V1',
    '城市更新优先级评分规则 V1',
    $rule$
{
  "schemaVersion": "1.0",
  "scoreDirection": "HIGHER_IS_HIGHER_PRIORITY",
  "precision": 2,
  "engineVersion": "phase4-rule-engine-v1",
  "dimensions": [
    {
      "code": "RISK",
      "label": "安全风险",
      "weight": "0.45"
    },
    {
      "code": "POPULATION_IMPACT",
      "label": "人口影响",
      "weight": "0.15"
    },
    {
      "code": "BUILDING_AGE",
      "label": "楼龄",
      "weight": "0.10"
    },
    {
      "code": "PUBLIC_VALUE",
      "label": "公共价值",
      "weight": "0.10"
    },
    {
      "code": "FEEDBACK_URGENCY",
      "label": "公众反馈紧迫性",
      "weight": "0.10"
    },
    {
      "code": "GOVERNANCE_URGENCY",
      "label": "治理紧迫性",
      "weight": "0.10"
    }
  ],
  "populationImpact": {
    "residentWeight": "0.70",
    "vulnerableWeight": "0.30",
    "residentScores": [
      {
        "max": 0,
        "score": 0
      },
      {
        "max": 99,
        "score": 20
      },
      {
        "max": 299,
        "score": 40
      },
      {
        "max": 599,
        "score": 60
      },
      {
        "max": 999,
        "score": 80
      },
      {
        "max": 2147483647,
        "score": 100
      }
    ],
    "vulnerableFullScoreCount": 200
  },
  "reliabilityFactor": {
    "base": "0.85",
    "confidenceWeight": "0.15"
  },
  "rankingScopes": [
    "ALL",
    "REGION",
    "COMMUNITY"
  ],
  "rankingKeys": [
    "priorityScore:DESC",
    "riskScore:DESC",
    "confidenceScore:DESC",
    "residentCount:DESC",
    "buildingCode:ASC",
    "buildingId:ASC"
  ],
  "levels": [
    {
      "code": "P4",
      "min": "0",
      "maxExclusive": "40"
    },
    {
      "code": "P3",
      "min": "40",
      "maxExclusive": "60"
    },
    {
      "code": "P2",
      "min": "60",
      "maxExclusive": "80"
    },
    {
      "code": "P1",
      "min": "80",
      "maxInclusive": "100"
    }
  ],
  "metadata": {
    "purpose": "比赛第一版城市更新优先级评分",
    "disclaimer": "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。"
  }
}$rule$::jsonb,
    'db49a05db089326987ea2fb38f11a3f71b1c93ae6398be7f6372955970a55140',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 兼容第三阶段的 EXPERT 角色，同时提供第四阶段语义更明确的角色名。
INSERT INTO core.role (id, role_code, role_name, description, permissions)
SELECT
    '42000000-0000-0000-0000-000000000001',
    'PROFESSIONAL_REVIEWER',
    '专业复核人员',
    '查看评分、证据、排除原因和复核建议',
    '["assessment:read","assessment:evidence:read"]'::jsonb
WHERE NOT EXISTS (
    SELECT 1 FROM core.role
    WHERE role_code = 'PROFESSIONAL_REVIEWER' AND deleted_at IS NULL
);

COMMENT ON COLUMN core.risk_assessment.risk_score IS
    '安全风险筛查分，0-100，越高表示风险特征越明显，不是房屋安全概率';
COMMENT ON COLUMN core.risk_assessment.confidence_score IS
    '判断置信度，0-100，越高表示当前证据越充分可靠';
COMMENT ON COLUMN core.renewal_priority.priority_score IS
    '城市更新优先级分，0-100，越高越应优先进入治理或更新评估';
COMMENT ON COLUMN core.renewal_priority.ranking_scope_key IS
    '稳定排名范围键：ALL、REGION:<region> 或 COMMUNITY:<uuid>';
