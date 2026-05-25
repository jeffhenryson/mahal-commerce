package com.security_spring.core.service;

import com.security_spring.core.domain.exception.RefreshTokenAlreadyUsedException;
import com.security_spring.core.domain.model.TokenPair;
import com.security_spring.core.ports.out.AccessTokenPort;
import com.security_spring.core.ports.out.CredentialVerifierPort;
import com.security_spring.core.ports.out.RefreshTokenPort;
import com.security_spring.core.ports.out.TokenBlocklistPort;
import com.security_spring.core.ports.out.UserAuthoritiesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock CredentialVerifierPort credentialVerifier;
    @Mock AccessTokenPort accessToken;
    @Mock RefreshTokenPort refreshToken;
    @Mock UserAuthoritiesPort userAuthorities;
    @Mock TokenBlocklistPort tokenBlocklist;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(credentialVerifier, accessToken, refreshToken,
                userAuthorities, tokenBlocklist);
    }

    @Test
    void login_returnsTokenPair() {
        when(credentialVerifier.verify("alice", "pass"))
                .thenReturn(new CredentialVerifierPort.VerifiedUser("alice", Set.of("ROLE_USER")));
        when(accessToken.generateFor("alice", Set.of("ROLE_USER"))).thenReturn("access-token");
        when(refreshToken.issue("alice")).thenReturn("refresh-token");

        TokenPair pair = authService.login("alice", "pass");

        assertThat(pair.getAccessToken()).isEqualTo("access-token");
        assertThat(pair.getRefreshToken()).isEqualTo("refresh-token");
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
    void logout_revokesRefreshToken() {
        authService.logout("some-refresh-token");
        verify(refreshToken).revoke("some-refresh-token");
    }

    @Test
    void logoutAll_revokesAllAndBlocksAccessTokens() {
        authService.logoutAll("alice");
        verify(refreshToken).revokeAll("alice");
        verify(tokenBlocklist).blockAllBefore(eq("alice"), any());
    }
}
