package com.security_spring.infra.security.redis;

import com.security_spring.core.ports.out.LoginRateLimiterPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!dev")
public class RedisLoginRateLimiterAdapter implements LoginRateLimiterPort {

    private static final String KEY_PREFIX = "rate:login:";

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
        String key = KEY_PREFIX + ip;
        long nowMillis = Instant.now().toEpochMilli();
        long cutoffMillis = nowMillis - (windowSeconds * 1000);
        String member = String.valueOf(nowMillis);

        ZSetOperations<String, String> zset = redis.opsForZSet();

        // Sliding window: add current timestamp, remove expired entries, count remaining
        zset.add(key, member, nowMillis);
        zset.removeRangeByScore(key, 0, cutoffMillis);
        Long count = zset.zCard(key);
        redis.expire(key, windowSeconds, TimeUnit.SECONDS);

        return count == null || count <= maxRequests;
    }
}
