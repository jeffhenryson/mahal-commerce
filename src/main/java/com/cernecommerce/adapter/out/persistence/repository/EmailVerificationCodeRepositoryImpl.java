package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.EmailVerificationCodeEntity;
import com.cernecommerce.core.domain.model.auth.EmailVerificationCode;
import com.cernecommerce.core.ports.out.notification.EmailVerificationCodeRepository;
import com.cernecommerce.core.domain.TokenHashUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class EmailVerificationCodeRepositoryImpl implements EmailVerificationCodeRepository {

    private final EmailVerificationCodeJpaRepository jpaRepository;

    public EmailVerificationCodeRepositoryImpl(EmailVerificationCodeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public EmailVerificationCode save(String username, String code, Instant expiresAt) {
        EmailVerificationCodeEntity entity = new EmailVerificationCodeEntity();
        entity.setUsername(username);
        entity.setCode(TokenHashUtils.sha256(code));
        entity.setExpiresAt(expiresAt);
        EmailVerificationCodeEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailVerificationCode> findByCode(String code) {
        return jpaRepository.findByCode(TokenHashUtils.sha256(code)).map(this::toDomain);
    }

    @Override
    public boolean markAsUsed(String code) {
        return jpaRepository.markAsUsedIfNotClaimed(TokenHashUtils.sha256(code)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailVerificationCode> findByUsername(String username) {
        return jpaRepository.findFirstByUsernameOrderByExpiresAtDescIdDesc(username).map(this::toDomain);
    }

    @Override
    public void deleteByUsername(String username) {
        jpaRepository.deleteByUsername(username);
    }

    @Override
    public void deleteExpiredBefore(Instant before) {
        jpaRepository.deleteExpiredBefore(before);
    }

    private EmailVerificationCode toDomain(EmailVerificationCodeEntity e) {
        return new EmailVerificationCode(e.getId(), e.getUsername(), e.getCode(), e.getExpiresAt(), e.getSentAt(), e.isUsed());
    }
}
