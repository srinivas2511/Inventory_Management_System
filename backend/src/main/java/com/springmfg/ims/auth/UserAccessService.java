package com.springmfg.ims.auth;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.springmfg.ims.common.tx.AfterCommit;
import com.springmfg.ims.config.ImsSecurityProperties;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.iam.UserRepository;

/**
 * Resolves {@code userId -> (active, permission_version, authorities)} for every authenticated request from a
 * short-lived Caffeine cache (DESIGN.md section 7.2), so a request costs no database round trip.
 * <p>
 * Anything that changes a user's roles, a role's permissions, or a user's active flag MUST call {@link #evict}
 * (or {@link #evictAll}) after committing, so the change applies at once instead of after the cache TTL.
 */
@Service
public class UserAccessService {

    private final UserRepository users;
    private final TransactionTemplate tx;
    private final Cache<Long, Optional<UserAccess>> cache;

    UserAccessService(UserRepository users, TransactionTemplate tx, ImsSecurityProperties properties) {
        this.users = users;
        this.tx = tx;
        this.cache = Caffeine.newBuilder().expireAfterWrite(properties.accessCacheTtl()).maximumSize(10_000).build();
    }

    /** Empty if the user does not exist. */
    public Optional<UserAccess> find(long userId) {
        return cache.get(userId, id -> tx.execute(status -> load(id)));
    }

    public void evict(long userId) {
        cache.invalidate(userId);
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    /**
     * Evicts once the surrounding transaction has committed (immediately if there is none). Evicting before the
     * commit would let a concurrent request re-cache the old state for a full TTL.
     */
    public void evictAfterCommit(long userId) {
        AfterCommit.run(() -> evict(userId));
    }

    public void evictAllAfterCommit() {
        AfterCommit.run(this::evictAll);
    }

    private Optional<UserAccess> load(long userId) {
        return users.findById(userId).map(UserAccessService::toAccess);
    }

    private static UserAccess toAccess(User user) {
        return new UserAccess(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.isActive(), user.isMustChangePassword(),
                user.getPermissionVersion(), List.copyOf(user.roleCodes().stream().sorted().toList()),
                Set.copyOf(user.permissionCodes()));
    }
}
