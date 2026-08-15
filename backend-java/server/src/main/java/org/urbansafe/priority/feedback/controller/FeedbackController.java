package org.urbansafe.priority.feedback.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.feedback.service.FeedbackClosureService;
import org.urbansafe.priority.feedback.service.FeedbackManagementQueryService;
import org.urbansafe.priority.feedback.service.FeedbackService;

@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class FeedbackController {
    private static final String MANAGER_ROLES =
            "hasAnyRole('COMMUNITY_MANAGER','GOVERNMENT_MANAGER','DISPOSAL_OPERATOR','ADMIN')";
    private final FeedbackService service;
    private final FeedbackManagementQueryService managementQueryService;
    private final FeedbackClosureService closureService;

    public FeedbackController(FeedbackService service,
            FeedbackManagementQueryService managementQueryService,
            FeedbackClosureService closureService) {
        this.service = service;
        this.managementQueryService = managementQueryService;
        this.closureService = closureService;
    }

    @GetMapping("/public/feedback/communities")
    public ResponseEntity<Map<String, Object>> publicCommunities() {
        return ResponseEntity.ok(success(service.listPublicCommunities()));
    }
    @GetMapping("/public/feedback/communities/{communityId}/buildings")
    public ResponseEntity<Map<String, Object>> publicBuildings(@PathVariable UUID communityId) {
        return ResponseEntity.ok(success(service.listPublicBuildings(communityId)));
    }
    @PostMapping("/public/feedback/reports")
    public ResponseEntity<Map<String, Object>> createPublic(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(success(service.createPublic(body)));
    }
    @PostMapping(path="/public/feedback/reports/{reportCode}/images", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPublicImage(@PathVariable String reportCode,
            @RequestParam String trackingSecret, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(service.uploadPublicImage(reportCode, trackingSecret, file)));
    }
    @GetMapping("/public/feedback/reports/{reportCode}/images")
    public ResponseEntity<Map<String, Object>> listPublicImages(@PathVariable String reportCode,
            @RequestParam String trackingSecret) {
        return ResponseEntity.ok(success(service.listPublicImages(reportCode, trackingSecret)));
    }
    @GetMapping("/public/feedback/reports/{reportCode}/images/{assetId}/content")
    public ResponseEntity<byte[]> publicImageContent(@PathVariable String reportCode,
            @PathVariable UUID assetId, @RequestParam String trackingSecret) {
        FeedbackService.PublicImageContent image = service.publicImageContent(reportCode, trackingSecret, assetId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }
    @GetMapping("/public/feedback/reports/{reportCode}")
    public ResponseEntity<Map<String, Object>> track(@PathVariable String reportCode,
            @RequestParam String trackingSecret) {
        return ResponseEntity.ok(success(service.track(reportCode, trackingSecret)));
    }

    @GetMapping("/feedback/reports")
    @PreAuthorize(MANAGER_ROLES)
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required=false) String status,
            @RequestParam(required=false) String feedbackChannel,
            @RequestParam(required=false) UUID communityId,
            @RequestParam(required=false) UUID buildingId,
            @RequestParam(required=false) String keyword,
            @RequestParam(required=false) String submittedFrom,
            @RequestParam(required=false) String submittedTo,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(success(managementQueryService.list(status, feedbackChannel,
                communityId, buildingId, keyword, submittedFrom, submittedTo, page, size)));
    }
    @GetMapping("/feedback/reports/{reportId}/images")
    @PreAuthorize(MANAGER_ROLES)
    public ResponseEntity<Map<String, Object>> listManagementImages(@PathVariable UUID reportId) {
        return ResponseEntity.ok(success(service.listManagementImages(reportId)));
    }
    @PostMapping("/feedback/reports/manual")
    @PreAuthorize(MANAGER_ROLES)
    public ResponseEntity<Map<String, Object>> createManual(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(service.createManual(body, CurrentUser.getUserId())));
    }
    @PostMapping("/feedback/reports/{reportId}/status")
    @PreAuthorize(MANAGER_ROLES)
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable UUID reportId,
            @RequestBody Map<String, Object> body) {
        String requestedStatus = text(body.get("status"));
        if ("RESOLVED".equalsIgnoreCase(requestedStatus)) {
            throw new InvalidRequestException("FEEDBACK_RECTIFICATION_ENDPOINT_REQUIRED",
                    "整改完成必须通过整改提交入口并校验整改证据");
        }
        if ("CLOSED".equalsIgnoreCase(requestedStatus)) {
            throw new InvalidRequestException("FEEDBACK_REINSPECTION_ENDPOINT_REQUIRED",
                    "工单关闭必须通过复查复验结论入口");
        }
        return ResponseEntity.ok(success(service.updateStatus(reportId, body, CurrentUser.getUserId())));
    }
    @PostMapping("/feedback/reports/{reportId}/rectification/submit")
    @PreAuthorize(MANAGER_ROLES)
    public ResponseEntity<Map<String, Object>> submitRectification(@PathVariable UUID reportId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(success(closureService.submitRectification(reportId,
                text(body.get("handlingSummary")), text(body.get("message")), CurrentUser.getUserId())));
    }
    @PostMapping("/feedback/reports/{reportId}/reinspection")
    @PreAuthorize(MANAGER_ROLES)
    public ResponseEntity<Map<String, Object>> createReinspection(@PathVariable UUID reportId) {
        return ResponseEntity.ok(success(closureService.createReinspection(reportId, CurrentUser.getUserId())));
    }
    @GetMapping("/feedback/reports/{reportId}/reinspection")
    @PreAuthorize(MANAGER_ROLES)
    public ResponseEntity<Map<String, Object>> latestReinspection(@PathVariable UUID reportId) {
        return ResponseEntity.ok(success(closureService.latestReinspection(reportId)));
    }
    @PostMapping("/feedback/reports/{reportId}/reinspection/result")
    @PreAuthorize(MANAGER_ROLES)
    public ResponseEntity<Map<String, Object>> completeReinspection(@PathVariable UUID reportId,
            @RequestBody Map<String, Object> body) {
        Object passedValue = body.get("passed");
        if (!(passedValue instanceof Boolean passed)) {
            throw new InvalidRequestException("FEEDBACK_REINSPECTION_RESULT_REQUIRED", "passed 必须为布尔值");
        }
        return ResponseEntity.ok(success(closureService.completeReinspection(reportId, passed,
                text(body.get("summary")), CurrentUser.getUserId())));
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static Map<String, Object> success(Object data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", metadata.success()); body.put("data", data); body.put("error", null);
        body.put("requestId", metadata.requestId()); body.put("timestamp", metadata.timestamp());
        return body;
    }
}
