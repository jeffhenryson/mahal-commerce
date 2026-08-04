package com.cernecommerce.core.domain.model.pagamento;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Linha de pagamento de um {@link com.cernecommerce.core.domain.model.pedido.Order} (PDV-F006).
 *
 * <p>Pagamento mora em tabela própria, fora de {@code Order}, porque o que muda entre balcão e
 * marketplace é só <b>quem confirma</b> — o operador ou o webhook do gateway — não o que é
 * registrado. Várias linhas por pedido = pagamento dividido (ex.: R$50 dinheiro + R$30 débito).</p>
 *
 * <p><b>{@code amount} nunca é negativo, mesmo em dinheiro.</b> Troco não é uma linha de pagamento
 * — é {@code Order.changeAmount}, derivado. Modelar troco como pagamento negativo faria todo
 * {@code SUM(amount)} mentir sobre quanto entrou de fato.</p>
 */
public record OrderPayment(Long id, Long orderId, PaymentMethod method, BigDecimal amount,
        PaymentStatus status, Integer installments, String gatewayRef, Instant authorizedAt,
        Instant capturedAt, Instant createdAt) {

    public OrderPayment {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId é obrigatório");
        }
        if (method == null) {
            throw new IllegalArgumentException("method é obrigatório");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount deve ser maior que zero");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        if (installments != null) {
            if (method != PaymentMethod.CREDITO) {
                throw new IllegalArgumentException("installments só existe em pagamento CREDITO");
            }
            if (installments < 1 || installments > 24) {
                throw new IllegalArgumentException("installments deve estar entre 1 e 24");
            }
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
        // Espelha o CHECK da V68: status e captura não podem discordar.
        if ((status == PaymentStatus.CAPTURED) != (capturedAt != null)) {
            throw new IllegalArgumentException(
                    "status CAPTURED e capturedAt têm que coexistir: status=" + status + ", capturedAt=" + capturedAt);
        }
    }

    /**
     * Pagamento de balcão: já nasce {@link PaymentStatus#CAPTURED} — o dinheiro está na gaveta no
     * instante da venda, sem passar por autorização assíncrona.
     */
    public static OrderPayment captured(Long orderId, PaymentMethod method, BigDecimal amount,
            Integer installments) {
        Instant now = Instant.now();
        return new OrderPayment(null, orderId, method, amount, PaymentStatus.CAPTURED, installments,
                null, now, now, now);
    }

    /**
     * Estorna um pagamento {@link PaymentStatus#CAPTURED} (PDV-F007): linha nova, nunca um
     * update na original — mesma regra append-only do resto do ledger. Nasce {@code REFUNDED},
     * com o MESMO método e valor do original: o que voltou é exatamente o que entrou.
     *
     * <p>{@code gatewayRef} não é herdado (fica {@code null}): o estorno é um evento novo, com sua
     * própria referência quando a Fatia 10 (gateway) existir — herdar arriscaria colidir com um
     * índice único de referência de gateway no futuro.</p>
     */
    public static OrderPayment refunded(OrderPayment original) {
        if (original.status() != PaymentStatus.CAPTURED) {
            throw new IllegalArgumentException("só se estorna um pagamento CAPTURED");
        }
        Instant now = Instant.now();
        return new OrderPayment(null, original.orderId(), original.method(), original.amount(),
                PaymentStatus.REFUNDED, original.installments(), null, now, null, now);
    }

    /** Reconstitui um pagamento a partir de persistência. */
    public static OrderPayment of(Long id, Long orderId, PaymentMethod method, BigDecimal amount,
            PaymentStatus status, Integer installments, String gatewayRef, Instant authorizedAt,
            Instant capturedAt, Instant createdAt) {
        return new OrderPayment(id, orderId, method, amount, status, installments, gatewayRef,
                authorizedAt, capturedAt, createdAt);
    }

    /**
     * Cobrança de gateway criada no checkout (ECM-F004), aguardando confirmação do webhook.
     * {@code gatewayRef} ainda não existe aqui de propósito — o InfinitePay só o revela via
     * webhook, não na criação do link de checkout.
     */
    public static OrderPayment pending(Long orderId, PaymentMethod method, BigDecimal amount) {
        Instant now = Instant.now();
        return new OrderPayment(null, orderId, method, amount, PaymentStatus.PENDING, null,
                null, null, null, now);
    }

    /**
     * Confirma um pagamento {@link PaymentStatus#PENDING} como {@link PaymentStatus#CAPTURED}
     * (ECM-F004) — atualiza a MESMA linha, ao contrário de {@link #refunded}. Exceção documentada
     * à regra append-only do ledger: {@code uk_order_payment_gateway_ref} é único, e diferente do
     * estorno (que ganha uma referência própria), a confirmação não tem um segundo identificador
     * para usar — uma segunda linha colidiria no índice. {@code OrderPaymentRepositoryImpl.save}
     * já faz UPDATE (não INSERT) quando o id não é nulo, então nenhuma mudança de persistência é
     * necessária além de não gerar um id novo aqui.
     */
    public OrderPayment confirmCaptured(String gatewayRef, Instant capturedAt) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("só se confirma um pagamento PENDING");
        }
        if (gatewayRef == null || gatewayRef.isBlank()) {
            throw new IllegalArgumentException("gatewayRef é obrigatório na confirmação");
        }
        if (capturedAt == null) {
            throw new IllegalArgumentException("capturedAt é obrigatório na confirmação");
        }
        return new OrderPayment(id, orderId, method, amount, PaymentStatus.CAPTURED, installments,
                gatewayRef, capturedAt, capturedAt, createdAt);
    }
}
