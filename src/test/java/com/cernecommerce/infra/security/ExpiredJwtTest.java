package com.cernecommerce.infra.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@ActiveProfiles("dev")
public class ExpiredJwtTest {

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void expired_token_returns_401() throws Exception {
        // Same secret default as dev profile; JwtService base64-encodes if not already, so we do the same here
        String secret = "dev-secret-please-change-to-256-bit-random-key";
        byte[] keyBytes = java.util.Base64.getEncoder().encode(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(new String(keyBytes)));

        String token = Jwts.builder()
                .setSubject("john")
                .claim("roles", Set.of("ROLE_USER"))
                .setIssuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
