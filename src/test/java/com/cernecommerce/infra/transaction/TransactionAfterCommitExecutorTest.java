package com.cernecommerce.infra.transaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato do {@link TransactionAfterCommitExecutor}: acumular durante a transação, despachar uma
 * vez só depois do commit, e não despachar nada em rollback (EST-C003).
 */
class TransactionAfterCommitExecutorTest {

    private final TransactionAfterCommitExecutor executor = new TransactionAfterCommitExecutor();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void simulateCommit() {
        List<TransactionSynchronization> syncs = List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        syncs.forEach(TransactionSynchronization::afterCommit);
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void simulateRollback() {
        List<TransactionSynchronization> syncs = List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void semTransacaoAtiva_despacha_na_hora() {
        List<List<String>> flushed = new ArrayList<>();

        executor.accumulate("k", "a", flushed::add);

        assertThat(flushed).containsExactly(List.of("a"));
    }

    @Test
    void comTransacaoAtiva_agrega_e_despacha_uma_unica_vez_no_commit() {
        TransactionSynchronizationManager.initSynchronization();
        List<List<String>> flushed = new ArrayList<>();

        executor.accumulate("k", "a", flushed::add);
        executor.accumulate("k", "b", flushed::add);
        executor.accumulate("k", "c", flushed::add);

        assertThat(flushed).as("nada pode sair antes do commit").isEmpty();

        simulateCommit();

        assertThat(flushed).as("três acumulações viram um único despacho").hasSize(1);
        assertThat(flushed.get(0)).containsExactly("a", "b", "c");
    }

    @Test
    void em_rollback_nao_despacha_nada() {
        TransactionSynchronizationManager.initSynchronization();
        List<List<String>> flushed = new ArrayList<>();

        executor.accumulate("k", "a", flushed::add);
        simulateRollback();

        assertThat(flushed).as("transação revertida não pode notificar ninguém").isEmpty();
    }

    @Test
    void chaves_diferentes_sao_despachadas_separadamente() {
        TransactionSynchronizationManager.initSynchronization();
        List<List<String>> alertas = new ArrayList<>();
        List<List<String>> emails = new ArrayList<>();

        executor.accumulate("alertas", "a1", alertas::add);
        executor.accumulate("emails", "e1", emails::add);
        executor.accumulate("alertas", "a2", alertas::add);

        simulateCommit();

        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0)).containsExactly("a1", "a2");
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0)).containsExactly("e1");
    }

    @Test
    void falha_em_um_lote_nao_impede_o_proximo() {
        TransactionSynchronizationManager.initSynchronization();
        List<List<String>> ok = new ArrayList<>();

        executor.accumulate("quebra", "x", items -> {
            throw new IllegalStateException("falha simulada no despacho");
        });
        executor.accumulate("ok", "y", ok::add);

        simulateCommit();

        assertThat(ok).as("o commit já aconteceu; um despacho com falha não pode derrubar o resto")
                .hasSize(1);
        assertThat(ok.get(0)).containsExactly("y");
    }

    @Test
    void estado_nao_vaza_entre_transacoes_na_mesma_thread() {
        TransactionSynchronizationManager.initSynchronization();
        List<List<String>> flushed = new ArrayList<>();
        executor.accumulate("k", "primeira", flushed::add);
        simulateCommit();

        TransactionSynchronizationManager.initSynchronization();
        executor.accumulate("k", "segunda", flushed::add);
        simulateCommit();

        assertThat(flushed).hasSize(2);
        assertThat(flushed.get(0)).containsExactly("primeira");
        assertThat(flushed.get(1)).as("a segunda transação não pode reenviar o item da primeira")
                .containsExactly("segunda");
    }
}
