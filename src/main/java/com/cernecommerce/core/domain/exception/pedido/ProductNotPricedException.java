package com.cernecommerce.core.domain.exception.pedido;

/**
 * Tentativa de vender um produto que não tem preço definido no catálogo (PDV-F004).
 *
 * <p>Recusar é deliberado, e a intenção já estava declarada na V63: <i>"preço zero não é a mesma
 * coisa que preço desconhecido, e um DEFAULT 0 aqui faria o PDV vender de graça em vez de recusar a
 * venda de item sem preço"</i>. Um produto sem preço é um cadastro incompleto, e a hora de
 * descobrir isso é no balcão com o cliente esperando — não no fechamento do mês.</p>
 *
 * <p>Distinta de {@code ProductNotFoundException}: o produto existe, só não está precificado. São
 * duas ações diferentes para quem opera — cadastrar o produto ou precificá-lo.</p>
 */
public class ProductNotPricedException extends RuntimeException {

    public ProductNotPricedException(String sku) {
        super("Produto sem preço definido no catálogo: " + sku
                + ". Defina o preço antes de vender — preço zero e preço desconhecido não são a mesma coisa.");
    }
}
