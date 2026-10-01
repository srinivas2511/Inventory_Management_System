package com.springmfg.ims.admin;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.AuditCommand;
import com.springmfg.ims.auth.UserAccessService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.exception.ValidationFailedException;
import com.springmfg.ims.iam.Permission;
import com.springmfg.ims.iam.PermissionRepository;
import com.springmfg.ims.iam.Role;
import com.springmfg.ims.iam.RoleRepository;
import com.springmfg.ims.iam.UserRepository;

/**
 * Roles and their permission sets (DESIGN.md section 5.1). System roles can have their permissions edited but
 * never be deleted. Changing a role's permissions invalidates the tokens of everyone who holds it.
 */
@Service
public class RoleAdminService {

    /**
     * The ADMIN role must always keep these, or no one could repair a mistaken change: managing roles and
     * users is how permissions are restored.
     */
    static final Set<String> ADMIN_ESSENTIAL = Set.of("ROLE_MANAGE", "USER_VIEW", "USER_UPDATE");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRepository users;
    private final UserAccessService access;
    private final AdminMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RoleAdminService(RoleRepository roles, PermissionRepository permissions, UserRepository users,
            UserAccessService access, AdminMapper mapper, ApplicationEventPublisher events, Clock clock) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
        this.access = access;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.RoleSummary> list() {
        Map<Long, Long> counts = new HashMap<>();
        roles.countUsersPerRole().forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return roles.findAllByOrderByCodeAsc().stream()
                .map(r -> mapper.toSummary(r, counts.getOrDefault(r.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public RoleDtos.RoleResponse get(long id) {
        Role role = find(id);
        return mapper.toResponse(role, roles.countUsers(id));
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.PermissionResponse> permissionCatalogue() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public RoleDtos.RoleResponse create(RoleDtos.CreateRoleRequest request) {
        if (roles.existsByCode(request.code())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Role '" + request.code() + "' already exists.");
        }
        Role role = new Role(request.code(), request.name().trim(), blankToNull(request.description()), false);
        role.replacePermissions(resolvePermissions(request.permissions()));
        Role saved = roles.saveAndFlush(role);
        events.publishEvent(new AuditCommand("ROLE_CREATED", "Role", String.valueOf(saved.getId()), null, snapshot(saved), null));
        return mapper.toResponse(saved, 0);
    }

    @Transactional
    public RoleDtos.RoleResponse update(long id, RoleDtos.UpdateRoleRequest request) {
        Role role = find(id);
        if (role.getVersion() != request.version().longValue()) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT,
                    "The role was modified by someone else. Reload and try again.");
        }
        Map<String, Object> before = snapshot(role);
        role.updateDetails(request.name().trim(), blankToNull(request.description()));
        roles.saveAndFlush(role);
        events.publishEvent(new AuditCommand("ROLE_UPDATED", "Role", String.valueOf(id), before, snapshot(role), null));
        return mapper.toResponse(role, roles.countUsers(id));
    }

    @Transactional
    public RoleDtos.RoleResponse setPermissions(long id, RoleDtos.SetPermissionsRequest request) {
        Role role = find(id);
        List<Permission> newPermissions = resolvePermissions(request.permissions());
        if (UserAdminService.ADMIN_ROLE.equals(role.getCode())) {
            Set<String> codes = newPermissions.stream().map(Permission::getCode).collect(Collectors.toSet());
            if (!codes.containsAll(ADMIN_ESSENTIAL)) {
                throw new BusinessRuleException(ErrorCode.ROLE_PROTECTED,
                        "The ADMIN role must keep " + String.join(", ", ADMIN_ESSENTIAL.stream().sorted().toList())
                                + " so that permissions can always be repaired.");
            }
        }
        Map<String, Object> before = snapshot(role);
        role.replacePermissions(newPermissions);
        roles.saveAndFlush(role);
        users.bumpPermissionVersionForRole(id, clock.instant()); // everyone holding the role gets TOKEN_EXPIRED
        access.evictAllAfterCommit();

        events.publishEvent(new AuditCommand("ROLE_PERMISSIONS_CHANGED", "Role", String.valueOf(id), before,
                snapshot(role), request.reason()));
        return mapper.toResponse(role, roles.countUsers(id));
    }

    @Transactional
    public void delete(long id) {
        Role role = find(id);
        if (role.isSystemRole()) {
            throw new BusinessRuleException(ErrorCode.ROLE_PROTECTED, "System role '" + role.getCode() + "' cannot be deleted.");
        }
        long holders = roles.countUsers(id);
        if (holders > 0) {
            throw new BusinessRuleException(ErrorCode.ROLE_PROTECTED,
                    "Role '" + role.getCode() + "' is assigned to " + holders + " user(s). Reassign them first.");
        }
        Map<String, Object> before = snapshot(role);
        roles.delete(role);
        roles.flush();
        events.publishEvent(new AuditCommand("ROLE_DELETED", "Role", String.valueOf(id), before, null, null));
    }

    private Role find(long id) {
        return roles.findById(id).orElseThrow(() -> new NotFoundException("Role", id));
    }

    private List<Permission> resolvePermissions(Set<String> codes) {
        List<Permission> found = permissions.findByCodeIn(codes);
        Set<String> foundCodes = found.stream().map(Permission::getCode).collect(Collectors.toSet());
        List<String> unknown = codes.stream().filter(c -> !foundCodes.contains(c)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new ValidationFailedException("permissions", "Unknown permission(s): " + String.join(", ", unknown));
        }
        return found;
    }

    private static Map<String, Object> snapshot(Role role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", role.getCode());
        map.put("name", role.getName());
        map.put("description", role.getDescription());
        map.put("systemRole", role.isSystemRole());
        map.put("permissions", role.getPermissions().stream().map(Permission::getCode).sorted().toList());
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
