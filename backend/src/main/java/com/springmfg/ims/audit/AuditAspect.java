package com.springmfg.ims.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Records {@link Audited} methods. It is ordered <em>inside</em> the transaction interceptor (see
 * {@code TransactionConfig}), so the audit row and the business change commit or roll back together.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class AuditAspect {

    private final AuditService audit;

    AuditAspect(AuditService audit) {
        this.audit = audit;
    }

    @Around("@annotation(audited)")
    Object record(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        if (!(result instanceof Auditable auditable)) {
            throw new IllegalStateException(((MethodSignature) joinPoint.getSignature()).getMethod()
                    + " is @Audited but does not return an Auditable (it returned "
                    + (result == null ? "null" : result.getClass().getName()) + ")");
        }
        AuditChange change = auditable.auditChange();
        audit.record(new AuditCommand(audited.action(), audited.entity(), change.entityId(), change.oldValue(),
                change.newValue(), change.reason()));
        return result;
    }
}
