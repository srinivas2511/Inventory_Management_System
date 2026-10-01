package com.springmfg.ims.common.exception;

import java.util.List;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central exception handling. Every error leaves the API as an RFC 7807 problem with a stable {@code code}
 * and a {@code traceId}; internals (stack traces, SQL) are never exposed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex) {
        return respond(Problems.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ValidationFailedException.class)
    ResponseEntity<ProblemDetail> handleValidationFailed(ValidationFailedException ex) {
        return respond(Problems.of(ErrorCode.VALIDATION_FAILED, ex.getMessage(), ex.getFieldErrors()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return respond(Problems.of(ErrorCode.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConflictException ex) {
        return respond(Problems.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return respond(Problems.of(ErrorCode.VERSION_CONFLICT,
                "The record was modified by someone else. Reload and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(Problems.of(ErrorCode.DUPLICATE_KEY,
                "The value conflicts with an existing record or violates a data constraint."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<Problems.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new Problems.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(Problems.of(ErrorCode.VALIDATION_FAILED, "Validation failed", errors));
    }

    /** Thrown by method security from inside controllers/services. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(Problems.of(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action."));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(Problems.of(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return respond(Problems.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Problems.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new Problems.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        ProblemDetail problem = Problems.of(ErrorCode.VALIDATION_FAILED, "Validation failed", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        // Framework-level 4xx (malformed JSON, unsupported media type, unknown property ...) keep their status
        // but use the same body shape as every other error.
        if (statusCode.is4xxClientError() && !(body instanceof ProblemDetail pd && pd.getProperties() != null
                && pd.getProperties().containsKey("code"))) {
            ErrorCode code = statusCode.value() == 404 ? ErrorCode.NOT_FOUND : ErrorCode.VALIDATION_FAILED;
            ProblemDetail problem = Problems.of(code, safeDetail(ex, body));
            problem.setStatus(statusCode.value());
            return ResponseEntity.status(statusCode).headers(headers).body(problem);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static String safeDetail(Exception ex, Object body) {
        if (body instanceof ProblemDetail pd && pd.getDetail() != null) {
            return pd.getDetail();
        }
        return "The request could not be processed.";
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
