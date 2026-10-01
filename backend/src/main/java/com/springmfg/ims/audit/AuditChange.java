package com.springmfg.ims.audit;

import java.util.Map;

/**
 * What an {@link Audited} method changed. Snapshots must never contain secrets (they are also masked defensively).
 *
 * @param entityId the affected row's id, as text
 * @param oldValue state before the change; null on create
 * @param newValue state after the change; null on delete
 * @param reason   why, where the operation takes one (mandatory for quantity corrections and overrides)
 */
public record AuditChange(String entityId, Map<String, Object> oldValue, Map<String, Object> newValue, String reason) {
}
