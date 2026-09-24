package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de entrada do cardápio de sessão da mesa (PDV-F021): cadastro de faixas, utensílios e
 * configuração (admin) e a leitura do cardápio para a tela da mesa (atendente).
 */
public interface SessionMenuUseCase {

    /** Faixas ativas, utensílios ativos com disponibilidade, configuração e se hoje é duplo rosh. */
    SessionMenu getMenu();

    List<SessionTier> listTiers();

    /** @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException nome repetido */
    SessionTier createTier(String nome, BigDecimal preco, String marcas, int ordem);

    /**
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionTierNotFoundException se não existir
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException nome repetido
     */
    SessionTier updateTier(Long id, String nome, BigDecimal preco, String marcas, int ordem, boolean ativo);

    List<SessionAssetType> listAssetTypes();

    /** @throws com.cernecommerce.core.domain.exception.pdv.SessionMenuConflictException código repetido */
    SessionAssetType createAssetType(String codigo, String nome, int quantidadeTotal, boolean incluso);

    /** @throws com.cernecommerce.core.domain.exception.pdv.SessionAssetTypeNotFoundException se não existir */
    SessionAssetType updateAssetType(Long id, String nome, int quantidadeTotal, boolean incluso, boolean ativo);

    SessionSettings getSettings();

    /**
     * @throws com.cernecommerce.core.domain.exception.pdv.SessionAssetTypeNotFoundException se o
     *         código de vaso não existir
     */
    SessionSettings updateSettings(SessionSettings settings);
}
