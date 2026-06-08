package com.securityspring.core.service;

import com.securityspring.core.domain.exception.auth.AccountLockedException;
import com.securityspring.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.securityspring.core.domain.model.auth.LoginResponse;
import com.securityspring.core.domain.model.auth.TokenPair;
import com.securityspring.core.ports.out.token.AccessTokenPort;
import com.securityspring.core.ports.out.credential.CredentialVerifierPort;
import com.securityspring.core.ports.out.ratelimit.LoginAttemptPort;
import com.securityspring.core.ports.out.token.RefreshTokenPort;
import com.securityspring.core.ports.out.SystemConfigPort;
import com.securityspring.core.ports.out.token.TokenBlocklistPort;
import com.securityspring.core.ports.out.twofa.TwoFactorAuthPort;
import com.securityspring.core.ports.out.user.UserAuthoritiesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock CredentialVerifierPort credentialVerifier;
    @Mock AccessTokenPort accessToken;
    @Mock RefreshTokenPort refreshToken;
    @Mock UserAuthoritiesPort userAuthorities;
    @Mock TokenBlocklistPort tokenBlocklist;
    @Mock LoginAttemptPort loginAttempt;
    @Mock TwoFactorAuthPort twoFactorAuth;
    @Mock SystemConfigPort systemConfig;

    AuthService authService;

    @BeforeEach
    void setUp() {
        // lenient: só é invocado em testes de login; outros testes (logout, refresh) não o chamam.
        lenient().when(systemConfig.getBoolean("security.2fa.required", false)).thenReturn(false);
        authService = new AuthService(credentialVerifier, accessToken, refreshToken,
                userAuthorities, tokenBlocklist, loginAttempt, twoFactorAuth, systemConfig);
    }

    @Test
    void login_returnsTokenPair() {
        when(loginAttempt.isLocked("alice")).thenReturn(false);
        when(credentialVerifier.verify("alice", "pass"))
                .thenReturn(new CredentialVerifierPort.VerifiedUser("alice", Set.of("ROLE_USER")));
        when(twoFactorAuth.isEnabled("alice")).thenReturn(false);
        when(accessToken.generateFor("alice", Set.of("ROLE_USER"))).thenReturn("access-token");
        when(refreshToken.issue("alice")).thenReturn("refresh-token");

        LoginResponse response = authService.login("alice", "pass");

        assertThat(response.twoFactorRequired()).isFalse();
        assertThat(response.tokenPair().getAccessToken()).isEqualTo("access-token");
        assertThat(response.tokenPair().getRefreshToken()).isEqualTo("refresh-token");
        verify(loginAttempt).recordSuccess("alice");
    }

    @Test
    void login_whenAccountLocked_throwsAccountLockedException() {
        when(loginAttempt.isLocked("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.login("alice", "pass"))
                .isInstanceOf(AccountLockedException.class);

        verifyNoInteractions(credentialVerifier);
    }

    @Test
    void login_onBadCredentials_recordsFailure() {
        when(loginAttempt.isLocked("alice")).thenReturn(false);
        when(credentialVerifier.verify("alice", "wrong"))
                .thenThrow(new RuntimeException("bad credentials"));

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(RuntimeException.class);

        verify(loginAttempt).recordFailure("alice");
    }

    @Test
    void refresh_rotatesTokenAndReturnsNewPair() {
        when(refreshToken.rotate("old-refresh"))
                .thenReturn(new RefreshTokenPort.RotationResult("alice", "new-refresh"));
        when(userAuthorities.loadAuthoritiesByUsername("alice")).thenReturn(Set.of("ROLE_USER"));
        when(accessToken.generateFor("alice", Set.of("ROLE_USER"))).thenReturn("new-access");

        TokenPair pair = authService.refresh("old-refresh");

        assertThat(pair.getAccessToken()).isEqualTo("new-access");
        assertThat(pair.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refresh_onTokenReuse_revokesAllSessionsAndRethrows() {
        when(refreshToken.rotate("stolen-token"))
                .thenThrow(new RefreshTokenAlreadyUsedException("alice"));

        assertThatThrownBy(() -> authService.refresh("stolen-token"))
                .isInstanceOf(RefreshTokenAlreadyUsedException.class);

        verify(refreshToken).revokeAll("alice");
        verify(tokenBlocklist).blockAllBefore(eq("alice"), any());
    }

    @Test
    void logout_revokesRefreshTokenAndBlocksAccessToken() {
        when(refreshToken.revoke("some-refresh-token")).thenReturn(Optional.of("alice"));

        authService.logout("some-refresh-token");

        verify(refreshToken).revoke("some-refresh-token");
        verify(tokenBlocklist).blockAllBefore(eq("alice"), any());
    }

    @Test
    void logout_withUnknownToken_doesNotCallBlocklist() {
        when(refreshToken.revoke("unknown-token")).thenReturn(Optional.empty());

        authService.logout("unknown-token");

        verify(refreshToken).revoke("unknown-token");
        verifyNoInteractions(tokenBlocklist);
    }

    @Test
    void login_when2faEnabled_issuesChallengeAndReturnsTwoFactorResponse() {
        when(loginAttempt.isLocked("alice")).thenReturn(false);
        when(credentialVerifier.verify("alice", "pass"))
                .thenReturn(new CredentialVerifierPort.VerifiedUser("alice", Set.of("ROLE_USER")));
        when(twoFactorAuth.isEnabled("alice")).thenReturn(true);
        when(twoFactorAuth.issueChallengeToken("alice")).thenReturn("challenge-abc");

        LoginResponse response = authService.login("alice", "pass");

        assertThat(response.twoFactorRequired()).isTrue();
        assertThat(response.challengeToken()).isEqualTo("challenge-abc");
        assertThat(response.tokenPair()).isNull();
        verify(twoFactorAuth).issueChallengeToken("alice");
        verify(accessToken, never()).generateFor(any(), any());
    }

    @Test
    void completeTwoFactorLogin_returnsTokenPairOnSuccess() {
        when(twoFactorAuth.completeChallengeLogin("challenge-token", "123456")).thenReturn("alice");
        when(userAuthorities.loadAuthoritiesByUsername("alice")).thenReturn(Set.of("ROLE_USER"));
        when(accessToken.generateFor("alice", Set.of("ROLE_USER"))).thenReturn("new-access");
        when(refreshToken.issue("alice")).thenReturn("new-refresh");

        TokenPair pair = authService.completeTwoFactorLogin("challenge-token", "123456");

        assertThat(pair.getAccessToken()).isEqualTo("new-access");
        assertThat(pair.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void completeTwoFactorLogin_propagatesInvalidCodeException() {
        when(twoFactorAuth.completeChallengeLogin(any(), any()))
                .thenThrow(new com.securityspring.core.domain.exception.auth.InvalidTotpCodeException());

        assertThatThrownBy(() -> authService.completeTwoFactorLogin("token", "bad-code"))
                .isInstanceOf(com.securityspring.core.domain.exception.auth.InvalidTotpCodeException.class);
    }

    @Test
    void completeTwoFactorLogin_propagatesChallengeExpiredException() {
        when(twoFactorAuth.completeChallengeLogin(any(), any()))
                .thenThrow(new com.securityspring.core.domain.exception.auth.TotpChallengeExpiredException());

        assertThatThrownBy(() -> authService.completeTwoFactorLogin("expired-token", "123456"))
                .isInstanceOf(com.securityspring.core.domain.exception.auth.TotpChallengeExpiredException.class);
    }

    @Test
    void logoutAll_revokesAllAndBlocksAccessTokens() {
        authService.logoutAll("alice");
        verify(refreshToken).revokeAll("alice");
        verify(tokenBlocklist).blockAllBefore(eq("alice"), any());
    }

    @Test
    void login_com_2fa_obrigatorio_e_usuario_sem_totp_lanca_TotpSetupRequiredException() {
        when(loginAttempt.isLocked("alice")).thenReturn(false);
        when(credentialVerifier.verify("alice", "pass"))
                .thenReturn(new CredentialVerifierPort.VerifiedUser("alice", Set.of("ROLE_USER")));
        when(systemConfig.getBoolean("security.2fa.required", false)).thenReturn(true);
        when(twoFactorAuth.isEnabled("alice")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "pass"))
                .isInstanceOf(com.securityspring.core.domain.exception.auth.TotpSetupRequiredException.class);

        verify(loginAttempt).recordSuccess("alice");
        verifyNoInteractions(accessToken, refreshToken);
    }

    @Test
    void login_com_2fa_obrigatorio_e_usuario_com_totp_prossegue_para_challenge() {
        when(loginAttempt.isLocked("alice")).thenReturn(false);
        when(credentialVerifier.verify("alice", "pass"))
                .thenReturn(new CredentialVerifierPort.VerifiedUser("alice", Set.of("ROLE_USER")));
        when(systemConfig.getBoolean("security.2fa.required", false)).thenReturn(true);
        when(twoFactorAuth.isEnabled("alice")).thenReturn(true);
        when(twoFactorAuth.issueChallengeToken("alice")).thenReturn("challenge-xyz");

        LoginResponse response = authService.login("alice", "pass");

        assertThat(response.twoFactorRequired()).isTrue();
        assertThat(response.challengeToken()).isEqualTo("challenge-xyz");
        verifyNoInteractions(accessToken, refreshToken);
    }
}
