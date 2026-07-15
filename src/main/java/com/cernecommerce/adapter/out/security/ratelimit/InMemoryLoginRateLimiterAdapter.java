package com.cernecommerce.adapter.out.security.ratelimit;

import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("dev")
public class InMemoryLoginRateLimiterAdapter implements LoginRateLimiterPort {

    private final long windowSeconds;
    private final int maxRequests;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public InMemoryLoginRateLimiterAdapter(
            @Value("${rate.limit.login.window-seconds:60}") long windowSeconds,
            @Value("${rate.limit.login.max-requests:10}") int maxRequests) {
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
    }

    @Override
    public boolean tryConsume(String ip) {
        long now = Instant.now().getEpochSecond();
        long cutoff = now - windowSeconds;

        Deque<Long> queue = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < cutoff) {
                queue.removeFirst();
            }
            if (queue.size() >= maxRequests) {
                return false;
            }
            queue.addLast(now);
        }
        return true;
    }

    public void reset() {
        hits.clear();
    }
}
