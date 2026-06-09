package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.SystemConfigEntity;
import com.securityspring.core.domain.model.config.SystemConfig;
import com.securityspring.core.ports.out.SystemConfigPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@EnableCaching
@Import(SystemConfigAdapter.class)
class SystemConfigAdapterTest {

    @TestConfiguration
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(SystemConfigAdapter.CACHE_NAME);
        }
    }

    @MockitoBean
    SystemConfigJpaRepository jpa;

    @Autowired
    SystemConfigPort adapter;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        Cache cache = cacheManager.getCache(SystemConfigAdapter.CACHE_NAME);
        if (cache != null) cache.clear();
    }

    @Test
    void findByKey_segunda_chamada_retorna_do_cache() {
        when(jpa.findById("feature.x")).thenReturn(Optional.of(entity("feature.x", "true")));

        adapter.findByKey("feature.x");
        adapter.findByKey("feature.x");

        verify(jpa, times(1)).findById("feature.x");
    }

    @Test
    void save_evicta_todo_cache_e_proxima_chamada_vai_ao_db() {
        when(jpa.findById("feature.x")).thenReturn(Optional.of(entity("feature.x", "true")));
        when(jpa.save(any())).thenAnswer(i -> i.getArgument(0));

        adapter.findByKey("feature.x");
        adapter.save(new SystemConfig("feature.x", "false", Instant.now(), "admin"));
        adapter.findByKey("feature.x");

        verify(jpa, times(2)).findById("feature.x");
    }

    @Test
    void getBoolean_segunda_chamada_retorna_do_cache() {
        when(jpa.findById("maintenance.enabled")).thenReturn(Optional.of(entity("maintenance.enabled", "false")));

        adapter.getBoolean("maintenance.enabled", false);
        adapter.getBoolean("maintenance.enabled", false);

        verify(jpa, times(1)).findById("maintenance.enabled");
    }

    @Test
    void getBoolean_retorna_defaultValue_quando_chave_ausente() {
        when(jpa.findById("missing.key")).thenReturn(Optional.empty());

        boolean result = adapter.getBoolean("missing.key", true);

        assertThat(result).isTrue();
    }

    @Test
    void save_evicta_cache_de_getBoolean_tambem() {
        when(jpa.findById("feature.x")).thenReturn(Optional.of(entity("feature.x", "true")));
        when(jpa.save(any())).thenAnswer(i -> i.getArgument(0));

        adapter.getBoolean("feature.x", false);
        adapter.save(new SystemConfig("feature.x", "false", Instant.now(), "admin"));
        adapter.getBoolean("feature.x", false);

        verify(jpa, times(2)).findById("feature.x");
    }

    @Test
    void findByKey_caches_sao_independentes_por_chave() {
        when(jpa.findById("key.a")).thenReturn(Optional.of(entity("key.a", "1")));
        when(jpa.findById("key.b")).thenReturn(Optional.of(entity("key.b", "2")));

        adapter.findByKey("key.a");
        adapter.findByKey("key.a");
        adapter.findByKey("key.b");
        adapter.findByKey("key.b");

        verify(jpa, times(1)).findById("key.a");
        verify(jpa, times(1)).findById("key.b");
    }

    private SystemConfigEntity entity(String key, String value) {
        SystemConfigEntity e = new SystemConfigEntity();
        e.setKey(key);
        e.setValue(value);
        return e;
    }
}
