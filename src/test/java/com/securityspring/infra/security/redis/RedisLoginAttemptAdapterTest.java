package com.securityspring.infra.security.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisLoginAttemptAdapterTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    RedisLoginAttemptAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisLoginAttemptAdapter(redis, 5, 15L);
    }

    @Test
    void recordFailure_incrementsCounter() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:alice")).thenReturn(1L);

        adapter.recordFailure("alice");

        verify(valueOps).increment("login:attempts:alice");
        verify(redis).expire(eq("login:attempts:alice"), any(Duration.class));
    }

    @Test
    void recordFailure_setsLockKeyWhenMaxAttemptsReached() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:alice")).thenReturn(5L);

        adapter.recordFailure("alice");

        verify(valueOps).set(eq("login:lock:alice"), eq("1"), any(Duration.class));
    }

    @Test
    void recordFailure_doesNotLockBeforeMaxAttempts() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:alice")).thenReturn(4L);

        adapter.recordFailure("alice");

        verify(valueOps, never()).set(eq("login:lock:alice"), anyString(), any(Duration.class));
    }

    @Test
    void recordSuccess_clearsAttemptsAndLock() {
        adapter.recordSuccess("alice");

        verify(redis).delete("login:attempts:alice");
        verify(redis).delete("login:lock:alice");
    }

    @Test
    void isLocked_returnsTrueWhenLockKeyExists() {
        when(redis.hasKey("login:lock:alice")).thenReturn(Boolean.TRUE);

        assertThat(adapter.isLocked("alice")).isTrue();
    }

    @Test
    void isLocked_returnsFalseWhenLockKeyAbsent() {
        when(redis.hasKey("login:lock:alice")).thenReturn(Boolean.FALSE);

        assertThat(adapter.isLocked("alice")).isFalse();
    }
}
