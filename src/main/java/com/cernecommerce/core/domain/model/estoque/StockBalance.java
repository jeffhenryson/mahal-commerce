package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ReservedStockException;

import java.math.BigDecimal;

/**
 * Saldo de um SKU em um {@link Warehouse}. {@code version} suporta locking otimista para as
 * escritas concorrentes de {@link StockMovement} (movimentação manual de estoque) e de
 * {@link StockReservation}.
 *
 * <h2>Físico, reservado e disponível (EST-F021)</h2>
 * <p>{@link #quantity} é o que existe na prateleira. {@link #reservedQuantity} é a parte dele já
 * prometida a um pedido que ainda não saiu — tipicamente um pedido de marketplace aguardando
 * pagamento. {@link #availableQuantity()} é a diferença, e é contra ele que toda saída nova é
 * validada.</p>
 *
 * <p>O contador de reservado mora <b>aqui</b>, na mesma linha e sob o mesmo {@code @Version} do
 * saldo, e não numa soma sobre a tabela de reservas. É isso que faz duas reservas concorrentes do
 * mesmo SKU disputarem a mesma linha e uma delas levar 409 — exatamente como duas movimentações
 * concorrentes já se comportam. Uma agregação à parte não travaria nada.</p>
 */
public record StockBalance(Long id, String sku, Long warehouseId, BigDecimal quantity,
        BigDecimal reservedQuantity, long version) {

    public StockBalance {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity não pode ser negativa");
        }
        reservedQuantity = reservedQuantity == null ? BigDecimal.ZERO : reservedQuantity;
        if (reservedQuantity.signum() < 0) {
            throw new IllegalArgumentException("reservedQuantity não pode ser negativa");
        }
        if (reservedQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException(
                    "reservedQuantity não pode ser maior que quantity: não se reserva o que não existe");
        }
    }

    /** Saldo inicial (zero) de um SKU em um depósito, quando ainda não houve nenhuma movimentação. */
    public static StockBalance zero(String sku, Long warehouseId) {
        return new StockBalance(null, sku, warehouseId, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
    }

    /**
     * Saldo DERIVADO de um kit virtual (EST-F015): nunca corresponde a uma linha real de
     * {@code stock_balance} — {@code id} nulo e {@code version} zero são placeholders
     * estruturais, nunca persistidos. {@code reservedQuantity} é sempre zero: reserva de kit não
     * existe nesta fatia. Existe como fábrica própria (em vez de reaproveitar {@link #zero}) só
     * para deixar explícito, no call site de {@code EstoqueService}, que aquele saldo é calculado
     * na hora, não lido do banco.
     */
    public static StockBalance derived(String sku, Long warehouseId, BigDecimal quantity) {
        return new StockBalance(null, sku, warehouseId, quantity, BigDecimal.ZERO, 0L);
    }

    /**
     * Reconstitui um saldo <b>sem reserva</b> a partir de persistência. Sobrecarga de conveniência
     * para os chamadores anteriores a EST-F021 — equivale a {@code reservedQuantity = 0}.
     */
    public static StockBalance of(Long id, String sku, Long warehouseId, BigDecimal quantity, long version) {
        return new StockBalance(id, sku, warehouseId, quantity, BigDecimal.ZERO, version);
    }

    /** Reconstitui um saldo a partir de persistência. */
    public static StockBalance of(Long id, String sku, Long warehouseId, BigDecimal quantity,
            BigDecimal reservedQuantity, long version) {
        return new StockBalance(id, sku, warehouseId, quantity, reservedQuantity, version);
    }

    /**
     * Saldo livre para venda: {@code quantity - reservedQuantity}. É o número que a vitrine mostra
     * e contra o qual toda saída nova é validada — não o físico.
     */
    public BigDecimal availableQuantity() {
        return quantity.subtract(reservedQuantity);
    }

    /**
     * Aplica uma movimentação e retorna o saldo resultante.
     *
     * <ul>
     *   <li>{@code ENTRADA} — soma {@code movementQuantity} ao saldo.</li>
     *   <li>{@code SAIDA} — subtrai, validando contra o <b>disponível</b>. Lança
     *       {@link InsufficientStockException} se nem o físico bastaria, ou
     *       {@link ReservedStockException} se o físico bastaria mas parte dele está reservada.
     *       Zerar exatamente continua permitido.</li>
     *   <li>{@code AJUSTE} — <b>substitui</b> o saldo por {@code movementQuantity}, que é o valor
     *       contado na prateleira, não um delta (EST-C009). Sobe ou desce, e zero é válido.</li>
     * </ul>
     *
     * <p>Um {@code AJUSTE} negativo é {@link IllegalArgumentException}, e não
     * {@code InsufficientStockException}: não existe saldo insuficiente para uma contagem — o que
     * há é um alvo inválido. Já um {@code AJUSTE} que levaria o saldo <b>abaixo do reservado</b> é
     * {@link ReservedStockException}: a contagem encontrou menos unidades do que já foram
     * prometidas, e quais pedidos perder é decisão humana, não arredondamento do sistema.</p>
     *
     * <p>A distinção entre as duas exceções de saída importa para quem está no balcão: "não tem" e
     * "tem, mas está separado para um pedido online" pedem ações diferentes.</p>
     */
    public StockBalance apply(MovementType type, BigDecimal movementQuantity) {
        if (type == MovementType.AJUSTE) {
            if (movementQuantity == null || movementQuantity.signum() < 0) {
                throw new IllegalArgumentException("quantidade de AJUSTE não pode ser negativa");
            }
            if (movementQuantity.compareTo(reservedQuantity) < 0) {
                throw new ReservedStockException(sku, warehouseId, quantity, reservedQuantity, movementQuantity);
            }
            return new StockBalance(id, sku, warehouseId, movementQuantity, reservedQuantity, version);
        }
        if (type == MovementType.SAIDA) {
            BigDecimal remainingPhysical = quantity.subtract(movementQuantity);
            if (remainingPhysical.signum() < 0) {
                throw new InsufficientStockException(sku, warehouseId, quantity, movementQuantity);
            }
            // O físico dá conta, mas parte dele está prometida. Exceção diferente de propósito: aqui
            // o operador tem uma saída — cancelar a reserva — que não existe quando simplesmente não há.
            if (remainingPhysical.compareTo(reservedQuantity) < 0) {
                throw new ReservedStockException(sku, warehouseId, quantity, reservedQuantity, movementQuantity);
            }
            return new StockBalance(id, sku, warehouseId, remainingPhysical, reservedQuantity, version);
        }
        return new StockBalance(id, sku, warehouseId, quantity.add(movementQuantity), reservedQuantity, version);
    }

    /**
     * Compromete {@code reserveQuantity} do disponível, sem tirar nada da prateleira.
     *
     * @throws InsufficientStockException se não houver disponível suficiente. O saldo reportado na
     *         mensagem é o <b>disponível</b>, não o físico: é o número que interessa a quem tentou
     *         reservar
     */
    public StockBalance reserve(BigDecimal reserveQuantity) {
        if (reserveQuantity == null || reserveQuantity.signum() <= 0) {
            throw new IllegalArgumentException("quantidade a reservar deve ser maior que zero");
        }
        if (reserveQuantity.compareTo(availableQuantity()) > 0) {
            throw new InsufficientStockException(sku, warehouseId, availableQuantity(), reserveQuantity);
        }
        return new StockBalance(id, sku, warehouseId, quantity, reservedQuantity.add(reserveQuantity), version);
    }

    /**
     * Devolve {@code releaseQuantity} ao disponível sem mexer no físico — a reserva foi cancelada
     * ou venceu, e a mercadoria nunca saiu.
     */
    public StockBalance releaseReservation(BigDecimal releaseQuantity) {
        return new StockBalance(id, sku, warehouseId, quantity, subtractReserved(releaseQuantity), version);
    }

    /**
     * Converte a reserva em saída de verdade: o físico e o reservado caem <b>juntos</b>, e o
     * disponível não se mexe — ele já estava descontado desde a reserva.
     */
    public StockBalance consumeReservation(BigDecimal consumeQuantity) {
        BigDecimal newReserved = subtractReserved(consumeQuantity);
        BigDecimal newQuantity = quantity.subtract(consumeQuantity);
        if (newQuantity.signum() < 0) {
            throw new InsufficientStockException(sku, warehouseId, quantity, consumeQuantity);
        }
        return new StockBalance(id, sku, warehouseId, newQuantity, newReserved, version);
    }

    private BigDecimal subtractReserved(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("quantidade de reserva deve ser maior que zero");
        }
        BigDecimal newReserved = reservedQuantity.subtract(amount);
        if (newReserved.signum() < 0) {
            throw new IllegalArgumentException("liberação maior que o reservado para o SKU " + sku
                    + ": reservado " + reservedQuantity + ", solicitado " + amount);
        }
        return newReserved;
    }
}
