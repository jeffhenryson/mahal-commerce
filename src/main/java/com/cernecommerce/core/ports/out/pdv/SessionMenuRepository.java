package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.pdv.SessionAssetAllocation;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionTier;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port de saída do cardápio de sessão (PDV-F021): faixas, utensílios, configuração e as alocações
 * de utensílio por linha de comanda.
 */
public interface SessionMenuRepository {

    List<SessionTier> findAllTiers();

    Optional<SessionTier> findTierById(Long id);

    Optional<SessionTier> findTierByNome(String nome);

    SessionTier saveTier(SessionTier tier);

    // PDV-F024 — adicionais pagos
    List<SessionAddon> findAllAddons();

    Optional<SessionAddon> findAddonById(Long id);

    Optional<SessionAddon> findAddonByNome(String nome);

    SessionAddon saveAddon(SessionAddon addon);

    List<SessionAssetType> findAllAssetTypes();

    Optional<SessionAssetType> findAssetTypeById(Long id);

    Optional<SessionAssetType> findAssetTypeByCodigo(String codigo);

    SessionAssetType saveAssetType(SessionAssetType type);

    /**
     * Trava os tipos informados até o fim da transação (em ordem de id). Quem aloca utensílio
     * chama isto antes de contar o que está em uso — senão duas sessões levariam o mesmo vaso.
     */
    List<SessionAssetType> lockAssetTypes(Collection<Long> ids);

    SessionSettings getSettings();

    SessionSettings saveSettings(SessionSettings settings);

    /** Quantidade em uso (alocações não liberadas) por id de tipo. Tipo sem uso não aparece. */
    Map<Long, Integer> countInUseByAssetType();

    void saveAllocations(List<SessionAssetAllocation> allocations);

    /** Alocações ainda abertas das linhas informadas. */
    List<SessionAssetAllocation> findOpenAllocations(Collection<Long> comandaItemIds);

    /** Libera as alocações abertas das linhas informadas; devolve quantas foram liberadas. */
    int releaseAllocations(Collection<Long> comandaItemIds, Instant at);
}
