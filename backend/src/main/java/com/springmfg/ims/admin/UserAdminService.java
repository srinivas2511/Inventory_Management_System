package com.springmfg.ims.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.AuditCommand;
import com.springmfg.ims.auth.PasswordService;
import com.springmfg.ims.auth.SessionService;
import com.springmfg.ims.auth.UserAccessService;
import com.springmfg.ims.common.api.PageRequests;
import com.springmfg.ims.common.api.PageResponse;
import com.springmfg.ims.common.api.Specs;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.exception.ValidationFailedException;
import com.springmfg.ims.iam.Role;
import com.springmfg.ims.iam.RoleRepository;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.iam.UserRepository;

/**
 * User administration (DESIGN.md section 5.1): create, update, assign roles, deactivate/activate, admin password
 * reset. Users are never deleted. Every change publishes an {@link AuditCommand} in the same transaction, ends
 * the affected sessions where needed and evicts the permission cache after commit.
 */
@Service
public class UserAdminService {

    static final String ADMIN_ROLE = "ADMIN";
    private static final Set<String> SORTABLE = Set.of("username", "fullName", "email", "active", "createdAt", "lastLoginAt");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordService passwords;
    private final SessionService sessions;
    private final UserAccessService access;
    private final AdminMapper mapper;
    private final ApplicationEventPublisher events;

    UserAdminService(UserRepository users, RoleRepository roles, PasswordService passwords, SessionService sessions,
            UserAccessService access, AdminMapper mapper, ApplicationEventPublisher events) {
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.sessions = sessions;
        this.access = access;
        this.mapper = mapper;
        this.events = events;
    }

    // ---------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.UserSummary> list(String q, Boolean active, String role, Pageable pageable) {
        Pageable page = PageRequests.restrictSort(pageable, SORTABLE, Sort.by("username"));
        return PageResponse.from(users.findAll(filter(q, active, role), page), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse get(long id) {
        return mapper.toResponse(find(id));
    }

    private static Specification<User> filter(String q, Boolean active, String role) {
        return Specs.<User>of().search(q, "username", "fullName", "email", "employeeCode").eq("active", active)
                .anyOf("roles", "code", role).build();
    }

    // ---------------------------------------------------------------------------------------------- commands

    @Transactional
    public UserDtos.UserResponse create(UserDtos.CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String employeeCode = blankToNull(request.employeeCode());
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Username '" + username + "' is already taken.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "E-mail '" + email + "' is already in use.");
        }
        if (employeeCode != null && users.existsByEmployeeCode(employeeCode)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Employee code '" + employeeCode + "' is already in use.");
        }
        List<Role> assigned = resolveRoles(request.roles(), "roles");

        User user = new User(username, request.fullName().trim(), email, "!");
        user.updateProfile(request.fullName().trim(), email, blankToNull(request.phone()), employeeCode);
        passwords.setTemporaryPassword(user, request.temporaryPassword(), "temporaryPassword");
        user.replaceRoles(assigned);
        User saved = users.saveAndFlush(user);
        passwords.recordCurrentPassword(saved);

        audit("USER_CREATED", saved, null, snapshot(saved), null);
        return mapper.toResponse(saved);
    }

    @Transactional
    public UserDtos.UserResponse update(long id, UserDtos.UpdateUserRequest request) {
        User user = find(id);
        if (user.getVersion() != request.version().longValue()) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT,
                    "The user was modified by someone else. Reload and try again.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String employeeCode = blankToNull(request.employeeCode());
        if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "E-mail '" + email + "' is already in use.");
        }
        if (employeeCode != null && !employeeCode.equals(user.getEmployeeCode()) && users.existsByEmployeeCode(employeeCode)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Employee code '" + employeeCode + "' is already in use.");
        }
        Map<String, Object> before = snapshot(user);
        user.updateProfile(request.fullName().trim(), email, blankToNull(request.phone()), employeeCode);
        users.saveAndFlush(user);
        access.evictAfterCommit(id); // /api/auth/me serves the cached name and e-mail

        audit("USER_UPDATED", user, before, snapshot(user), null);
        return mapper.toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse assignRoles(long id, UserDtos.AssignRolesRequest request) {
        User user = find(id);
        List<Role> newRoles = resolveRoles(request.roles(), "roles");
        boolean losesAdmin = user.isActive() && hasAdmin(user) && newRoles.stream().noneMatch(r -> ADMIN_ROLE.equals(r.getCode()));
        if (losesAdmin) {
            requireAnotherAdmin(user);
        }
        Map<String, Object> before = snapshot(user);
        user.replaceRoles(newRoles);
        user.bumpPermissionVersion(); // tokens issued before this change are refused (TOKEN_EXPIRED) and refreshed
        users.saveAndFlush(user);
        access.evictAfterCommit(id);

        audit("USER_ROLES_CHANGED", user, before, snapshot(user), request.reason());
        return mapper.toResponse(user);
    }

    @Transactional
    public void deactivate(long id, String reason) {
        User user = find(id);
        if (!user.isActive()) {
            return; // idempotent
        }
        if (hasAdmin(user)) {
            requireAnotherAdmin(user);
        }
        Map<String, Object> before = snapshot(user);
        user.setActive(false);
        user.bumpPermissionVersion();
        users.saveAndFlush(user);
        sessions.revokeAllSessions(id);
        access.evictAfterCommit(id);

        audit("USER_DEACTIVATED", user, before, snapshot(user), reason);
    }

    @Transactional
    public UserDtos.UserResponse activate(long id) {
        User user = find(id);
        if (!user.isActive()) {
            Map<String, Object> before = snapshot(user);
            user.setActive(true);
            user.bumpPermissionVersion();
            users.saveAndFlush(user);
            access.evictAfterCommit(id);
            audit("USER_ACTIVATED", user, before, snapshot(user), null);
        }
        return mapper.toResponse(user);
    }

    /** The user must choose their own password at next sign-in; any lock is lifted and every session ends. */
    @Transactional
    public void resetPassword(long id, UserDtos.ResetPasswordRequest request) {
        User user = find(id);
        Map<String, Object> before = snapshot(user);
        passwords.setTemporaryPassword(user, request.temporaryPassword(), "temporaryPassword");
        user.bumpPermissionVersion();
        users.saveAndFlush(user);
        passwords.recordCurrentPassword(user);
        sessions.revokeAllSessions(id);
        access.evictAfterCommit(id);

        audit("USER_PASSWORD_RESET", user, before, snapshot(user), request.reason());
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private User find(long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private List<Role> resolveRoles(Set<String> codes, String field) {
        List<Role> found = roles.findByCodeIn(codes);
        Set<String> foundCodes = found.stream().map(Role::getCode).collect(Collectors.toSet());
        List<String> unknown = codes.stream().filter(c -> !foundCodes.contains(c)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new ValidationFailedException(field, "Unknown role(s): " + String.join(", ", unknown));
        }
        return found;
    }

    private static boolean hasAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> ADMIN_ROLE.equals(r.getCode()));
    }

    /**
     * The system must always keep an active Admin. The ADMIN role row is locked so two concurrent changes cannot
     * each see "someone else is still Admin" and together remove them all.
     */
    private void requireAnotherAdmin(User user) {
        roles.findByCodeForUpdate(ADMIN_ROLE);
        if (users.countOtherActiveAdmins(user.getId()) == 0) {
            throw new BusinessRuleException(ErrorCode.LAST_ADMIN_REQUIRED,
                    "'" + user.getUsername() + "' is the last active Admin. Make another user an Admin first.");
        }
    }

    private void audit(String action, User user, Map<String, Object> before, Map<String, Object> after, String reason) {
        events.publishEvent(new AuditCommand(action, "User", String.valueOf(user.getId()), before, after, reason));
    }

    /** Audit snapshot: profile, state and roles; never the password hash. */
    static Map<String, Object> snapshot(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("employeeCode", user.getEmployeeCode());
        map.put("fullName", user.getFullName());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("active", user.isActive());
        map.put("mustChangePassword", user.isMustChangePassword());
        map.put("roles", user.roleCodes().stream().sorted().toList());
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
