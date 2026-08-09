package org.urbansafe.priority.map.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.CommunityBoundaryCandidateService;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

/** 小区候选边界预览入口。候选结果仅供人工确认，保存仍走版本化空间边界写接口。 */
@Controller
@ResponseBody
@RequestMapping("/api/v1/map/boundary-candidates/community")
public class CommunityBoundaryCandidateController {

    private final CommunityBoundaryCandidateService service;

    public CommunityBoundaryCandidateController(CommunityBoundaryCandidateService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.preview(
                stringValue(body.get("communityName")),
                stringValue(body.get("address")),
                stringValue(body.get("city")))));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
