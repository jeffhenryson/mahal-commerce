package com.securityspring.core.ports.out.ratelimit;

public interface LoginRateLimiterPort {
    boolean tryConsume(String ip);
}
