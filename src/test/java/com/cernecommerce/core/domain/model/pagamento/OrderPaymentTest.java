package com.cernecommerce.core.domain.model.pagamento;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPaymentTest {

    @Test
    void captured_startsAsCapturedWithTimestampsSet() {
        OrderPayment payment = OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("44.00"), null);

        assertThat(payment.id()).isNull();
        assertThat(payment.orderId()).isEqualTo(7L);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.capturedAt()).isNotNull();
        assertThat(payment.authorizedAt()).isNotNull();
        assertThat(payment.createdAt()).isNotNull();
        assertThat(payment.gatewayRef()).isNull();
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.DINHEIRO, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("-1"), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("amount");
    }

    @Test
    void rejectsMissingOrderIdMethodOrStatus() {
        assertThatThrownBy(() -> OrderPayment.captured(null, PaymentMethod.DINHEIRO, BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("orderId");
        assertThatThrownBy(() -> OrderPayment.captured(7L, null, BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("method");
    }

    @Test
    void installments_onlyAllowedWithCredito() {
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.DINHEIRO, BigDecimal.TEN, 3))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CREDITO");

        OrderPayment credito = OrderPayment.captured(7L, PaymentMethod.CREDITO, BigDecimal.TEN, 3);
        assertThat(credito.installments()).isEqualTo(3);
    }

    @Test
    void installments_mustBeWithinRange() {
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.CREDITO, BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 e 24");
        assertThatThrownBy(() -> OrderPayment.captured(7L, PaymentMethod.CREDITO, BigDecimal.TEN, 25))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 e 24");
    }

    @Test
    void statusAndCapturedAtHaveToCoexist() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> OrderPayment.of(1L, 7L, PaymentMethod.DINHEIRO, BigDecimal.TEN,
                PaymentStatus.CAPTURED, null, null, now, null, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CAPTURED");
        assertThatThrownBy(() -> OrderPayment.of(1L, 7L, PaymentMethod.DINHEIRO, BigDecimal.TEN,
                PaymentStatus.PENDING, null, null, now, now, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CAPTURED");
    }

    @Test
    void refunded_startsAsRefundedWithSameMethodAndAmount() {
        OrderPayment original = OrderPayment.captured(7L, PaymentMethod.CREDITO, new BigDecimal("44.00"), 3);

        OrderPayment refunded = OrderPayment.refunded(original);

        assertThat(refunded.id()).isNull();
        assertThat(refunded.orderId()).isEqualTo(7L);
        assertThat(refunded.method()).isEqualTo(PaymentMethod.CREDITO);
        assertThat(refunded.amount()).isEqualByComparingTo("44.00");
        assertThat(refunded.installments()).isEqualTo(3);
        assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.capturedAt()).isNull();
        assertThat(refunded.authorizedAt()).isNotNull();
        assertThat(refunded.gatewayRef()).isNull();
    }

    @Test
    void refunded_rejectsNonCapturedOriginal() {
        Instant now = Instant.now();
        OrderPayment pending = OrderPayment.of(1L, 7L, PaymentMethod.DINHEIRO, BigDecimal.TEN,
                PaymentStatus.PENDING, null, null, null, null, now);

        assertThatThrownBy(() -> OrderPayment.refunded(pending))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CAPTURED");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant createdAt = Instant.parse("2026-07-29T14:30:00Z");
        OrderPayment payment = OrderPayment.of(9L, 7L, PaymentMethod.PIX, new BigDecimal("22.00"),
                PaymentStatus.CAPTURED, null, null, createdAt, createdAt, createdAt);

        assertThat(payment.id()).isEqualTo(9L);
        assertThat(payment.method()).isEqualTo(PaymentMethod.PIX);
        assertThat(payment.createdAt()).isEqualTo(createdAt);
    }
}
