package com.cernecommerce.adapter.out.cache;

import com.cernecommerce.core.ports.out.user.UserCachePort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class UserCacheAdapter implements UserCachePort {

    public static final String CACHE_NAME = "userDetails";

    private final CacheManager cacheManager;

    public UserCacheAdapter(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void evict(String username) {
        evictFrom(CACHE_NAME, username);
        evictFrom(USER_DOMAIN_CACHE, username);
        evictFrom(USER_AUTHORITIES_CACHE, username);
    }

    private void evictFrom(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) cache.evict(key);
    }
}
