package com.security_spring.infra.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security_spring.adapter.out.entities.RefreshTokenEntity;
import com.security_spring.adapter.out.entities.UserEntity;
import com.security_spring.adapter.out.repository.RefreshTokenJpaRepository;
import com.security_spring.adapter.out.repository.UserJpaRepository;

@Service
public class RefreshTokenService {

    private final RefreshTokenJpaRepository refreshRepo;
    private final UserJpaRepository userRepo;
    private final long refreshTtlDays;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RefreshTokenService.class);

    public RefreshTokenService(RefreshTokenJpaRepository refreshRepo,
                               UserJpaRepository userRepo,
                               @Value("${jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.refreshRepo = refreshRepo;
        this.userRepo = userRepo;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    public String issueNewRefreshToken(String username) {
        UserEntity user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        String token = generateOpaqueToken();
        String tokenHash = sha256(token);

        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUser(user);
        rt.setTokenHash(tokenHash);
        rt.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        refreshRepo.save(rt);
        log.info("audit.refresh.issued user={}", username);
        return token;
    }

    @Transactional
    public String rotate(String oldToken) {
        var found = refreshRepo.findByTokenHash(sha256(oldToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (found.isRevoked() || found.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }
        found.setRevoked(true);
        found.setRotatedAt(Instant.now());
        refreshRepo.save(found);

        String newToken = generateOpaqueToken();
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUser(found.getUser());
        rt.setTokenHash(sha256(newToken));
        rt.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        refreshRepo.save(rt);
        log.info("audit.refresh.rotated user={}", found.getUser().getUsername());
        return newToken;
    }

    public static record RotationResult(String username, String newToken) {}

    @Transactional
    public RotationResult rotateAndGetUsername(String oldToken) {
        var found = refreshRepo.findByTokenHash(sha256(oldToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (found.isRevoked() || found.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }
        String username = found.getUser().getUsername();
        found.setRevoked(true);
        found.setRotatedAt(Instant.now());
        refreshRepo.save(found);

        String newToken = generateOpaqueToken();
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUser(found.getUser());
        rt.setTokenHash(sha256(newToken));
        rt.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        refreshRepo.save(rt);
        return new RotationResult(username, newToken);
    }

    @Transactional
    public void revoke(String token) {
        refreshRepo.findByTokenHash(sha256(token)).ifPresent(rt -> {
            rt.setRevoked(true);
            rt.setRotatedAt(Instant.now());
            refreshRepo.save(rt);
            log.info("audit.refresh.revoked user={} tokenHash={}", rt.getUser().getUsername(), sha256(token));
        });
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
