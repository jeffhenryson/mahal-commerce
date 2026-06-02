package com.securityspring.core.ports.out.twofa;

public interface TwoFactorAuthPort {
    boolean isEnabled(String username);

    /** Gera e persiste um challenge token de curta duração. Retorna o token em texto puro. */
    String issueChallengeToken(String username);

    /** Valida challenge token + código TOTP (ou backup code). Retorna o username se válido. */
    String completeChallengeLogin(String challengeToken, String totpCode);

    /**
     * Etapa 1 DEV: valida o primeiro código TOTP e armazena o período T.
     * Retorna um rawDevToken (TTL 90s) que identifica o desafio para a etapa 2.
     */
    String issueDevFirstCode(String username, String firstTotpCode);

    /**
     * Etapa 2 DEV: valida que o segundo código pertence ao período T+1.
     * Retorna o username se o par de códigos consecutivos é válido.
     */
    String completeDevChallenge(String rawDevToken, String secondTotpCode);

    /**
     * Valida um código TOTP (ou backup code) para o usuário sem gerar tokens.
     * Retorna true se o código é válido; não lança exceção — o chamador decide o erro.
     */
    boolean validateTotpCode(String username, String code);
}
