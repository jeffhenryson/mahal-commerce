package com.cernecommerce.adapter.out.oauth;

import com.cernecommerce.core.domain.exception.auth.OAuthTokenInvalidException;
import com.cernecommerce.core.domain.model.auth.GoogleUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GoogleTokenVerifierAdapterTest {

    private JwtDecoder jwtDecoder;
    private GoogleTokenVerifierAdapter adapter;

    @BeforeEach
    void setup() {
        jwtDecoder = mock(JwtDecoder.class);
        adapter = new GoogleTokenVerifierAdapter(jwtDecoder);
    }

    @Test
    void verify_valid_token_returns_user_info() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "google-123",
                "email", "alice@example.com",
                "name", "Alice",
                "email_verified", true
        ));
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        GoogleUserInfo info = adapter.verify("valid-token");

        assertThat(info.googleId()).isEqualTo("google-123");
        assertThat(info.email()).isEqualTo("alice@example.com");
        assertThat(info.name()).isEqualTo("Alice");
    }

    @Test
    void verify_unverified_email_throws_OAuthTokenInvalidException() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "google-123",
                "email", "unverified@example.com",
                "name", "Bob",
                "email_verified", false
        ));
        when(jwtDecoder.decode("unverified-token")).thenReturn(jwt);

        assertThatThrownBy(() -> adapter.verify("unverified-token"))
                .isInstanceOf(OAuthTokenInvalidException.class)
                .hasMessageContaining("não verificado");
    }

    @Test
    void verify_missing_email_verified_claim_throws_OAuthTokenInvalidException() {
        Jwt jwt = buildJwt(Map.of(
                "sub", "google-123",
                "email", "noVerified@example.com",
                "name", "Charlie"
                // sem email_verified
        ));
        when(jwtDecoder.decode("no-verified-token")).thenReturn(jwt);

        assertThatThrownBy(() -> adapter.verify("no-verified-token"))
                .isInstanceOf(OAuthTokenInvalidException.class);
    }

    @Test
    void verify_invalid_jwt_throws_OAuthTokenInvalidException() {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> adapter.verify("bad-token"))
                .isInstanceOf(OAuthTokenInvalidException.class)
                .hasMessageContaining("inválido");
    }

    private static Jwt buildJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
