package com.securityspring.infra.metrics;

import com.securityspring.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ActiveSessionsMetric {

    public ActiveSessionsMetric(MeterRegistry registry, RefreshTokenJpaRepository repo) {
        Gauge.builder("auth.active_sessions", repo, r -> r.countAllActive(Instant.now()))
                .description("Active (non-expired, non-revoked) refresh token sessions")
                .register(registry);
    }
}
