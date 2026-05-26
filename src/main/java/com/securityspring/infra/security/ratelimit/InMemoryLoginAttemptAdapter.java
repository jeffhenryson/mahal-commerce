package com.securityspring.infra.security.ratelimit;

import com.securityspring.core.ports.out.LoginAttemptPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Diferença comportamental vs RedisLoginAttemptAdapter: após expiração do lock, o estado
// é removido explicitamente em isLocked() e a contagem de falhas recomeça do zero.
// No Redis, o contador tem TTL automático de lockDurationMinutes*2 e pode ter contagem
// residual após o lock expirar. Em dev isso é irrelevante; não promova este adaptador a prod.
@Component
@Profile("dev")
public class InMemoryLoginAttemptAdapter implements LoginAttemptPort {

    private record LockState(int failures, Instant lockedUntil) {}

    private final int maxAttempts;
    private final long lockDurationMinutes;
    private final Map<String, LockState> state = new ConcurrentHashMap<>();

    public InMemoryLoginAttemptAdapter(
            @Value("${auth.lockout.max-attempts:5}") int maxAttempts,
            @Value("${auth.lockout.duration-minutes:15}") long lockDurationMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
    }

    @Override
    public void recordFailure(String username) {
        state.merge(username,
                new LockState(1, null),
                (existing, ignored) -> {
                    int failures = existing.failures() + 1;
                    Instant lockedUntil = failures >= maxAttempts
                            ? Instant.now().plusSeconds(lockDurationMinutes * 60)
                            : existing.lockedUntil();
                    return new LockState(failures, lockedUntil);
                });
    }

    @Override
    public void recordSuccess(String username) {
        state.remove(username);
    }

    @Override
    public boolean isLocked(String username) {
        LockState s = state.get(username);
        if (s == null || s.lockedUntil() == null) return false;
        if (Instant.now().isAfter(s.lockedUntil())) {
            state.remove(username);
            return false;
        }
        return true;
    }
}
