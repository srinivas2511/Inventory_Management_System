package com.springmfg.ims.common.idempotency;

import java.util.Optional;

/** Tells the idempotency filter who is calling, without making {@code common} depend on the security module. */
@FunctionalInterface
public interface IdempotencyIdentity {

    /** Id of the authenticated user of the current request; empty if nobody is signed in. */
    Optional<Long> currentUserId();
}
