package com.springmfg.ims.common.exception;

import java.util.List;

/** A business-level validation failure with per-field messages. Mapped to HTTP 400 {@code VALIDATION_FAILED}. */
public class ValidationFailedException extends RuntimeException {

    private final transient List<Problems.FieldError> fieldErrors;

    public ValidationFailedException(List<Problems.FieldError> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ValidationFailedException(String field, String message) {
        this(List.of(new Problems.FieldError(field, message)));
    }

    public List<Problems.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
