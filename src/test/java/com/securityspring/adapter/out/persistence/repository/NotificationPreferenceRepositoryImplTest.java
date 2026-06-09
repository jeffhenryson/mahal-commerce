package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.NotificationPreferenceEntity;
import com.securityspring.core.domain.model.notification.NotificationPreference;
import com.securityspring.core.domain.model.notification.NotificationType;
import com.securityspring.core.ports.out.notification.NotificationPreferenceRepository;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@EnableCaching
@Import(NotificationPreferenceRepositoryImpl.class)
class NotificationPreferenceRepositoryImplTest {

    @TestConfiguration
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(NotificationPreferenceRepositoryImpl.CACHE_NAME);
        }
    }

    @MockitoBean
    NotificationPreferenceJpaRepository jpaRepo;

    @Autowired
    NotificationPreferenceRepository repository;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        Cache cache = cacheManager.getCache(NotificationPreferenceRepositoryImpl.CACHE_NAME);
        if (cache != null) cache.clear();
    }

    @Test
    void findByUsername_segunda_chamada_retorna_do_cache() {
        when(jpaRepo.findByUsername("alice")).thenReturn(List.of(entity("alice", "PASSWORD_CHANGED", false, true)));

        repository.findByUsername("alice");
        repository.findByUsername("alice");

        verify(jpaRepo, times(1)).findByUsername("alice");
    }

    @Test
    void upsert_evicta_cache_e_proxima_chamada_vai_ao_db() {
        when(jpaRepo.findByUsername("alice")).thenReturn(List.of());

        repository.findByUsername("alice");
        repository.upsert(new NotificationPreference("alice", NotificationType.PASSWORD_CHANGED, false, false));
        repository.findByUsername("alice");

        verify(jpaRepo, times(2)).findByUsername("alice");
    }

    @Test
    void findByUsername_caches_por_usuario_independentemente() {
        when(jpaRepo.findByUsername("alice")).thenReturn(List.of());
        when(jpaRepo.findByUsername("bob")).thenReturn(List.of());

        repository.findByUsername("alice");
        repository.findByUsername("alice");
        repository.findByUsername("bob");
        repository.findByUsername("bob");

        verify(jpaRepo, times(1)).findByUsername("alice");
        verify(jpaRepo, times(1)).findByUsername("bob");
    }

    @Test
    void findByUsername_mapeia_entidade_para_dominio_corretamente() {
        when(jpaRepo.findByUsername("alice")).thenReturn(
                List.of(entity("alice", "PASSWORD_CHANGED", false, true)));

        Map<NotificationType, NotificationPreference> result = repository.findByUsername("alice");

        assertThat(result).containsKey(NotificationType.PASSWORD_CHANGED);
        NotificationPreference pref = result.get(NotificationType.PASSWORD_CHANGED);
        assertThat(pref.inAppEnabled()).isFalse();
        assertThat(pref.emailEnabled()).isTrue();
    }

    private NotificationPreferenceEntity entity(String username, String type, boolean inApp, boolean email) {
        NotificationPreferenceEntity e = new NotificationPreferenceEntity();
        e.setUsername(username);
        e.setType(type);
        e.setInAppEnabled(inApp);
        e.setEmailEnabled(email);
        return e;
    }
}
