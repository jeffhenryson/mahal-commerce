package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderEntity;
import com.cernecommerce.adapter.out.persistence.entity.OrderItemEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class OrderRepositoryImpl implements OrderRepository {

    /** Largura da numeração de pedido, zero-padded — {@code 000001000}. */
    private static final int ORDER_NUMBER_WIDTH = 9;

    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = orderJpaRepository.findById(order.id() == null ? -1L : order.id())
                .orElseGet(OrderEntity::new);
        entity.setOrderNumber(order.orderNumber());
        entity.setChannel(order.channel().name());
        entity.setStatus(order.status().name());
        entity.setCustomerId(order.customerId());
        entity.setSessionId(order.sessionId());
        entity.setWarehouseCode(order.warehouseCode());
        entity.setTotalAmount(order.grossAmount());
        entity.setDiscountAmount(order.discountAmount());
        entity.setCashbackRedeemed(order.cashbackRedeemed());
        entity.setNetAmount(order.netAmount());
        entity.setChangeAmount(order.changeAmount());
        entity.setCancelReason(order.cancelReason());
        entity.setCreatedAt(order.createdAt());
        entity.setPaidAt(order.paidAt());
        entity.setConcludedAt(order.concludedAt());
        entity.setCancelledAt(order.cancelledAt());
        entity.setRefundedAt(order.refundedAt());
        entity.setReservedAt(order.reservedAt());
        entity.setSeparatedAt(order.separatedAt());
        entity.setShippedAt(order.shippedAt());
        entity.setDeliveredAt(order.deliveredAt());
        entity.setComandaId(order.comandaId());
        entity.setTableLabel(order.tableLabel());
        entity.setServiceFeeAmount(order.serviceFeeAmount());

        // Os itens são reescritos por inteiro: o pedido é imutável depois de concluído, então este
        // caminho só é exercitado antes da conclusão. orphanRemoval limpa os antigos.
        entity.getItems().clear();
        for (OrderItem item : order.items()) {
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setOrder(entity);
            itemEntity.setSku(item.sku());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            itemEntity.setCostPrice(item.costPrice());
            itemEntity.setDiscountAmount(item.discountAmount());
            itemEntity.setCashbackPercent(item.cashbackPercent());
            itemEntity.setProductName(item.productName());
            itemEntity.setMode(item.mode().name());
            itemEntity.setCourtesy(item.courtesy());
            itemEntity.setNotes(item.notes());
            itemEntity.setSurchargeAmount(item.surchargeAmount());
            entity.getItems().add(itemEntity);
        }
        return toDomain(orderJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findBySessionId(Long sessionId, int page, int size) {
        return withItems(orderJpaRepository
                .findBySessionIdOrderByIdDesc(sessionId, PageRequest.of(page, size)), page, size);
    }

    /**
     * Segunda fase do ID-first (PED-C002): recebe a página já resolvida — <b>sem</b> ter tocado a
     * coleção de itens — e carrega os itens de todos os pedidos dela numa consulta só.
     *
     * <p>Existe compartilhada porque {@code findAll} e {@code findBySessionId} fazem exatamente a
     * mesma coisa depois de obterem sua {@code Page}: o que difere entre as duas é só como a página
     * é filtrada. Antes desta correção, ambas mapeavam direto com {@code toDomain}, que toca
     * {@code e.getItems()} e disparava uma consulta por pedido.</p>
     *
     * <p>A ordenação vem do {@code ORDER BY o.id DESC} da própria consulta de fetch, que casa com a
     * ordem das duas chamadoras — ver o javadoc de {@code findAllByIdsWithItems}.</p>
     */
    private PageResult<Order> withItems(Page<OrderEntity> pageResult, int page, int size) {
        List<Long> ids = pageResult.getContent().stream().map(OrderEntity::getId).toList();
        // Página vazia não emite o `IN ()`: é desnecessário, e nem todo banco o aceita. Mesma
        // guarda de ComandaRepositoryImpl.findOpen.
        if (ids.isEmpty()) {
            return new PageResult<>(List.of(), page, size,
                    pageResult.getTotalElements(), pageResult.getTotalPages());
        }
        List<Order> content = orderJpaRepository.findAllByIdsWithItems(ids).stream()
                .map(this::toDomain).toList();
        return new PageResult<>(content, page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    /**
     * Listagem filtrada da visão do administrador. Cada filtro é opcional, resolvido com uma
     * {@link Specification} — o predicado só é adicionado quando o valor não é nulo, então um
     * filtro ausente nunca vira um bind ambíguo no Postgres. Era uma query {@code @Query} com o
     * padrão {@code :param IS NULL OR ...}, mas isso fazia o Postgres real recusar inferir o tipo
     * do bind de {@code from}/{@code to} (Instant) quando vinham nulos, e o CAST explícito que
     * corrigiria isso tem um bug conhecido de interação Hibernate/pgjdbc que troca o tipo do
     * parâmetro por {@code bytea}. Specification evita a classe inteira do problema — mesmo padrão
     * já usado em {@code AuditLogRepositoryImpl.findFiltered}.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findAll(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size) {
        Specification<OrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channel    != null) predicates.add(cb.equal(root.get("channel"), channel.name()));
            if (status     != null) predicates.add(cb.equal(root.get("status"), status.name()));
            if (customerId != null) predicates.add(cb.equal(root.get("customerId"), customerId));
            if (from       != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to         != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // A Specification resolve só QUAIS pedidos entram na página, sem tocar a coleção de itens;
        // quem os carrega é o withItems, numa consulta só (PED-C002).
        return withItems(orderJpaRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumConcludedNetAmountBySessionId(Long sessionId) {
        BigDecimal sum = orderJpaRepository.sumConcludedNetAmountBySessionId(sessionId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumChangeAmountBySessionId(Long sessionId) {
        BigDecimal sum = orderJpaRepository.sumChangeAmountBySessionId(sessionId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public String nextOrderNumber() {
        Long next = orderJpaRepository.nextOrderNumber();
        return String.format("%0" + ORDER_NUMBER_WIDTH + "d", next);
    }

    private Order toDomain(OrderEntity e) {
        return Order.of(e.getId(), e.getOrderNumber(), SalesChannel.valueOf(e.getChannel()),
                OrderStatus.valueOf(e.getStatus()), e.getCustomerId(), e.getSessionId(),
                e.getWarehouseCode(), e.getItems().stream().map(this::toDomain).toList(),
                e.getTotalAmount(), e.getDiscountAmount(), e.getCashbackRedeemed(), e.getNetAmount(),
                e.getChangeAmount(), e.getCancelReason(), e.getCreatedAt(), e.getPaidAt(),
                e.getConcludedAt(), e.getCancelledAt(), e.getRefundedAt(), e.getReservedAt(),
                e.getSeparatedAt(), e.getShippedAt(), e.getDeliveredAt(),
                e.getVersion() == null ? 0L : e.getVersion(), e.getComandaId(), e.getTableLabel(),
                // Pedido anterior a PDV-F015 lê como zero: o DEFAULT da V118 cobre as linhas já
                // gravadas, e este null-check cobre carga direta.
                e.getServiceFeeAmount() == null ? java.math.BigDecimal.ZERO : e.getServiceFeeAmount());
    }

    private OrderItem toDomain(OrderItemEntity e) {
        return OrderItem.of(e.getId(), e.getSku(), e.getQuantity(), e.getUnitPrice(), e.getCostPrice(),
                e.getDiscountAmount(), e.getCashbackPercent(), e.getProductName(),
                e.getMode() == null ? ConsumptionMode.NORMAL : ConsumptionMode.valueOf(e.getMode()),
                e.isCourtesy(), e.getNotes(), e.getSurchargeAmount());
    }
}
