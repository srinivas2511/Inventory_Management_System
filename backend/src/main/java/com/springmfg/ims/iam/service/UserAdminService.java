package com.springmfg.ims.iam.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.AuthService;
import com.springmfg.ims.auth.service.UserPermissionCache;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.iam.domain.Role;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.dto.AdminResetPasswordRequest;
import com.springmfg.ims.iam.dto.AdminResetPasswordResponse;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.dto.UpdateUserRequest;
import com.springmfg.ims.iam.dto.UserDto;
import com.springmfg.ims.iam.repository.RoleRepository;
import com.springmfg.ims.iam.repository.UserRepository;

@Service
public class UserAdminService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserPermissionCache permissionCache;
    private final AuditService auditService;
    private final AuthService authService;
    private final AdminGuard adminGuard;

    public UserAdminService(UserRepository userRepo, RoleRepository roleRepo,
                            PasswordEncoder passwordEncoder,
                            UserPermissionCache permissionCache,
                            AuditService auditService,
                            AuthService authService,
                            AdminGuard adminGuard) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        this.permissionCache = permissionCache;
        this.auditService = auditService;
        this.authService = authService;
        this.adminGuard = adminGuard;
    }

    @Transactional(readOnly = true)
    public Page<UserDto> search(String q, Boolean active, Pageable pageable) {
        return userRepo.search(q, active, pageable).map(UserDto::from);
    }

    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        return UserDto.from(requireUser(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        authService.validateComplexity(req.password(), req.username());
        if (userRepo.existsByUsername(req.username())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Username already taken: " + req.username());
        }
        if (userRepo.existsByEmail(req.email())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Email already registered: " + req.email());
        }

        User user = new User();
        user.setUsername(req.username());
        user.setEmployeeCode(req.employeeCode());
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        user.setPhone(req.phone());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setMustChangePassword(true);

        if (req.roleCodes() != null && !req.roleCodes().isEmpty()) {
            List<Role> found = roleRepo.findAll().stream()
                .filter(r -> req.roleCodes().contains(r.getCode())).toList();
            Set<String> unknown = new HashSet<>(req.roleCodes());
            found.forEach(r -> unknown.remove(r.getCode()));
            if (!unknown.isEmpty()) {
                throw new NotFoundException("Unknown role codes: " + unknown);
            }
            user.setRoles(new HashSet<>(found));
        }

        userRepo.save(user);
        auditService.record("CREATE", "USER", String.valueOf(user.getId()),
            null, user.getUsername(), null);
        return UserDto.from(user);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest req) {
        User user = requireUser(id);
        String old = user.getFullName();

        if (!user.getEmail().equals(req.email()) && userRepo.existsByEmail(req.email())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Email already registered: " + req.email());
        }

        user.setEmployeeCode(req.employeeCode());
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        user.setPhone(req.phone());
        userRepo.save(user);

        auditService.record("UPDATE", "USER", String.valueOf(id), old, req.fullName(), null);
        permissionCache.evict(id);
        return UserDto.from(user);
    }

    @Transactional
    public UserDto assignRoles(Long id, Set<String> roleCodes) {
        adminGuard.lock();
        User user = requireUser(id);
        List<Role> roles = roleRepo.findAll().stream()
            .filter(r -> roleCodes.contains(r.getCode())).toList();

        Set<String> foundCodes = new HashSet<>();
        roles.forEach(r -> foundCodes.add(r.getCode()));
        Set<String> unknown = new HashSet<>(roleCodes);
        unknown.removeAll(foundCodes);
        if (!unknown.isEmpty()) {
            throw new NotFoundException("Unknown role codes: " + unknown);
        }

        if (adminGuard.isActiveAdmin(user) && !foundCodes.contains(AdminGuard.ADMIN_ROLE)) {
            adminGuard.requireAnotherAdmin(user,
                "Cannot remove the Administrator role from the last active administrator");
        }

        String oldRoles = String.join(",", user.getRoleCodes());
        user.setRoles(new HashSet<>(roles));
        user.setPermissionVersion(user.getPermissionVersion() + 1);
        userRepo.save(user);

        auditService.record("ASSIGN_ROLES", "USER", String.valueOf(id),
            oldRoles, String.join(",", roleCodes), null);
        permissionCache.evict(id);
        return UserDto.from(user);
    }

    @Transactional
    public void deactivate(Long id) {
        adminGuard.lock();
        User user = requireUser(id);
        if (!user.isActive()) return;

        if (adminGuard.isActiveAdmin(user)) {
            adminGuard.requireAnotherAdmin(user, "Cannot deactivate the last active administrator");
        }

        user.setActive(false);
        userRepo.save(user);
        auditService.record("DEACTIVATE", "USER", String.valueOf(id), "active", "inactive", null);
        permissionCache.evict(id);
    }

    @Transactional
    public void reactivate(Long id) {
        User user = requireUser(id);
        if (user.isActive()) return;
        user.setActive(true);
        userRepo.save(user);
        auditService.record("REACTIVATE", "USER", String.valueOf(id), "inactive", "active", null);
        permissionCache.evict(id);
    }

    /**
     * Sets a temporary password on another user's account. The reason goes to the audit trail with the old and new
     * flag state; the password itself is never audited, logged or stored in plaintext. An administrator resets their
     * own password with Change password, which asks for the current one.
     */
    @Transactional
    public AdminResetPasswordResponse resetPassword(Long id, AdminResetPasswordRequest req, Long actorId) {
        User user = requireUser(id);
        if (user.getId().equals(actorId)) {
            throw new BusinessRuleException(ErrorCode.SELF_PASSWORD_RESET,
                "Use Change password for your own account");
        }
        boolean generated = req.temporaryPassword() == null || req.temporaryPassword().isBlank();
        String temporary = generated ? authService.generateTemporaryPassword(user.getUsername()) : req.temporaryPassword();
        boolean hadFlag = user.isMustChangePassword();

        authService.setTemporaryPassword(user, temporary);

        auditService.record("ADMIN_PASSWORD_RESET", "USER", String.valueOf(id),
            "mustChangePassword=" + hadFlag,
            "mustChangePassword=true; sessions ended; " + (generated ? "generated" : "supplied"),
            req.reason());
        return new AdminResetPasswordResponse(generated ? temporary : null, generated);
    }

    /** Clears a lockout (the failed-attempt counter and the lock-until time). Nothing to do for an account that is not locked. */
    @Transactional
    public UserDto unlock(Long id) {
        User user = requireUser(id);
        boolean wasLocked = user.isLocked();
        int attempts = user.getFailedAttempts();
        if (!wasLocked && attempts == 0) {
            return UserDto.from(user);
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepo.save(user);
        auditService.record("UNLOCK", "USER", String.valueOf(id),
            "locked=" + wasLocked + ", failedAttempts=" + attempts, "locked=false, failedAttempts=0", null);
        return UserDto.from(user);
    }

    private User requireUser(Long id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
