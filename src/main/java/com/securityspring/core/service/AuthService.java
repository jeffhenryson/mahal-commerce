package com.securityspring.core.service;

import com.securityspring.core.domain.exception.RefreshTokenAlreadyUsedException;
import com.securityspring.core.domain.model.TokenPair;
import com.securityspring.core.ports.in.AuthUseCase;
import com.securityspring.core.ports.out.AccessTokenPort;
import com.securityspring.core.ports.out.CredentialVerifierPort;
import com.securityspring.core.ports.out.RefreshTokenPort;
import com.securityspring.core.ports.out.TokenBlocklistPort;
import com.securityspring.core.ports.out.UserAuthoritiesPort;

import java.time.Instant;
import java.util.Set;

public class AuthService implements AuthUseCase {

    private final CredentialVerifierPort credentialVerifier;
    private final AccessTokenPort accessToken;
    private final RefreshTokenPort refreshToken;
    private final UserAuthoritiesPort userAuthorities;
    private final TokenBlocklistPort tokenBlocklist;

    public AuthService(CredentialVerifierPort credentialVerifier,
                       AccessTokenPort accessToken,
                       RefreshTokenPort refreshToken,
                       UserAuthoritiesPort userAuthorities,
                       TokenBlocklistPort tokenBlocklist) {
        this.credentialVerifier = credentialVerifier;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userAuthorities = userAuthorities;
        this.tokenBlocklist = tokenBlocklist;
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
        try {
            RefreshTokenPort.RotationResult result = refreshToken.rotate(oldRefreshToken);
            Set<String> authorities = userAuthorities.loadAuthoritiesByUsername(result.username());
            String access = accessToken.generateFor(result.username(), authorities);
            return new TokenPair(access, result.newToken());
        } catch (RefreshTokenAlreadyUsedException ex) {
            // Token reuse detected: revoke all sessions for this user before re-throwing.
            refreshToken.revokeAll(ex.getUsername());
            tokenBlocklist.blockAllBefore(ex.getUsername(), Instant.now());
            throw ex;
        }
    }

    @Override
    public void logout(String refreshTokenValue) {
        refreshToken.revoke(refreshTokenValue)
                .ifPresent(username -> tokenBlocklist.blockAllBefore(username, Instant.now()));
    }

    @Override
    public void logoutAll(String username) {
        refreshToken.revokeAll(username);
        tokenBlocklist.blockAllBefore(username, Instant.now());
    }
}
