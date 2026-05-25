package com.securityspring.core.ports.out;

public interface LoginRateLimiterPort {
    boolean tryConsume(String ip);
}
