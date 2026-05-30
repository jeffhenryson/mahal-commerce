package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.StatsResult;

public interface StatsUseCase {
    StatsResult getStats();
}
