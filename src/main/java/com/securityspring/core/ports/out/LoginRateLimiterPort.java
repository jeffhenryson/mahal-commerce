package com.security_spring.core.ports.out;

public interface LoginRateLimiterPort {
    boolean tryConsume(String ip);
}
