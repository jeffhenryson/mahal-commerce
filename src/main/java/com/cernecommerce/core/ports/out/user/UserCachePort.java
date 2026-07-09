package com.cernecommerce.core.ports.out.user;

public interface UserCachePort {
    String USER_DOMAIN_CACHE = "users";
    String USER_AUTHORITIES_CACHE = "userAuthorities";

    void evict(String username);
}
