package com.security_spring.core.ports.in;

import com.security_spring.core.domain.model.TokenPair;

public interface AuthUseCase {
    TokenPair login(String username, String password);

    TokenPair refresh(String oldRefreshToken);

    void logout(String refreshToken);

    void logoutAll(String username);
}
