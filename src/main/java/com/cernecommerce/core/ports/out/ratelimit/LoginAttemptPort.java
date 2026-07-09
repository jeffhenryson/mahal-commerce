package com.cernecommerce.core.ports.out.ratelimit;

public interface LoginAttemptPort {
    void recordFailure(String username);

    void recordSuccess(String username);

    boolean isLocked(String username);
}
