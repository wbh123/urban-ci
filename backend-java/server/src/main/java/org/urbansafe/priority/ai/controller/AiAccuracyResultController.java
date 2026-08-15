package org.urbansafe.priority.ai.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.ai.service.AiRichDetectionDetailService;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;

/** 高精度异步推理完成后的富检测结果读取接口。 */
@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class AiAccuracyResultController {

    private final AiInferenceService inferenceService;
    private final AiRichDetectionDetailService richDetectionDetailService;

    public AiAccuracyResultController(
            AiInferenceService inferenceService,
            AiRichDetectionDetailService richDetectionDetailService) {
        this.inferenceService = inferenceService;
        this.richDetectionDetailService = richDetectionDetailService;
    }

    @GetMapping("/ai-inferences/{inferenceId}/rich-result")
    public ResponseEntity<Map<String, Object>> getRichResult(@PathVariable UUID inferenceId) {
        Map<String, Object> detail = inferenceService.getDetail(inferenceId);
        return ResponseEntity.ok(success(richDetectionDetailService.enrich(inferenceId, detail)));
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
