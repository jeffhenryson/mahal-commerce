package com.cernecommerce.adapter.out.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Lenient strictness is required because redis.execute() receives dynamic timestamp arguments
// (epoch millis + UUID) that cannot be matched with static Mockito matchers.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisLoginRateLimiterAdapterTest {

    @Mock StringRedisTemplate redis;

    RedisLoginRateLimiterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisLoginRateLimiterAdapter(redis, 60L, 10);
    }

    @Test
    void tryConsume_returnsTrueWhenScriptReturnsOne() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        assertThat(adapter.tryConsume("192.168.0.1")).isTrue();
    }

    @Test
    void tryConsume_returnsFalseWhenScriptReturnsZero() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        assertThat(adapter.tryConsume("192.168.0.1")).isFalse();
    }

    @Test
    void tryConsume_returnsFalseWhenScriptReturnsNull() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(null);

        assertThat(adapter.tryConsume("192.168.0.1")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_executesScriptWithCorrectKeyPrefix() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        adapter.tryConsume("10.0.0.1");

        verify(redis).execute(any(RedisScript.class),
                argThat((List<String> keys) -> keys.size() == 1 && keys.get(0).equals("rate:login:10.0.0.1")),
                any(Object[].class));
    }
}
