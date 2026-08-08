package org.urbansafe.priority.map.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.urbansafe.priority.map.service.CommunityBoundaryCandidateService;
import org.urbansafe.priority.map.service.Phase2MapService;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class Phase2MapController {
    private final Phase2MapService service;
    private final CommunityBoundaryCandidateService boundaryCandidates;

    public Phase2MapController(
            Phase2MapService service,
            CommunityBoundaryCandidateService boundaryCandidates
    ) {
        this.service = service;
        this.boundaryCandidates = boundaryCandidates;
    }

    @GetMapping("/map/runtime-config")
    public ResponseEntity<Map<String,Object>> config() {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.runtimeConfig()));
    }

    @PostMapping("/map/geocoding/preview")
    public ResponseEntity<Map<String,Object>> geocode(@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.geocode(
                stringValue(body.get("address")),
                stringValue(body.get("city")))));
    }

    @PostMapping("/map/boundary-candidates/community")
    public ResponseEntity<Map<String,Object>> previewCommunityBoundaryCandidate(
            @RequestBody Map<String,Object> body
    ) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(boundaryCandidates.preview(
                stringValue(body.get("communityName")),
                stringValue(body.get("address")),
                stringValue(body.get("city")))));
    }

    @GetMapping("/map/communities")
    public ResponseEntity<Map<String,Object>> communities() {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.communityPoints()));
    }

    @GetMapping("/communities/{communityId}/location")
    public ResponseEntity<Map<String,Object>> location(@PathVariable UUID communityId) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.getLocation(communityId)));
    }

    @PutMapping("/communities/{communityId}/location")
    public ResponseEntity<Map<String,Object>> save(@PathVariable UUID communityId,
            @RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.saveLocation(communityId, body)));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
