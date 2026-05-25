package com.security_spring.infra.security.jwt;

import com.security_spring.core.ports.out.AccessTokenPort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtAccessTokenAdapter implements AccessTokenPort {

    private final JwtService jwtService;

    public JwtAccessTokenAdapter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public String generateFor(String username, Set<String> authorities) {
        var userDetails = User.withUsername(username)
                .password("")
                .authorities(authorities.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toSet()))
                .build();
        return jwtService.generateAccessToken(userDetails);
    }
}
