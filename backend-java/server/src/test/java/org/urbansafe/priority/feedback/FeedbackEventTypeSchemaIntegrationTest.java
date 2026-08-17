package org.urbansafe.priority.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 验证公众反馈事件类型字段能够容纳完整的业务事件名称。 */
class FeedbackEventTypeSchemaIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void residentReportEventTypeShouldSupportLongBusinessEventNames() {
        Integer maxLength = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema='core'
                  AND table_name='resident_report_event'
                  AND column_name='event_type'
                """, Integer.class);

        assertThat(maxLength).isNotNull().isGreaterThanOrEqualTo(64);
    }
}
