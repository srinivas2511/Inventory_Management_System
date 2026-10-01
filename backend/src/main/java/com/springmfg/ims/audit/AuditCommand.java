package com.springmfg.ims.audit;

import java.util.Map;

/**
 * "Record this in the audit log." Services publish it with an {@code ApplicationEventPublisher} inside their own
 * transaction; the audit module (task 1.6) persists it synchronously in that same transaction, so a failed audit
 * write rolls the business change back (CLAUDE.md rule 7).
 * <p>
 * Snapshots must never contain secrets (password hashes, tokens).
 *
 * @param action   e.g. {@code USER_ROLES_CHANGED}
 * @param entity   e.g. {@code User}
 * @param entityId the affected row's id, as text
 * @param oldValue state before the change; null on create
 * @param newValue state after the change; null on delete
 * @param reason   why, when the caller gave one
 */
public record AuditCommand(String action, String entity, String entityId, Map<String, Object> oldValue,
        Map<String, Object> newValue, String reason) {
}
