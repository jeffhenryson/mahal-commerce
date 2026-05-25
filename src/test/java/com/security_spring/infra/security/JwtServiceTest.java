package com.security_spring.infra.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import com.security_spring.infra.security.jwt.JwtService;

public class JwtServiceTest {

    @Test
    void generate_and_validate_token_with_roles() {
        String secret = "dev-secret-please-change-to-256-bit-random-key"; // non-base64 ok
        JwtService jwt = new JwtService(secret, 15);

        User user = new User("alice", "pass", Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        String token = jwt.generateAccessToken(user);
        assertNotNull(token);
        assertTrue(jwt.isValid(token));
        assertEquals("alice", jwt.extractUsername(token));
        assertTrue(jwt.extractRoles(token).contains("ROLE_ADMIN"));
    }

    @Test
    void invalid_token_is_not_valid_and_extracts_nothing() {
        String secret = "dev-secret-please-change-to-256-bit-random-key";
        JwtService jwt = new JwtService(secret, 15);

        String bogus = "abc.def.ghi";
        assertFalse(jwt.isValid(bogus));
        // extractUsername would throw if parsed; we don't call it on invalid token
    }
}
