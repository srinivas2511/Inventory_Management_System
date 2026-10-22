package com.mfg.ims.enterprise.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.auth.service.UserPermissionCache;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.enterprise.dto.PlantDtos.UserPlantsDto;
import com.mfg.ims.enterprise.repository.PlantRepository;
import com.mfg.ims.enterprise.scope.PlantAccessService;
import com.mfg.ims.iam.domain.User;
import com.mfg.ims.iam.repository.UserRepository;

/** Assigns users to plants (ENT-004). A user with no assignment, or with PLANT_ALL_ACCESS, sees every plant. */
@Service
public class UserPlantService {

    private final PlantAccessService access;
    private final PlantRepository plantRepo;
    private final UserRepository userRepo;
    private final UserPermissionCache permissionCache;
    private final AuditService auditService;

    public UserPlantService(PlantAccessService access, PlantRepository plantRepo, UserRepository userRepo,
                            UserPermissionCache permissionCache, AuditService auditService) {
        this.access = access;
        this.plantRepo = plantRepo;
        this.userRepo = userRepo;
        this.permissionCache = permissionCache;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserPlantsDto get(Long userId) {
        User user = require(userId);
        return new UserPlantsDto(userId, new ArrayList<>(access.plantIdsOf(userId)),
                user.getPermissionCodes().contains("PLANT_ALL_ACCESS"));
    }

    @Transactional
    public UserPlantsDto replace(Long userId, List<Long> requested) {
        User user = require(userId);
        Set<Long> plantIds = new LinkedHashSet<>(requested);
        for (Long plantId : plantIds) {
            plantRepo.findById(plantId).orElseThrow(() -> new NotFoundException("Plant", plantId));
        }
        Set<Long> old = access.plantIdsOf(userId);
        if (old.equals(plantIds)) {
            return get(userId);
        }
        access.replace(userId, plantIds, currentUserId());
        auditService.record("USER_PLANTS_ASSIGN", "USER", String.valueOf(userId), old.toString(), plantIds.toString(),
                null);
        permissionCache.evictAfterCommit(userId);
        return new UserPlantsDto(userId, new ArrayList<>(plantIds),
                user.getPermissionCodes().contains("PLANT_ALL_ACCESS"));
    }

    private User require(Long userId) {
        return userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        throw new BusinessRuleException(ErrorCode.UNAUTHENTICATED, "Authentication required");
    }
}
