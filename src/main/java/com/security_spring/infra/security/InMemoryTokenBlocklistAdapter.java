package com.security_spring.infra.security;

import com.security_spring.core.ports.out.TokenBlocklistPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryTokenBlocklistAdapter implements TokenBlocklistPort {

    private final ConcurrentHashMap<String, Instant> blockedBefore = new ConcurrentHashMap<>();

    @Override
    public void blockAllBefore(String username, Instant instant) {
        blockedBefore.merge(username, instant,
                (existing, incoming) -> incoming.isAfter(existing) ? incoming : existing);
    }

    @Override
    public boolean isBlockedAt(String username, Instant tokenIssuedAt) {
        Instant threshold = blockedBefore.get(username);
        return threshold != null && !tokenIssuedAt.isAfter(threshold);
    }
}
