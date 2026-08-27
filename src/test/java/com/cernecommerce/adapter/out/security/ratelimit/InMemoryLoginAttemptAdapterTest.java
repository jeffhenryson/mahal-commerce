package com.cernecommerce.adapter.out.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o comportamento de lockout do {@link InMemoryLoginAttemptAdapter}
 * sem depender de Spring context ou Redis.
 */
class InMemoryLoginAttemptAdapterTest {

    private InMemoryLoginAttemptAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemoryLoginAttemptAdapter(3, 15L);
    }

    // ── isLocked — estado inicial ─────────────────────────────────────────────

    @Test
    void isLocked_returnsFalse_forUnknownUser() {
        assertThat(adapter.isLocked("alice")).isFalse();
    }

    // ── recordFailure ─────────────────────────────────────────────────────────

    @Test
    void recordFailure_belowMax_doesNotLock() {
        adapter.recordFailure("alice"); // 1
        adapter.recordFailure("alice"); // 2 (max=3)
        assertThat(adapter.isLocked("alice")).isFalse();
    }

    @Test
    void recordFailure_atMax_locks() {
        adapter.recordFailure("alice"); // 1
        adapter.recordFailure("alice"); // 2
        adapter.recordFailure("alice"); // 3 → lock ativado
        assertThat(adapter.isLocked("alice")).isTrue();
    }

    @Test
    void recordFailure_beyondMax_staysLocked() {
        for (int i = 0; i < 5; i++) adapter.recordFailure("alice");
        assertThat(adapter.isLocked("alice")).isTrue();
    }

    // ── recordSuccess ─────────────────────────────────────────────────────────

    @Test
    void recordSuccess_clearsFailuresBeforeLock() {
        adapter.recordFailure("alice");
        adapter.recordFailure("alice");
        adapter.recordSuccess("alice");
        assertThat(adapter.isLocked("alice")).isFalse();
    }

    @Test
    void recordSuccess_clearsLockedState() {
        for (int i = 0; i < 3; i++) adapter.recordFailure("alice");
        assertThat(adapter.isLocked("alice")).isTrue();

        adapter.recordSuccess("alice");
        assertThat(adapter.isLocked("alice")).isFalse();
    }

    // ── expiração do lock ─────────────────────────────────────────────────────

    @Test
    void isLocked_returnsFalse_afterLockDurationExpires() {
        // lock com duração negativa → lockedUntil = 60s atrás → expirado imediatamente
        InMemoryLoginAttemptAdapter expiredLock = new InMemoryLoginAttemptAdapter(2, -1L);
        expiredLock.recordFailure("bob");
        expiredLock.recordFailure("bob"); // ativa lock em instante passado
        assertThat(expiredLock.isLocked("bob")).isFalse();
    }

    // ── isolamento entre usuários ─────────────────────────────────────────────

    @Test
    void lockout_of_one_user_does_not_affect_another() {
        for (int i = 0; i < 3; i++) adapter.recordFailure("alice");
        assertThat(adapter.isLocked("alice")).isTrue();
        assertThat(adapter.isLocked("bob")).isFalse();
    }

    // ── clearAll (helper de teste) ────────────────────────────────────────────

    @Test
    void clearAll_resetsAllState() {
        for (int i = 0; i < 3; i++) adapter.recordFailure("alice");
        assertThat(adapter.isLocked("alice")).isTrue();

        adapter.clearAll();
        assertThat(adapter.isLocked("alice")).isFalse();
    }
}
