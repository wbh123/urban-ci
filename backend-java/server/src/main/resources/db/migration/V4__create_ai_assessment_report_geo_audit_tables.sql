-- UrbanSafe Priority Flyway migration V4
-- 人工智能推理、完整度、风险评分、更新优先级、报告、空间和审计表。

CREATE TABLE ai.model_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_code VARCHAR(128) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    model_type VARCHAR(64) NOT NULL,
    framework VARCHAR(64),
    source_type VARCHAR(32) NOT NULL DEFAULT 'PLACEHOLDER',
    license_name VARCHAR(128),
    artifact_asset_id UUID REFERENCES asset.file_asset(id),
    input_spec JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_spec JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE ai.inference_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_code VARCHAR(64) NOT NULL,
    inspection_record_id UUID REFERENCES core.inspection_record(id),
    building_id UUID REFERENCES core.building(id),
    input_asset_id UUID NOT NULL REFERENCES asset.file_asset(id),
    model_id UUID NOT NULL REFERENCES ai.model_registry(id),
    inference_mode VARCHAR(32) NOT NULL DEFAULT 'MOCK'
        CHECK (inference_mode IN ('MOCK', 'REAL')),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED', 'RESOURCE_READY', 'SUBMITTED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    progress_current INTEGER NOT NULL DEFAULT 0 CHECK (progress_current >= 0),
    progress_total INTEGER NOT NULL DEFAULT 1 CHECK (progress_total > 0),
    idempotency_key VARCHAR(128) NOT NULL,
    request_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_snapshot JSONB,
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    max_retry_count INTEGER NOT NULL DEFAULT 3 CHECK (max_retry_count >= 0),
    error_code VARCHAR(64),
    error_message TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai.defect_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inference_job_id UUID NOT NULL REFERENCES ai.inference_job(id),
    building_id UUID NOT NULL REFERENCES core.building(id),
    source_asset_id UUID NOT NULL REFERENCES asset.file_asset(id),
    annotated_asset_id UUID REFERENCES asset.file_asset(id),
    defect_type VARCHAR(64) NOT NULL,
    confidence NUMERIC(6, 5) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    severity VARCHAR(32) NOT NULL,
    bbox JSONB,
    segmentation JSONB,
    measurement JSONB NOT NULL DEFAULT '{}'::jsonb,
    description TEXT,
    raw_output JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_code VARCHAR(128) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai.defect_review (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    defect_result_id UUID NOT NULL REFERENCES ai.defect_result(id),
    review_action VARCHAR(32) NOT NULL
        CHECK (review_action IN ('CONFIRMED', 'MODIFIED', 'REJECTED')),
    corrected_defect_type VARCHAR(64),
    corrected_severity VARCHAR(32),
    review_comment TEXT,
    reviewed_by UUID NOT NULL REFERENCES core.user_account(id),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    review_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE ai.embedding (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    model_id UUID NOT NULL REFERENCES ai.model_registry(id),
    embedding vector,
    embedding_dimension INTEGER NOT NULL CHECK (embedding_dimension > 0),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN ai.embedding.embedding IS
    '模型固定后应改为固定维度 vector(n)，再建立 HNSW 或 IVFFlat 索引';

CREATE TABLE core.rule_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_type VARCHAR(32) NOT NULL
        CHECK (rule_type IN ('COMPLETENESS', 'RISK', 'RENEWAL')),
    version_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_content JSONB NOT NULL,
    checksum CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    activated_at TIMESTAMPTZ,
    created_by UUID REFERENCES core.user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE core.completeness_assessment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES core.building(id),
    assessment_version VARCHAR(64) NOT NULL,
    rule_version_id UUID NOT NULL REFERENCES core.rule_version(id),
    completeness_score NUMERIC(5, 2) NOT NULL CHECK (completeness_score BETWEEN 0 AND 100),
    completeness_level VARCHAR(32) NOT NULL,
    available_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    suggestions JSONB NOT NULL DEFAULT '[]'::jsonb,
    input_snapshot JSONB NOT NULL,
    input_checksum CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CURRENT'
        CHECK (status IN ('CURRENT', 'STALE', 'SUPERSEDED')),
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE core.risk_assessment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_code VARCHAR(64) NOT NULL,
    building_id UUID NOT NULL REFERENCES core.building(id),
    assessment_version VARCHAR(64) NOT NULL,
    rule_version_id UUID NOT NULL REFERENCES core.rule_version(id),
    completeness_assessment_id UUID REFERENCES core.completeness_assessment(id),
    safety_score NUMERIC(5, 2) NOT NULL CHECK (safety_score BETWEEN 0 AND 100),
    confidence_score NUMERIC(5, 2) NOT NULL CHECK (confidence_score BETWEEN 0 AND 100),
    risk_level VARCHAR(32) NOT NULL,
    dimension_scores JSONB NOT NULL DEFAULT '{}'::jsonb,
    score_explanation JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_snapshot JSONB NOT NULL,
    input_checksum CHAR(64) NOT NULL,
    recommendation TEXT,
    need_manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    need_professional_inspection BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'CURRENT'
        CHECK (status IN ('CURRENT', 'STALE', 'SUPERSEDED', 'CONFIRMED')),
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE core.renewal_priority (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES core.building(id),
    risk_assessment_id UUID NOT NULL REFERENCES core.risk_assessment(id),
    rule_version_id UUID NOT NULL REFERENCES core.rule_version(id),
    priority_version VARCHAR(64) NOT NULL,
    priority_score NUMERIC(5, 2) NOT NULL CHECK (priority_score BETWEEN 0 AND 100),
    priority_level VARCHAR(32) NOT NULL,
    ranking INTEGER CHECK (ranking IS NULL OR ranking > 0),
    ranking_scope JSONB NOT NULL DEFAULT '{}'::jsonb,
    factor_details JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_snapshot JSONB NOT NULL,
    input_checksum CHAR(64) NOT NULL,
    recommendation TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'CURRENT'
        CHECK (status IN ('CURRENT', 'STALE', 'SUPERSEDED')),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE asset.generated_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_code VARCHAR(64) NOT NULL,
    report_type VARCHAR(64) NOT NULL,
    community_id UUID REFERENCES core.community(id),
    building_id UUID REFERENCES core.building(id),
    risk_assessment_id UUID REFERENCES core.risk_assessment(id),
    renewal_priority_id UUID REFERENCES core.renewal_priority(id),
    file_asset_id UUID REFERENCES asset.file_asset(id),
    report_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (report_status IN ('DRAFT', 'GENERATING', 'GENERATED', 'FAILED', 'STALE')),
    report_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    data_version VARCHAR(64),
    model_version VARCHAR(64),
    risk_rule_version VARCHAR(64),
    renewal_rule_version VARCHAR(64),
    generated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE geo.community_boundary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES core.community(id),
    boundary geometry(MultiPolygon, 4326) NOT NULL,
    centroid geometry(Point, 4326),
    coordinate_reference_system VARCHAR(64) NOT NULL DEFAULT 'EPSG:4326',
    source VARCHAR(128) NOT NULL,
    quality_score NUMERIC(5, 2) CHECK (quality_score BETWEEN 0 AND 100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE geo.building_geometry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES core.building(id),
    footprint geometry(MultiPolygon, 4326),
    centroid geometry(Point, 4326) NOT NULL,
    coordinate_reference_system VARCHAR(64) NOT NULL DEFAULT 'EPSG:4326',
    coordinate_source VARCHAR(128) NOT NULL,
    spatial_quality_score NUMERIC(5, 2)
        CHECK (spatial_quality_score BETWEEN 0 AND 100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE geo.hazard_zone (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hazard_code VARCHAR(64) NOT NULL,
    hazard_name VARCHAR(255) NOT NULL,
    hazard_type VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32),
    geom geometry(MultiPolygon, 4326) NOT NULL,
    coordinate_reference_system VARCHAR(64) NOT NULL DEFAULT 'EPSG:4326',
    source VARCHAR(128) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE geo.spatial_metric (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES core.building(id),
    metric_code VARCHAR(128) NOT NULL,
    metric_value NUMERIC(18, 6),
    metric_text VARCHAR(255),
    metric_unit VARCHAR(32),
    calculation_version VARCHAR(64) NOT NULL,
    context JSONB NOT NULL DEFAULT '{}'::jsonb,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ
);

CREATE TABLE audit.operation_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES core.user_account(id),
    operation_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id UUID,
    request_id VARCHAR(128),
    client_ip INET,
    operation_detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    success BOOLEAN NOT NULL,
    error_code VARCHAR(64),
    error_message TEXT,
    operated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE integration.outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version BIGINT NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    next_retry_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (aggregate_type, aggregate_id, event_type, event_version)
);
