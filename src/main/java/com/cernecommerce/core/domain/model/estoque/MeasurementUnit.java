package com.cernecommerce.core.domain.model.estoque;

/**
 * Unidade de medida em que um {@link Product} é contado — unidade, caixa, quilo, etc.
 *
 * <p>Sem isso, "quantidade 10" era ambíguo entre 10 unidades, 10 caixas e 10 pacotes em toda a
 * cadeia de estoque (saldo, movimentação, contagem, alerta de reposição). Default {@link #UN}
 * preserva o comportamento implícito de todo produto já cadastrado antes deste campo existir.</p>
 */
public enum MeasurementUnit {
    UN,
    CX,
    KG,
    G,
    L,
    ML,
    PCT,
    DZ
}
