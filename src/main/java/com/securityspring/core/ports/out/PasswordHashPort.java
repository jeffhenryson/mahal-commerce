package com.security_spring.core.ports.out;

public interface PasswordHashPort {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
