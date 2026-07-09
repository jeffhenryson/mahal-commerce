package com.cernecommerce.infra.security.jwt;

import java.io.IOException;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlocklistPort tokenBlocklist;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsService userDetailsService,
                                   TokenBlocklistPort tokenBlocklist) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlocklist = tokenBlocklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String username = jwtService.extractUsername(token);
            Instant issuedAt = jwtService.extractIssuedAt(token);

            if (!tokenBlocklist.isBlockedAt(username, issuedAt)) {
                try {
                    UserDetails user = userDetailsService.loadUserByUsername(username);
                    if (user.isEnabled()) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                    // isEnabled() == false: conta desabilitada — trata como não autenticado (401 downstream).
                    // Defesa em profundidade: a blocklist já bloqueia os tokens ao desabilitar,
                    // mas esta checagem protege se a blocklist estiver indisponível.
                } catch (UsernameNotFoundException ignored) {
                    // User deleted after token was issued — treat as unauthenticated (401 downstream).
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
