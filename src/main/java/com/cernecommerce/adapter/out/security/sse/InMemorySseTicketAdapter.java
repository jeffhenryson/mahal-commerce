package com.cernecommerce.adapter.out.security.sse;

import com.cernecommerce.core.ports.out.sse.SseTicketPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contraparte de {@code dev} do bilhete de SSE (PLAT-C051), mesmo par de
 * {@code InMemoryTokenBlocklistAdapter}/{@code RedisTokenBlocklistAdapter}.
 *
 * <p>O TTL é conferido na leitura em vez de por expiração ativa: o mapa é pequeno por construção
 * (um bilhete por conexão de SSE, com vida de segundos) e a varredura de vencidos acontece a cada
 * emissão, que é o momento em que ele pode crescer.</p>
 */
@Component
@Profile("dev")
public class InMemorySseTicketAdapter implements SseTicketPort {

    private record Entry(String username, Instant expiresAt) {
    }

    // O gerador é duplicado do RedisSseTicketAdapter de propósito: os dois adapters do mesmo port
    // não compartilham código em nenhum outro par do projeto (ver InMemoryTokenBlocklistAdapter),
    // e fazer um depender do outro acoplaria o perfil dev ao pacote de Redis por quatro linhas.
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, Entry> tickets = new ConcurrentHashMap<>();

    @Override
    public String issue(String username, long ttlSeconds) {
        evictExpired();
        String ticket = newTicket();
        tickets.put(ticket, new Entry(username, Instant.now().plusSeconds(ttlSeconds)));
        return ticket;
    }

    @Override
    public Optional<String> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.username());
    }

    private static String newTicket() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void evictExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
