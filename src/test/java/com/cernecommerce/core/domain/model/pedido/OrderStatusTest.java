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
    void onlyCancelledIsTrulyTerminal() {
        assertThat(OrderStatus.CANCELADO.isTerminal()).isTrue();

        // ENTREGUE e CONCLUIDO ainda aceitam cancelamento, porque devolução existe.
        assertThat(OrderStatus.ENTREGUE.isTerminal()).isFalse();
        assertThat(OrderStatus.CONCLUIDO.isTerminal()).isFalse();
    }

    @Test
    void everyNonTerminalStatusCanBeCancelled() {
        Arrays.stream(OrderStatus.values())
                .filter(status -> status != OrderStatus.CANCELADO)
                .forEach(status -> assertThat(status.canTransitionTo(OrderStatus.CANCELADO))
                        .as("%s deve poder ser cancelado", status)
                        .isTrue());
    }

    @Test
    void counterPathIsCreatedThenConcluded() {
        assertThat(OrderStatus.CRIADO.canTransitionTo(OrderStatus.CONCLUIDO)).isTrue();
        assertThat(OrderStatus.CRIADO.canTransitionTo(OrderStatus.PAGO)).isFalse();
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
