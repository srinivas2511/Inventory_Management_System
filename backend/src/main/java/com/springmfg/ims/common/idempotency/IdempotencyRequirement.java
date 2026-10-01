package com.springmfg.ims.common.idempotency;

import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Declares requests that <b>must</b> carry an {@code Idempotency-Key} (DESIGN.md section 6.1: stock issue,
 * production output, goods receipt, dispatch, adjustment posting). A module registers one as a Spring bean, e.g.
 * {@code () -> new AntPathRequestMatcher("/api/dispatches", "POST")}. Requests with the header are protected even
 * without a requirement; the requirement only makes a missing header an error.
 */
@FunctionalInterface
public interface IdempotencyRequirement {

    RequestMatcher matcher();
}
