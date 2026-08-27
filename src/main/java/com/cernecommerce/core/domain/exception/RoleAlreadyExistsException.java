package com.cernecommerce.core.domain.exception;

public class RoleAlreadyExistsException extends RuntimeException {
    public RoleAlreadyExistsException(String name) {
        super("Role already exists: " + name);
    }
}
