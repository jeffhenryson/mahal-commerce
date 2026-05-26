package com.securityspring.adapter.out.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTokenBlocklistAdapterTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    RedisTokenBlocklistAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisTokenBlocklistAdapter(redis, 15L);
    }

    @Test
    void blockAllBefore_executesLuaScript() {
        adapter.blockAllBefore("alice", Instant.now());
        verify(redis).execute(any(RedisScript.class), anyList(), anyString(), anyString());
    }

    @Test
    void isBlockedAt_returnsFalseWhenNoEntry() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("blocklist:alice")).thenReturn(null);

        assertThat(adapter.isBlockedAt("alice", Instant.now())).isFalse();
    }

    @Test
    void isBlockedAt_returnsTrueWhenTokenIssuedBeforeBlockThreshold() {
        Instant blockTime = Instant.now();
        Instant tokenIssuedAt = blockTime.minusSeconds(5);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("blocklist:alice")).thenReturn(String.valueOf(blockTime.getEpochSecond()));

        assertThat(adapter.isBlockedAt("alice", tokenIssuedAt)).isTrue();
    }

    @Test
    void isBlockedAt_returnsFalseWhenTokenIssuedAfterBlockThreshold() {
        Instant blockTime = Instant.now().minusSeconds(10);
        Instant tokenIssuedAt = blockTime.plusSeconds(5);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("blocklist:alice")).thenReturn(String.valueOf(blockTime.getEpochSecond()));

        assertThat(adapter.isBlockedAt("alice", tokenIssuedAt)).isFalse();
    }

    @Test
    void blockAllBefore_isIdempotent() {
        Instant t = Instant.now();
        adapter.blockAllBefore("alice", t);
        adapter.blockAllBefore("alice", t);
        verify(redis, times(2)).execute(any(RedisScript.class), any(List.class), anyString(), anyString());
    }
}
