package org.urbansafe.priority.assessment.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.assessment.rule.RuleVersionService;
import org.urbansafe.priority.assessment.security.AssessmentAccessService;
import org.urbansafe.priority.assessment.service.AssessmentApplicationService;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.AssessmentApi;
import org.urbansafe.priority.model.dto.AssessmentHistoryPageResponse;
import org.urbansafe.priority.model.dto.AssessmentHistoryPageSuccessResponse;
import org.urbansafe.priority.model.dto.AssessmentRuleActivationResponse;
import org.urbansafe.priority.model.dto.AssessmentRuleActivationSuccessResponse;
import org.urbansafe.priority.model.dto.AssessmentRuleListResponse;
import org.urbansafe.priority.model.dto.AssessmentRuleListSuccessResponse;
import org.urbansafe.priority.model.dto.AssessmentRuleResponse;
import org.urbansafe.priority.model.dto.AssessmentRuleSuccessResponse;
import org.urbansafe.priority.model.dto.BatchAssessmentRequest;
import org.urbansafe.priority.model.dto.BatchAssessmentResultResponse;
import org.urbansafe.priority.model.dto.BatchAssessmentSuccessResponse;
import org.urbansafe.priority.model.dto.BuildingAssessmentCalculationRequest;
import org.urbansafe.priority.model.dto.BuildingAssessmentCalculationResponse;
import org.urbansafe.priority.model.dto.BuildingAssessmentCalculationSuccessResponse;
import org.urbansafe.priority.model.dto.BuildingAssessmentSummaryResponse;
import org.urbansafe.priority.model.dto.BuildingAssessmentSummarySuccessResponse;
import org.urbansafe.priority.model.dto.BuildingCurrentAssessmentResponse;
import org.urbansafe.priority.model.dto.BuildingCurrentAssessmentSuccessResponse;
import org.urbansafe.priority.model.dto.CreateAssessmentRuleRequest;
import org.urbansafe.priority.model.dto.RenewalPriorityPageResponse;
import org.urbansafe.priority.model.dto.RenewalPriorityPageSuccessResponse;

/** 第四阶段生成接口实现。所有权限同时在后端方法层执行。 */
@RestController
public class AssessmentController implements AssessmentApi {

    private final RuleVersionService ruleService;
    private final AssessmentApplicationService assessmentService;
    private final AssessmentAccessService accessService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper conversionMapper;

    public AssessmentController(
            RuleVersionService ruleService,
            AssessmentApplicationService assessmentService,
            AssessmentAccessService accessService,
            ObjectMapper objectMapper) {
        this.ruleService = ruleService;
        this.assessmentService = assessmentService;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
        this.conversionMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    @PreAuthorize(AssessmentAccessService.FULL_READ_ROLES)
    public ResponseEntity<AssessmentRuleListSuccessResponse> listAssessmentRules(
            String ruleType, String status) {
        AssessmentRuleListResponse data = new AssessmentRuleListResponse();
        data.setContent(ruleService.list(ruleType, status).stream()
                .map(row -> convert(row, AssessmentRuleResponse.class))
                .toList());
        return ResponseEntity.ok(wrapRuleList(data));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.ADMIN_ONLY)
    public ResponseEntity<AssessmentRuleSuccessResponse> createAssessmentRule(
            CreateAssessmentRuleRequest request) {
        Map<String, Object> row = ruleService.createDraft(
                String.valueOf(request.getRuleType()),
                request.getVersionCode(),
                request.getRuleName(),
                objectMapper.valueToTree(request.getRuleContent()),
                CurrentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wrapRule(convert(row, AssessmentRuleResponse.class)));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.FULL_READ_ROLES)
    public ResponseEntity<AssessmentRuleSuccessResponse> getAssessmentRule(UUID ruleId) {
        return ResponseEntity.ok(wrapRule(
                convert(ruleService.get(ruleId), AssessmentRuleResponse.class)));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.ADMIN_ONLY)
    public ResponseEntity<AssessmentRuleActivationSuccessResponse> activateAssessmentRule(UUID ruleId) {
        AssessmentRuleActivationResponse data = convert(
                ruleService.activate(ruleId), AssessmentRuleActivationResponse.class);
        return ResponseEntity.ok(wrapActivation(data));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.CALCULATE_ROLES)
    public ResponseEntity<BuildingAssessmentCalculationSuccessResponse> calculateBuildingAssessment(
            UUID buildingId, BuildingAssessmentCalculationRequest request) {
        accessService.assertCanCalculate(buildingId);
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());
        Set<String> scopes = request == null ? Set.of() : stringSet(request.getRankingScopes());
        Map<String, Object> result = assessmentService.calculate(
                buildingId, force, scopes, "MANUAL", CurrentUser.getUserId());
        return ResponseEntity.ok(wrapCalculation(
                convert(result, BuildingAssessmentCalculationResponse.class)));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.FULL_READ_ROLES)
    public ResponseEntity<BuildingCurrentAssessmentSuccessResponse> getCurrentBuildingAssessment(
            UUID buildingId) {
        accessService.assertCanReadFull(buildingId);
        BuildingCurrentAssessmentResponse data = convert(
                assessmentService.current(buildingId), BuildingCurrentAssessmentResponse.class);
        return ResponseEntity.ok(wrapCurrent(data));
    }


    @Override
    @PreAuthorize(AssessmentAccessService.SUMMARY_READ_ROLES)
    public ResponseEntity<BuildingAssessmentSummarySuccessResponse> getBuildingAssessmentSummary(
            UUID buildingId) {
        accessService.assertCanReadSummary(buildingId);
        BuildingAssessmentSummaryResponse data = convert(
                assessmentService.summary(buildingId), BuildingAssessmentSummaryResponse.class);
        return ResponseEntity.ok(wrapSummary(data));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.FULL_READ_ROLES)
    public ResponseEntity<AssessmentHistoryPageSuccessResponse> getBuildingAssessmentHistory(
            UUID buildingId, String assessmentType, Integer page, Integer size) {
        accessService.assertCanReadFull(buildingId);
        AssessmentHistoryPageResponse data = convert(
                assessmentService.history(buildingId, assessmentType,
                        page == null ? 0 : page, size == null ? 20 : size),
                AssessmentHistoryPageResponse.class);
        return ResponseEntity.ok(wrapHistory(data));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.BATCH_AND_RANKING_ROLES)
    public ResponseEntity<BatchAssessmentSuccessResponse> recalculateAssessments(
            BatchAssessmentRequest request) {
        Map<String, Object> result = assessmentService.batch(
                String.valueOf(request.getScopeType()), request.getScopeId(),
                Boolean.TRUE.equals(request.getForce()),
                request.getMaxBuildings() == null ? 100 : request.getMaxBuildings(),
                CurrentUser.getUserId());
        return ResponseEntity.ok(wrapBatch(
                convert(result, BatchAssessmentResultResponse.class)));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.BATCH_AND_RANKING_ROLES)
    public ResponseEntity<RenewalPriorityPageSuccessResponse> listRenewalPriorities(
            String scopeType, String scopeId, String priorityLevel,
            String riskLevel, Integer page, Integer size) {
        RenewalPriorityPageResponse data = convert(
                assessmentService.ranking(scopeType, scopeId, priorityLevel, riskLevel,
                        page == null ? 0 : page, size == null ? 20 : size),
                RenewalPriorityPageResponse.class);
        return ResponseEntity.ok(wrapRanking(data));
    }

    private Set<String> stringSet(Iterable<?> values) {
        if (values == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(String.valueOf(value)));
        return result;
    }

    private <T> T convert(Object value, Class<T> type) {
        return conversionMapper.convertValue(value, type);
    }

    private AssessmentRuleSuccessResponse wrapRule(AssessmentRuleResponse data) {
        return wrap(data, AssessmentRuleSuccessResponse.class);
    }

    private AssessmentRuleListSuccessResponse wrapRuleList(AssessmentRuleListResponse data) {
        return wrap(data, AssessmentRuleListSuccessResponse.class);
    }

    private AssessmentRuleActivationSuccessResponse wrapActivation(AssessmentRuleActivationResponse data) {
        return wrap(data, AssessmentRuleActivationSuccessResponse.class);
    }

    private BuildingAssessmentCalculationSuccessResponse wrapCalculation(
            BuildingAssessmentCalculationResponse data) {
        return wrap(data, BuildingAssessmentCalculationSuccessResponse.class);
    }

    private BuildingCurrentAssessmentSuccessResponse wrapCurrent(BuildingCurrentAssessmentResponse data) {
        return wrap(data, BuildingCurrentAssessmentSuccessResponse.class);
    }


    private BuildingAssessmentSummarySuccessResponse wrapSummary(BuildingAssessmentSummaryResponse data) {
        return wrap(data, BuildingAssessmentSummarySuccessResponse.class);
    }

    private AssessmentHistoryPageSuccessResponse wrapHistory(AssessmentHistoryPageResponse data) {
        return wrap(data, AssessmentHistoryPageSuccessResponse.class);
    }

    private BatchAssessmentSuccessResponse wrapBatch(BatchAssessmentResultResponse data) {
        return wrap(data, BatchAssessmentSuccessResponse.class);
    }

    private RenewalPriorityPageSuccessResponse wrapRanking(RenewalPriorityPageResponse data) {
        return wrap(data, RenewalPriorityPageSuccessResponse.class);
    }

    private <T> T wrap(Object data, Class<T> type) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("success", metadata.success());
        envelope.put("data", data);
        envelope.put("error", null);
        envelope.put("requestId", metadata.requestId());
        envelope.put("timestamp", metadata.timestamp());
        return objectMapper.convertValue(envelope, type);
    }
}
