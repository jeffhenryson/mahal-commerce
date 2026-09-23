package com.cernecommerce.core.domain.model.ecommerce;

import java.math.BigDecimal;

/**
 * Linha do {@link Cart} do cliente (ECM-F003). Só SKU e quantidade — o carrinho
 * <b>não guarda preço</b>. Preço é resolvido do catálogo na exibição e congelado
 * só no checkout; guardá-lo aqui criaria a promessa de um preço que o sistema não
 * se comprometeu a honrar.
 *
 * <p><b>ECM-F008 — kit montável.</b> Linha que pertence a um kit carrega o pacote
 * ({@code kitBundleId}), o modelo e o passo em que o item foi escolhido. Os três andam juntos ou
 * não existem. O passo é guardado porque o checkout recota o kit, e a cotação valida cada item
 * contra o passo dele — mesma razão de o preço não estar aqui.</p>
 */
public record CartItem(String sku, BigDecimal quantity, String kitBundleId, Long kitTemplateId, Long kitStepId) {

    public CartItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        boolean anyKit = kitBundleId != null || kitTemplateId != null || kitStepId != null;
        boolean allKit = kitBundleId != null && kitTemplateId != null && kitStepId != null;
        if (anyKit && !allKit) {
            throw new IllegalArgumentException("kitBundleId, kitTemplateId e kitStepId vêm juntos");
        }
    }

    /** Linha avulsa — fora de kit. */
    public CartItem(String sku, BigDecimal quantity) {
        this(sku, quantity, null, null, null);
    }

    public boolean inKit() {
        return kitBundleId != null;
    }
}
