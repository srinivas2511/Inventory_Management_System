package com.springmfg.ims.iam;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ManyToMany(fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 50)
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    protected Role() {
    }

    public Role(String code, String name, String description, boolean systemRole) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
    }

    public void updateDetails(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** Replaces the permission set. The caller bumps affected users' permission versions and evicts the cache. */
    public void replacePermissions(java.util.Collection<Permission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
