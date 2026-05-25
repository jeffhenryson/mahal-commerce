package com.securityspring.core.domain.exception;

public class PermissionAlreadyExistsException extends RuntimeException {
    public PermissionAlreadyExistsException(String name) {
        super("Permission already exists: " + name);
    }
}
