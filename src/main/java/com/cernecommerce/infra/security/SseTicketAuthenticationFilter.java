package com.cernecommerce.infra.security;

import com.cernecommerce.core.ports.out.sse.SseTicketPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Autentica {@code GET /notifications/stream} por bilhete de uso único (PLAT-C051).
 *
 * <p>O QA de 06/09/2026 reportou que o stream recusava um Bearer válido. O 401 era real, mas a
 * causa não era o token: a API {@code EventSource} do navegador não envia headers, então a
 * requisição chegava <b>sem autenticação nenhuma</b> e caía no {@code anyRequest().authenticated()}
 * — e o efeito colateral era pior que a falta da notificação, porque o cliente reagia ao 401
 * refazendo a sessão e estourava o rate limit do {@code /auth/refresh}, derrubando o usuário no
 * meio de qualquer fluxo.</p>
 *
 * <p><b>Escopo deliberadamente estreito.</b> O bilhete vale para uma rota, um uso e alguns
 * segundos; qualquer outro caminho continua exigindo o Bearer. O filtro roda <b>depois</b> do
 * {@code JwtAuthenticationFilter} e só age se o contexto ainda estiver vazio, então um cliente que
 * consiga mandar o header (curl, teste, um proxy próprio) segue autenticando pelo caminho normal —
 * o bilhete é acréscimo, não substituição.</p>
 */
@Component
public class SseTicketAuthenticationFilter extends OncePerRequestFilter {

    static final String STREAM_PATH = "/notifications/stream";
    static final String TICKET_PARAM = "ticket";

    private final SseTicketPort sseTickets;
    private final UserDetailsService userDetailsService;

    public SseTicketAuthenticationFilter(SseTicketPort sseTickets, UserDetailsService userDetailsService) {
        this.sseTickets = sseTickets;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("GET".equalsIgnoreCase(request.getMethod()) && STREAM_PATH.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<String> username = sseTickets.consume(request.getParameter(TICKET_PARAM));
            if (username.isPresent()) {
                authenticate(request, username.get());
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String username) {
        try {
            UserDetails user = userDetailsService.loadUserByUsername(username);
            // Mesmas guardas do JwtAuthenticationFilter: conta desabilitada ou apagada entre a
            // emissão e o resgate do bilhete não abre stream — segue como não autenticada (401).
            if (user.isEnabled()) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (UsernameNotFoundException ignored) {
            // Usuário removido depois de emitir o bilhete.
        }
    }
}
