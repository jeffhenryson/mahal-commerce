package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.infra.security.DeviceInfoContext;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.cernecommerce.adapter.out.persistence.entity.RefreshTokenEntity;
import com.cernecommerce.adapter.out.persistence.entity.UserEntity;
import com.cernecommerce.core.domain.exception.auth.InvalidRefreshTokenException;
import com.cernecommerce.core.domain.exception.auth.SessionNotFoundException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenExpiredException;
import com.cernecommerce.core.domain.model.auth.SessionInfo;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.domain.TokenHashUtils;

@Repository
@Transactional
public class RefreshTokenRepositoryImpl implements RefreshTokenPort {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRepositoryImpl.class);

    private final RefreshTokenJpaRepository refreshRepo;
    private final UserJpaRepository userRepo;
    private final long refreshTtlDays;
    private final int maxSessionsPerUser;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenRepositoryImpl(RefreshTokenJpaRepository refreshRepo,
                                      UserJpaRepository userRepo,
                                      @Value("${jwt.refresh-ttl-days}") long refreshTtlDays,
                                      @Value("${auth.max-sessions-per-user:5}") int maxSessionsPerUser) {
        this.refreshRepo = refreshRepo;
        this.userRepo = userRepo;
        this.refreshTtlDays = refreshTtlDays;
        this.maxSessionsPerUser = maxSessionsPerUser;
    }

    @Override
    public String issue(String username) {
        // Obtém apenas o ID do usuário para montar a FK — sem carregar a entidade completa.
        // Toda lógica de negócio sobre o usuário vai pelo UserRepository (port), não por aqui.
        Long userId = userRepo.findIdByUsername(username)
                .orElseThrow(InvalidRefreshTokenException::new);
        UserEntity userRef = userRepo.getReferenceById(userId);

        // Limite de sessões: revoga as mais antigas se o limite (maxSessionsPerUser > 0) for atingido.
        // A nova sessão ainda não foi salva, então o count atual é o número de sessões existentes.
        if (maxSessionsPerUser > 0) {
            Instant now = Instant.now();
            long active = refreshRepo.countActiveByUsername(username, now);
            if (active >= maxSessionsPerUser) {
                // Revoga as (active - maxSessionsPerUser + 1) mais antigas para abrir espaço.
                long toRevoke = active - maxSessionsPerUser + 1;
                refreshRepo.findActiveByUsernameOrderByCreatedAtAsc(username, now)
                        .stream()
                        .limit(toRevoke)
                        .forEach(old -> {
                            old.setRevoked(true);
                            old.setRotatedAt(now);
                            refreshRepo.save(old);
                            log.info("audit.refresh.evicted user={} id={} (session limit={})", username, old.getId(), maxSessionsPerUser);
                        });
            }
        }

        String token = generateOpaqueToken();
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUser(userRef);
        rt.setTokenHash(TokenHashUtils.sha256(token));
        rt.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        resolveDeviceInfo(rt);
        refreshRepo.save(rt);
        log.info("audit.refresh.issued user={}", username);
        return token;
    }

    private void resolveDeviceInfo(RefreshTokenEntity rt) {
        DeviceInfoContext.DeviceInfo info = DeviceInfoContext.get();
        if (info != null) {
            rt.setIpAddress(info.ipAddress());
            rt.setUserAgent(info.userAgent());
        }
    }

    @Override
    public RotationResult rotate(String oldToken) {
        String hash = TokenHashUtils.sha256(oldToken);
        var found = refreshRepo.findByTokenHashForUpdate(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        String username = found.getUser().getUsername();

        // Signal theft to the service layer — it will revoke all sessions.
        if (found.isRevoked()) {
            throw new RefreshTokenAlreadyUsedException(username);
        }

        if (found.getExpiresAt().isBefore(Instant.now())) {
            throw new RefreshTokenExpiredException();
        }

        found.setRevoked(true);
        found.setRotatedAt(Instant.now());
        refreshRepo.save(found);

        String newToken = generateOpaqueToken();
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setUser(found.getUser());
        rt.setTokenHash(TokenHashUtils.sha256(newToken));
        rt.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        // Preserva device info da sessão original — evita perda de IP/UA após rotação.
        rt.setIpAddress(found.getIpAddress());
        rt.setUserAgent(found.getUserAgent());
        refreshRepo.save(rt);
        log.info("audit.refresh.rotated user={}", username);
        return new RotationResult(username, newToken);
    }

    @Override
    public java.util.Optional<String> revoke(String token) {
        return refreshRepo.findByTokenHash(TokenHashUtils.sha256(token)).map(rt -> {
            rt.setRevoked(true);
            rt.setRotatedAt(Instant.now());
            refreshRepo.save(rt);
            String username = rt.getUser().getUsername();
            log.info("audit.refresh.revoked user={}", username);
            return username;
        });
    }

    @Override
    public void revokeAll(String username) {
        int count = refreshRepo.revokeAllByUsername(username, Instant.now());
        log.info("audit.refresh.revokedAll user={} count={}", username, count);
    }

    @Override
    public void deleteExpiredAndRevoked() {
        int deleted = refreshRepo.deleteExpiredAndRevoked(Instant.now());
        if (deleted > 0) log.info("audit.refresh.cleanup deleted={}", deleted);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionInfo> findActiveSessions(String username) {
        return refreshRepo.findActiveByUsername(username, Instant.now())
                .stream()
                .map(r -> new SessionInfo(r.getId(), r.getCreatedAt(), r.getExpiresAt(),
                        r.getIpAddress(), r.getUserAgent()))
                .toList();
    }

    @Override
    public void revokeByIdForUser(Long id, String username) {
        RefreshTokenEntity rt = refreshRepo.findActiveByIdAndUsername(id, username, Instant.now())
                .orElseThrow(SessionNotFoundException::new);
        rt.setRevoked(true);
        rt.setRotatedAt(Instant.now());
        refreshRepo.save(rt);
        log.info("audit.refresh.revokedSingle user={} id={}", username, id);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
