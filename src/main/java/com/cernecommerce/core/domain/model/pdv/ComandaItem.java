package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item lançado numa {@link Comanda} (PDV-F009).
 *
 * <p><b>Não reaproveita {@code OrderItem}</b>: aquele é pensado para uma venda atômica única
 * (todo o pedido nasce de uma vez, via {@code fromCatalog}), enquanto a comanda acumula linhas ao
 * longo de horas — cada uma precisa do próprio {@link #addedAt} e não carrega desconto nem taxa de
 * cashback (resolvidos só no fechamento, quando o item vira {@code OrderItem} de verdade).</p>
 *
 * <p>{@link #unitPrice}/{@link #costPrice} são congelados no instante em que o item é lançado —
 * mesma razão do {@code OrderItem}: a comanda pode ficar aberta por horas, e reprecificar um item
 * já servido no meio do caminho seria incoerente com o que o cliente já consumiu.</p>
 */
public record ComandaItem(
        Long id,
        String sku,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal costPrice,
        String productName,
        Instant addedAt,
        ConsumptionMode mode,
        boolean courtesy,
        Long linkedItemId) {

    public ComandaItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice é obrigatório e não pode ser negativo");
        }
        if (costPrice != null && costPrice.signum() < 0) {
            throw new IllegalArgumentException("costPrice não pode ser negativo");
        }
        if (addedAt == null) {
            throw new IllegalArgumentException("addedAt é obrigatório");
        }
        mode = mode == null ? ConsumptionMode.NORMAL : mode;
        // Cortesia é preço zero por definição — espelha o CHECK ck_comanda_item_courtesy_is_free.
        // A recíproca NÃO vale: um item pode custar zero sem ser cortesia (produto de brinde
        // cadastrado a zero), e é por isso que o campo existe em vez de ser inferido do preço.
        if (courtesy && unitPrice.signum() != 0) {
            throw new IllegalArgumentException(
                    "linha de cortesia tem que ter unitPrice zero: " + unitPrice);
        }
        if (linkedItemId != null && !mode.requiresLinkedItem()) {
            throw new IllegalArgumentException(
                    "linkedItemId só faz sentido em SABOR_EXTRA ou TROCA: mode=" + mode);
        }
    }

    /**
     * Monta um item novo com o preço e o custo vindos do <b>catálogo</b>, nunca do chamador —
     * mesma garantia de {@code OrderItem.fromCatalog}.
     *
     * @throws ProductNotPricedException se o produto não tem preço a cobrar
     */
    public static ComandaItem fromCatalog(String sku, BigDecimal quantity, Pricing pricing, String productName) {
        if (pricing == null || !pricing.isPriced()) {
            throw new ProductNotPricedException(sku);
        }
        return new ComandaItem(null, sku, quantity, pricing.effectivePrice(), pricing.costPrice(),
                productName, Instant.now(), ConsumptionMode.NORMAL, false, null);
    }

    /**
     * Monta uma linha de sessão de narguilé (PDV-F010): o preço vem de fora, e não do
     * {@code pricing} do SKU.
     *
     * <p>É a diferença que a feature inteira gira em torno. Em {@code OPEN_ROSH} o valor é o
     * {@code openRoshPrice} do produto <b>pai</b> — resolver pelo SKU da linha, como
     * {@link #fromCatalog} faz, cobraria o preço da variação do sabor. Em cortesia o valor é zero.
     * O {@code costPrice} continua vindo do catálogo <b>em todos os casos</b>, inclusive na
     * cortesia: é ele que faz a margem do pedido mostrar o prejuízo real da promo, e um custo nulo
     * ali mentiria sobre a pergunta de negócio por trás do open rosh.</p>
     *
     * @param unitPrice já resolvido pelo chamador segundo o modo — ver {@code ComandaService.addItem}.
     * @throws ProductNotPricedException se o produto não tem custo/preço conhecido no catálogo. A
     *         checagem continua valendo mesmo em cortesia, justamente para não gravar custo nulo.
     */
    public static ComandaItem forSession(String sku, BigDecimal quantity, BigDecimal unitPrice, Pricing pricing,
            String productName, ConsumptionMode mode, boolean courtesy, Long linkedItemId) {
        if (pricing == null || !pricing.isPriced()) {
            throw new ProductNotPricedException(sku);
        }
        return new ComandaItem(null, sku, quantity, unitPrice, pricing.costPrice(), productName,
                Instant.now(), mode, courtesy, linkedItemId);
    }

    /** Reconstitui um item a partir de persistência. */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, ConsumptionMode.NORMAL,
                false, null);
    }

    /** Reconstitui um item a partir de persistência, com o modo da sessão (PDV-F010). */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId);
    }

    /** {@code quantity * unitPrice}. */
    public BigDecimal subtotal() {
        return quantity.multiply(unitPrice).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }
}
