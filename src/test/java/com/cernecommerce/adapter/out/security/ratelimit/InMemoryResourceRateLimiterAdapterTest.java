package com.cernecommerce.adapter.out.security.ratelimit;

import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o rate limiter de recursos de negócio do {@link InMemoryResourceRateLimiterAdapter}.
 *
 * <p>A janela deslizante é implementada com um deque de timestamps por bucket+key.
 * Requisições dentro da janela são contadas; ao exceder o {@code maxRequests} da política
 * do bucket, {@code checkOrThrow} lança {@link RateLimitExceededException}.</p>
 */
class InMemoryResourceRateLimiterAdapterTest {

    private InMemoryResourceRateLimiterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryResourceRateLimiterAdapter();
    }

    // ── dentro do limite ──────────────────────────────────────────────────────

    @Test
    void crmExport_requestsUpToMax_areAllowed() {
        for (int i = 0; i < 5; i++) {
            adapter.checkOrThrow("crm-export", "joao");
        }
    }

    // ── acima do limite ───────────────────────────────────────────────────────

    @Test
    void crmExport_requestBeyondMax_throws() {
        for (int i = 0; i < 5; i++) {
            adapter.checkOrThrow("crm-export", "joao");
        }

        assertThatThrownBy(() -> adapter.checkOrThrow("crm-export", "joao"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    // ── isolamento por key dentro do mesmo bucket ───────────────────────────────

    @Test
    void differentKeys_sameBucket_trackedIndependently() {
        for (int i = 0; i < 5; i++) {
            adapter.checkOrThrow("crm-export", "joao");
        }
        assertThatThrownBy(() -> adapter.checkOrThrow("crm-export", "joao"))
                .isInstanceOf(RateLimitExceededException.class);

        // outra key não deve ser afetada
        adapter.checkOrThrow("crm-export", "maria");
    }

    // ── isolamento por bucket para a mesma key ──────────────────────────────────

    @Test
    void sameKey_differentBuckets_doNotInterfere() {
        for (int i = 0; i < 5; i++) {
            adapter.checkOrThrow("crm-export", "joao");
        }
        assertThatThrownBy(() -> adapter.checkOrThrow("crm-export", "joao"))
                .isInstanceOf(RateLimitExceededException.class);

        // mesma key "joao", bucket diferente não deve ser afetado
        adapter.checkOrThrow("estoque-movements", "joao");
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    void reset_allowsNewRequestsFromBlockedBucketAndKey() {
        for (int i = 0; i < 5; i++) {
            adapter.checkOrThrow("crm-export", "joao");
        }
        assertThatThrownBy(() -> adapter.checkOrThrow("crm-export", "joao"))
                .isInstanceOf(RateLimitExceededException.class);

        adapter.reset();

        adapter.checkOrThrow("crm-export", "joao");
    }

    // ── bucket desconhecido ──────────────────────────────────────────────────────

    @Test
    void unknownBucket_usesDefaultPolicy() {
        for (int i = 0; i < 30; i++) {
            adapter.checkOrThrow("bucket-que-nao-existe", "joao");
        }

        assertThatThrownBy(() -> adapter.checkOrThrow("bucket-que-nao-existe", "joao"))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
