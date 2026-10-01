package com.springmfg.ims.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.Problems;

class ProblemsTest {

    @Test
    void buildsRfc7807BodyWithStableCodeAndTraceId() {
        MDC.put(Problems.TRACE_ID_KEY, "abc123");
        try {
            ProblemDetail p = Problems.of(ErrorCode.DISPATCH_EXCEEDS_STOCK, "Requested 6000; available 4800");

            assertThat(p.getStatus()).isEqualTo(422);
            assertThat(p.getTitle()).isEqualTo("Dispatch exceeds available stock");
            assertThat(p.getType().toString()).isEqualTo("https://ims.local/errors/dispatch-exceeds-stock");
            assertThat(p.getProperties()).containsEntry("code", "DISPATCH_EXCEEDS_STOCK")
                    .containsEntry("traceId", "abc123")
                    .containsKey("timestamp");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void carriesFieldErrors() {
        ProblemDetail p = Problems.of(ErrorCode.VALIDATION_FAILED, "Validation failed",
                List.of(new Problems.FieldError("wireDiameter", "must be greater than 0")));
        assertThat(p.getStatus()).isEqualTo(400);
        assertThat(p.getProperties().get("fieldErrors")).isEqualTo(
                List.of(new Problems.FieldError("wireDiameter", "must be greater than 0")));
    }

    @Test
    void everyErrorCodeHasAStatusAndTitle() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.status()).as(code.name()).isNotNull();
            assertThat(code.title()).as(code.name()).isNotBlank();
            assertThat(code.type()).startsWith("https://ims.local/errors/");
        }
    }

    @Test
    void businessRuleCodesFromTheDesignCatalogueAreAllPresent() {
        List<String> required = List.of(
                "INSUFFICIENT_STOCK", "NEGATIVE_STOCK_NOT_ALLOWED", "BATCH_NOT_AVAILABLE", "QUARANTINE_NOT_ISSUABLE",
                "REJECTED_NOT_USABLE", "OVER_CONSUMPTION", "ISSUE_EXCEEDS_PLAN", "OPERATION_QTY_EXCEEDS_INPUT",
                "FG_NOT_APPROVED", "DISPATCH_EXCEEDS_STOCK", "ORDER_LOCKED", "ILLEGAL_STATE_TRANSITION",
                "BOM_NOT_ACTIVE", "SEGREGATION_OF_DUTIES", "APPROVAL_REQUIRED", "INSPECTION_INCOMPLETE",
                "VERSION_CONFLICT", "DUPLICATE_KEY", "VALIDATION_FAILED", "ACCESS_DENIED", "UNAUTHENTICATED");
        for (String name : required) {
            assertThat(ErrorCode.valueOf(name)).isNotNull();
        }
    }
}
