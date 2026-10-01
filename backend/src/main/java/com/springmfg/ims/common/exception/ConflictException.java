package com.springmfg.ims.common.exception;

/** Conflict with current state (duplicate key, stale version). Maps to HTTP 409. */
public class ConflictException extends RuntimeException {

    private final ErrorCode code;

    public ConflictException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
