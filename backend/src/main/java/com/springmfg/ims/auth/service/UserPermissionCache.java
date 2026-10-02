package com.springmfg.ims.auth.service;

import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.repository.UserRepository;

/**
 * Caches the resolved permission set per user for up to 60 s (Caffeine TTL in CacheConfig).
 * A permission_version mismatch in the JWT triggers an immediate eviction and refresh.
 */
@Service
public class UserPermissionCache {

    private final UserRepository userRepository;

    public UserPermissionCache(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record CachedUser(Long id, String username, boolean active, int permissionVersion,
                             Set<String> roles, Set<String> permissions) {}

    @Cacheable(value = "userPermissions", key = "#userId")
    @Transactional(readOnly = true)
    public CachedUser load(Long userId) {
        User u = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User", userId));
        // fetch roles + permissions eagerly so they are available after transaction
        return new CachedUser(
            u.getId(), u.getUsername(), u.isActive(), u.getPermissionVersion(),
            u.getRoleCodes(), u.getPermissionCodes()
        );
    }

    @CacheEvict(value = "userPermissions", key = "#userId")
    public void evict(Long userId) { /* triggers cache invalidation */ }
}
