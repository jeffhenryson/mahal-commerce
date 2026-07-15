package com.cernecommerce.adapter.out.jwt;

import com.cernecommerce.core.ports.out.token.AccessTokenPort;
import com.cernecommerce.infra.security.jwt.JwtService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class JwtAccessTokenAdapter implements AccessTokenPort {

    private final JwtService jwtService;

    public JwtAccessTokenAdapter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public String generateFor(String username, Set<String> authorities) {
        return jwtService.generateAccessToken(username, authorities);
    }
}
