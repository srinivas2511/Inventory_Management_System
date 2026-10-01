package com.springmfg.ims.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** Builds RFC 7807 problem bodies in the single format used by the whole API (DESIGN.md section 6.1). */
public final class Problems {

    public static final String TRACE_ID_KEY = "traceId";

    private Problems() {
    }

    public record FieldError(String field, String message) {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        return of(code, detail, List.of());
    }

    public static ProblemDetail of(ErrorCode code, String detail, List<FieldError> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(code.type()));
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("traceId", MDC.get(TRACE_ID_KEY));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
