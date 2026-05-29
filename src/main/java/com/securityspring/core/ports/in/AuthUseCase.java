package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.auth.LoginResponse;
import com.securityspring.core.domain.model.auth.SessionInfo;
import com.securityspring.core.domain.model.auth.TokenPair;

import java.util.List;

public interface AuthUseCase {
    LoginResponse login(String username, String password);

    /** Conclui o login após validação do código TOTP ou backup code. */
    TokenPair completeTwoFactorLogin(String challengeToken, String totpCode);

    TokenPair refresh(String oldRefreshToken);

    void logout(String refreshToken);

    void logoutAll(String username);

    List<SessionInfo> listActiveSessions(String username);

    void revokeSession(Long sessionId, String username);
}
