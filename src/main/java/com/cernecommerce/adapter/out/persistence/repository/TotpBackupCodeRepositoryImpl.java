package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.TotpBackupCodeEntity;
import com.cernecommerce.core.domain.TokenHashUtils;
import com.cernecommerce.core.domain.model.auth.TotpBackupCode;
import com.cernecommerce.core.ports.out.twofa.TotpBackupCodeRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class TotpBackupCodeRepositoryImpl implements TotpBackupCodeRepository {

    private final TotpBackupCodeJpaRepository jpaRepository;

    public TotpBackupCodeRepositoryImpl(TotpBackupCodeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void saveAll(String username, List<String> rawCodes) {
        List<TotpBackupCodeEntity> entities = rawCodes.stream().map(raw -> {
            TotpBackupCodeEntity e = new TotpBackupCodeEntity();
            e.setUsername(username);
            e.setCodeHash(TokenHashUtils.sha256(raw));
            return e;
        }).toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TotpBackupCode> findByCode(String rawCode) {
        return jpaRepository.findByCodeHash(TokenHashUtils.sha256(rawCode)).map(this::toDomain);
    }

    @Override
    public boolean markAsUsed(String rawCode) {
        return jpaRepository.markAsUsedIfAvailable(TokenHashUtils.sha256(rawCode), Instant.now()) > 0;
    }

    @Override
    public void deleteByUsername(String username) {
        jpaRepository.deleteByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public int countRemainingByUsername(String username) {
        return jpaRepository.countRemainingByUsername(username);
    }

    private TotpBackupCode toDomain(TotpBackupCodeEntity e) {
        return new TotpBackupCode(e.getId(), e.getUsername(), e.getCodeHash(), e.getUsedAt());
    }
}
