package com.cernecommerce.core.domain.model.pedido;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void everyStatusDeclaresItsTransitions() {
        // Guarda contra o esquecimento clássico: acrescentar um valor ao enum e não mapeá-lo,
        // o que faria canTransitionTo estourar NullPointerException em produção.
        Arrays.stream(OrderStatus.values())
                .forEach(status -> assertThat(status.allowedTransitions()).isNotNull());
    }

    @Test
    void cancelledAndRefundedAreTheOnlyTerminalStates() {
        assertThat(OrderStatus.CANCELADO.isTerminal()).isTrue();
        assertThat(OrderStatus.REEMBOLSADO.isTerminal()).isTrue();

        // ENTREGUE e CONCLUIDO ainda aceitam reembolso, porque devolução existe.
        assertThat(OrderStatus.ENTREGUE.isTerminal()).isFalse();
        assertThat(OrderStatus.CONCLUIDO.isTerminal()).isFalse();
    }

    /**
     * Pré-pagamento (CRIADO/AGUARDANDO_PAGAMENTO) nunca teve dinheiro capturado nem baixa real de
     * estoque — só CANCELADO faz sentido como saída antecipada.
     */
    @Test
    void prePaymentStatesCanBeCancelledButNotRefunded() {
        for (OrderStatus status : new OrderStatus[]{OrderStatus.CRIADO, OrderStatus.AGUARDANDO_PAGAMENTO}) {
            assertThat(status.canTransitionTo(OrderStatus.CANCELADO))
                    .as("%s deve poder ser cancelado", status).isTrue();
            assertThat(status.canTransitionTo(OrderStatus.REEMBOLSADO))
                    .as("%s não deve poder ser reembolsado", status).isFalse();
        }
    }

    /**
     * Pós-pagamento (PAGO em diante) sempre tem dinheiro capturado e estoque real decrementado —
     * só REEMBOLSADO faz sentido como saída antecipada. Cancelar e reembolsar são eventos
     * diferentes (PDV-F007): contá-los juntos esconderia quanto dinheiro voltou de fato.
     */
    @Test
    void postPaymentStatesCanBeRefundedButNotCancelled() {
        OrderStatus[] postPayment = {OrderStatus.PAGO, OrderStatus.SEPARADO, OrderStatus.ENVIADO,
                OrderStatus.ENTREGUE, OrderStatus.CONCLUIDO};
        for (OrderStatus status : postPayment) {
            assertThat(status.canTransitionTo(OrderStatus.REEMBOLSADO))
                    .as("%s deve poder ser reembolsado", status).isTrue();
            assertThat(status.canTransitionTo(OrderStatus.CANCELADO))
                    .as("%s não deve poder ser cancelado", status).isFalse();
        }
    }

    @Test
    void counterPathIsCreatedThenConcluded() {
        assertThat(OrderStatus.CRIADO.canTransitionTo(OrderStatus.CONCLUIDO)).isTrue();
        assertThat(OrderStatus.CRIADO.canTransitionTo(OrderStatus.PAGO)).isFalse();
    }

    /**
     * Retirada e pagamento no balcão: o cliente montou o pedido no app e veio buscar. Termina em
     * {@code CONCLUIDO} — o mesmo estado de uma venda de balcão, porque foi exatamente isso que
     * aconteceu. Passar por {@code SEPARADO} e {@code ENVIADO} descreveria uma entrega que não houve.
     */
    @Test
    void pendingOnlineOrderCanBeSettledAtTheCounter() {
        assertThat(OrderStatus.AGUARDANDO_PAGAMENTO.canTransitionTo(OrderStatus.CONCLUIDO)).isTrue();
    }

    @Test
    void marketplacePathIsStrictlySequential() {
        assertThat(OrderStatus.AGUARDANDO_PAGAMENTO.canTransitionTo(OrderStatus.PAGO)).isTrue();
        assertThat(OrderStatus.PAGO.canTransitionTo(OrderStatus.SEPARADO)).isTrue();
        assertThat(OrderStatus.SEPARADO.canTransitionTo(OrderStatus.ENVIADO)).isTrue();
        assertThat(OrderStatus.ENVIADO.canTransitionTo(OrderStatus.ENTREGUE)).isTrue();

        assertThat(OrderStatus.PAGO.canTransitionTo(OrderStatus.ENTREGUE)).isFalse();
        assertThat(OrderStatus.ENTREGUE.canTransitionTo(OrderStatus.ENVIADO)).isFalse();
    }

    @Test
    void nullTargetIsNeverAllowed() {
        assertThat(OrderStatus.CRIADO.canTransitionTo(null)).isFalse();
    }
}
