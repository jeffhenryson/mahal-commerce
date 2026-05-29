package com.securityspring.core.ports.in;

import java.util.List;

public interface TotpUseCase {
    /** Gera um novo secret TOTP. Retorna {secret, otpauthUri}. */
    TotpSetupResult setup(String username);

    /** Confirma o código TOTP após escanear o QR. Ativa 2FA e retorna os backup codes gerados. */
    List<String> confirm(String username, String totpCode);

    /** Desativa 2FA. Exige senha atual e código TOTP (ou backup code) por segurança. */
    void disable(String username, String currentPassword, String totpCode);

    /** Regenera backup codes. Invalida os anteriores. Exige senha atual. */
    List<String> regenerateBackupCodes(String username, String currentPassword);

    record TotpSetupResult(String secret, String otpauthUri) {}
}
