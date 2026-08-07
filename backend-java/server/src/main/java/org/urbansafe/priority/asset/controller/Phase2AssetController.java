package org.urbansafe.priority.asset.controller;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.urbansafe.priority.ai.automation.AiUploadAutomationResult;
import org.urbansafe.priority.ai.automation.AiUploadAutomationService;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

@Controller
@ResponseBody
@RequestMapping("/api/v1/assets")
public class Phase2AssetController {
    private final Phase2AssetService service;
    private final AiUploadAutomationService automationService;

    public Phase2AssetController(
            Phase2AssetService service,
            AiUploadAutomationService automationService) {
        this.service = service;
        this.automationService = automationService;
    }

    @PostMapping(path="/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String,Object>> upload(@RequestParam MultipartFile file,
            @RequestParam String businessType,@RequestParam UUID businessId,
            @RequestParam(required=false) String bindingRole) {
        Map<String, Object> uploaded = new LinkedHashMap<>(
                service.upload(file,businessType,businessId,bindingRole));
        UUID assetId = (UUID) uploaded.get("assetId");
        AiUploadAutomationResult autoInference = automationService.triggerIfEnabled(
                assetId, businessType, CurrentUser.getUserId());
        uploaded.put("autoInference", autoInference);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                Phase2ResponseFactory.success(uploaded));
    }

    @GetMapping({"", "/images"})
    public ResponseEntity<Map<String,Object>> list(@RequestParam String businessType,
            @RequestParam UUID businessId) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.list(businessType,businessId)));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Void> preview(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(service.previewUrl(id))).build();
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        Map<String,Object> asset = service.get(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(String.valueOf(asset.get("contentType"))))
                .body(service.content(id));
    }
}
