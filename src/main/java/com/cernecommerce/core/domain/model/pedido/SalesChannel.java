package com.cernecommerce.core.domain.model.pedido;

/**
 * Canal em que o pedido nasceu (PDV-F003).
 *
 * <p>É o discriminador que permite balcão e marketplace compartilharem <b>uma</b> tabela de
 * pedido. A alternativa — duas tabelas — obrigaria o extrato do cliente, o ledger de cashback, a
 * devolução, o faturamento e o relatório de margem a pagarem um {@code UNION} cada um, ou a
 * duplicarem lógica. Com um canal só, quem precisa distinguir paga um {@code WHERE channel = ?} e
 * quem não precisa não paga nada.</p>
 *
 * <p>O que muda entre os dois não é a natureza do documento — é <b>quando</b> ele termina. Uma
 * venda de balcão não é um pedido <i>sem</i> máquina de estados: é um pedido que nasce e termina
 * na mesma transação.</p>
 */
public enum SalesChannel {

    /**
     * Venda presencial na loja. Sempre vinculada a uma sessão de caixa; cliente é opcional
     * (a venda anônima de passagem é a maioria do balcão).
     */
    BALCAO,

    /**
     * Consumo de salão, gerado pelo <b>fechamento de uma comanda de mesa</b> (PDV-F010). Sempre
     * vinculado a uma sessão de caixa e a uma comanda; cliente é opcional, como no balcão.
     *
     * <p><b>Por que não é BALCAO.</b> Os dois são presenciais e liquidam na mesma gaveta, mas a
     * pergunta que o canal responde é "de onde este pedido veio", e mesa e balcão são operações
     * distintas: o balcão é instantâneo, a mesa acumula consumo por horas e baixa estoque a cada
     * item lançado. Sem o canal, o histórico da mesa fica indistinguível de uma venda avulsa, e
     * relatório por canal soma coisas que a operação separa.</p>
     *
     * <p><b>Não abre uma terceira tabela</b>, pelo mesmo motivo que MARKETPLACE não abriu a
     * segunda: quem precisa distinguir paga um {@code WHERE channel = ?} e quem não precisa não
     * paga nada.</p>
     */
    MESA,

    /**
     * Pedido online. Nunca tem sessão de caixa e <b>sempre</b> tem cliente — um pedido online sem
     * cliente não tem para quem entregar nem para quem estornar.
     */
    MARKETPLACE
}
