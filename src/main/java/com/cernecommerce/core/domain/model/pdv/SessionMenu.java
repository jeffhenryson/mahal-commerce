package com.cernecommerce.core.domain.model.pdv;

import java.util.List;

/**
 * O cardápio de sessão como a tela da mesa o vê (PDV-F021): faixas ativas, utensílios com quantos
 * estão livres agora, a configuração e se hoje é dia de duplo rosh.
 */
public record SessionMenu(List<SessionTier> faixas, List<AssetAvailability> utensilios, SessionSettings settings,
        boolean duploRoshHoje, List<SessionAddon> adicionais) {

    public SessionMenu {
        adicionais = adicionais == null ? List.of() : List.copyOf(adicionais);
    }

    public record AssetAvailability(SessionAssetType tipo, int emUso) {
        public int disponivel() {
            return Math.max(0, tipo.quantidadeTotal() - emUso);
        }
    }
}
