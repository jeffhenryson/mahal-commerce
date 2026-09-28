package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F024 — a mesa só recebe sessões do cardápio; produto do catálogo passa pelo balcão. Controlado
 * por {@code pdv.mesa.catalog-items-enabled} (padrão {@code false}). 409.
 */
public class CatalogItemNotAllowedOnTableException extends RuntimeException {
    public CatalogItemNotAllowedOnTableException(Long comandaId) {
        super("A mesa " + comandaId + " não aceita produto do catálogo — venda-o pelo balcão");
    }
}
