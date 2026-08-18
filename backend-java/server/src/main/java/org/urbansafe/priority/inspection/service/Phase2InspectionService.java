package org.urbansafe.priority.inspection.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.phase2.repository.Phase2Repository;

@Service
public class Phase2InspectionService {
    private static final List<String> STATUSES = List.of("PENDING","IN_PROGRESS","ONSITE_COMPLETED","COMPLETED","CANCELLED");
    private static final List<String> SEVERITIES = List.of("LOW","MEDIUM","HIGH");
    private final Phase2Repository repository;

    public Phase2InspectionService(Phase2Repository repository) { this.repository = repository; }

    @Transactional
    public Map<String,Object> createTask(Map<String,Object> body) {
        UUID buildingId = uuid(body.get("buildingId"), "buildingId");
        if (!repository.buildingExists(buildingId)) {
            throw new ResourceNotFoundException("BUILDING_NOT_FOUND", "楼栋不存在");
        }
        UUID id = UUID.randomUUID();
        String code = "IT-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "")
                + "-" + id.toString().substring(0,8).toUpperCase();
        return repository.createTask(id, code, buildingId,
                required(body.get("inspectionType"), "inspectionType"),
                value(body.get("title"), "现场巡检"), value(body.get("description"), null),
                dateTime(body.get("plannedAt")));
    }

    public List<Map<String,Object>> listTasks(UUID buildingId, String status) {
        return listTasks(buildingId, status, null, null);
    }

    public List<Map<String,Object>> listTasks(UUID buildingId, String status, Integer page, Integer size) {
        String normalized = value(status, null);
        if (normalized != null) {
            normalized = normalized.toUpperCase();
            if (!STATUSES.contains(normalized)) {
                throw new InvalidRequestException("INSPECTION_STATUS_INVALID", "巡检任务状态无效");
            }
        }
        List<Map<String,Object>> rows = repository.listTasks(buildingId, normalized);
        if (page == null && size == null) return rows;

        int normalizedPage = page == null ? 0 : page;
        int normalizedSize = size == null ? 20 : size;
        if (normalizedPage < 0) {
            throw new InvalidRequestException("INSPECTION_PAGE_INVALID", "page 不能小于 0");
        }
        if (normalizedSize < 1 || normalizedSize > 100) {
            throw new InvalidRequestException("INSPECTION_PAGE_SIZE_INVALID", "size 必须在 1 到 100 之间");
        }
        long start = (long) normalizedPage * normalizedSize;
        if (start >= rows.size()) return List.of();
        int fromIndex = (int) start;
        int toIndex = Math.min(fromIndex + normalizedSize, rows.size());
        return rows.subList(fromIndex, toIndex);
    }

    public Map<String,Object> getTask(UUID id) {
        return repository.findTask(id).orElseThrow(() ->
                new ResourceNotFoundException("INSPECTION_TASK_NOT_FOUND", "巡检任务不存在"));
    }

    @Transactional
    public Map<String,Object> start(UUID id) {
        getTask(id);
        transition(repository.transitionTask(id,"PENDING","IN_PROGRESS"), "只有待开始任务可以开始");
        return getTask(id);
    }

    @Transactional
    public Map<String,Object> onsiteComplete(UUID id) {
        getTask(id);
        if (repository.countRecords(id) == 0) {
            throw new ResourceConflictException("INSPECTION_RECORD_REQUIRED", "提交现场巡查前至少填写一条巡检记录");
        }
        transition(repository.transitionTask(id,"IN_PROGRESS","ONSITE_COMPLETED"),
                "只有进行中任务可以提交现场巡查完毕");
        return getTask(id);
    }

    @Transactional
    public Map<String,Object> complete(UUID id) {
        getTask(id);
        transition(repository.transitionTask(id,"ONSITE_COMPLETED","COMPLETED"), "只有待后台确认任务可以完成");
        return getTask(id);
    }

    @Transactional
    public Map<String,Object> cancel(UUID id) {
        Map<String,Object> task = getTask(id);
        String status = String.valueOf(task.get("status"));
        if (!List.of("PENDING","IN_PROGRESS").contains(status)) {
            throw new ResourceConflictException("INSPECTION_STATE_CONFLICT", "已结束或已提交现场巡查的任务不能取消");
        }
        transition(repository.transitionTask(id,status,"CANCELLED"), "任务状态已发生变化");
        return getTask(id);
    }

    @Transactional
    public Map<String,Object> createRecord(Map<String,Object> body) {
        UUID taskId = uuid(body.get("taskId"), "taskId");
        Map<String,Object> task = getTask(taskId);
        if (!"IN_PROGRESS".equals(task.get("status"))) {
            throw new ResourceConflictException("INSPECTION_TASK_NOT_IN_PROGRESS", "请先开始巡检任务");
        }
        String severity = value(body.get("severity"), "LOW").toUpperCase();
        if (!SEVERITIES.contains(severity)) {
            throw new InvalidRequestException("INSPECTION_SEVERITY_INVALID", "严重程度无效");
        }
        String suggestion = value(body.get("rectificationSuggestion"), null);
        if ("HIGH".equals(severity) && suggestion == null) {
            throw new InvalidRequestException("INSPECTION_SUGGESTION_REQUIRED", "高风险问题必须填写整改建议");
        }
        return repository.createRecord(UUID.randomUUID(), taskId, (UUID) task.get("buildingId"),
                value(body.get("inspectionPart"), null), value(body.get("issueType"), "OTHER"),
                severity, required(body.get("summary"), "summary"), suggestion,
                repository.json(body.getOrDefault("formData", Map.of())));
    }

    public List<Map<String,Object>> listRecords(UUID taskId) {
        getTask(taskId);
        return repository.listRecords(taskId);
    }

    private void transition(int updated, String message) {
        if (updated == 0) throw new ResourceConflictException("INSPECTION_STATE_CONFLICT", message);
    }
    private UUID uuid(Object value, String field) {
        try { return UUID.fromString(String.valueOf(value)); }
        catch (RuntimeException ex) { throw new InvalidRequestException("INSPECTION_FIELD_INVALID", field + " 必须为 UUID"); }
    }
    private String required(Object value, String field) {
        String result = value(value, null);
        if (result == null) throw new InvalidRequestException("INSPECTION_FIELD_REQUIRED", field + " 不能为空");
        return result;
    }
    private String value(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }
    private OffsetDateTime dateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return OffsetDateTime.parse(String.valueOf(value)); }
        catch (DateTimeParseException ex) { throw new InvalidRequestException("INSPECTION_DATETIME_INVALID", "时间必须为 ISO-8601 格式"); }
    }
}
