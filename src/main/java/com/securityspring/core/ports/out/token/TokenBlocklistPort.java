package com.securityspring.core.ports.out.token;

import java.time.Instant;

public interface TokenBlocklistPort {
    void blockAllBefore(String username, Instant instant);
    boolean isBlockedAt(String username, Instant tokenIssuedAt);
}
