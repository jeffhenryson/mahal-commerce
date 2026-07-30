package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Linha da receita de um kit virtual (EST-F015, §2.10): {@code quantity} unidades de
 * {@code componentSku} são consumidas por unidade vendida de {@code kitSku}.
 *
 * <p><b>Não valida aqui</b> que {@code componentSku != kitSku} nem que o componente seja
 * {@link ProductType#SIMPLES} — as duas regras dependem de consultar outro agregado
 * ({@link Product} do próprio kit e do componente), que um compact constructor não alcança.
 * Ambas moram em {@code EstoqueService.defineKitRecipe}, como exceções nomeadas distinguíveis
 * (o kit dentro de kit teria virado só um {@link IllegalArgumentException}/400 genérico aqui,
 * e o desenho pede um 409 específico para cada caso).</p>
 */
public record KitComponent(Long id, String kitSku, String componentSku, BigDecimal quantity) {

    public KitComponent {
        if (kitSku == null || kitSku.isBlank()) {
            throw new IllegalArgumentException("kitSku é obrigatório");
        }
        if (componentSku == null || componentSku.isBlank()) {
            throw new IllegalArgumentException("componentSku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
    }

    /** Cria uma linha de receita nova (sem id). */
    public static KitComponent create(String kitSku, String componentSku, BigDecimal quantity) {
        return new KitComponent(null, kitSku, componentSku, quantity);
    }

    /** Reconstitui uma linha de receita a partir de persistência. */
    public static KitComponent of(Long id, String kitSku, String componentSku, BigDecimal quantity) {
        return new KitComponent(id, kitSku, componentSku, quantity);
    }
}
