package com.cernecommerce.adapter.out.redis;

import com.cernecommerce.core.ports.out.token.TokenBlocklistPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile({"hml", "prod"})
public class RedisTokenBlocklistAdapter implements TokenBlocklistPort {

    private static final String KEY_PREFIX = "blocklist:";

    // Lua script atômico: só sobrescreve se o novo threshold for posterior ao existente,
    // evitando race condition entre GET + SET separados em múltiplas instâncias.
    private static final RedisScript<Long> SET_IF_LATER_SCRIPT = new DefaultRedisScript<>("""
            local existing = redis.call('GET', KEYS[1])
            if existing == false or tonumber(ARGV[1]) > tonumber(existing) then
                redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2]))
                return 1
            end
            return 0
            """, Long.class);

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
        redis.execute(SET_IF_LATER_SCRIPT,
                List.of(KEY_PREFIX + username),
                String.valueOf(instant.getEpochSecond()),
                String.valueOf(accessTtlSeconds));
    }

    @Override
    public boolean isBlockedAt(String username, Instant tokenIssuedAt) {
        String value = redis.opsForValue().get(KEY_PREFIX + username);
        if (value == null) return false;
        return !tokenIssuedAt.isAfter(Instant.ofEpochSecond(Long.parseLong(value)));
    }
}
