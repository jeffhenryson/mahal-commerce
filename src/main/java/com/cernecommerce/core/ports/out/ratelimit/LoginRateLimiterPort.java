package com.cernecommerce.core.ports.out.ratelimit;

public interface LoginRateLimiterPort {
    boolean tryConsume(String ip);
}
