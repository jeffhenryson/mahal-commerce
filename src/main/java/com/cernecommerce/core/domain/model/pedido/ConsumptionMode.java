package com.cernecommerce.core.domain.model.pedido;

/**
 * Por que uma linha existe na comanda (PDV-F010).
 *
 * <p>Sem isto a comanda é uma lista plana de {@code sku + quantity} e não há onde dizer se a linha
 * é a sessão em si, o segundo sabor de um duplo ou uma troca cortesia durante um consumo livre —
 * informação que o pedido gerado precisa carregar para o histórico da mesa contar a história
 * certa, e que a tela precisa para decidir se mostra "Trocar sabor" na linha.</p>
 *
 * <p><b>É ortogonal a {@code courtesy}.</b> O modo diz <i>o que</i> a linha é; a cortesia diz se
 * ela foi cobrada. {@code SABOR_EXTRA} pode ser cobrado (duplo sem promo) ou não (promo "pague 1
 * leve 2"); {@code TROCA} é sempre cortesia. Inferir um do outro pelo preço zero mentiria — um
 * desconto de 100% dá o mesmo zero.</p>
 */
public enum ConsumptionMode {

    /** Linha comum: item de balcão lançado na mesa, ou a sessão de narguilé em si. */
    NORMAL,

    /**
     * Consumo livre por valor fixo, cobrado <b>uma única vez</b>, com trocas de sabor ilimitadas.
     *
     * <p>É o único modo em que o preço <b>não</b> sai do SKU da linha: o SKU identifica qual
     * essência sair do estoque, mas o valor cobrado é o {@code openRoshPrice} do produto pai.</p>
     */
    OPEN_ROSH,

    /**
     * Segundo sabor de uma sessão dupla. Linha <b>própria</b>, não um adendo à primeira — é o que
     * deixa o consumo por sabor explícito no relatório e o que permite cobrá-la pelo seu próprio
     * preço quando a promo não está valendo.
     */
    SABOR_EXTRA,

    /**
     * Troca de sabor durante um {@link #OPEN_ROSH} já aberto. Sempre cortesia: o cliente não paga
     * de novo, mas a essência sai do estoque e o custo entra na margem.
     */
    TROCA;

    /** Modos que só fazem sentido ligados a outra linha da mesma comanda. */
    public boolean requiresLinkedItem() {
        return this == SABOR_EXTRA || this == TROCA;
    }

    /** Modos que exigem um produto marcado como {@code sessionProduct}. */
    public boolean isSessionMode() {
        return this != NORMAL;
    }

    /**
     * Modos que <b>são</b> cortesia por definição, independentemente do que o cliente HTTP mandou.
     *
     * <p>Existe para o servidor não depender do cliente marcar {@code courtesy} junto: uma
     * {@code TROCA} cobrada seria narguilé vendido duas vezes na mesma sessão de valor fixo.</p>
     */
    public boolean impliesCourtesy() {
        return this == TROCA;
    }
}
