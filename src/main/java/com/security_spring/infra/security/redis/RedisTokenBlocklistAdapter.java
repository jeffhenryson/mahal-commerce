package com.security_spring.infra.security.redis;

import com.security_spring.core.ports.out.TokenBlocklistPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@Profile("!dev")
public class RedisTokenBlocklistAdapter implements TokenBlocklistPort {

    private static final String KEY_PREFIX = "blocklist:";

    private final StringRedisTemplate redis;
    private final long accessTtlSeconds;

    public RedisTokenBlocklistAdapter(
            StringRedisTemplate redis,
            @Value("${jwt.access-ttl-minutes:15}") long accessTtlMinutes) {
        this.redis = redis;
        this.accessTtlSeconds = accessTtlMinutes * 60;
    }

    @Override
    public void blockAllBefore(String username, Instant instant) {
        String key = KEY_PREFIX + username;
        String newValue = String.valueOf(instant.getEpochSecond());

        // Only update if new threshold is later than the stored one
        String existing = redis.opsForValue().get(key);
        if (existing == null || instant.getEpochSecond() > Long.parseLong(existing)) {
            redis.opsForValue().set(key, newValue, accessTtlSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isBlockedAt(String username, Instant tokenIssuedAt) {
        String value = redis.opsForValue().get(KEY_PREFIX + username);
        if (value == null) return false;
        long threshold = Long.parseLong(value);
        return !tokenIssuedAt.isAfter(Instant.ofEpochSecond(threshold));
    }
}
