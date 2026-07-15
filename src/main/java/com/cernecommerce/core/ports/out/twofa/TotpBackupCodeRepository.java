package com.cernecommerce.core.ports.out.twofa;

import com.cernecommerce.core.domain.model.auth.TotpBackupCode;

import java.util.List;
import java.util.Optional;

public interface TotpBackupCodeRepository {
    void saveAll(String username, List<String> rawCodes);

    Optional<TotpBackupCode> findByCode(String rawCode);

    boolean markAsUsed(String rawCode);

    void deleteByUsername(String username);

    /** Conta quantos backup codes ainda não foram usados para o usuário. */
    int countRemainingByUsername(String username);
}
