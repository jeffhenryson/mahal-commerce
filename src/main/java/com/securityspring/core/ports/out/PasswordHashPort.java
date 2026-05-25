package com.securityspring.core.ports.out;

public interface PasswordHashPort {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
