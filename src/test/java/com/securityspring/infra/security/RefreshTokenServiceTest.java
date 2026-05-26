package com.securityspring.infra.security;

import static org.junit.jupiter.api.Assertions.*;

import com.securityspring.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.securityspring.adapter.out.persistence.repository.RefreshTokenRepositoryImpl;
import com.securityspring.adapter.out.persistence.repository.UserJpaRepository;
import com.securityspring.adapter.out.persistence.entity.UserEntity;
import com.securityspring.infra.security.support.TestHashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

@SpringBootTest
@Testcontainers
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
        // Run Flyway so migrations V1–V7 are applied against the real schema.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.h2.console.enabled", () -> "false");
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        registry.add("jwt.secret", () -> "test-only-secret-key-must-be-32-chars-min");
        registry.add("jwt.access-ttl-minutes", () -> "15");
        registry.add("jwt.refresh-ttl-days", () -> "7");
    }

    @Autowired
    RefreshTokenRepositoryImpl refreshTokenRepository;
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
        String token = refreshTokenRepository.issue("bob");
        assertNotNull(token);

        String hash = TestHashUtils.sha256(token);
        var row = refreshRepo.findByTokenHash(hash).orElseThrow();
        assertFalse(row.isRevoked());
        assertTrue(row.getExpiresAt().isAfter(Instant.now()));

        var rotated = refreshTokenRepository.rotate(token);
        assertEquals("bob", rotated.username());
        assertNotEquals(token, rotated.newToken());

        var oldRow = refreshRepo.findByTokenHash(hash).orElseThrow();
        assertTrue(oldRow.isRevoked());

        refreshTokenRepository.revoke(rotated.newToken());
        var newHash = TestHashUtils.sha256(rotated.newToken());
        var newRow = refreshRepo.findByTokenHash(newHash).orElseThrow();
        assertTrue(newRow.isRevoked());
    }
}
