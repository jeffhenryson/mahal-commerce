package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.estoque.Pricing;

import java.math.BigDecimal;

/**
 * Item de um {@link Order} (PDV-F004): o que foi vendido, por quanto, quanto custou e qual taxa de
 * cashback valia no instante da venda.
 *
 * <h2>Por que três valores são congelados aqui</h2>
 * <p>O item guarda cópias, não referências ao catálogo, e cada uma existe por um motivo diferente:</p>
 * <ul>
 *   <li>{@link #unitPrice} — o preço praticado. Sem ele, mudar o preço amanhã reescreveria o
 *       faturamento de ontem.</li>
 *   <li>{@link #costPrice} — <b>o snapshot que mais gente esquece e o mais caro de retrofitar.</b>
 *       Sem ele, a próxima compra que alterar o custo do produto reescreve a margem histórica de
 *       todos os pedidos passados, e não há como reconstruir: o custo antigo não fica em lugar
 *       nenhum. Como o cashback sai da margem, isso não é detalhe contábil — é a base de
 *       calibragem do programa de fidelidade sendo apagada.</li>
 *   <li>{@link #cashbackPercent} — a taxa vigente. Sem ela, mudar a taxa amanhã reescreveria o
 *       valor gerado por pedidos de ontem.</li>
 * </ul>
 *
 * <h2>Desconto é campo próprio, não preço menor</h2>
 * <p>{@link #discountAmount} existe em vez de simplesmente gravar um {@code unitPrice} mais baixo
 * porque é o que permite responder "quanto de margem o desconto do balcão comeu no mês" com um
 * {@code SUM}, em vez de com arqueologia. Um preço menor no request seria indistinguível de erro de
 * digitação, de desconto autorizado e de fraude.</p>
 *
 * <p>Os três campos congelados são anuláveis <b>apenas</b> para reconstituir pedidos anteriores à
 * migração, que nasceram sem eles. Um default zero mentiria sobre a margem; nulo diz a verdade,
 * que é "não se sabe". Item novo passa por {@link #fromCatalog}, que exige preço.</p>
 */
public record OrderItem(
        Long id,
        String sku,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal costPrice,
        BigDecimal discountAmount,
        BigDecimal cashbackPercent) {

    public OrderItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (unitPrice != null && unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice não pode ser negativo");
        }
        if (costPrice != null && costPrice.signum() < 0) {
            throw new IllegalArgumentException("costPrice não pode ser negativo");
        }
        discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        if (discountAmount.signum() < 0) {
            throw new IllegalArgumentException("discountAmount não pode ser negativo");
        }
        if (unitPrice != null && discountAmount.compareTo(quantity.multiply(unitPrice)) > 0) {
            throw new IllegalArgumentException(
                    "discountAmount não pode ser maior que o bruto do item: desconto que zera o item é devolução, não venda");
        }
        if (cashbackPercent != null
                && (cashbackPercent.signum() < 0 || cashbackPercent.compareTo(Money.HUNDRED) > 0)) {
            throw new IllegalArgumentException("cashbackPercent deve estar entre 0 e 100");
        }
    }

    /**
     * Monta um item novo com o preço e o custo vindos do <b>catálogo</b>, nunca do chamador
     * (PDV-F004). É o único caminho para item de pedido novo, e é isso que fecha o furo de quem
     * tem {@code PDV_SALE_MANAGE} vender qualquer coisa por qualquer preço.
     *
     * <p>A taxa de cashback fica nula aqui: ela é resolvida pela cadeia de escopos do programa de
     * fidelidade, não pelo catálogo, e é carimbada depois via {@link #withCashbackPercent}.</p>
     *
     * @param pricing precificação vigente, tipicamente de {@code EstoqueUseCase.findPricingBySku}
     * @throws ProductNotPricedException se o produto não tem preço a cobrar. Preço zero e preço
     *         desconhecido não são a mesma coisa: um default zero faria o PDV vender de graça em
     *         vez de recusar a venda de item sem preço.
     */
    public static OrderItem fromCatalog(String sku, BigDecimal quantity, Pricing pricing,
            BigDecimal discountAmount) {
        if (pricing == null || !pricing.isPriced()) {
            throw new ProductNotPricedException(sku);
        }
        return new OrderItem(null, sku, quantity, pricing.effectivePrice(), pricing.costPrice(),
                discountAmount, null);
    }

    /** Reconstitui um item a partir de persistência. */
    public static OrderItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, BigDecimal discountAmount, BigDecimal cashbackPercent) {
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent);
    }

    /** Carimba a taxa de cashback vigente. Cópia — o item permanece imutável. */
    public OrderItem withCashbackPercent(BigDecimal newCashbackPercent) {
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, discountAmount, newCashbackPercent);
    }

    /** Concede desconto neste item. Cópia — o item permanece imutável. */
    public OrderItem withDiscount(BigDecimal newDiscountAmount) {
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, newDiscountAmount, cashbackPercent);
    }

    /**
     * Bruto do item: {@code quantity * unitPrice}.
     *
     * @return {@code null} para item legado sem preço congelado.
     */
    public BigDecimal grossAmount() {
        return unitPrice == null ? null : quantity.multiply(unitPrice).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }

    /**
     * Líquido do item: bruto menos desconto. É a base do cashback e da margem.
     *
     * @return {@code null} para item legado sem preço congelado.
     */
    public BigDecimal netAmount() {
        BigDecimal gross = grossAmount();
        return gross == null ? null : gross.subtract(discountAmount);
    }

    /**
     * Cashback gerado: {@code netAmount * cashbackPercent / 100}. Sobre o <b>líquido</b>, com a
     * taxa <b>do próprio item</b> — uma taxa média sobre o total do pedido impediria qualquer
     * análise por item, que é justamente onde o programa se paga ou se perde.
     *
     * @return {@code null} se falta preço ou taxa carimbada.
     */
    public BigDecimal cashbackAmount() {
        BigDecimal net = netAmount();
        if (net == null || cashbackPercent == null) {
            return null;
        }
        return net.multiply(cashbackPercent)
                .divide(Money.HUNDRED, Money.MONEY_SCALE, Money.ROUNDING);
    }

    /**
     * Lucro bruto do item: {@code netAmount - costPrice * quantity}. Negativo quando se vendeu
     * abaixo do custo, o que continua permitido — queima de estoque e produto-isca são decisões
     * comerciais legítimas, na mesma política de {@code Pricing.isBelowCost()}.
     *
     * @return {@code null} se falta preço ou custo congelado.
     */
    public BigDecimal marginAmount() {
        BigDecimal net = netAmount();
        if (net == null || costPrice == null) {
            return null;
        }
        return net.subtract(quantity.multiply(costPrice)).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }
}
