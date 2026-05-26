package com.securityspring.core.ports.out;

public interface UserCachePort {
    void evict(String username);
}
