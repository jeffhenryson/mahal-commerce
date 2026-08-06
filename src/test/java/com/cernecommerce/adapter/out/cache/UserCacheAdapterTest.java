package com.cernecommerce.adapter.out.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cernecommerce.core.ports.out.user.UserCachePort;

@ExtendWith(MockitoExtension.class)
public class UserCacheAdapterTest {

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private UserCacheAdapter adapter;

    @Test
    void evict_clearsAllRelatedCaches() {
        Cache cache1 = mock(Cache.class);
        Cache cache2 = mock(Cache.class);
        Cache cache3 = mock(Cache.class);
        
        when(cacheManager.getCache(UserCacheAdapter.CACHE_NAME)).thenReturn(cache1);
        when(cacheManager.getCache(UserCachePort.USER_DOMAIN_CACHE)).thenReturn(cache2);
        when(cacheManager.getCache(UserCachePort.USER_AUTHORITIES_CACHE)).thenReturn(cache3);
        
        adapter.evict("admin");
        
        verify(cache1).evict("admin");
        verify(cache2).evict("admin");
        verify(cache3).evict("admin");
    }

    @Test
    void evict_toleratesMissingCaches() {
        when(cacheManager.getCache(UserCacheAdapter.CACHE_NAME)).thenReturn(null);
        when(cacheManager.getCache(UserCachePort.USER_DOMAIN_CACHE)).thenReturn(null);
        when(cacheManager.getCache(UserCachePort.USER_AUTHORITIES_CACHE)).thenReturn(null);
        
        adapter.evict("admin");
        
        // Should not throw NPE
    }
}
