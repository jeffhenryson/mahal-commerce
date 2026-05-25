package com.security_spring.core.ports.out;

import java.time.Instant;

public interface TokenBlocklistPort {
    void blockAllBefore(String username, Instant instant);
    boolean isBlockedAt(String username, Instant tokenIssuedAt);
}
