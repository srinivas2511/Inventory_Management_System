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
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    OUT_OF_SCOPE(HttpStatus.FORBIDDEN, "Outside your data scope"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account locked"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // 409
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Version conflict"),
    DUPLICATE_KEY(HttpStatus.CONFLICT, "Duplicate value"),
    IDEMPOTENCY_REPLAY_MISMATCH(HttpStatus.CONFLICT, "Idempotency key reused with a different request"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "A request with this idempotency key is still being processed"),

    // 422 - stock rules
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient stock"),
    NEGATIVE_STOCK_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Negative stock not allowed"),
    BATCH_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Batch not available"),
    QUARANTINE_NOT_ISSUABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Quarantined material cannot be issued"),
    REJECTED_NOT_USABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Rejected material cannot be used"),

    // 422 - production rules
    OVER_CONSUMPTION(HttpStatus.UNPROCESSABLE_ENTITY, "Consumption exceeds issued quantity"),
    ISSUE_EXCEEDS_PLAN(HttpStatus.UNPROCESSABLE_ENTITY, "Issue exceeds planned quantity"),
    OPERATION_QTY_EXCEEDS_INPUT(HttpStatus.UNPROCESSABLE_ENTITY, "Operation quantities exceed input"),

    // 422 - other business rules
    FG_NOT_APPROVED(HttpStatus.UNPROCESSABLE_ENTITY, "Finished goods not approved"),
    DISPATCH_EXCEEDS_STOCK(HttpStatus.UNPROCESSABLE_ENTITY, "Dispatch exceeds available stock"),
    ORDER_LOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Order is locked"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Illegal state transition"),
    BOM_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "No active BOM"),
    SEGREGATION_OF_DUTIES(HttpStatus.UNPROCESSABLE_ENTITY, "Requester cannot approve own request"),
    APPROVAL_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Approval required"),
    INSPECTION_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "Inspection incomplete"),

    // 422 - administration
    LAST_ADMIN_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "At least one active Admin is required"),
    ROLE_PROTECTED(HttpStatus.UNPROCESSABLE_ENTITY, "Role is protected"),

    // 422 - master data
    DEACTIVATION_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Item is in use; confirm to deactivate"),
    UNIT_CHANGE_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Unit of measure cannot change while the material is in use"),

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
