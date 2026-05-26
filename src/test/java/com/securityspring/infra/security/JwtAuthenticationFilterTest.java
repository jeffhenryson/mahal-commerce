package com.securityspring.infra.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.securityspring.core.ports.out.token.TokenBlocklistPort;
import com.securityspring.infra.security.jwt.JwtAuthenticationFilter;
import com.securityspring.infra.security.jwt.JwtService;

import java.time.Instant;
import java.util.Collections;

public class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sets_authentication_when_token_valid() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserDetailsService uds = mock(UserDetailsService.class);
        TokenBlocklistPort blocklist = mock(TokenBlocklistPort.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, uds, blocklist);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer abc.xyz");
        when(jwt.isValid("abc.xyz")).thenReturn(true);
        when(jwt.extractUsername("abc.xyz")).thenReturn("john");
        when(jwt.extractIssuedAt("abc.xyz")).thenReturn(Instant.now());
        when(blocklist.isBlockedAt(eq("john"), any())).thenReturn(false);
        when(uds.loadUserByUsername("john")).thenReturn(new User("john", "pwd", Collections.emptyList()));

        filter.doFilter(req, res, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("john", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void skips_when_no_bearer_header() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserDetailsService uds = mock(UserDetailsService.class);
        TokenBlocklistPort blocklist = mock(TokenBlocklistPort.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, uds, blocklist);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(req, res);
    }
}
