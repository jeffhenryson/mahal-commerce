package com.cernecommerce.core.domain.model.rbac;

import java.util.HashSet;
import java.util.Set;

public class Role {

    private Long id;
    private String name;
    private Set<Permission> permissions = new HashSet<>();

    public Role() {
    }

    public Role(String name) {
        this.name = name;
    }

    /** Factory para reconstituição a partir de persistência. */
    public static Role of(Long id, String name, Set<Permission> permissions) {
        Role r = new Role();
        r.id = id;
        r.name = name;
        r.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
        return r;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }
}
