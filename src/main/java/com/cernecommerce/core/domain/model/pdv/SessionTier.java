package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;

/**
 * Faixa de preço da sessão de narguilé (PDV-F021) — "Tradicional R$ 25 (Zgy, Zomo, Pred)".
 *
 * <p>É um item do cardápio da mesa, não um produto do catálogo: não tem estoque nem SKU real. Na
 * comanda vira uma linha com o SKU sintético {@link #sku()}.</p>
 */
public record SessionTier(Long id, String nome, BigDecimal preco, String marcas, int ordem, boolean ativo) {

    /** Prefixo do SKU sintético da linha de sessão — ver V128. */
    public static final String SKU_PREFIX = "SESS-";

    public static final int NOME_MAX_LENGTH = 60;
    public static final int MARCAS_MAX_LENGTH = 255;

    public SessionTier {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome da faixa é obrigatório");
        }
        nome = nome.trim();
        if (nome.length() > NOME_MAX_LENGTH) {
            throw new IllegalArgumentException("nome da faixa excede " + NOME_MAX_LENGTH + " caracteres");
        }
        if (preco == null || preco.signum() < 0) {
            throw new IllegalArgumentException("preço da faixa é obrigatório e não pode ser negativo");
        }
        if (marcas != null) {
            marcas = marcas.isBlank() ? null : marcas.trim();
        }
        if (marcas != null && marcas.length() > MARCAS_MAX_LENGTH) {
            throw new IllegalArgumentException("marcas excede " + MARCAS_MAX_LENGTH + " caracteres");
        }
        if (ordem < 0) {
            throw new IllegalArgumentException("ordem não pode ser negativa");
        }
    }

    public static SessionTier create(String nome, BigDecimal preco, String marcas, int ordem) {
        return new SessionTier(null, nome, preco, marcas, ordem, true);
    }

    public SessionTier withData(String novoNome, BigDecimal novoPreco, String novasMarcas, int novaOrdem,
            boolean novoAtivo) {
        return new SessionTier(id, novoNome, novoPreco, novasMarcas, novaOrdem, novoAtivo);
    }

    /** SKU sintético da linha de sessão desta faixa na comanda e no pedido. */
    public String sku() {
        return SKU_PREFIX + id;
    }
}
