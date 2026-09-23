package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kit montável (EST-F031) — o "Kit Mahal". Cadastro dos modelos pelo admin, e o fluxo de escolha
 * que o marketplace (ECM-F008) e a comanda (PDV-F019) consomem: listar opções de cada passo,
 * cotar a escolha. Pôr no carrinho e lançar na comanda moram em {@link ShopUseCase} e
 * {@link ComandaUseCase}, que chamam {@link #quote} para validar e precificar.
 */
public interface KitBuilderUseCase {

    /** Um produto (ou variação) escolhível num passo, já com preço e disponibilidade. */
    record StepOption(String sku, String productSku, String name, List<ProductAttribute> attributes,
            BigDecimal price, String imageUrl, Boolean available) {
    }

    List<KitTemplate> listTemplates();

    /** Só os ativos e visíveis no canal — é o que a vitrine e o PDV listam. */
    List<KitTemplate> listSellableTemplates(KitChannel channel);

    /**
     * @throws com.cernecommerce.core.domain.exception.estoque.KitTemplateNotFoundException se não
     *         existir
     */
    KitTemplate getTemplate(Long id);

    /**
     * Mesmo que {@link #getTemplate}, mas modelo inativo ou invisível no canal responde 404 — o
     * público não distingue "não existe" de "não está à venda" (mesma regra do catálogo).
     */
    KitTemplate getSellableTemplate(Long id, KitChannel channel);

    /**
     * @throws com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException se algum
     *         passo apontar para categoria inexistente
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicateKitTemplateNameException se
     *         o nome já estiver em uso
     */
    KitTemplate createTemplate(KitTemplate template);

    /** Substitui o modelo inteiro, passos incluídos. Mesmas exceções de {@link #createTemplate}. */
    KitTemplate updateTemplate(Long id, KitTemplate template);

    void deleteTemplate(Long id);

    /**
     * Opções de um passo: produtos ativos, publicados, precificados, não-kit, visíveis no canal e
     * da categoria do passo. Produto com grade vira uma opção por variação ativa.
     *
     * @param warehouseCode depósito para {@code available}; {@code null} deixa {@code available}
     *        nulo (não consultado)
     */
    List<StepOption> listStepOptions(Long templateId, Long stepId, KitChannel channel, String warehouseCode);

    /**
     * Valida a escolha contra o modelo e devolve a cotação com o desconto já rateado por linha.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException se a
     *         escolha não fecha um kit válido — o código diz qual regra falhou
     */
    KitQuote quote(KitSelection selection, KitChannel channel);
}
