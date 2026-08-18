/**
 * Ports de saída do domínio <b>estoque</b>.
 *
 * <p><b>Status:</b> operacional. {@code ProductRepository}, {@code WarehouseRepository},
 * {@code StockBalanceRepository}, {@code StockMovementRepository} e
 * {@code ReorderPointRepository} têm adapter JPA correspondente em
 * {@code adapter/out/persistence/repository}.</p>
 *
 * <p>{@code NfeXmlImportPort} (EST-F005, entrada de mercadoria por XML de NF-e) implementado por
 * {@code adapter/out/nfe/JdkDomNfeXmlImportAdapter} — nenhum port previsto continua pendente.</p>
 */
package com.cernecommerce.core.ports.out.estoque;
