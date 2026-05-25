package com.security_spring.infra.security.redis;

import com.security_spring.core.ports.out.LoginRateLimiterPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Profile("!dev")
public class RedisLoginRateLimiterAdapter implements LoginRateLimiterPort {

    private static final String KEY_PREFIX = "rate:login:";

    // Lua script atômico: adiciona entrada, remove expirados, conta e decide em um único round-trip.
    // UUID no member evita colisão de timestamps no mesmo milissegundo (undercount sem isso).
    private static final RedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local key      = KEYS[1]
            local score    = tonumber(ARGV[1])
            local cutoff   = tonumber(ARGV[2])
            local member   = ARGV[3]
            local ttl      = tonumber(ARGV[4])
            local maxReqs  = tonumber(ARGV[5])
            redis.call('ZADD', key, score, member)
            redis.call('ZREMRANGEBYSCORE', key, 0, cutoff)
            local count = redis.call('ZCARD', key)
            redis.call('EXPIRE', key, ttl)
            if count <= maxReqs then return 1 else return 0 end
            """, Long.class);

    private final StringRedisTemplate redis;
    private final long windowSeconds;
    private final int maxRequests;

    public RedisLoginRateLimiterAdapter(
            StringRedisTemplate redis,
            @Value("${rate.limit.login.window-seconds:60}") long windowSeconds,
            @Value("${rate.limit.login.max-requests:10}") int maxRequests) {
        this.redis = redis;
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
    }

    @Override
    public boolean tryConsume(String ip) {
        long nowMillis  = Instant.now().toEpochMilli();
        long cutoffMillis = nowMillis - (windowSeconds * 1000);
        String member = nowMillis + ":" + UUID.randomUUID();

        Long allowed = redis.execute(SLIDING_WINDOW_SCRIPT,
                List.of(KEY_PREFIX + ip),
                String.valueOf(nowMillis),
                String.valueOf(cutoffMillis),
                member,
                String.valueOf(windowSeconds),
                String.valueOf(maxRequests));

        return allowed != null && allowed == 1L;
    }
}
