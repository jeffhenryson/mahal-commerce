package com.securityspring.adapter.out.security.blocklist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a lógica de blocklist em memória do {@link InMemoryTokenBlocklistAdapter}.
 *
 * <p>A blocklist bloqueia tokens emitidos <em>em ou antes</em> do threshold definido
 * por {@code blockAllBefore}, implementando revogação eficiente de sessões em dev.</p>
 */
class InMemoryTokenBlocklistAdapterTest {

    private InMemoryTokenBlocklistAdapter adapter;

    @BeforeEach
    void setUp() {
        // TTL de 15 minutos — suficiente para que nenhum entry seja evictado durante os testes
        adapter = new InMemoryTokenBlocklistAdapter(15L);
    }

    // ── usuário desconhecido ──────────────────────────────────────────────────

    @Test
    void isBlockedAt_returnsFalse_forUnknownUser() {
        assertThat(adapter.isBlockedAt("alice", Instant.now())).isFalse();
    }

    // ── bloqueio por threshold ────────────────────────────────────────────────

    @Test
    void isBlockedAt_returnsFalse_forTokenIssuedAfterThreshold() {
        Instant threshold = Instant.now();
        adapter.blockAllBefore("alice", threshold);

        Instant tokenIssuedAfter = threshold.plusSeconds(1);
        assertThat(adapter.isBlockedAt("alice", tokenIssuedAfter)).isFalse();
    }

    @Test
    void isBlockedAt_returnsTrue_forTokenIssuedBeforeThreshold() {
        Instant threshold = Instant.now();
        adapter.blockAllBefore("alice", threshold);

        Instant tokenIssuedBefore = threshold.minusSeconds(1);
        assertThat(adapter.isBlockedAt("alice", tokenIssuedBefore)).isTrue();
    }

    @Test
    void isBlockedAt_returnsTrue_forTokenIssuedAtExactSameInstant() {
        // Tokens emitidos no mesmo segundo do threshold são considerados bloqueados
        // (!isAfter(same) == true — comportamento intencional para cobrir logout simultâneo)
        Instant threshold = Instant.now();
        adapter.blockAllBefore("alice", threshold);

        assertThat(adapter.isBlockedAt("alice", threshold)).isTrue();
    }

    // ── idempotência: threshold só avança ────────────────────────────────────

    @Test
    void blockAllBefore_keepsMostRecentThreshold() {
        Instant older = Instant.now().minusSeconds(120);
        Instant newer = Instant.now();

        adapter.blockAllBefore("alice", newer);
        adapter.blockAllBefore("alice", older); // não deve regredir o threshold

        // Token emitido 1s antes do newer ainda deve ser bloqueado
        assertThat(adapter.isBlockedAt("alice", newer.minusSeconds(1))).isTrue();
    }

    @Test
    void blockAllBefore_advancesThresholdWhenNewerIsGiven() {
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now();

        adapter.blockAllBefore("alice", t1);
        adapter.blockAllBefore("alice", t2); // avança para t2

        // Token emitido entre t1 e t2 deve agora ser bloqueado
        assertThat(adapter.isBlockedAt("alice", t1.plusSeconds(30))).isTrue();
    }

    // ── isolamento entre usuários ─────────────────────────────────────────────

    @Test
    void blocklist_of_one_user_does_not_affect_another() {
        Instant threshold = Instant.now();
        adapter.blockAllBefore("alice", threshold);

        assertThat(adapter.isBlockedAt("alice", threshold.minusSeconds(1))).isTrue();
        assertThat(adapter.isBlockedAt("bob",   threshold.minusSeconds(1))).isFalse();
    }

    // ── clearAll ──────────────────────────────────────────────────────────────

    @Test
    void clearAll_removesAllEntries() {
        Instant threshold = Instant.now();
        adapter.blockAllBefore("alice", threshold);
        adapter.blockAllBefore("bob",   threshold);

        adapter.clearAll();

        assertThat(adapter.isBlockedAt("alice", threshold.minusSeconds(1))).isFalse();
        assertThat(adapter.isBlockedAt("bob",   threshold.minusSeconds(1))).isFalse();
    }
}
