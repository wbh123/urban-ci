package org.urbansafe.priority.map.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.MapDiscoveryService;
import org.urbansafe.priority.map.service.Phase2MapService;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

/** 第二阶段旧地图兼容入口。新增建档发现接口由 ArchiveMapController 承担。 */
@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class Phase2MapController {
    private final Phase2MapService service;
    private final MapDiscoveryService discovery;

    public Phase2MapController(Phase2MapService service, MapDiscoveryService discovery) {
        this.service = service;
        this.discovery = discovery;
    }

    @GetMapping("/map/runtime-config")
    public ResponseEntity<Map<String,Object>> config() {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.runtimeConfig()));
    }

    @PostMapping("/map/geocoding/preview")
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<Map<String,Object>> geocode(@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(discovery.geocode(
                stringValue(body.get("address")), stringValue(body.get("city")))));
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
