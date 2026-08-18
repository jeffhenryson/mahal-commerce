package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.SaleItemRequest;
import com.cernecommerce.adapter.in.dtos.request.SalePaymentRequest;
import com.cernecommerce.adapter.in.dtos.response.DailyRevenueResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderItemAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderPaymentResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PaymentTotalResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SaleReceiptItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SaleReceiptResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TopProductResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentTotal;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrderDTOConverter {

    /**
     * Converte os itens do request em comandos de venda. Note que <b>não há preço</b> aqui: o
     * request só informa SKU, quantidade e desconto (PDV-F004).
     */
    public List<SaleItemCommand> toCommands(List<SaleItemRequest> requests) {
        return requests.stream()
                .map(r -> new SaleItemCommand(r.getSku(), r.getQuantity(), r.getDiscountAmount()))
                .toList();
    }

    /** Converte as linhas de pagamento do request em comandos (PDV-F006). */
    public List<PaymentCommand> toPaymentCommands(List<SalePaymentRequest> requests) {
        return requests.stream()
                .map(r -> new PaymentCommand(PaymentMethod.valueOf(r.getMethod()), r.getAmount(),
                        r.getInstallments()))
                .toList();
    }

    public OrderResponseDTO toResponse(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.id());
        dto.setOrderNumber(order.orderNumber());
        dto.setChannel(order.channel().name());
        dto.setStatus(order.status().name());
        dto.setCustomerId(order.customerId());
        dto.setSessionId(order.sessionId());
        dto.setWarehouseCode(order.warehouseCode());
        dto.setGrossAmount(order.grossAmount());
        dto.setDiscountAmount(order.discountAmount());
        dto.setCashbackRedeemed(order.cashbackRedeemed());
        dto.setNetAmount(order.netAmount());
        dto.setChangeAmount(order.changeAmount());
        dto.setCancelReason(order.cancelReason());
        dto.setCreatedAt(order.createdAt());
        dto.setPaidAt(order.paidAt());
        dto.setConcludedAt(order.concludedAt());
        dto.setCancelledAt(order.cancelledAt());
        dto.setReservedAt(order.reservedAt());
        dto.setItems(order.items().stream().map(this::toResponse).toList());
        return dto;
    }

    /** Igual a {@link #toResponse(Order)}, mas com os pagamentos anexados (PDV-F006). */
    public OrderResponseDTO toResponse(Order order, List<OrderPayment> payments) {
        OrderResponseDTO dto = toResponse(order);
        dto.setPayments(payments.stream().map(this::toResponse).toList());
        return dto;
    }

    /** Resposta de {@code POST /shop/checkout} (ECM-F004): o único lugar com {@code checkoutUrl}. */
    public OrderResponseDTO toCheckoutResponse(Order order, String checkoutUrl) {
        OrderResponseDTO dto = toResponse(order);
        dto.setCheckoutUrl(checkoutUrl);
        return dto;
    }

    public PageResult<OrderResponseDTO> toResponse(PageResult<Order> page) {
        return new PageResult<>(page.content().stream().map(this::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    public OrderPaymentResponseDTO toResponse(OrderPayment payment) {
        OrderPaymentResponseDTO dto = new OrderPaymentResponseDTO();
        dto.setId(payment.id());
        dto.setMethod(payment.method().name());
        dto.setAmount(payment.amount());
        dto.setStatus(payment.status().name());
        dto.setInstallments(payment.installments());
        dto.setCapturedAt(payment.capturedAt());
        dto.setCreatedAt(payment.createdAt());
        return dto;
    }

    public List<PaymentTotalResponseDTO> toPaymentTotalResponse(List<PaymentTotal> totals) {
        return totals.stream().map(t -> {
            PaymentTotalResponseDTO dto = new PaymentTotalResponseDTO();
            dto.setMethod(t.method().name());
            dto.setAmount(t.amount());
            return dto;
        }).toList();
    }

    /**
     * Comprovante interno, não fiscal (PDV §Comprovante). {@code items} não carrega nome de
     * produto — o domínio {@code OrderItem} não guarda um, e resolver por SKU aqui acoplaria o PDV
     * ao catálogo só para exibição; quem consome já tem o catálogo carregado.
     */
    public SaleReceiptResponseDTO toReceipt(Order order, List<OrderPayment> payments) {
        SaleReceiptResponseDTO dto = new SaleReceiptResponseDTO();
        dto.setOrderId(order.id());
        dto.setOrderNumber(order.orderNumber());
        dto.setWarehouseCode(order.warehouseCode());
        dto.setConcludedAt(order.concludedAt());
        dto.setCustomerId(order.customerId());
        dto.setItems(order.items().stream().map(this::toReceiptItem).toList());
        dto.setGrossAmount(order.grossAmount());
        dto.setDiscountAmount(order.discountAmount());
        dto.setNetAmount(order.netAmount());
        dto.setChangeAmount(order.changeAmount());
        dto.setPayments(payments.stream().map(this::toResponse).toList());
        return dto;
    }

    private SaleReceiptItemResponseDTO toReceiptItem(OrderItem item) {
        SaleReceiptItemResponseDTO dto = new SaleReceiptItemResponseDTO();
        dto.setSku(item.sku());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setDiscountAmount(item.discountAmount());
        dto.setNetAmount(item.netAmount());
        return dto;
    }

    // ── Visão do administrador: acrescenta custo e margem ────────────────────────────────────

    public OrderAdminResponseDTO toAdminResponse(Order order) {
        OrderAdminResponseDTO dto = new OrderAdminResponseDTO();
        dto.setId(order.id());
        dto.setOrderNumber(order.orderNumber());
        dto.setChannel(order.channel().name());
        dto.setStatus(order.status().name());
        dto.setCustomerId(order.customerId());
        dto.setSessionId(order.sessionId());
        dto.setWarehouseCode(order.warehouseCode());
        dto.setGrossAmount(order.grossAmount());
        dto.setDiscountAmount(order.discountAmount());
        dto.setCashbackRedeemed(order.cashbackRedeemed());
        dto.setNetAmount(order.netAmount());
        dto.setChangeAmount(order.changeAmount());
        dto.setMarginAmount(totalMargin(order));
        dto.setCancelReason(order.cancelReason());
        dto.setCreatedAt(order.createdAt());
        dto.setPaidAt(order.paidAt());
        dto.setConcludedAt(order.concludedAt());
        dto.setCancelledAt(order.cancelledAt());
        dto.setReservedAt(order.reservedAt());
        dto.setSeparatedAt(order.separatedAt());
        dto.setShippedAt(order.shippedAt());
        dto.setDeliveredAt(order.deliveredAt());
        dto.setAllowedTransitions(order.status().allowedTransitions().stream()
                .map(Enum::name).sorted().toList());
        dto.setItems(order.items().stream().map(this::toAdminResponse).toList());
        return dto;
    }

    public PageResult<OrderAdminResponseDTO> toAdminResponse(PageResult<Order> page) {
        return new PageResult<>(page.content().stream().map(this::toAdminResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    private OrderItemAdminResponseDTO toAdminResponse(OrderItem item) {
        OrderItemAdminResponseDTO dto = new OrderItemAdminResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setDiscountAmount(item.discountAmount());
        dto.setGrossAmount(item.grossAmount());
        dto.setNetAmount(item.netAmount());
        dto.setCostPrice(item.costPrice());
        dto.setMarginAmount(item.marginAmount());
        dto.setCashbackPercent(item.cashbackPercent());
        dto.setCashbackAmount(item.cashbackAmount());
        return dto;
    }

    /**
     * Margem total do pedido. Devolve {@code null} — e não um total parcial — se <b>algum</b> item
     * não tiver custo congelado: somar só os itens conhecidos produziria um número que parece a
     * margem do pedido e não é, o que é pior do que não ter número nenhum.
     */
    private BigDecimal totalMargin(Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.items()) {
            BigDecimal margin = item.marginAmount();
            if (margin == null) {
                return null;
            }
            total = total.add(margin);
        }
        return total;
    }

    private OrderItemResponseDTO toResponse(OrderItem item) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setDiscountAmount(item.discountAmount());
        dto.setGrossAmount(item.grossAmount());
        dto.setNetAmount(item.netAmount());
        dto.setCashbackPercent(item.cashbackPercent());
        dto.setCashbackAmount(item.cashbackAmount());
        return dto;
    }

    // ── Resumo agregado de vendas (GET /orders/summary) ──────────────────────────────────────

    public OrderSummaryResponseDTO toSummaryResponse(OrderSummary summary) {
        OrderSummaryResponseDTO dto = new OrderSummaryResponseDTO();
        dto.setTotalOrders(summary.totalOrders());
        dto.setTotalRevenueNet(summary.totalRevenueNet());
        dto.setTotalRevenueGross(summary.totalRevenueGross());
        dto.setAverageTicket(summary.averageTicket());
        dto.setRevenueByChannel(summary.revenueByChannel().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
        dto.setOrdersByStatus(summary.ordersByStatus().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)));
        dto.setCancelledOrRefundedRate(summary.cancelledOrRefundedRate());
        dto.setDailyRevenue(summary.dailyRevenue().stream().map(this::toResponse).toList());
        dto.setTopProducts(summary.topProducts().stream().map(this::toResponse).toList());
        return dto;
    }

    private DailyRevenueResponseDTO toResponse(OrderSummary.DailyRevenue daily) {
        DailyRevenueResponseDTO dto = new DailyRevenueResponseDTO();
        dto.setDate(daily.date());
        dto.setRevenue(daily.revenue());
        dto.setOrderCount(daily.orderCount());
        return dto;
    }

    // ── Ranking de produtos mais vendidos (GET /orders/analytics/top-products) ──────────────

    public List<TopProductResponseDTO> toTopProductsResponse(List<OrderSummary.TopProduct> topProducts) {
        return topProducts.stream().map(this::toResponse).toList();
    }

    private TopProductResponseDTO toResponse(OrderSummary.TopProduct product) {
        TopProductResponseDTO dto = new TopProductResponseDTO();
        dto.setSku(product.sku());
        dto.setProductName(product.productName());
        dto.setQuantitySold(product.quantitySold());
        dto.setRevenue(product.revenue());
        return dto;
    }
}
