package org.urbansafe.priority.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.urbansafe.priority.feedback.service.FeedbackManagementQueryService;

class FeedbackManagementQueryServiceReinspectionTest {

    @Test
    void managementListExcludesReinspectionTasksWhoseVerdictWasAlreadyRecorded() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        FeedbackManagementQueryService service = new FeedbackManagementQueryService(jdbc);
        service.list(null, null, null, null, null, null, null, 0, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("NOT EXISTS")
                .contains("REINSPECTION_PASSED")
                .contains("REINSPECTION_FAILED")
                .contains("result_event.event_data->>'taskId'");
    }
}
