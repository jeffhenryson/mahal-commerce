package com.securityspring.adapter.out.cache;

import com.securityspring.core.ports.out.user.UserCachePort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Adaptador de cache de UserDetails.
 *
 * <p>Usa o {@link CacheManager} do Spring diretamente — sem depender de
 * {@code CustomUserDetailsService} — para que a troca de provedor de cache
 * (Caffeine ↔ Redis ↔ outro) não exija mudanças neste adaptador.</p>
 *
 * <p>O nome do cache ({@value #CACHE_NAME}) deve coincidir com o
 * {@code cacheNames} declarado em {@code CustomUserDetailsService}.</p>
 */
@Component
public class UserCacheAdapter implements UserCachePort {

    public static final String CACHE_NAME = "userDetails";

    private final CacheManager cacheManager;

    public UserCacheAdapter(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void evict(String username) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(username);
        }
    }
}
