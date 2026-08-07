package org.urbansafe.priority.ai.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.urbansafe.priority.ai.service.AiModelCatalogService;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;

/** 前端模型选择入口。前端不得直接访问 FastAPI。 */
@Controller
@ResponseBody
@RequestMapping("/api/v1/ai-models")
public class AiModelCatalogController {

    private final AiModelCatalogService service;

    public AiModelCatalogController(AiModelCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", metadata.success());
        body.put("data", service.list());
        body.put("error", null);
        body.put("requestId", metadata.requestId());
        body.put("timestamp", metadata.timestamp());
        return ResponseEntity.ok(body);
    }
}
