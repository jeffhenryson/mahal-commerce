package com.securityspring.adapter.out.oauth;

import com.securityspring.core.domain.exception.auth.OAuthTokenInvalidException;
import com.securityspring.core.domain.model.auth.GoogleUserInfo;
import com.securityspring.core.ports.out.oauth.GoogleTokenVerifierPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenVerifierAdapter implements GoogleTokenVerifierPort {

    private final JwtDecoder googleJwtDecoder;

    public GoogleTokenVerifierAdapter(@Qualifier("googleJwtDecoder") JwtDecoder googleJwtDecoder) {
        this.googleJwtDecoder = googleJwtDecoder;
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        try {
            Jwt jwt = googleJwtDecoder.decode(idToken);
            if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
                throw new OAuthTokenInvalidException("Email Google não verificado");
            }
            return new GoogleUserInfo(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name")
            );
        } catch (OAuthTokenInvalidException ex) {
            throw ex;
        } catch (JwtException ex) {
            throw new OAuthTokenInvalidException("Token Google inválido ou expirado");
        }
    }
}
