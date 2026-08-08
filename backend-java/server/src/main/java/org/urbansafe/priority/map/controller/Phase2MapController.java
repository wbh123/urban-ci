package org.urbansafe.priority.map.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.MapDiscoveryService;
import org.urbansafe.priority.map.service.Phase2MapService;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

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

    @PostMapping("/map/places/search")
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<Map<String,Object>> searchPlaces(@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(discovery.searchPlaces(
                stringValue(body.get("keyword")),
                stringValue(body.get("region")),
                booleanValue(body.get("cityLimit"), false, "cityLimit"),
                intValue(body.get("pageSize"), 8, "pageSize"))));
    }

    @PostMapping("/map/reverse-geocoding/preview")
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<Map<String,Object>> reverseGeocode(@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(discovery.reverseGeocode(
                doubleValue(body.get("longitude"), "longitude"),
                doubleValue(body.get("latitude"), "latitude"))));
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

    private boolean booleanValue(Object value, boolean defaultValue, String field) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new InvalidRequestException("MAP_FIELD_INVALID", field + " 必须为布尔值");
    }

    private int intValue(Object value, int defaultValue, String field) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value).trim());
        } catch (RuntimeException ex) {
            throw new InvalidRequestException("MAP_FIELD_INVALID", field + " 必须为整数");
        }
    }

    private double doubleValue(Object value, String field) {
        if (value == null) {
            throw new InvalidRequestException("MAP_FIELD_INVALID", field + " 不能为空");
        }
        try {
            return value instanceof Number number
                    ? number.doubleValue()
                    : Double.parseDouble(String.valueOf(value).trim());
        } catch (RuntimeException ex) {
            throw new InvalidRequestException("MAP_FIELD_INVALID", field + " 必须为数字");
        }
    }
}
