package com.cernecommerce.adapter.out.redis;

import com.cernecommerce.core.ports.out.sse.SseTicketPort;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Bilhete de SSE no Redis (PLAT-C051), no mesmo molde de {@link RedisTokenBlocklistAdapter}:
 * adapter de {@code hml}/{@code prod}, com a contraparte em memória para {@code dev}.
 *
 * <p>Redis e não banco porque o bilhete vive segundos e o TTL é do próprio armazenamento — e
 * porque com mais de uma instância da aplicação o bilhete emitido numa precisa ser resgatável na
 * outra, que é justamente o que o {@code EventSource} faz ao reconectar.</p>
 */
@Component
@Profile({"hml", "prod"})
public class RedisSseTicketAdapter implements SseTicketPort {

    private static final String KEY_PREFIX = "sse-ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public RedisSseTicketAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String issue(String username, long ttlSeconds) {
        String ticket = newTicket();
        redis.opsForValue().set(KEY_PREFIX + ticket, username, Duration.ofSeconds(ttlSeconds));
        return ticket;
    }

    @Override
    public Optional<String> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        // GETDEL: ler e queimar são um passo só, então duas conexões que corram com o mesmo
        // bilhete não abrem dois streams.
        return Optional.ofNullable(redis.opsForValue().getAndDelete(KEY_PREFIX + ticket));
    }

    private static String newTicket() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
