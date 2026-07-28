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
     * Pedido online. Nunca tem sessão de caixa e <b>sempre</b> tem cliente — um pedido online sem
     * cliente não tem para quem entregar nem para quem estornar.
     */
    MARKETPLACE
}
