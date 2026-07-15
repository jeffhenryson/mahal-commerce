package com.cernecommerce.adapter.out.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o rate limiter de login por IP do {@link InMemoryLoginRateLimiterAdapter}.
 *
 * <p>A janela deslizante é implementada com um deque de timestamps por IP.
 * Requisições dentro da janela são contadas; ao exceder {@code maxRequests},
 * {@code tryConsume} retorna {@code false}.</p>
 */
class InMemoryLoginRateLimiterAdapterTest {

    private InMemoryLoginRateLimiterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryLoginRateLimiterAdapter(60L, 3);
    }

    // ── dentro do limite ──────────────────────────────────────────────────────

    @Test
    void firstRequest_isAllowed() {
        assertThat(adapter.tryConsume("1.2.3.4")).isTrue();
    }

    @Test
    void requestsUpToMax_areAllowed() {
        assertThat(adapter.tryConsume("10.0.0.1")).isTrue();  // 1
        assertThat(adapter.tryConsume("10.0.0.1")).isTrue();  // 2
        assertThat(adapter.tryConsume("10.0.0.1")).isTrue();  // 3 (limite)
    }

    // ── acima do limite ───────────────────────────────────────────────────────

    @Test
    void requestBeyondMax_isBlocked() {
        adapter.tryConsume("10.0.0.2");
        adapter.tryConsume("10.0.0.2");
        adapter.tryConsume("10.0.0.2"); // 3 — limite atingido

        assertThat(adapter.tryConsume("10.0.0.2")).isFalse(); // 4 — bloqueado
    }

    @Test
    void multiple_requests_beyond_max_remainBlocked() {
        for (int i = 0; i < 3; i++) adapter.tryConsume("10.0.0.3");

        assertThat(adapter.tryConsume("10.0.0.3")).isFalse();
        assertThat(adapter.tryConsume("10.0.0.3")).isFalse();
    }

    // ── isolamento por IP ─────────────────────────────────────────────────────

    @Test
    void differentIps_trackedIndependently() {
        // Esgota o limite para o IP A
        adapter.tryConsume("192.168.0.1");
        adapter.tryConsume("192.168.0.1");
        adapter.tryConsume("192.168.0.1");
        assertThat(adapter.tryConsume("192.168.0.1")).isFalse();

        // IP B não deve ser afetado
        assertThat(adapter.tryConsume("192.168.0.2")).isTrue();
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    void reset_allowsNewRequestsFromBlockedIp() {
        for (int i = 0; i < 3; i++) adapter.tryConsume("10.0.0.4");
        assertThat(adapter.tryConsume("10.0.0.4")).isFalse();

        adapter.reset();
        assertThat(adapter.tryConsume("10.0.0.4")).isTrue();
    }
}
