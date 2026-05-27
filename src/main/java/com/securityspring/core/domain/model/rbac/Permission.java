package com.securityspring.core.domain.model.rbac;

public class Permission {

    private Long id;
    private String name;

    public Permission() {
    }

    public Permission(String name) {
        this.name = name;
    }

    /** Factory para reconstituição a partir de persistência. */
    public static Permission of(Long id, String name) {
        Permission p = new Permission();
        p.id = id;
        p.name = name;
        return p;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
