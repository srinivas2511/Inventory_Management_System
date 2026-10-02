package com.springmfg.ims.iam.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "users", schema = "ims")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(unique = true, length = 20)
    private String employeeCode;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    private Instant passwordChangedAt;

    @Column(nullable = false)
    private boolean mustChangePassword = true;

    @Column(nullable = false)
    private int failedAttempts = 0;

    private Instant lockedUntil;

    @Column(nullable = false)
    private int permissionVersion = 1;

    @Column(nullable = false)
    private boolean active = true;

    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles", schema = "ims",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new LinkedHashSet<>();

    public User() {}

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public Set<String> getPermissionCodes() {
        return roles.stream()
            .flatMap(r -> r.getPermissions().stream())
            .map(Permission::getCode)
            .collect(Collectors.toSet());
    }

    public Set<String> getRoleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toSet());
    }

    // ── getters ──────────────────────────────────────────────────────────────
    public String getUsername() { return username; }
    public String getEmployeeCode() { return employeeCode; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public int getPermissionVersion() { return permissionVersion; }
    public boolean isActive() { return active; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Set<Role> getRoles() { return roles; }

    // ── setters ──────────────────────────────────────────────────────────────
    public void setUsername(String username) { this.username = username; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setPasswordChangedAt(Instant passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public void setPermissionVersion(int permissionVersion) { this.permissionVersion = permissionVersion; }
    public void setActive(boolean active) { this.active = active; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}
