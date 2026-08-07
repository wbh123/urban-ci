package org.urbansafe.priority.assessment.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RuleChecksumServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RuleChecksumService service = new RuleChecksumService(mapper);

    @Test
    void objectKeyOrderDoesNotChangeChecksum() throws Exception {
        assertThat(service.checksum(mapper.readTree("{\"b\":2,\"a\":1}")))
                .isEqualTo(service.checksum(mapper.readTree("{\"a\":1,\"b\":2}")));
    }

    @Test
    void valueChangeChangesChecksum() throws Exception {
        assertThat(service.checksum(mapper.readTree("{\"a\":1}")))
                .isNotEqualTo(service.checksum(mapper.readTree("{\"a\":2}")));
    }
}
