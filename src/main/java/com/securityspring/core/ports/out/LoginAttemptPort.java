package com.securityspring.core.ports.out;

public interface LoginAttemptPort {
    void recordFailure(String username);

    void recordSuccess(String username);

    boolean isLocked(String username);
}
