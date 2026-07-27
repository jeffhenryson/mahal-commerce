package com.cernecommerce.core.ports.out;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port de saída para efeitos colaterais que devem sair de dentro da transação de escrita.
 *
 * <p>Existe porque efeito colateral externo (notificação, email, webhook) disparado no meio de uma
 * transação tem dois problemas: prolonga a transação pelo tempo do envio, e pode ser enviado mesmo
 * que a transação acabe revertida — avisando sobre algo que não aconteceu.</p>
 *
 * <p>A implementação vive em {@code infra}, porque amarrar-se ao commit é responsabilidade de
 * adapter, não do domínio.</p>
 */
public interface AfterCommitExecutor {

    /**
     * Acumula {@code item} na coleção identificada por {@code key} dentro da transação corrente e
     * garante que {@code flush} rode <b>uma única vez</b>, depois do commit, recebendo tudo que foi
     * acumulado sob aquela chave. É o que permite que uma venda com N itens abaixo do mínimo gere
     * um aviso só, em vez de N.
     *
     * <p>Sem transação ativa, {@code flush} roda na hora com o item isolado.</p>
     */
    <T> void accumulate(String key, T item, Consumer<List<T>> flush);
}
