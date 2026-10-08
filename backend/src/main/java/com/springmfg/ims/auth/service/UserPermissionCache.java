package com.springmfg.ims.auth.service;

import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.repository.UserRepository;

/**
 * Caches the resolved permission set per user for up to 60 s (Caffeine TTL in CacheConfig).
 * A permission_version mismatch in the JWT triggers an immediate eviction and refresh.
 */
@Service
public class UserPermissionCache {

    private static final String CACHE = "userPermissions";

    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    private final com.springmfg.ims.enterprise.scope.PlantAccessService plantAccess;

    public UserPermissionCache(UserRepository userRepository, CacheManager cacheManager,
                               com.springmfg.ims.enterprise.scope.PlantAccessService plantAccess) {
        this.userRepository = userRepository;
        this.cacheManager = cacheManager;
        this.plantAccess = plantAccess;
    }

    /**
     * {@code mustChangePassword} is part of the cached state because the server refuses every call except the
     * password-change allow-list while it is set (see JwtAuthenticationFilter). Whatever flips it evicts this
     * entry after the commit, so the longest a stale value can live is the cache TTL.
     */
    public record CachedUser(Long id, String username, boolean active, int permissionVersion,
                             Set<String> roles, Set<String> permissions, boolean mustChangePassword,
                             Set<Long> plantIds) {

        public CachedUser(Long id, String username, boolean active, int permissionVersion,
                          Set<String> roles, Set<String> permissions, boolean mustChangePassword) {
            this(id, username, active, permissionVersion, roles, permissions, mustChangePassword, Set.of());
        }

        public CachedUser(Long id, String username, boolean active, int permissionVersion,
                          Set<String> roles, Set<String> permissions) {
            this(id, username, active, permissionVersion, roles, permissions, false, Set.of());
        }
    }

    @Cacheable(value = "userPermissions", key = "#userId")
    @Transactional(readOnly = true)
    public CachedUser load(Long userId) {
        User u = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User", userId));
        // fetch roles + permissions eagerly so they are available after transaction
        Set<String> permissions = u.getPermissionCodes();
        // A user is plant-scoped only when assigned to plants AND not holding PLANT_ALL_ACCESS. An empty set means
        // "every plant", which is what a default single-plant install and every pre-existing user keep.
        Set<Long> plantIds = permissions.contains("PLANT_ALL_ACCESS") ? Set.of() : plantAccess.plantIdsOf(userId);
        return new CachedUser(
            u.getId(), u.getUsername(), u.isActive(), u.getPermissionVersion(),
            u.getRoleCodes(), permissions, u.isMustChangePassword(), plantIds
        );
    }

    @CacheEvict(value = CACHE, key = "#userId")
    public void evict(Long userId) { /* triggers cache invalidation */ }

    /**
     * Evicts now and again once the surrounding transaction has committed. The second eviction closes the race
     * where a concurrent request reloads the pre-commit row between the first eviction and the commit and would
     * otherwise keep serving the old flag until the TTL.
     */
    public void evictAfterCommit(Long userId) {
        evictNow(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(userId);
                }
            });
        }
    }

    private void evictNow(Long userId) {
        Cache cache = cacheManager.getCache(CACHE);
        if (cache != null) {
            cache.evict(userId);
        }
    }
}
