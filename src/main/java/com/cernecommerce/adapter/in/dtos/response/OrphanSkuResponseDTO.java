package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha do diagnóstico de integridade do estoque (EST-C011): par SKU/depósito com dados
 * gravados cujo SKU não existe no catálogo.
 *
 * <p>Os campos existem para sustentar a decisão humana sobre o destino do órfão:
 * {@code quantity} e {@code movementCount} dizem o tamanho do passivo, {@code lastMovementAt}
 * diz se é dado morto ou se algo ainda escreve nele, e {@code hasReorderPoint} avisa que há
 * também uma configuração de mínimo a limpar.</p>
 */
@Data
public class OrphanSkuResponseDTO {
    private String sku;
    private String warehouseCode;
    /** Saldo gravado. Zero quando o órfão só tem movimentações ou só ponto de reposição. */
    private BigDecimal quantity;
    private long movementCount;
    private boolean hasReorderPoint;
    /** Nulo quando o par nunca foi movimentado. */
    private Instant lastMovementAt;
}
