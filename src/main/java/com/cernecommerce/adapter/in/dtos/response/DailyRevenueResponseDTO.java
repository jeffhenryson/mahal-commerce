package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DailyRevenueResponseDTO {

    private LocalDate date;
    private BigDecimal revenue;
    private long orderCount;
}
