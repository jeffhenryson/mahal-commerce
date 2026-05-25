package com.securityspring.core.domain.exception;

public class PermissionNotFoundException extends RuntimeException {
    public PermissionNotFoundException(String name) {
        super("Permission not found: " + name);
    }
}
