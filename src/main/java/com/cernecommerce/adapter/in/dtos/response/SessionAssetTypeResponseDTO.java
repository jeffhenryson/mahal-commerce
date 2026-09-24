package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;

/** {@code emUso}/{@code disponivel} só vêm preenchidos no cardápio da mesa. */
public record SessionAssetTypeResponseDTO(Long id, String codigo, String nome, int quantidadeTotal, boolean incluso,
        boolean ativo, Integer emUso, Integer disponivel) {

    public static SessionAssetTypeResponseDTO of(SessionAssetType t) {
        return new SessionAssetTypeResponseDTO(t.id(), t.codigo(), t.nome(), t.quantidadeTotal(), t.incluso(),
                t.ativo(), null, null);
    }

    public static SessionAssetTypeResponseDTO of(SessionMenu.AssetAvailability a) {
        SessionAssetType t = a.tipo();
        return new SessionAssetTypeResponseDTO(t.id(), t.codigo(), t.nome(), t.quantidadeTotal(), t.incluso(),
                t.ativo(), a.emUso(), a.disponivel());
    }
}
