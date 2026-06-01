package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.DevChallengeTokenEntity;
import com.securityspring.core.domain.TokenHashUtils;
import com.securityspring.core.domain.model.auth.DevChallengeToken;
import com.securityspring.core.ports.out.twofa.DevChallengeRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class DevChallengeRepositoryImpl implements DevChallengeRepository {

    private final DevChallengeTokenJpaRepository jpa;

    public DevChallengeRepositoryImpl(DevChallengeTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public DevChallengeToken save(String username, String rawToken, long periodT, Instant expiresAt) {
        DevChallengeTokenEntity entity = new DevChallengeTokenEntity();
        entity.setUsername(username);
        entity.setTokenHash(TokenHashUtils.sha256(rawToken));
        entity.setPeriodT(periodT);
        entity.setExpiresAt(expiresAt);
        return toDomain(jpa.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DevChallengeToken> findByToken(String rawToken) {
        return jpa.findByTokenHash(TokenHashUtils.sha256(rawToken)).map(this::toDomain);
    }

    @Override
    public boolean consume(String rawToken) {
        return jpa.consumeIfAvailable(TokenHashUtils.sha256(rawToken), Instant.now()) > 0;
    }

    @Override
    public void deleteExpiredBefore(Instant before) {
        jpa.deleteExpiredBefore(before);
    }

    private DevChallengeToken toDomain(DevChallengeTokenEntity e) {
        return new DevChallengeToken(e.getId(), e.getUsername(), e.getTokenHash(),
                e.getPeriodT(), e.getExpiresAt(), e.getCreatedAt(), e.getUsedAt());
    }
}
