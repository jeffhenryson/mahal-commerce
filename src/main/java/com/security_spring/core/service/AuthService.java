package com.security_spring.core.service;

import com.security_spring.core.domain.model.TokenPair;
import com.security_spring.core.ports.in.AuthUseCase;
import com.security_spring.core.ports.out.AccessTokenPort;
import com.security_spring.core.ports.out.CredentialVerifierPort;
import com.security_spring.core.ports.out.RefreshTokenPort;
import com.security_spring.core.ports.out.UserAuthoritiesPort;

import java.util.Set;

public class AuthService implements AuthUseCase {

    private final CredentialVerifierPort credentialVerifier;
    private final AccessTokenPort accessToken;
    private final RefreshTokenPort refreshToken;
    private final UserAuthoritiesPort userAuthorities;

    public AuthService(CredentialVerifierPort credentialVerifier,
                       AccessTokenPort accessToken,
                       RefreshTokenPort refreshToken,
                       UserAuthoritiesPort userAuthorities) {
        this.credentialVerifier = credentialVerifier;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userAuthorities = userAuthorities;
    }

    @Override
    public TokenPair login(String username, String password) {
        CredentialVerifierPort.VerifiedUser verified = credentialVerifier.verify(username, password);
        String access = accessToken.generateFor(verified.username(), verified.authorities());
        String refresh = refreshToken.issue(verified.username());
        return new TokenPair(access, refresh);
    }

    @Override
    public TokenPair refresh(String oldRefreshToken) {
        RefreshTokenPort.RotationResult result = refreshToken.rotate(oldRefreshToken);
        Set<String> authorities = userAuthorities.loadAuthoritiesByUsername(result.username());
        String access = accessToken.generateFor(result.username(), authorities);
        return new TokenPair(access, result.newToken());
    }

    @Override
    public void logout(String refreshTokenValue) {
        refreshToken.revoke(refreshTokenValue);
    }

    @Override
    public void logoutAll(String username) {
        refreshToken.revokeAll(username);
    }
}
