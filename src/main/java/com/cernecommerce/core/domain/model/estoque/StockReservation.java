package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reserva temporária de saldo de um SKU em um depósito (EST-F021).
 *
 * <p>Existe para que dois canais — o balcão e o marketplace — consumam a <b>mesma</b> prateleira
 * sem overselling. O PDV não reserva: o produto sai da mão do cliente na hora, e a {@code SAIDA}
 * é imediata. O marketplace reserva no checkout, porque entre montar o pedido e o pagamento
 * confirmar existe uma janela em que o saldo precisa estar comprometido sem ter saído.</p>
 *
 * <h2>Por que reserva em vez de depósito separado</h2>
 * <p>{@link WarehouseType#ECOMMERCE} existe e seria o caminho óbvio para separar os canais, mas
 * numa loja só a prateleira é uma só: partir o saldo em dois pools lógicos criaria rebalanceamento
 * manual permanente e a situação de "o site tem 5 e a loja tem 0" com tudo no mesmo armário. A
 * reserva é o que deixa um pool servir dois canais.</p>
 *
 * <h2>Estado x ledger</h2>
 * <p>Mesma divisão que o resto do módulo: {@link StockBalance#reservedQuantity()} é o
 * <b>contador</b>, escrito na mesma linha e sob o mesmo {@code @Version} do saldo — é ele que faz
 * duas reservas concorrentes do mesmo SKU colidirem em 409 em vez de corromperem o disponível.
 * Esta entidade é o <b>ledger</b>: quem reservou, para quê, até quando e como terminou.</p>
 *
 * @param ownerReference identificador livre de quem pediu a reserva. Hoje é o checkout do
 *        chamador; quando o domínio de pedido existir, passa a ser {@code ORDER:{id}}. É texto e
 *        não FK de propósito — uma FK para uma tabela que ainda não existe seria coluna morta, e
 *        o vínculo forte entra junto com a tabela de pedido.
 * @param expiresAt instante a partir do qual o varredor devolve a quantidade ao disponível.
 *        Obrigatório: reserva sem prazo é saldo perdido sem ninguém perceber.
 */
public record StockReservation(Long id, String sku, Long warehouseId, BigDecimal quantity,
        String ownerReference, ReservationStatus status, Instant expiresAt, Instant createdAt,
        Instant resolvedAt, String username) {

    public StockReservation {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (ownerReference == null || ownerReference.isBlank()) {
            throw new IllegalArgumentException("ownerReference é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt é obrigatório");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt deve ser posterior a createdAt");
        }
        // Reserva ativa ainda não terminou; reserva terminada tem que dizer quando.
        if (status.isActive() != (resolvedAt == null)) {
            throw new IllegalArgumentException(status.isActive()
                    ? "reserva ACTIVE não pode ter resolvedAt"
                    : "reserva encerrada exige resolvedAt");
        }
    }

    /** Cria uma reserva ativa que vence em {@code expiresAt}. */
    public static StockReservation create(String sku, Long warehouseId, BigDecimal quantity,
            String ownerReference, Instant expiresAt, String username) {
        return new StockReservation(null, sku, warehouseId, quantity, ownerReference,
                ReservationStatus.ACTIVE, expiresAt, Instant.now(), null, username);
    }

    /** Reconstitui uma reserva a partir de persistência. */
    public static StockReservation of(Long id, String sku, Long warehouseId, BigDecimal quantity,
            String ownerReference, ReservationStatus status, Instant expiresAt, Instant createdAt,
            Instant resolvedAt, String username) {
        return new StockReservation(id, sku, warehouseId, quantity, ownerReference, status, expiresAt,
                createdAt, resolvedAt, username);
    }

    /** Indica se a reserva ainda segura saldo. */
    public boolean isActive() {
        return status.isActive();
    }

    /**
     * Indica se a reserva já passou do prazo em {@code moment}. Só faz sentido para reserva
     * {@link ReservationStatus#ACTIVE} — uma reserva já consumida não "vence".
     */
    public boolean isExpiredAt(Instant moment) {
        return isActive() && !moment.isBefore(expiresAt);
    }

    /**
     * Marca como consumida: a reserva virou {@code SAIDA} de verdade. O service aplica a baixa de
     * saldo na mesma transação.
     */
    public StockReservation consumed() {
        return terminatedAs(ReservationStatus.CONSUMED);
    }

    /** Marca como liberada por cancelamento explícito, antes do vencimento. */
    public StockReservation released() {
        return terminatedAs(ReservationStatus.RELEASED);
    }

    /** Marca como expirada pelo varredor. */
    public StockReservation expired() {
        return terminatedAs(ReservationStatus.EXPIRED);
    }

    // As transições não checam o estado de origem — quem guarda isso é o service, como já ocorre
    // com o balanço de inventário (requireOpenStockCount). Manter a checagem num lugar só evita
    // duas mensagens de erro diferentes para a mesma regra.
    private StockReservation terminatedAs(ReservationStatus newStatus) {
        return new StockReservation(id, sku, warehouseId, quantity, ownerReference, newStatus,
                expiresAt, createdAt, Instant.now(), username);
    }
}
