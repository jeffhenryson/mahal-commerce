package com.security_spring.infra.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.security_spring.adapter.out.entities.UserEntity;
import com.security_spring.adapter.out.repository.RefreshTokenJpaRepository;
import com.security_spring.adapter.out.repository.UserJpaRepository;

@SpringBootTest
@Testcontainers
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
public class RefreshTokenServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("security")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    RefreshTokenService refreshTokenService;
    @Autowired
    UserJpaRepository userRepo;
    @Autowired
    RefreshTokenJpaRepository refreshRepo;

    @BeforeEach
    void seedUser() {
        if (userRepo.findByUsername("bob").isEmpty()) {
            UserEntity u = new UserEntity();
            u.setUsername("bob");
            u.setPassword("pwd");
            userRepo.save(u);
        }
    }

    @Test
    void issue_rotate_and_revoke_refresh_token() {
        String token = refreshTokenService.issueNewRefreshToken("bob");
        assertNotNull(token);

        String hash = sha256(token);
        var row = refreshRepo.findByTokenHash(hash).orElseThrow();
        assertFalse(row.isRevoked());
        assertTrue(row.getExpiresAt().isAfter(Instant.now()));

        var rotated = refreshTokenService.rotateAndGetUsername(token);
        assertEquals("bob", rotated.username());
        assertNotEquals(token, rotated.newToken());

        var oldRow = refreshRepo.findByTokenHash(hash).orElseThrow();
        assertTrue(oldRow.isRevoked());

        refreshTokenService.revoke(rotated.newToken());
        var newHash = sha256(rotated.newToken());
        var newRow = refreshRepo.findByTokenHash(newHash).orElseThrow();
        assertTrue(newRow.isRevoked());
    }

    private static String sha256(String value) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
