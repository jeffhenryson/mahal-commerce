package com.security_spring.infra.security.jwt;

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final Key key;
    private final long accessTtlMinutes;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-ttl-minutes}") long accessTtlMinutes) {
        byte[] keyBytes = Decoders.BASE64.decode(base64IfNot(secret));
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public String generateAccessToken(UserDetails user) {
        Instant now = Instant.now();
        Set<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("roles", roles)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(accessTtlMinutes, ChronoUnit.MINUTES)))
                .signWith(key, SignatureAlgorithm.HS256)
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

    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(String token) {
        Object roles = parseClaims(token).get("roles");
        if (roles instanceof Set<?>) {
            return (Set<String>) roles;
        }
        if (roles instanceof java.util.Collection<?>) {
            return ((java.util.Collection<?>) roles).stream().map(Object::toString).collect(Collectors.toSet());
        }
        return Set.of();
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    private static String base64IfNot(String s) {
        // If the provided secret looks like plain text, base64-encode expectation
        // For simplicity here we assume user provides base64 or a random ascii string.
        // JJWT requires a strong enough key; users should provide a proper base64 secret.
        if (isBase64(s)) return s;
        return java.util.Base64.getEncoder().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static boolean isBase64(String s) {
        try {
            Decoders.BASE64.decode(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
