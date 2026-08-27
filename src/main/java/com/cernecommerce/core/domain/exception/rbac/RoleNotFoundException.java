package com.cernecommerce.core.domain.exception.rbac;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(Long id) {
        super("Role not found: id=" + id);
    }

    public RoleNotFoundException(String name) {
        super("Role not found: name=" + name);
    }
}