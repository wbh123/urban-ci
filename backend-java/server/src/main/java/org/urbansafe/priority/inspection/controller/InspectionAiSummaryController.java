package org.urbansafe.priority.inspection.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.inspection.service.InspectionAiSummaryService;
import org.urbansafe.priority.model.api.InspectionAiApi;
import org.urbansafe.priority.model.dto.InspectionAiSummaryRequest;
import org.urbansafe.priority.model.dto.InspectionAiSummarySuccessResponse;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

/** 巡检员文字记录与既有 AI 视觉结果的只读综合总结。 */
@RestController
public class InspectionAiSummaryController implements InspectionAiApi {

    static final String SUMMARY_ROLES = "hasAnyRole('ADMIN', 'COMMUNITY_MANAGER')";

    private final InspectionAiSummaryService service;
    private final ObjectMapper objectMapper;

    public InspectionAiSummaryController(
            InspectionAiSummaryService service,
            ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    @PreAuthorize(SUMMARY_ROLES)
    public ResponseEntity<InspectionAiSummarySuccessResponse> summarizeInspectionWithAi(
            UUID taskId,
            InspectionAiSummaryRequest request) {
        UUID inferenceId = request == null ? null : request.getInferenceId();
        if (inferenceId == null) {
            throw new InvalidRequestException(
                    "INSPECTION_AI_INFERENCE_REQUIRED", "请选择已完成的 AI 视觉识别结果");
        }
        return ResponseEntity.ok(objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(
                        Phase2ResponseFactory.success(service.summarize(
                                taskId,
                                inferenceId,
                                CurrentUser.getUserId(),
                                CurrentUser.getUsername())),
                        InspectionAiSummarySuccessResponse.class));
    }
}
