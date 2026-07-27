package com.cernecommerce.infra.transaction;

import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implementação do {@link AfterCommitExecutor} sobre o
 * {@link TransactionSynchronizationManager} do Spring.
 *
 * <p>Os lotes ficam num {@link ThreadLocal} porque a sincronização de transação do Spring já é
 * ligada à thread; o mapa é descartado no {@code afterCompletion}, que roda tanto no commit quanto
 * no rollback.</p>
 */
@Component
public class TransactionAfterCommitExecutor implements AfterCommitExecutor {

    private static final Logger log = LoggerFactory.getLogger(TransactionAfterCommitExecutor.class);

    private final ThreadLocal<Map<String, Batch<?>>> batches = ThreadLocal.withInitial(LinkedHashMap::new);

    private record Batch<T>(List<T> items, Consumer<List<T>> flush) {
    }

    @Override
    public <T> void accumulate(String key, T item, Consumer<List<T>> flush) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Sem transação ativa não há commit para esperar — despacha o item isolado.
            flush.accept(List.of(item));
            return;
        }

        Map<String, Batch<?>> current = batches.get();
        boolean firstOfTransaction = current.isEmpty();

        @SuppressWarnings("unchecked")
        Batch<T> batch = (Batch<T>) current.computeIfAbsent(key, k -> new Batch<>(new ArrayList<T>(), flush));
        batch.items().add(item);

        if (firstOfTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    flushAll();
                }

                @Override
                public void afterCompletion(int status) {
                    // Roda também em rollback: nada é despachado e o estado da thread é limpo.
                    batches.remove();
                }
            });
        }
    }

    private void flushAll() {
        for (Batch<?> batch : List.copyOf(batches.get().values())) {
            try {
                flush(batch);
            } catch (RuntimeException e) {
                // O commit já aconteceu; falha no efeito colateral não pode derrubar a requisição.
                log.error("Falha ao despachar lote pós-commit", e);
            }
        }
    }

    private <T> void flush(Batch<T> batch) {
        batch.flush().accept(List.copyOf(batch.items()));
    }
}
