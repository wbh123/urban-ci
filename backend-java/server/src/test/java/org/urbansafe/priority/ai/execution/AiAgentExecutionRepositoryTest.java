package org.urbansafe.priority.ai.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AiAgentExecutionRepositoryTest {

    @Test
    void completePersistsActualModelCode() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AiAgentExecutionRepository repository = new AiAgentExecutionRepository(jdbc);
        UUID executionId = UUID.randomUUID();

        repository.complete(
                executionId,
                AiAgentExecutionStatus.SUCCEEDED,
                120L,
                "summary",
                "deepseek-v4-flash",
                null,
                null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());

        assertThat(sql.getValue()).contains("model_code=:modelCode");
        assertThat(params.getValue().getValue("modelCode")).isEqualTo("deepseek-v4-flash");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByIdRestoresPersistedTimestampsAndModelCode() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AiAgentExecutionRepository repository = new AiAgentExecutionRepository(jdbc);
        UUID executionId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-08-12T01:02:03Z");
        Instant finishedAt = Instant.parse("2026-08-12T01:02:05Z");

        Map<String, Object> row = Map.ofEntries(
                Map.entry("id", executionId),
                Map.entry("business_type", "BUILDING"),
                Map.entry("business_id", UUID.randomUUID()),
                Map.entry("question", "综合分析"),
                Map.entry("status", "SUCCEEDED"),
                Map.entry("requested_by", requestedBy),
                Map.entry("requested_by_name", "tester"),
                Map.entry("model_code", "deepseek-v4-flash"),
                Map.entry("started_at", Timestamp.from(startedAt)),
                Map.entry("finished_at", Timestamp.from(finishedAt)),
                Map.entry("duration_ms", 2000L),
                Map.entry("summary", "done"),
                Map.entry("error_code", ""),
                Map.entry("error_message", ""));

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    return sql.contains("agent_execution_step") ? List.of() : List.of(row);
                });

        AiAgentExecution execution = repository.findById(executionId).orElseThrow();

        assertThat(execution.modelCode()).isEqualTo("deepseek-v4-flash");
        assertThat(execution.startedAt()).isEqualTo(startedAt);
        assertThat(execution.finishedAt()).isEqualTo(finishedAt);
    }
}
