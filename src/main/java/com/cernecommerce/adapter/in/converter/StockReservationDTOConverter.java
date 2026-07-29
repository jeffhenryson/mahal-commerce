package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.ReservationIntegrityMismatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockReservationResponseDTO;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockReservation;

import java.time.Duration;

public class StockReservationDTOConverter {

    /** {@code null} atravessa: filtro ausente significa "não filtrar por status". */
    public ReservationStatus toStatusOrNull(String status) {
        return status == null ? null : ReservationStatus.valueOf(status);
    }

    /** {@code null} atravessa: o service aplica o TTL padrão configurado. */
    public Duration toTtlOrNull(Integer ttlMinutes) {
        return ttlMinutes == null ? null : Duration.ofMinutes(ttlMinutes);
    }

    /**
     * @param warehouseCode resolvido pelo controller — o domínio guarda {@code warehouseId}, e a
     *        API fala em código de depósito em toda parte
     */
    public StockReservationResponseDTO toResponse(StockReservation reservation, String warehouseCode) {
        StockReservationResponseDTO dto = new StockReservationResponseDTO();
        dto.setId(reservation.id());
        dto.setSku(reservation.sku());
        dto.setWarehouseCode(warehouseCode);
        dto.setQuantity(reservation.quantity());
        dto.setOwnerReference(reservation.ownerReference());
        dto.setStatus(reservation.status().name());
        dto.setExpiresAt(reservation.expiresAt());
        dto.setCreatedAt(reservation.createdAt());
        dto.setResolvedAt(reservation.resolvedAt());
        dto.setUsername(reservation.username());
        return dto;
    }

    public ReservationIntegrityMismatchResponseDTO toResponse(ReservationIntegrityMismatch mismatch) {
        ReservationIntegrityMismatchResponseDTO dto = new ReservationIntegrityMismatchResponseDTO();
        dto.setSku(mismatch.sku());
        dto.setWarehouseCode(mismatch.warehouseCode());
        dto.setReservedQuantity(mismatch.reservedQuantity());
        dto.setActiveReservationsTotal(mismatch.activeReservationsTotal());
        dto.setDifference(mismatch.difference());
        return dto;
    }
}
