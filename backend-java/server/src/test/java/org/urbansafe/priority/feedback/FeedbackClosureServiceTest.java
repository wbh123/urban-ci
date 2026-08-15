package org.urbansafe.priority.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.feedback.repository.FeedbackClosureRepository;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;
import org.urbansafe.priority.feedback.service.FeedbackClosureService;
import org.urbansafe.priority.feedback.service.FeedbackService;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

class FeedbackClosureServiceTest {
    private FeedbackRepository repository;
    private FeedbackClosureRepository closureRepository;
    private Phase2InspectionService inspectionService;
    private Phase2AssetService assetService;
    private FeedbackService feedbackService;
    private FeedbackClosureService service;

    @BeforeEach void setUp(){
        repository=mock(FeedbackRepository.class);closureRepository=mock(FeedbackClosureRepository.class);
        inspectionService=mock(Phase2InspectionService.class);assetService=mock(Phase2AssetService.class);feedbackService=mock(FeedbackService.class);
        service=new FeedbackClosureService(repository,closureRepository,inspectionService,assetService,feedbackService);
    }
    @Test void rectificationRequiresEvidence(){UUID id=UUID.randomUUID();when(repository.findReport(id)).thenReturn(Optional.of(Map.of("reportId",id,"reportCode","DEMO-1","status","PROCESSING","buildingId",UUID.randomUUID())));when(assetService.list("RESIDENT_REPORT",id)).thenReturn(List.of());assertThatThrownBy(()->service.submitRectification(id,"已完成外墙裂缝封闭。",null,UUID.randomUUID())).isInstanceOf(ResourceConflictException.class).hasMessageContaining("整改证据");}
    @Test void evidenceMovesToPendingReinspection(){UUID id=UUID.randomUUID(),actor=UUID.randomUUID();when(repository.findReport(id)).thenReturn(Optional.of(Map.of("reportId",id,"reportCode","DEMO-1","status","PROCESSING","buildingId",UUID.randomUUID())));when(assetService.list("RESIDENT_REPORT",id)).thenReturn(List.of(Map.of("assetId",UUID.randomUUID(),"bindingRole","RECTIFICATION_PHOTO")));when(feedbackService.updateStatus(eq(id),anyMap(),eq(actor))).thenReturn(Map.of("reportId",id,"status","RESOLVED"));Map<String,Object> result=service.submitRectification(id,"已完成外墙裂缝封闭。",null,actor);assertThat(result).containsEntry("status","RESOLVED").containsEntry("formalRiskChanged",false);}
    @Test void completedReinspectionCanClose(){UUID id=UUID.randomUUID(),actor=UUID.randomUUID(),task=UUID.randomUUID();when(repository.findReport(id)).thenReturn(Optional.of(Map.of("reportId",id,"reportCode","DEMO-1","status","RESOLVED","buildingId",UUID.randomUUID())));when(closureRepository.latestReinspection(id)).thenReturn(Optional.of(Map.of("taskId",task,"taskCode","RI-1","status","COMPLETED")));when(feedbackService.updateStatus(eq(id),anyMap(),eq(actor))).thenReturn(Map.of("reportId",id,"status","CLOSED"));Map<String,Object> result=service.completeReinspection(id,true,"复验通过，原问题未继续存在。",actor);assertThat(result).containsEntry("formalRiskChanged",false);@SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> body=ArgumentCaptor.forClass(Map.class);verify(feedbackService).updateStatus(eq(id),body.capture(),eq(actor));assertThat(body.getValue()).containsEntry("status","CLOSED");}
    @Test void failedReinspectionReturnsToProcessing(){UUID id=UUID.randomUUID(),actor=UUID.randomUUID();when(repository.findReport(id)).thenReturn(Optional.of(Map.of("reportId",id,"reportCode","DEMO-1","status","RESOLVED","buildingId",UUID.randomUUID())));when(closureRepository.latestReinspection(id)).thenReturn(Optional.of(Map.of("taskId",UUID.randomUUID(),"taskCode","RI-1","status","COMPLETED")));when(feedbackService.updateStatus(eq(id),anyMap(),eq(actor))).thenReturn(Map.of("reportId",id,"status","PROCESSING"));Map<String,Object> result=service.completeReinspection(id,false,"现场仍存在渗水痕迹，需要继续整改。",actor);assertThat(result).containsEntry("formalRiskChanged",false);}
    @Test void cannotCloseBeforeTaskCompleted(){UUID id=UUID.randomUUID();when(repository.findReport(id)).thenReturn(Optional.of(Map.of("reportId",id,"reportCode","DEMO-1","status","RESOLVED","buildingId",UUID.randomUUID())));when(closureRepository.latestReinspection(id)).thenReturn(Optional.of(Map.of("taskId",UUID.randomUUID(),"taskCode","RI-1","status","IN_PROGRESS")));assertThatThrownBy(()->service.completeReinspection(id,true,"提前提交复验结论。",UUID.randomUUID())).isInstanceOf(ResourceConflictException.class).hasMessageContaining("复查任务完成");}
}
