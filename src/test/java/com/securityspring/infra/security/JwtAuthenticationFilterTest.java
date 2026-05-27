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

    @Test
    void does_not_authenticate_when_token_is_blocklisted() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserDetailsService uds = mock(UserDetailsService.class);
        TokenBlocklistPort blocklist = mock(TokenBlocklistPort.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, uds, blocklist);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        Instant issuedAt = Instant.now().minusSeconds(60);
        when(req.getHeader("Authorization")).thenReturn("Bearer blocklisted.token");
        when(jwt.isValid("blocklisted.token")).thenReturn(true);
        when(jwt.extractUsername("blocklisted.token")).thenReturn("john");
        when(jwt.extractIssuedAt("blocklisted.token")).thenReturn(issuedAt);
        // Token emitido antes do threshold de revogação → bloqueado
        when(blocklist.isBlockedAt(eq("john"), eq(issuedAt))).thenReturn(true);

        filter.doFilter(req, res, chain);

        // Filtro deve deixar a request passar (chain.doFilter chamado), mas sem autenticar
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(req, res);
        // UserDetailsService NÃO deve ser consultado para token bloqueado
        verify(uds, never()).loadUserByUsername(any());
    }

    @Test
    void does_not_authenticate_when_user_is_disabled() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserDetailsService uds = mock(UserDetailsService.class);
        TokenBlocklistPort blocklist = mock(TokenBlocklistPort.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, uds, blocklist);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        Instant issuedAt = Instant.now();
        when(req.getHeader("Authorization")).thenReturn("Bearer valid.token");
        when(jwt.isValid("valid.token")).thenReturn(true);
        when(jwt.extractUsername("valid.token")).thenReturn("disabled_user");
        when(jwt.extractIssuedAt("valid.token")).thenReturn(issuedAt);
        when(blocklist.isBlockedAt(eq("disabled_user"), any())).thenReturn(false);

        // Usuário com conta desabilitada (ex: admin desativou a conta)
        var disabledUser = new User("disabled_user", "pwd",
                false,  // disabled
                true, true, true,
                Collections.emptyList());
        when(uds.loadUserByUsername("disabled_user")).thenReturn(disabledUser);

        filter.doFilter(req, res, chain);

        // Conta desabilitada → filtro não autentica (defesa em profundidade além da blocklist)
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void does_not_authenticate_when_user_deleted_after_token_issued() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserDetailsService uds = mock(UserDetailsService.class);
        TokenBlocklistPort blocklist = mock(TokenBlocklistPort.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, uds, blocklist);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer ghost.token");
        when(jwt.isValid("ghost.token")).thenReturn(true);
        when(jwt.extractUsername("ghost.token")).thenReturn("deleted_user");
        when(jwt.extractIssuedAt("ghost.token")).thenReturn(Instant.now());
        when(blocklist.isBlockedAt(eq("deleted_user"), any())).thenReturn(false);
        // Usuário foi deletado após o token ser emitido
        when(uds.loadUserByUsername("deleted_user"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("deleted_user"));

        filter.doFilter(req, res, chain);

        // UsernameNotFoundException deve ser silenciada — request continua sem autenticação
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void does_not_authenticate_when_jwt_is_invalid() throws Exception {
        JwtService jwt = mock(JwtService.class);
        UserDetailsService uds = mock(UserDetailsService.class);
        TokenBlocklistPort blocklist = mock(TokenBlocklistPort.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, uds, blocklist);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader("Authorization")).thenReturn("Bearer malformed.or.expired");
        when(jwt.isValid("malformed.or.expired")).thenReturn(false);

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(req, res);
        verify(uds, never()).loadUserByUsername(any());
        verify(blocklist, never()).isBlockedAt(any(), any());
    }
}
