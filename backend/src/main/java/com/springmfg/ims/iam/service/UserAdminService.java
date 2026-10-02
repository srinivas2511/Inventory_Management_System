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
import com.springmfg.ims.auth.service.UserPermissionCache;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.iam.domain.Role;
import com.springmfg.ims.iam.domain.User;
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

    public UserAdminService(UserRepository userRepo, RoleRepository roleRepo,
                            PasswordEncoder passwordEncoder,
                            UserPermissionCache permissionCache,
                            AuditService auditService) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        this.permissionCache = permissionCache;
        this.auditService = auditService;
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
            Set<Role> roles = new HashSet<>(roleRepo.findAllById(
                roleRepo.findAll().stream()
                    .filter(r -> req.roleCodes().contains(r.getCode()))
                    .map(Role::getId).toList()));
            user.setRoles(roles);
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
        User user = requireUser(id);
        if (!user.isActive()) return;

        if ("ADMIN".equals(getOnlyRole(user)) && userRepo.countActiveAdmins() <= 1) {
            throw new BusinessRuleException(ErrorCode.LAST_ADMIN,
                "Cannot deactivate the last active administrator");
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

    private User requireUser(Long id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    private String getOnlyRole(User user) {
        Set<String> codes = user.getRoleCodes();
        return codes.size() == 1 ? codes.iterator().next() : null;
    }
}
