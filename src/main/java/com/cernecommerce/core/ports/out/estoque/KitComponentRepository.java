package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.KitComponent;

import java.util.List;

/**
 * Port de saída para a receita de kits virtuais (EST-F015). Porta própria, não dentro de
 * {@link ProductRepository} — é uma tabela e um ciclo de vida distintos, substituídos por
 * inteiro a cada definição de receita, diferente do save-in-place de {@code Product}.
 */
public interface KitComponentRepository {

    /** Receita vigente de um kit, na ordem de cadastro. Lista vazia se o SKU nunca foi kit. */
    List<KitComponent> findByKitSku(String kitSku);

    /**
     * Substitui a receita inteira do kit (PUT idempotente): apaga tudo que existia e grava a
     * lista nova, atomicamente. Não há merge — redefinir com um conjunto diferente de
     * componentes remove os que saíram.
     */
    void replaceRecipe(String kitSku, List<KitComponent> components);

    /**
     * Verdadeiro se o SKU já é componente de <b>algum</b> kit. Usado para impedir promover a KIT
     * um SKU que já é componente de outro — sem isso, a invariante de um nível só valeria apenas
     * no sentido direto (checar se o componente novo é SIMPLES), nunca no inverso.
     */
    boolean isUsedAsComponent(String sku);
}
