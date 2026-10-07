package com.springmfg.ims.iam.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.repository.UserRepository;

/**
 * The invariant that keeps administration reachable: at least one active, unlocked user always holds the ADMIN
 * role. Every transaction that can remove an administrator (taking the role away, deactivating the account) must
 * call {@link #lock()} first and {@link #requireAnotherAdmin} before it changes anything.
 * <p>
 * The lock is a transaction-scoped advisory lock, so two administrators demoting each other at the same moment are
 * serialised: the second one re-counts after the first has committed and is refused. {@code RoleAdminService} has
 * no mutating path today; a future one that changes an administrator's access must use this guard too.
 */
@Component
public class AdminGuard {

    static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepo;

    public AdminGuard(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /** Take the administrator-invariant lock; it is held until the surrounding transaction ends. */
    public void lock() {
        userRepo.lockAdminInvariant();
    }

    /** An administrator in the sense of the invariant's loss side: active and holding the ADMIN role (locked or not). */
    public boolean isActiveAdmin(User user) {
        return user.isActive() && user.getRoleCodes().contains(ADMIN_ROLE);
    }

    /**
     * Refuses unless some other active, unlocked user holds ADMIN, so removing {@code target} cannot leave nobody
     * who can administer. A locked target is judged the same way: it is the others that must be usable.
     */
    public void requireAnotherAdmin(User target, String message) {
        if (userRepo.countActiveUnlockedAdminsExcept(target.getId(), Instant.now()) < 1) {
            throw new BusinessRuleException(ErrorCode.LAST_ADMIN, message);
        }
    }
}
