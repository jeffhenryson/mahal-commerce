package com.security_spring.core.domain.exception;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(Long id) {
        super("Role not found: id=" + id);
    }

    public RoleNotFoundException(String name) {
        super("Role not found: name=" + name);
    }
}