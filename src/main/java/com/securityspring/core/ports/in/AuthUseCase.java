package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.TokenPair;

public interface AuthUseCase {
    TokenPair login(String username, String password);

    TokenPair refresh(String oldRefreshToken);

    void logout(String refreshToken);

    void logoutAll(String username);
}
