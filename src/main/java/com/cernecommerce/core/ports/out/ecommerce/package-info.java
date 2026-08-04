/**
 * Ports de saída do domínio <b>ecommerce</b>.
 *
 * <p>{@code CartRepository} tem adapter real desde ECM-F003 (Fatia 9). {@code PaymentGatewayPort}
 * tem adapter real desde ECM-F004 (Fatia 10, {@code adapter/out/payment/InfinitePayAdapter}) — o
 * plano original supunha Mercado Pago, o gateway escolhido foi InfinitePay. Cupom e promoção
 * saíram do escopo do projeto (plano-pdv-marketplace.md §8.3) — não há {@code CouponRepository}
 * previsto.</p>
 */
package com.cernecommerce.core.ports.out.ecommerce;
