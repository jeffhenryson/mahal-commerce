package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.TotpChallengeTokenEntity;
import com.cernecommerce.core.domain.TokenHashUtils;
import com.cernecommerce.core.domain.model.auth.TotpChallengeToken;
import com.cernecommerce.core.ports.out.twofa.TotpChallengeTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class TotpChallengeTokenRepositoryImpl implements TotpChallengeTokenRepository {

    private final TotpChallengeTokenJpaRepository jpaRepository;

    public TotpChallengeTokenRepositoryImpl(TotpChallengeTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public TotpChallengeToken save(String username, String rawToken, Instant expiresAt) {
        TotpChallengeTokenEntity entity = new TotpChallengeTokenEntity();
        entity.setUsername(username);
        entity.setTokenHash(TokenHashUtils.sha256(rawToken));
        entity.setExpiresAt(expiresAt);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TotpChallengeToken> findByToken(String rawToken) {
        return jpaRepository.findByTokenHash(TokenHashUtils.sha256(rawToken)).map(this::toDomain);
    }

    @Override
    public boolean markAsUsed(String rawToken) {
        return jpaRepository.markAsUsedIfAvailable(TokenHashUtils.sha256(rawToken), Instant.now()) > 0;
    }

    @Override
    public void deleteByUsername(String username) {
        jpaRepository.deleteByUsername(username);
    }

    @Override
    public void deleteExpiredBefore(Instant before) {
        jpaRepository.deleteExpiredBefore(before);
    }

    private TotpChallengeToken toDomain(TotpChallengeTokenEntity e) {
        return new TotpChallengeToken(e.getId(), e.getUsername(), e.getTokenHash(),
                e.getExpiresAt(), e.getCreatedAt(), e.getUsedAt());
    }
}
