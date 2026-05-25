package com.securityspring.core.ports.out;

public interface LoginRateLimiterPort {
    boolean tryConsume(String ip);

    /** Limpa o estado interno para testes. No-op em adapters sem estado local (ex: Redis). */
    default void reset() {}
}
