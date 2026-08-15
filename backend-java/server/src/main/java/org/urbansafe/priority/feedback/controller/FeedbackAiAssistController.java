package org.urbansafe.priority.feedback.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.feedback.service.FeedbackAiAssistService;

/** 管理端公众反馈 AI 初步归类，只读辅助，不修改反馈业务状态。 */
@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class FeedbackAiAssistController {

    private final FeedbackAiAssistService service;

    public FeedbackAiAssistController(FeedbackAiAssistService service) {
        this.service = service;
    }

    @PostMapping("/feedback/reports/{reportId}/ai-assist")
    @PreAuthorize("hasAnyRole('COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> assist(@PathVariable UUID reportId) {
        return ResponseEntity.ok(success(service.analyze(
                reportId,
                CurrentUser.getUserId(),
                CurrentUser.getUsername())));
    }

    private static Map<String, Object> success(Object data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", metadata.success());
        body.put("data", data);
        body.put("error", null);
        body.put("requestId", metadata.requestId());
        body.put("timestamp", metadata.timestamp());
        return body;
    }
}
