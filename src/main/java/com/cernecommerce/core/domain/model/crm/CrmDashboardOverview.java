package com.cernecommerce.core.domain.model.crm;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Agregação de métricas do CRM para o dashboard overview.
 *
 * <p>{@code ltvMedio}, {@code disparosWhatsappMes} e {@code porSegmento} são placeholders
 * até os domínios de pedidos/cashback e de campanhas existirem no backend — ver
 * {@code crm/listagem-clientes-rfm} e {@code crm/automacoes-campanhas}. {@code clientesAtivos}
 * e {@code porEstagio} são dados reais, calculados sobre o campo {@code estagio} do Kanban.</p>
 */
public record CrmDashboardOverview(
    long totalClientes,
    long clientesAtivos,
    BigDecimal ltvMedio,
    long disparosWhatsappMes,
    Map<String, Long> porSegmento,
    Map<CustomerStage, Long> porEstagio
) {}
