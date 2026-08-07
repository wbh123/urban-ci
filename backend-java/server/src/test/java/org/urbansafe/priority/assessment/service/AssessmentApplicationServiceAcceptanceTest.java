package org.urbansafe.priority.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.urbansafe.priority.assessment.AssessmentTestFixtures;
import org.urbansafe.priority.assessment.calculator.RenewalPriorityCalculator;
import org.urbansafe.priority.assessment.checksum.AssessmentChecksumService;
import org.urbansafe.priority.assessment.checksum.AssessmentInputCanonicalizer;
import org.urbansafe.priority.assessment.input.AssessmentInputAssembler;
import org.urbansafe.priority.assessment.model.AssessmentResults.CompletenessResult;
import org.urbansafe.priority.assessment.model.AssessmentResults.RenewalResult;
import org.urbansafe.priority.assessment.model.AssessmentResults.RiskResult;
import org.urbansafe.priority.assessment.model.RuleSnapshot;
import org.urbansafe.priority.assessment.repository.AssessmentResultRepository;
import org.urbansafe.priority.assessment.rule.RuleVersionService;

class AssessmentApplicationServiceAcceptanceTest {

    private final AssessmentInputAssembler inputAssembler = mock(AssessmentInputAssembler.class);
    private final RuleVersionService ruleService = mock(RuleVersionService.class);
    private final CompletenessAssessmentService completenessService = mock(CompletenessAssessmentService.class);
    private final RiskAssessmentService riskService = mock(RiskAssessmentService.class);
    private final RenewalPriorityCalculator renewalCalculator = mock(RenewalPriorityCalculator.class);
    private final AssessmentResultRepository repository = mock(AssessmentResultRepository.class);
    private final RenewalRankingService rankingService = mock(RenewalRankingService.class);
    private AssessmentApplicationService service;
    private UUID buildingId;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var canonicalizer = new AssessmentInputCanonicalizer(objectMapper);
        service = new AssessmentApplicationService(
                inputAssembler,
                canonicalizer,
                new AssessmentChecksumService(canonicalizer),
                ruleService,
                completenessService,
                riskService,
                renewalCalculator,
                repository,
                rankingService,
                new NoopTransactionManager());
        buildingId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        RuleSnapshot completenessRule = AssessmentTestFixtures.completenessRule();
        RuleSnapshot riskRule = AssessmentTestFixtures.riskRule();
        RuleSnapshot renewalRule = renewalRule();
        when(ruleService.active("COMPLETENESS")).thenReturn(completenessRule);
        when(ruleService.active("RISK")).thenReturn(riskRule);
        when(ruleService.active("RENEWAL")).thenReturn(renewalRule);
        when(inputAssembler.assemble(buildingId)).thenReturn(AssessmentTestFixtures.fullInput());
        when(repository.reusableCompleteness(eq(buildingId), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.reusableRisk(eq(buildingId), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.saveCompleteness(eq(buildingId), any(), eq(completenessRule), any(), any(), any(), any(), any(), any()))
                .thenReturn(UUID.fromString("30000000-0000-0000-0000-000000000001"));
        when(repository.saveRisk(eq(buildingId), any(), any(), eq(riskRule), any(), any(), any(), any(), any(), any()))
                .thenReturn(UUID.fromString("30000000-0000-0000-0000-000000000002"));
        when(completenessService.calculate(any(), eq(completenessRule))).thenReturn(completenessResult());
        when(riskService.calculate(any(), eq(riskRule), any())).thenReturn(riskResult());
        when(renewalCalculator.calculate(any(), any(), eq(renewalRule), eq(riskRule))).thenReturn(renewalResult());
        when(repository.currentBuilding(buildingId)).thenReturn(Map.of(
                "buildingId", buildingId,
                "freshness", "CURRENT",
                "completeness", Map.of("status", "CURRENT"),
                "risk", Map.of("status", "CURRENT"),
                "renewalPriorities", List.of(Map.of("status", "CURRENT", "rankingScopeKey", "ALL"))));
    }

    @Test
    void singleBuildingCalculationPersistsAllAssessmentTypesAndRefreshesRankings() {
        Map<String, Object> result = service.calculate(
                buildingId, true, Set.of("COMMUNITY", "REGION", "ALL"), "MANUAL", null);

        assertThat(result).containsEntry("reused", false);
        verify(repository).lockBuilding(buildingId);
        verify(repository).saveCompleteness(eq(buildingId), any(), any(), any(), eq("MANUAL"), any(), any(), any(), any());
        verify(repository).saveRisk(eq(buildingId), any(), any(), any(), any(), eq("MANUAL"), any(), any(), any(), any());
        verify(repository).saveRenewal(eq(buildingId), any(), any(), any(), any(), eq("MANUAL"), any(), eq("ALL"), any(), any(), any(), any());
        verify(rankingService).refresh("ALL");
    }

    @Test
    void batchReportsSuccessReuseAndFailureCounts() {
        UUID success = UUID.randomUUID();
        UUID reused = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        when(repository.buildingIds("ALL", null, 3)).thenReturn(List.of(success, reused, failed));
        AssessmentApplicationService spied = spy(service);
        doReturn(Map.of("reused", false, "calculationBatchId", UUID.randomUUID()))
                .when(spied).calculate(eq(success), anyBoolean(), any(), any(), any());
        doReturn(Map.of("reused", true, "calculationBatchId", UUID.randomUUID()))
                .when(spied).calculate(eq(reused), anyBoolean(), any(), any(), any());
        doThrow(new IllegalStateException("boom"))
                .when(spied).calculate(eq(failed), anyBoolean(), any(), any(), any());

        Map<String, Object> result = spied.batch("ALL", null, false, 3, null);

        assertThat(result)
                .containsEntry("requestedCount", 3)
                .containsEntry("successCount", 1)
                .containsEntry("reusedCount", 1)
                .containsEntry("failedCount", 1);
        assertThat((List<?>) result.get("items")).hasSize(3);
    }

    @Test
    void calculationLocksBuildingBeforeAssemblingInputForConcurrentSafety() {
        service.calculate(buildingId, true, Set.of("ALL"), "MANUAL", null);

        InOrder order = inOrder(repository, inputAssembler);
        order.verify(repository).lockBuilding(buildingId);
        order.verify(inputAssembler).assemble(buildingId);
    }

    private CompletenessResult completenessResult() {
        return new CompletenessResult(new BigDecimal("90.00"), "EXCELLENT", List.of(), List.of(), List.of(), List.of());
    }

    private RiskResult riskResult() {
        return new RiskResult(new BigDecimal("70.00"), "HIGH", new BigDecimal("80.00"),
                new BigDecimal("75.00"), "HIGH", List.of(), List.of(), List.of(), List.of(),
                List.of("人工复核"), true, true);
    }

    private RenewalResult renewalResult() {
        return new RenewalResult(new BigDecimal("76.00"), "P2", new BigDecimal("0.96"),
                List.of(), List.of("纳入更新评估"));
    }

    private RuleSnapshot renewalRule() throws Exception {
        return new RuleSnapshot(UUID.randomUUID(), "RENEWAL", "RENEWAL-V1.1", "城市更新优先级 V1.1",
                AssessmentTestFixtures.MAPPER.readTree("""
                        {
                          "dimensions":[
                            {"code":"RISK","label":"安全风险","weight":"0.45"},
                            {"code":"POPULATION_IMPACT","label":"人口影响","weight":"0.15"},
                            {"code":"BUILDING_AGE","label":"楼龄","weight":"0.10"},
                            {"code":"PUBLIC_VALUE","label":"公共价值","weight":"0.10"},
                            {"code":"FEEDBACK_URGENCY","label":"反馈紧迫性","weight":"0.10"},
                            {"code":"GOVERNANCE_URGENCY","label":"治理紧迫性","weight":"0.10"}
                          ],
                          "levels":[
                            {"code":"P4","min":"0","maxExclusive":"40"},
                            {"code":"P3","min":"40","maxExclusive":"60"},
                            {"code":"P2","min":"60","maxExclusive":"80"},
                            {"code":"P1","min":"80","maxInclusive":"100"}
                          ]
                        }
                        """), "checksum", "ACTIVE", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
