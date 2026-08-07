-- UrbanSafe Priority Flyway migration V15
-- 第四阶段硬化：补齐城市更新优先级 V1.1 规则参数，不修改 V14 已执行迁移。
-- 规则摘要按 UTF-8、Unicode NFC、对象键字典序、无多余空白的规范化 JSON 计算 SHA-256。

UPDATE core.rule_version
SET status = 'RETIRED',
    updated_at = CURRENT_TIMESTAMP
WHERE rule_type = 'RENEWAL'
  AND status = 'ACTIVE'
  AND deleted_at IS NULL;

INSERT INTO core.rule_version (
    id, rule_type, version_code, rule_name, rule_content, checksum,
    status, activated_at, created_at, updated_at
) VALUES (
    '41000000-0000-0000-0000-000000000015',
    'RENEWAL',
    'RENEWAL-V1.1',
    '城市更新优先级评分规则 V1.1',
    $rule$
{
  "schemaVersion": "1.1",
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
  "buildingAge": {
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
    ]
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
    "purpose": "比赛第二版城市更新优先级评分，补齐规则驱动楼龄参数和证据口径说明",
    "evidencePolicy": {
      "PUBLIC_VALUE": "来自 core.building_evidence 的 PUBLIC_VALUE 证据；缺失按 0 分处理，不反向影响安全风险",
      "GOVERNANCE_URGENCY": "来自 core.building_evidence 的 GOVERNANCE_URGENCY 证据；楼栋违规改造和底商标记仅作为治理紧迫性补充"
    },
    "disclaimer": "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。"
  }
}
$rule$::jsonb,
    '424e4f3ef9626d6c9a5b65cc241c703f485ce6d863ab9972a2504cdbe4997ddd',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
