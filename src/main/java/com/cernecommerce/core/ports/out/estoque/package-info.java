/**
 * Ports de saída do domínio <b>estoque</b>.
 *
 * <p><b>Status:</b> operacional. {@code ProductRepository}, {@code WarehouseRepository},
 * {@code StockBalanceRepository}, {@code StockMovementRepository} e
 * {@code ReorderPointRepository} têm adapter JPA correspondente em
 * {@code adapter/out/persistence/repository}.</p>
 *
 * <p>Port ainda previsto: {@code NfeXmlImportPort}, para entrada de mercadoria por XML de NF-e
 * (EST-F005).</p>
 */
package com.cernecommerce.core.ports.out.estoque;
