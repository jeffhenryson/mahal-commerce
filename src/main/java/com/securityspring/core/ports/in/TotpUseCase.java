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

    /** Retorna true se o 2FA (TOTP) está ativado para o usuário. */
    boolean isEnabled(String username);

    /** Conta quantos backup codes ainda não foram usados para o usuário. */
    int countBackupCodesRemaining(String username);

    /**
     * Inicia troca de dispositivo 2FA. Valida o código atual antes de gerar novo secret,
     * garantindo que apenas quem possui acesso ao app atual pode trocar.
     */
    TotpSetupResult replaceTotp(String username, String currentTotpCode);

    /**
     * Etapa 1 do duplo TOTP DEV: valida o primeiro código e armazena o período T.
     * Retorna um rawDevToken temporário (TTL 90s) que o frontend usa na etapa 2.
     */
    String issueDevFirstCode(String username, String firstTotpCode);

    /**
     * Etapa 2 do duplo TOTP DEV: valida que o segundo código pertence ao período T+1.
     * Retorna o username se válido — o caller emite o access token DEV-elevado.
     */
    String completeDevChallenge(String rawDevToken, String secondTotpCode);

    record TotpSetupResult(String secret, String otpauthUri) {}
}
