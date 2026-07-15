package com.cernecommerce.adapter.out.security.blocklist;

import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("dev")
public class InMemoryTokenBlocklistAdapter implements TokenBlocklistPort {

    // threshold_instant → entries older than (threshold + access TTL) can never block any live token
    private final ConcurrentHashMap<String, Instant> blockedBefore = new ConcurrentHashMap<>();
    private final long accessTtlMinutes;

    public InMemoryTokenBlocklistAdapter(
            @Value("${jwt.access-ttl-minutes:15}") long accessTtlMinutes) {
        this.accessTtlMinutes = accessTtlMinutes;
    }

    @Override
    public void blockAllBefore(String username, Instant instant) {
        blockedBefore.merge(username, instant,
                (existing, incoming) -> incoming.isAfter(existing) ? incoming : existing);
        evictExpired();
    }

    @Override
    public boolean isBlockedAt(String username, Instant tokenIssuedAt) {
        Instant threshold = blockedBefore.get(username);
        if (threshold == null) return false;
        // JWT iat has second precision; a token issued at or before the threshold second is blocked.
        // !isAfter covers the equal case (same second) safely.
        return !tokenIssuedAt.isAfter(threshold);
    }

    public void clearAll() {
        blockedBefore.clear();
    }

    // A blocklist entry for user U set at time T can only affect tokens issued at or before T.
    // The latest such token expires at T + accessTtl. After that, no live token can match this
    // entry, so it is safe to remove.
    private void evictExpired() {
        Instant evictBefore = Instant.now().minus(accessTtlMinutes, ChronoUnit.MINUTES);
        blockedBefore.entrySet().removeIf(e -> e.getValue().isBefore(evictBefore));
    }
}
