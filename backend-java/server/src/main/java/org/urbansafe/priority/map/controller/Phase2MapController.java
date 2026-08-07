package org.urbansafe.priority.map.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.urbansafe.priority.map.service.Phase2MapService;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class Phase2MapController {
    private final Phase2MapService service;
    public Phase2MapController(Phase2MapService service) { this.service = service; }

    @GetMapping("/map/runtime-config")
    public ResponseEntity<Map<String,Object>> config() {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.runtimeConfig()));
    }
    @PostMapping("/map/geocoding/preview")
    public ResponseEntity<Map<String,Object>> geocode(@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.geocode(
                body.get("address") == null ? null : String.valueOf(body.get("address")),
                body.get("city") == null ? null : String.valueOf(body.get("city")))));
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
}
