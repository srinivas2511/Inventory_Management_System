package com.springmfg.ims.audit;

/** Implemented by the result of an {@link Audited} method to say what changed. */
public interface Auditable {

    AuditChange auditChange();
}
