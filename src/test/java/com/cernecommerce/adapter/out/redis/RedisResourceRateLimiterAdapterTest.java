package com.cernecommerce.adapter.out.redis;

import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Lenient strictness é necessária porque redis.execute() recebe argumentos dinâmicos de timestamp
// (epoch millis + UUID) que não podem ser casados com matchers estáticos do Mockito.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisResourceRateLimiterAdapterTest {

    @Mock StringRedisTemplate redis;

    RedisResourceRateLimiterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisResourceRateLimiterAdapter(redis);
    }

    @Test
    void checkOrThrow_doesNotThrowWhenScriptReturnsOne() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        adapter.checkOrThrow("crm-export", "alice");
    }

    @Test
    void checkOrThrow_throwsWhenScriptReturnsZero() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        assertThatThrownBy(() -> adapter.checkOrThrow("crm-export", "alice"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkOrThrow_throwsWhenScriptReturnsNull() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(null);

        assertThatThrownBy(() -> adapter.checkOrThrow("crm-export", "alice"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkOrThrow_executesScriptWithCorrectKeyPrefix() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        adapter.checkOrThrow("crm-export", "alice");

        verify(redis).execute(any(RedisScript.class),
                argThat((List<String> keys) -> keys.size() == 1 && keys.get(0).equals("rate:resource:crm-export:alice")),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkOrThrow_unknownBucket_usesDefaultPolicy() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        adapter.checkOrThrow("bucket-que-nao-existe", "alice");

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(any(RedisScript.class), anyList(), argsCaptor.capture());

        Object[] args = argsCaptor.getValue();
        // ARGV[4] = ttl (windowSeconds), ARGV[5] = maxReqs — índices 3 e 4 no array Java (0-based)
        assertThat(args[3]).isEqualTo("60");
        assertThat(args[4]).isEqualTo("30");
    }

    @Test
    void checkOrThrow_throws_withRetryAfterSecondsEqualToBucketWindow() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        assertThatThrownBy(() -> adapter.checkOrThrow("crm-export", "alice"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> assertThat(((RateLimitExceededException) ex).retryAfterSeconds()).isEqualTo(3600L));
    }
}
