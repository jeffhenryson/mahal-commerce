package com.securityspring.core.ports.in;

import com.securityspring.adapter.in.dtos.response.StatsResponseDTO;

public interface StatsUseCase {
    StatsResponseDTO getStats();
}
