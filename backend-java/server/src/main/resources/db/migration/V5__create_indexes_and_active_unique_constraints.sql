-- UrbanSafe Priority Flyway migration V5
-- 逻辑删除对象使用部分唯一索引，仅约束 deleted_at IS NULL 的有效记录。

CREATE UNIQUE INDEX uk_user_account_username_active
    ON core.user_account (username)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_role_code_active
    ON core.role (role_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_user_role_active
    ON core.user_role (user_id, role_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_community_code_active
    ON core.community (community_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_building_community_code_active
    ON core.building (community_id, building_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_inspection_task_code_active
    ON core.inspection_task (task_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_asset_object_active
    ON asset.file_asset (bucket_name, object_key)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_asset_binding_active
    ON asset.asset_binding (asset_id, business_type, business_id, binding_role)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_resident_report_code_active
    ON core.resident_report (report_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_model_registry_active
    ON ai.model_registry (model_code, model_version)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_rule_version_active
    ON core.rule_version (rule_type, version_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_generated_report_code_active
    ON asset.generated_report (report_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_community_boundary_active
    ON geo.community_boundary (community_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_building_geometry_active
    ON geo.building_geometry (building_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_hazard_zone_code_active
    ON geo.hazard_zone (hazard_code)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_inference_job_code
    ON ai.inference_job (job_code);

CREATE UNIQUE INDEX uk_inference_idempotency_key
    ON ai.inference_job (idempotency_key);

CREATE UNIQUE INDEX uk_embedding_entity_model
    ON ai.embedding (entity_type, entity_id, model_id);

CREATE UNIQUE INDEX uk_completeness_building_version
    ON core.completeness_assessment (building_id, assessment_version);

CREATE UNIQUE INDEX uk_risk_assessment_code
    ON core.risk_assessment (assessment_code);

CREATE UNIQUE INDEX uk_risk_building_version
    ON core.risk_assessment (building_id, assessment_version);

CREATE UNIQUE INDEX uk_renewal_building_version
    ON core.renewal_priority (building_id, priority_version);

CREATE UNIQUE INDEX uk_spatial_metric_version
    ON geo.spatial_metric (building_id, metric_code, calculation_version);

CREATE INDEX idx_building_community_active
    ON core.building (community_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_inspection_task_building_status_active
    ON core.inspection_task (building_id, status, planned_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_inspection_record_building_time_active
    ON core.inspection_record (building_id, inspected_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_building_evidence_building_type_active
    ON core.building_evidence (building_id, evidence_type)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_asset_sha256_active
    ON asset.file_asset (sha256)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_inference_status
    ON ai.inference_job (status, requested_at);

CREATE INDEX idx_defect_building_type
    ON ai.defect_result (building_id, defect_type);

CREATE INDEX idx_completeness_building_time
    ON core.completeness_assessment (building_id, assessed_at DESC);

CREATE INDEX idx_risk_building_time
    ON core.risk_assessment (building_id, assessed_at DESC);

CREATE INDEX idx_renewal_score
    ON core.renewal_priority (priority_score DESC, generated_at DESC);

CREATE INDEX idx_report_building_status_active
    ON asset.generated_report (building_id, report_status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_operation_log_resource_time
    ON audit.operation_log (resource_type, resource_id, operated_at DESC);

CREATE INDEX idx_operation_log_request
    ON audit.operation_log (request_id);

CREATE INDEX idx_outbox_status
    ON integration.outbox_event (status, next_retry_at, created_at);

CREATE INDEX idx_community_boundary_gist
    ON geo.community_boundary USING GIST (boundary)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_building_geometry_footprint_gist
    ON geo.building_geometry USING GIST (footprint)
    WHERE deleted_at IS NULL AND footprint IS NOT NULL;

CREATE INDEX idx_building_geometry_centroid_gist
    ON geo.building_geometry USING GIST (centroid)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_hazard_zone_gist
    ON geo.hazard_zone USING GIST (geom)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_building_extra_gin
    ON core.building USING GIN (extra_attributes)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_defect_raw_gin
    ON ai.defect_result USING GIN (raw_output);

CREATE INDEX idx_risk_scores_gin
    ON core.risk_assessment USING GIN (dimension_scores);
