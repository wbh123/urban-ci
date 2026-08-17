package org.urbansafe.priority.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AssessmentInvalidationServiceTest {

    @Test
    void invalidatesCompletenessRiskAndRenewalForReviewedRealInferenceBuilding() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AssessmentInvalidationService service = new AssessmentInvalidationService(jdbc);
        UUID inferenceId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), anyMap(), eq(UUID.class))).thenReturn(buildingId);
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);

        boolean changed = service.invalidateAfterAiReview(inferenceId);

        assertThat(changed).isTrue();
        ArgumentCaptor<String> lookupSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(lookupSql.capture(), anyMap(), eq(UUID.class));
        assertThat(lookupSql.getValue()).contains("mode='REAL'");

        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(3)).update(updateSql.capture(), anyMap());
        assertThat(updateSql.getAllValues()).anyMatch(value -> value.contains("core.completeness_assessment"));
        assertThat(updateSql.getAllValues()).anyMatch(value -> value.contains("core.risk_assessment"));
        assertThat(updateSql.getAllValues()).anyMatch(value -> value.contains("core.renewal_priority"));
        assertThat(updateSql.getAllValues()).allMatch(value -> value.contains("status='STALE'"));
    }
}
