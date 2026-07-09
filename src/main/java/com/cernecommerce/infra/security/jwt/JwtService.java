package com.cernecommerce.infra.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long accessTtlMinutes;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-ttl-minutes}") long accessTtlMinutes,
            @Value("${jwt.issuer:security-spring}") String issuer,
            @Value("${jwt.audience:api}") String audience) {
        byte[] keyBytes = decodeSecret(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "jwt.secret must produce at least 256 bits (32 bytes) of key material — " +
                "got " + keyBytes.length + " bytes. " +
                "Use a base64-encoded 256-bit random key or a raw string of at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlMinutes = accessTtlMinutes;
        this.issuer = issuer;
        this.audience = audience;
    }

    // Tries to decode as base64 first (production secrets); falls back to raw UTF-8 bytes.
    private static byte[] decodeSecret(String secret) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) return decoded;
            log.warn("jwt.secret: base64 decode produziu {} bytes (< 32) — usando UTF-8 raw como fallback. "
                    + "Configure uma chave base64 de 256 bits para eliminar este aviso.", decoded.length);
        } catch (IllegalArgumentException ignored) {
            log.warn("jwt.secret não é base64 válido — usando UTF-8 raw como fallback. "
                    + "Em produção use uma chave base64-encoded de 256 bits (32 bytes).");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateAccessToken(String username, Set<String> authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("roles", authorities)
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
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
