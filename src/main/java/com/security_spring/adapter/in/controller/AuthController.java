package com.security_spring.adapter.in.controller;

import com.security_spring.adapter.in.dtos.LoginRequest;
import com.security_spring.adapter.in.dtos.LogoutRequest;
import com.security_spring.adapter.in.dtos.RefreshRequest;
import com.security_spring.adapter.in.dtos.TokenPairResponse;
import com.security_spring.adapter.in.dtos.TokenResponse;
import com.security_spring.infra.security.JwtService;
import com.security_spring.infra.security.RefreshTokenService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          RefreshTokenService refreshService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshService = refreshService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserDetails user = (UserDetails) auth.getPrincipal();
        String access = jwtService.generateAccessToken(user);
        String refresh = refreshService.issueNewRefreshToken(user.getUsername());
        return ResponseEntity.ok(new TokenPairResponse(access, refresh));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        var result = refreshService.rotateAndGetUsername(request.getRefreshToken());
        // Emite novo access token para o mesmo usuário
        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(result.username())
                .password("")
                .authorities(java.util.Collections.emptyList()) // authorities serão resolvidas no filtro via UserDetailsService
                .build();
        String newAccess = jwtService.generateAccessToken(userDetails);
        return ResponseEntity.ok(new TokenPairResponse(newAccess, result.newToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        refreshService.revoke(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
