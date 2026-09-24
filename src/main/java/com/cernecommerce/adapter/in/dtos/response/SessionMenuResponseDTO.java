package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.SessionMenu;

import java.util.List;

/** Cardápio de sessão para a tela da mesa (PDV-F021). */
public record SessionMenuResponseDTO(List<SessionTierResponseDTO> faixas, List<SessionAssetTypeResponseDTO> utensilios,
        SessionSettingsResponseDTO config, boolean duploRoshHoje) {

    public static SessionMenuResponseDTO of(SessionMenu m) {
        return new SessionMenuResponseDTO(
                m.faixas().stream().map(SessionTierResponseDTO::of).toList(),
                m.utensilios().stream().map(SessionAssetTypeResponseDTO::of).toList(),
                SessionSettingsResponseDTO.of(m.settings()),
                m.duploRoshHoje());
    }
}
