package com.springmfg.ims.support;

import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import com.springmfg.ims.common.idempotency.IdempotencyRequirement;

/** Test-only: POSTs under /api/probe/idempotent must carry an Idempotency-Key. */
@Component
public class ProbeIdempotencyRequirement implements IdempotencyRequirement {

    @Override
    public RequestMatcher matcher() {
        return new AntPathRequestMatcher("/api/probe/idempotent/**", "POST");
    }
}
