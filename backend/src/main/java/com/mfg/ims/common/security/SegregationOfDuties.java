package com.mfg.ims.common.security;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/**
 * One place for the "different person" rule of the approval workflows (REQUIREMENTS SEC-008 and business rule 19,
 * ARCHITECTURE section 10: requester != approver). The server is the authority; holding a permission, the
 * Administrator role included, never bypasses it.
 *
 * <p>Automatic postings made by the system (the accounting bridge, the payroll journal inside the approval
 * transaction) are not human steps and do not call this class. Every HTTP request carries a real user id, because
 * the JWT filter is the only code that creates an authentication; an unidentified actor is refused.
 */
public final class SegregationOfDuties {

    private SegregationOfDuties() {
    }

    /** Refuses with {@code SEGREGATION_OF_DUTIES} when the acting user is the user who performed the earlier step. */
    public static void requireDifferentUser(Long actor, Long earlierActor, String message) {
        if (actor == null || actor.equals(earlierActor)) {
            throw new BusinessRuleException(ErrorCode.SEGREGATION_OF_DUTIES, message);
        }
    }

    /** Same rule against several earlier steps, for example the person who created and the person who submitted. */
    public static void requireDifferentFromAll(Long actor, String message, Long... earlierActors) {
        for (Long earlier : earlierActors) {
            requireDifferentUser(actor, earlier, message);
        }
        if (earlierActors.length == 0) {
            requireDifferentUser(actor, null, message);
        }
    }
}
