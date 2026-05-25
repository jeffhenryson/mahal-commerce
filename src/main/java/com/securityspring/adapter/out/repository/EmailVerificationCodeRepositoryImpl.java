package com.securityspring.adapter.out.repository;

import com.securityspring.adapter.out.entities.EmailVerificationCodeEntity;
import com.securityspring.core.domain.model.EmailVerificationCode;
import com.securityspring.core.ports.out.EmailVerificationCodeRepository;
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
        entity.setCode(code);
        entity.setExpiresAt(expiresAt);
        EmailVerificationCodeEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<EmailVerificationCode> findByCode(String code) {
        return jpaRepository.findByCode(code).map(this::toDomain);
    }

    @Override
    public void deleteByUsername(String username) {
        jpaRepository.deleteByUsername(username);
    }

    private EmailVerificationCode toDomain(EmailVerificationCodeEntity e) {
        return new EmailVerificationCode(e.getId(), e.getUsername(), e.getCode(), e.getExpiresAt(), e.isUsed());
    }
}
