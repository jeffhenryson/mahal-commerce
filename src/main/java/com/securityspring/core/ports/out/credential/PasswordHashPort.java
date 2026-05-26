package com.securityspring.core.ports.out.credential;

public interface PasswordHashPort {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
