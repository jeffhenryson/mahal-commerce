package com.cernecommerce.core.domain.model.pdv;

import java.util.Locale;

/**
 * Tipo de utensílio da sessão (PDV-F021): vaso pequeno, vaso grande, pinça, prato, tapete.
 *
 * <p>É <b>ativo</b> da casa, não estoque: não é vendido nem consumido. A sessão aloca um de cada
 * ao ser lançada e libera quando a mesa fecha, e o que se controla é quantos estão livres.
 * {@code incluso} marca o que acompanha toda sessão; o vaso não é incluso porque a sessão leva o
 * padrão OU o grande (ver {@link SessionSettings}).</p>
 */
public record SessionAssetType(Long id, String codigo, String nome, int quantidadeTotal, boolean incluso,
        boolean ativo) {

    public SessionAssetType {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("código do utensílio é obrigatório");
        }
        codigo = codigo.trim().toUpperCase(Locale.ROOT);
        if (codigo.length() > 30) {
            throw new IllegalArgumentException("código do utensílio excede 30 caracteres");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome do utensílio é obrigatório");
        }
        nome = nome.trim();
        if (nome.length() > 60) {
            throw new IllegalArgumentException("nome do utensílio excede 60 caracteres");
        }
        if (quantidadeTotal < 0) {
            throw new IllegalArgumentException("quantidade total não pode ser negativa");
        }
    }

    public static SessionAssetType create(String codigo, String nome, int quantidadeTotal, boolean incluso) {
        return new SessionAssetType(null, codigo, nome, quantidadeTotal, incluso, true);
    }

    public SessionAssetType withData(String novoNome, int novaQuantidade, boolean novoIncluso, boolean novoAtivo) {
        return new SessionAssetType(id, codigo, novoNome, novaQuantidade, novoIncluso, novoAtivo);
    }
}
