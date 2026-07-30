package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashbackBalanceResponseDTO {
    private BigDecimal available;
    private BigDecimal pending;
    private BigDecimal expiringSoon;
}
