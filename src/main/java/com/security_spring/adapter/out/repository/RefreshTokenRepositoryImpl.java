package com.security_spring.adapter.out.repository;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.security_spring.adapter.out.entities.RefreshTokenEntity;
import com.security_spring.adapter.out.entities.UserEntity;
import com.security_spring.core.ports.out.RefreshTokenPort;

@Repository
public class RefreshTokenRepositoryImpl implements RefreshTokenPort {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRepositoryImpl.class);

    private final RefreshTokenJpaRepository refreshRepo;
    private final UserJpaRepository userRepo;
    private final long refreshTtlDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenRepositoryImpl(RefreshTokenJpaRepository refreshRepo,
                                      UserJpaRepository userRepo,
                                      @Value("${jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.refreshRepo = refreshRepo;
        this.userRepo = userRepo;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Override
    @Transactional
    public String issue(String username) {
        UserEntity user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        String token = generateOpaqueToken();
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUser(user);
        rt.setTokenHash(sha256(token));
        rt.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        refreshRepo.save(rt);
        log.info("audit.refresh.issued user={}", username);
        return token;
    }

    @Override
    @Transactional
    public RotationResult rotate(String oldToken) {
        String hash = sha256(oldToken);
        var found = refreshRepo.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        String username = found.getUser().getUsername();

        // Token already revoked → possible theft; invalidate entire session family.
        if (found.isRevoked()) {
            int revoked = refreshRepo.revokeAllByUsername(username, Instant.now());
            log.warn("audit.refresh.reuse-detected user={} revokedAll={}", username, revoked);
            throw new IllegalArgumentException("Refresh token already used — all sessions revoked");
        }

        if (found.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired");
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
        log.info("audit.refresh.rotated user={}", username);
        return new RotationResult(username, newToken);
    }

    @Override
    @Transactional
    public void revoke(String token) {
        refreshRepo.findByTokenHash(sha256(token)).ifPresent(rt -> {
            rt.setRevoked(true);
            rt.setRotatedAt(Instant.now());
            refreshRepo.save(rt);
            log.info("audit.refresh.revoked user={}", rt.getUser().getUsername());
        });
    }

    @Override
    @Transactional
    public void revokeAll(String username) {
        int count = refreshRepo.revokeAllByUsername(username, Instant.now());
        log.info("audit.refresh.revokedAll user={} count={}", username, count);
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
