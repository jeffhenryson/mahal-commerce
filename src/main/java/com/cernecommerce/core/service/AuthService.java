package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.auth.AccountLockedException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.cernecommerce.core.domain.exception.auth.TotpSetupRequiredException;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.domain.model.auth.DevElevationResult;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.SessionInfo;
import com.cernecommerce.core.domain.model.auth.TokenPair;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.core.ports.out.credential.CredentialVerifierPort;
import com.cernecommerce.core.ports.out.ratelimit.LoginAttemptPort;
import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import com.cernecommerce.core.ports.out.twofa.TwoFactorAuthPort;
import com.cernecommerce.core.ports.out.user.UserAuthoritiesPort;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AuthService implements AuthUseCase {

    private final CredentialVerifierPort credentialVerifier;
    private final AccessTokenPort accessToken;
    private final RefreshTokenPort refreshToken;
    private final UserAuthoritiesPort userAuthorities;
    private final TokenBlocklistPort tokenBlocklist;
    private final LoginAttemptPort loginAttempt;
    private final TwoFactorAuthPort twoFactorAuth;
    private final SystemConfigPort systemConfig;

    public AuthService(CredentialVerifierPort credentialVerifier,
                       AccessTokenPort accessToken,
                       RefreshTokenPort refreshToken,
                       UserAuthoritiesPort userAuthorities,
                       TokenBlocklistPort tokenBlocklist,
                       LoginAttemptPort loginAttempt,
                       TwoFactorAuthPort twoFactorAuth,
                       SystemConfigPort systemConfig) {
        this.credentialVerifier = credentialVerifier;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userAuthorities = userAuthorities;
        this.tokenBlocklist = tokenBlocklist;
        this.loginAttempt = loginAttempt;
        this.twoFactorAuth = twoFactorAuth;
        this.systemConfig = systemConfig;
    }

    @Override
    public LoginResponse login(String username, String password) {
        if (loginAttempt.isLocked(username)) {
            throw new AccountLockedException(username);
        }
        try {
            CredentialVerifierPort.VerifiedUser verified = credentialVerifier.verify(username, password);
            loginAttempt.recordSuccess(username);

            // Bloqueia login se 2FA obrigatório globalmente e o usuário ainda não o configurou.
            if (systemConfig.getBoolean("security.2fa.required", false)
                    && !twoFactorAuth.isEnabled(verified.username())) {
                throw new TotpSetupRequiredException();
            }

            if (twoFactorAuth.isEnabled(verified.username())) {
                String challengeToken = twoFactorAuth.issueChallengeToken(verified.username());
                return LoginResponse.twoFactorChallenge(challengeToken);
            }

            String access = accessToken.generateFor(verified.username(), verified.authorities());
            String refresh = refreshToken.issue(verified.username());
            return LoginResponse.success(new TokenPair(access, refresh));
        } catch (RuntimeException ex) {
            loginAttempt.recordFailure(username);
            throw ex;
        }
    }

    @Override
    public TokenPair completeTwoFactorLogin(String challengeToken, String totpCode) {
        String username = twoFactorAuth.completeChallengeLogin(challengeToken, totpCode);
        Set<String> authorities = userAuthorities.loadAuthoritiesByUsername(username);
        String access = accessToken.generateFor(username, authorities);
        String refresh = refreshToken.issue(username);
        return new TokenPair(access, refresh, username);
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

    @Override
    @Transactional(readOnly = true)
    public List<SessionInfo> listActiveSessions(String username) {
        return refreshToken.findActiveSessions(username);
    }

    @Override
    public void revokeSession(Long sessionId, String username) {
        refreshToken.revokeByIdForUser(sessionId, username);
    }

    @Override
    public DevElevationResult completeDevElevation(String rawDevToken, String secondTotpCode) {
        String username = twoFactorAuth.completeDevChallenge(rawDevToken, secondTotpCode);
        Set<String> authorities = new HashSet<>(userAuthorities.loadAuthoritiesByUsername(username));
        // DEV_ELEVATED sinaliza que o duplo TOTP foi concluído — protege endpoints da área DEV.
        authorities.add("DEV_ELEVATED");
        String devAccessToken = accessToken.generateFor(username, authorities);
        return new DevElevationResult(username, devAccessToken);
    }
}
