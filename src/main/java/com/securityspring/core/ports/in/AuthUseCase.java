package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.auth.SessionInfo;
import com.securityspring.core.domain.model.auth.TokenPair;

import java.util.List;

public interface AuthUseCase {
    TokenPair login(String username, String password);

    TokenPair refresh(String oldRefreshToken);

    void logout(String refreshToken);

    void logoutAll(String username);

    List<SessionInfo> listActiveSessions(String username);
}
