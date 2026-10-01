package com.springmfg.ims.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful completion must be written to the audit log in the same transaction
 * (DESIGN.md section 4.11). The method must return an {@link Auditable} carrying the old and new values; the
 * {@link AuditAspect} records them after the method returns and before its transaction commits. If the method
 * throws, nothing is recorded (the change did not happen).
 * <p>
 * For actions that do not fit "one method returns one change" (several entities, no useful return value) publish
 * an {@link AuditCommand} instead.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    /** e.g. {@code STOCK_ADJUSTMENT}; at most 60 characters. */
    String action();

    /** e.g. {@code StockAdjustment}; at most 60 characters. */
    String entity();
}
