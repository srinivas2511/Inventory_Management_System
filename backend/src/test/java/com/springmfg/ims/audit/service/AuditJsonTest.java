package com.springmfg.ims.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

/** old_value/new_value are JSONB columns, so every audited string must reach PostgreSQL as valid JSON. */
class AuditJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static void assertValidJson(String stored) throws Exception {
        JSON.readTree(stored);
        assertThat(JSON.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(stored)).isNotNull();
    }

    @Test
    void null_stays_null() {
        assertThat(AuditService.asJson(null)).isNull();
    }

    @Test
    void valid_json_passes_through_unchanged() {
        assertThat(AuditService.asJson("{\"status\":\"OPEN\"}")).isEqualTo("{\"status\":\"OPEN\"}");
        assertThat(AuditService.asJson("42")).isEqualTo("42");
        assertThat(AuditService.asJson("\"DRAFT\"")).isEqualTo("\"DRAFT\"");
    }

    @Test
    void plain_text_is_wrapped_as_a_json_string() throws Exception {
        assertThat(AuditService.asJson("PENDING")).isEqualTo("\"PENDING\"");
        assertValidJson(AuditService.asJson("LV/26/00001 CL 2.0d"));
    }

    @Test
    void text_that_merely_starts_with_a_number_or_literal_is_wrapped_not_passed_through() throws Exception {
        for (String text : new String[] {"2 slips; gross 16800.00; net 15034.00", "123 abc", "true story", "null and void",
                "{\"a\":1} trailing"}) {
            String stored = AuditService.asJson(text);
            assertThat(stored).as(text).startsWith("\"");
            assertValidJson(stored);
            assertThat(JSON.readTree(stored).asText()).isEqualTo(text);
        }
    }
}
