package com.securityspring.core.ports.out.twofa;

public interface TwoFactorAuthPort {
    boolean isEnabled(String username);

    /** Gera e persiste um challenge token de curta duração. Retorna o token em texto puro. */
    String issueChallengeToken(String username);
}
