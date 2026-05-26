package com.securityspring.core.ports.out.user;

public interface UserCachePort {
    void evict(String username);
}
