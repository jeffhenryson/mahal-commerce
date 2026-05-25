package com.security_spring.infra.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMinutes;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-ttl-minutes}") long accessTtlMinutes) {
        this.key = Keys.hmacShaKeyFor(decodeSecret(secret));
        this.accessTtlMinutes = accessTtlMinutes;
    }

    // Tries to decode as base64 first (production secrets); falls back to raw UTF-8 bytes.
    private static byte[] decodeSecret(String secret) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) return decoded;
        } catch (IllegalArgumentException ignored) {
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateAccessToken(UserDetails user) {
        Instant now = Instant.now();
        Set<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Instant extractIssuedAt(String token) {
        return parseClaims(token).getIssuedAt().toInstant();
    }

    public Set<String> extractRoles(String token) {
        Object roles = parseClaims(token).get("roles");
        if (roles instanceof Collection<?> coll) {
            return coll.stream().map(Object::toString).collect(Collectors.toSet());
        }
        return Set.of();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
