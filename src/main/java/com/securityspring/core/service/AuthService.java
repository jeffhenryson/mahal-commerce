package com.securityspring.core.service;

import com.securityspring.core.domain.exception.AccountLockedException;
import com.securityspring.core.domain.exception.RefreshTokenAlreadyUsedException;
import com.securityspring.core.domain.model.TokenPair;
import com.securityspring.core.ports.in.AuthUseCase;
import com.securityspring.core.ports.out.token.AccessTokenPort;
import com.securityspring.core.ports.out.credential.CredentialVerifierPort;
import com.securityspring.core.ports.out.ratelimit.LoginAttemptPort;
import com.securityspring.core.ports.out.token.RefreshTokenPort;
import com.securityspring.core.ports.out.token.TokenBlocklistPort;
import com.securityspring.core.ports.out.user.UserAuthoritiesPort;

import java.time.Instant;
import java.util.Set;

public class AuthService implements AuthUseCase {

    private final CredentialVerifierPort credentialVerifier;
    private final AccessTokenPort accessToken;
    private final RefreshTokenPort refreshToken;
    private final UserAuthoritiesPort userAuthorities;
    private final TokenBlocklistPort tokenBlocklist;
    private final LoginAttemptPort loginAttempt;

    public AuthService(CredentialVerifierPort credentialVerifier,
                       AccessTokenPort accessToken,
                       RefreshTokenPort refreshToken,
                       UserAuthoritiesPort userAuthorities,
                       TokenBlocklistPort tokenBlocklist,
                       LoginAttemptPort loginAttempt) {
        this.credentialVerifier = credentialVerifier;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userAuthorities = userAuthorities;
        this.tokenBlocklist = tokenBlocklist;
        this.loginAttempt = loginAttempt;
    }

    @Override
    public TokenPair login(String username, String password) {
        if (loginAttempt.isLocked(username)) {
            throw new AccountLockedException(username);
        }
        try {
            CredentialVerifierPort.VerifiedUser verified = credentialVerifier.verify(username, password);
            loginAttempt.recordSuccess(username);
            String access = accessToken.generateFor(verified.username(), verified.authorities());
            String refresh = refreshToken.issue(verified.username());
            return new TokenPair(access, refresh);
        } catch (RuntimeException ex) {
            loginAttempt.recordFailure(username);
            throw ex;
        }
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
