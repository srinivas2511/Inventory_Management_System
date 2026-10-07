package com.springmfg.ims.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to API clients (DESIGN.md section 6.2).
 * Codes are append-only: never rename or reuse a code once released.
 */
public enum ErrorCode {

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),

    // 401 / 403 / 404 / 423 / 429
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired token"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    OUT_OF_SCOPE(HttpStatus.FORBIDDEN, "Outside your data scope"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account locked"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account is disabled"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Password change required"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // 400 - business rule
    PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "Password does not meet policy requirements"),
    CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Current password is incorrect"),
    LAST_ADMIN(HttpStatus.BAD_REQUEST, "Cannot remove the last active administrator"),
    SELF_PASSWORD_RESET(HttpStatus.UNPROCESSABLE_ENTITY, "Use Change password for your own account"),
    DEACTIVATION_BLOCKED(HttpStatus.BAD_REQUEST, "Cannot deactivate — active references exist"),

    // 409
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Version conflict"),
    DUPLICATE_KEY(HttpStatus.CONFLICT, "Duplicate value"),
    IDEMPOTENCY_REPLAY_MISMATCH(HttpStatus.CONFLICT, "Idempotency key reused with a different request"),

    // 422 - stock rules
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient stock"),
    NEGATIVE_STOCK_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Negative stock not allowed"),
    BATCH_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Batch not available"),
    QUARANTINE_NOT_ISSUABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Quarantined material cannot be issued"),
    REJECTED_NOT_USABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Rejected material cannot be used"),

    // 422 - procurement rules
    OVER_RECEIPT(HttpStatus.UNPROCESSABLE_ENTITY, "Receipt exceeds ordered quantity beyond tolerance"),
    ILLEGAL_PO_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Illegal purchase order state transition"),

    // 422 - production rules
    OVER_CONSUMPTION(HttpStatus.UNPROCESSABLE_ENTITY, "Consumption exceeds issued quantity"),
    ISSUE_EXCEEDS_PLAN(HttpStatus.UNPROCESSABLE_ENTITY, "Issue exceeds planned quantity"),
    OPERATION_QTY_EXCEEDS_INPUT(HttpStatus.UNPROCESSABLE_ENTITY, "Operation quantities exceed input"),

    // 422 - other business rules
    FG_NOT_APPROVED(HttpStatus.UNPROCESSABLE_ENTITY, "Finished goods not approved"),
    DISPATCH_EXCEEDS_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "Dispatch exceeds available stock"),
    ORDER_LOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Order is locked"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Illegal state transition"),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "Schedule conflict"),
    BOM_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "No active BOM"),
    SEGREGATION_OF_DUTIES(HttpStatus.UNPROCESSABLE_ENTITY, "Requester cannot approve own request"),
    APPROVAL_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Approval required"),
    INSPECTION_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "Inspection incomplete"),

    // 422 - finance rules (R9+)
    PERIOD_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "Fiscal period is closed"),
    JOURNAL_NOT_BALANCED(HttpStatus.UNPROCESSABLE_ENTITY, "Journal entry does not balance (Dr ≠ Cr)"),

    // 422 - HRM rules (R13+)
    LEAVE_BALANCE_INSUFFICIENT(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient leave balance"),
    LEAVE_OVERLAP(HttpStatus.UNPROCESSABLE_ENTITY, "Leave overlaps an existing application"),
    PAYROLL_LOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Payroll for this period is already approved"),

    // 422 - WMS rules (R14+)
    BIN_CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Bin capacity exceeded"),
    SCAN_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Scanned code does not match the task"),
    PUTAWAY_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Stock cannot be put away yet"),

    // 413 / 415 - document uploads (R17+)
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File is larger than the configured limit"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file type is not accepted"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** URI-style problem type, e.g. {@code https://ims.local/errors/insufficient-stock}. */
    public String type() {
        return "https://ims.local/errors/" + name().toLowerCase().replace('_', '-');
    }
}
