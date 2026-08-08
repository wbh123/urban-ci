package org.urbansafe.priority.map.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.ArchiveLocationService;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

/** 楼栋地图中心点查询与保存接口。 */
@RestController
@RequestMapping("/api/v1/buildings")
public class ArchiveLocationController {

    private final ArchiveLocationService service;

    public ArchiveLocationController(ArchiveLocationService service) {
        this.service = service;
    }

    @GetMapping("/{buildingId}/location")
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<Map<String, Object>> getBuildingLocation(@PathVariable UUID buildingId) {
        return ResponseEntity.ok(
                Phase2ResponseFactory.success(service.getBuildingLocation(buildingId)));
    }

    @PutMapping("/{buildingId}/location")
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<Map<String, Object>> saveBuildingLocation(
            @PathVariable UUID buildingId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(
                Phase2ResponseFactory.success(service.saveBuildingLocation(buildingId, request)));
    }
}
