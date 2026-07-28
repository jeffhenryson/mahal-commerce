package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockReservationEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.ports.out.estoque.StockReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class StockReservationRepositoryImpl implements StockReservationRepository {

    private final StockReservationJpaRepository stockReservationJpaRepository;

    public StockReservationRepositoryImpl(StockReservationJpaRepository stockReservationJpaRepository) {
        this.stockReservationJpaRepository = stockReservationJpaRepository;
    }

    @Override
    public StockReservation save(StockReservation reservation) {
        StockReservationEntity entity = new StockReservationEntity();
        entity.setId(reservation.id());
        entity.setSku(reservation.sku());
        entity.setWarehouseId(reservation.warehouseId());
        entity.setQuantity(reservation.quantity());
        entity.setOwnerReference(reservation.ownerReference());
        entity.setStatus(reservation.status().name());
        entity.setExpiresAt(reservation.expiresAt());
        entity.setCreatedAt(reservation.createdAt());
        entity.setResolvedAt(reservation.resolvedAt());
        entity.setUsername(reservation.username());
        return toDomain(stockReservationJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockReservation> findById(Long id) {
        return stockReservationJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockReservation> findActiveByOwnerReference(String ownerReference) {
        return stockReservationJpaRepository
                .findByOwnerReferenceAndStatus(ownerReference, ReservationStatus.ACTIVE.name())
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockReservation> findByFilters(String sku, Long warehouseId, ReservationStatus status,
            int page, int size) {
        Page<StockReservationEntity> result = stockReservationJpaRepository.findByFilters(
                sku, warehouseId, status == null ? null : status.name(), PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockReservation> findExpired(Instant now, int limit) {
        return stockReservationJpaRepository.findExpired(now, PageRequest.of(0, limit))
                .stream().map(this::toDomain).toList();
    }

    private StockReservation toDomain(StockReservationEntity e) {
        return StockReservation.of(e.getId(), e.getSku(), e.getWarehouseId(), e.getQuantity(),
                e.getOwnerReference(), ReservationStatus.valueOf(e.getStatus()), e.getExpiresAt(),
                e.getCreatedAt(), e.getResolvedAt(), e.getUsername());
    }
}
