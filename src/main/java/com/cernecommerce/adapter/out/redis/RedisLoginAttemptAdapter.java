package com.cernecommerce.adapter.out.redis;

import com.cernecommerce.core.ports.out.ratelimit.LoginAttemptPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile({"hml", "prod"})
public class RedisLoginAttemptAdapter implements LoginAttemptPort {

    private static final String ATTEMPTS_KEY = "login:attempts:";
    private static final String LOCK_KEY = "login:lock:";

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final long lockDurationMinutes;

    public RedisLoginAttemptAdapter(
            StringRedisTemplate redis,
            @Value("${auth.lockout.max-attempts:5}") int maxAttempts,
            @Value("${auth.lockout.duration-minutes:15}") long lockDurationMinutes) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
    }

    @Override
    public void recordFailure(String username) {
        String attemptsKey = ATTEMPTS_KEY + username;
        Long count = redis.opsForValue().increment(attemptsKey);
        // Reset TTL em cada falha para manter a janela deslizante de contagem
        redis.expire(attemptsKey, Duration.ofMinutes(lockDurationMinutes * 2));

        if (count != null && count >= maxAttempts) {
            redis.opsForValue().set(LOCK_KEY + username, "1", Duration.ofMinutes(lockDurationMinutes));
        }
    }

    @Override
    public void recordSuccess(String username) {
        redis.delete(ATTEMPTS_KEY + username);
        redis.delete(LOCK_KEY + username);
    }

    @Override
    public boolean isLocked(String username) {
        return Boolean.TRUE.equals(redis.hasKey(LOCK_KEY + username));
    }
}
