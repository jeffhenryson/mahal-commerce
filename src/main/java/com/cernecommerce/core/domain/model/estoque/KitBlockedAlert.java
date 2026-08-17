package com.cernecommerce.core.domain.model.estoque;

/**
 * Um kit que acabou de transicionar de "montável" para "sem estoque de algum componente" numa
 * movimentação/reserva (Bloco 1.2 do BACKEND_TODO de mahal-admin). Acumulado durante a operação e
 * despachado em lote após o commit — mesmo idioma de {@link ReorderAlert} — ver
 * {@code EstoqueService.notifyIfKitsNewlyBlocked}.
 */
public record KitBlockedAlert(String kitSku, String kitName) {

    public KitBlockedAlert {
        if (kitSku == null || kitSku.isBlank()) {
            throw new IllegalArgumentException("kitSku é obrigatório");
        }
        if (kitName == null || kitName.isBlank()) {
            throw new IllegalArgumentException("kitName é obrigatório");
        }
    }
}
