package org.urbansafe.priority.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;
import org.urbansafe.priority.feedback.service.FeedbackService;

class FeedbackServiceTest {

    private FeedbackRepository repository;
    private Phase2AssetService assetService;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        repository = mock(FeedbackRepository.class);
        assetService = mock(Phase2AssetService.class);
        service = new FeedbackService(repository, assetService, mock(AuditService.class));
    }

    @Test
    void publicCreateReturnsOneTimeTrackingSecretAndStoresOnlyHash() {
        UUID communityId = UUID.randomUUID();
        when(repository.communityExists(communityId)).thenReturn(true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("communityId", communityId.toString());
        body.put("reportType", "WALL_CRACK");
        body.put("description", "外墙窗角附近出现明显裂缝，请安排现场核查。");
        body.put("urgency", "HIGH");
        body.put("contactPhone", "13800138000");
        body.put("contactConsent", true);

        Map<String, Object> result = service.createPublic(body);

        String secret = String.valueOf(result.get("trackingSecret"));
        assertFalse(secret.isBlank());
        assertEquals(6, result.get("maxImageCount"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(repository).insertReport(captor.capture());
        assertEquals(FeedbackService.hashTrackingSecret(secret), captor.getValue().get("trackingSecretHash"));
        assertEquals("WEB", captor.getValue().get("feedbackChannel"));
        verify(repository).insertEvent(any(UUID.class), eq("CREATED"), isNull(), eq("SUBMITTED"),
                any(), eq("PUBLIC"), eq("CITIZEN"), isNull(), anyMap());
    }

    @Test
    void trackRequiresCodeAndSecretAndMasksContactAndReturnsImages() {
        UUID reportId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportId", reportId);
        row.put("reportCode", "FB-20260724-TEST0001");
        row.put("contactPhone", "13800138000");
        row.put("contactEmail", "citizen@example.com");
        when(repository.findPublicReport(eq("FB-20260724-TEST0001"), anyString()))
                .thenReturn(Optional.of(row));
        when(repository.listPublicEvents(reportId)).thenReturn(List.of());
        when(repository.listReportImages(reportId)).thenReturn(List.of(Map.of(
                "assetId", UUID.randomUUID(), "originalFilename", "wall.jpg")));

        Map<String, Object> result = service.track("FB-20260724-TEST0001", "tracking-secret");

        assertEquals("138****8000", result.get("contactPhone"));
        assertEquals("c***@example.com", result.get("contactEmail"));
        assertNotNull(result.get("events"));
        assertEquals(1, result.get("imageCount"));
        assertEquals(6, result.get("maxImageCount"));
    }

    @Test
    void publicImageUploadBindsToFeedbackAndReturnsRemainingSlots() {
        UUID reportId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(repository.lockPublicReport(eq("FB-IMAGE-001"), anyString()))
                .thenReturn(Optional.of(Map.of(
                        "reportId", reportId,
                        "reportCode", "FB-IMAGE-001",
                        "status", "SUBMITTED")));
        when(repository.countReportImages(reportId)).thenReturn(2);
        when(assetService.upload(any(), eq("RESIDENT_REPORT"), eq(reportId), eq("FEEDBACK_PHOTO")))
                .thenReturn(Map.of(
                        "assetId", assetId,
                        "originalFilename", "crack.jpg",
                        "contentType", "image/jpeg",
                        "fileSize", 1024L,
                        "previewUrl", "/api/v1/assets/" + assetId + "/preview"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "crack.jpg", "image/jpeg", new byte[] {1, 2, 3});

        Map<String, Object> result = service.uploadPublicImage(
                "FB-IMAGE-001", "tracking-secret", file);

        assertEquals(assetId, result.get("assetId"));
        assertEquals(3, result.get("imageCount"));
        assertEquals(3, result.get("remainingSlots"));
        assertFalse(result.containsKey("previewUrl"));
        verify(assetService).upload(file, "RESIDENT_REPORT", reportId, "FEEDBACK_PHOTO");
        verify(repository).insertEvent(eq(reportId), eq("IMAGE_UPLOADED"), isNull(),
                eq("SUBMITTED"), anyString(), eq("INTERNAL"), eq("CITIZEN"), isNull(), anyMap());
    }

    @Test
    void seventhPublicImageIsRejectedBeforeStorageWrite() {
        UUID reportId = UUID.randomUUID();
        when(repository.lockPublicReport(eq("FB-IMAGE-LIMIT"), anyString()))
                .thenReturn(Optional.of(Map.of(
                        "reportId", reportId,
                        "reportCode", "FB-IMAGE-LIMIT",
                        "status", "PROCESSING")));
        when(repository.countReportImages(reportId)).thenReturn(6);
        MockMultipartFile file = new MockMultipartFile(
                "file", "seventh.png", "image/png", new byte[] {1});

        assertThrows(ResourceConflictException.class, () ->
                service.uploadPublicImage("FB-IMAGE-LIMIT", "tracking-secret", file));

        verify(assetService, never()).upload(any(), anyString(), any(), anyString());
    }

    @Test
    void publicImageContentRejectsAssetFromAnotherReport() {
        UUID reportId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(repository.findPublicReport(eq("FB-IMAGE-READ"), anyString()))
                .thenReturn(Optional.of(new LinkedHashMap<>(Map.of(
                        "reportId", reportId,
                        "reportCode", "FB-IMAGE-READ",
                        "status", "PROCESSING"))));
        when(repository.assetBelongsToReport(reportId, assetId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                service.publicImageContent("FB-IMAGE-READ", "tracking-secret", assetId));

        verify(assetService, never()).content(assetId);
    }

    @Test
    void terminalStatusCannotTransition() {
        UUID reportId = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(Map.of(
                "reportId", reportId,
                "reportCode", "FB-1",
                "status", "CLOSED")));

        assertThrows(ResourceConflictException.class, () -> service.updateStatus(
                reportId, Map.of("status", "PROCESSING"), UUID.randomUUID()));
        verify(repository).lockReport(reportId);
    }
}
