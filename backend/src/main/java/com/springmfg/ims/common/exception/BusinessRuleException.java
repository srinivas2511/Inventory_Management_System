package com.springmfg.ims.common.exception;

/** A business rule was violated. Mapped to the HTTP status of its {@link ErrorCode} (normally 422). */
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode code;

    public BusinessRuleException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
