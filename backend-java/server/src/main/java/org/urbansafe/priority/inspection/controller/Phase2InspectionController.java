package org.urbansafe.priority.inspection.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;
import org.urbansafe.priority.phase2.common.Phase2ResponseFactory;

@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class Phase2InspectionController {
    private static final String TASK_MANAGEMENT_ROLES =
            "hasAnyRole('COMMUNITY_MANAGER','GOVERNMENT_MANAGER','ADMIN')";

    private final Phase2InspectionService service;
    public Phase2InspectionController(Phase2InspectionService service) { this.service = service; }

    @PostMapping("/inspection-tasks")
    public ResponseEntity<Map<String,Object>> createTask(@RequestBody Map<String,Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Phase2ResponseFactory.success(service.createTask(body)));
    }
    @GetMapping("/inspection-tasks")
    public ResponseEntity<Map<String,Object>> tasks(@RequestParam(required=false) UUID buildingId,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) Integer page,
            @RequestParam(required=false) Integer size) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.listTasks(buildingId,status,page,size)));
    }
    @GetMapping("/inspection-tasks/{id}")
    public ResponseEntity<Map<String,Object>> task(@PathVariable UUID id) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.getTask(id)));
    }
    @PostMapping("/inspection-tasks/{id}/start")
    public ResponseEntity<Map<String,Object>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.start(id)));
    }
    @PostMapping("/inspection-tasks/{id}/onsite-complete")
    public ResponseEntity<Map<String,Object>> onsiteComplete(@PathVariable UUID id) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.onsiteComplete(id)));
    }
    @PostMapping("/inspection-tasks/{id}/complete")
    @PreAuthorize(TASK_MANAGEMENT_ROLES)
    public ResponseEntity<Map<String,Object>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.complete(id)));
    }
    @PostMapping("/inspection-tasks/{id}/cancel")
    public ResponseEntity<Map<String,Object>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.cancel(id)));
    }
    @PostMapping("/inspection-records")
    public ResponseEntity<Map<String,Object>> createRecord(@RequestBody Map<String,Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Phase2ResponseFactory.success(service.createRecord(body)));
    }
    @GetMapping("/inspection-records")
    public ResponseEntity<Map<String,Object>> records(@RequestParam UUID taskId) {
        return ResponseEntity.ok(Phase2ResponseFactory.success(service.listRecords(taskId)));
    }
}
