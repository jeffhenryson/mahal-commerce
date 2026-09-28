package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.config.StoreProfile;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreProfileServiceTest {

    /** Port em memória — o serviço só lê e grava chaves. */
    private final Map<String, SystemConfig> store = new HashMap<>();
    private StoreProfileService service;

    @BeforeEach
    void setup() {
        store.clear();
        service = new StoreProfileService(new SystemConfigPort() {
            public Optional<SystemConfig> findByKey(String key) { return Optional.ofNullable(store.get(key)); }
            public List<SystemConfig> findAll() { return List.copyOf(store.values()); }
            public SystemConfig save(SystemConfig c) { store.put(c.key(), c); return c; }
            public boolean getBoolean(String key, boolean d) { return d; }
            public int getInt(String key, int d) { return d; }
            public BigDecimal getDecimal(String key, BigDecimal d) { return d; }
        });
    }

    @Test
    void get_withoutProfile_returnsAllNull() {
        assertThat(service.get()).isEqualTo(StoreProfile.empty());
    }

    @Test
    void update_persistsAndReadsBack() {
        StoreProfile profile = new StoreProfile("Mahal", "Mahal LTDA", "12.345.678/0001-90", null,
                "Av. Principal, 1000", "Loja B", "Rio de Janeiro", "RJ", "20000-000", "(21) 99999-0000",
                "@mahal", null, "https://cdn/logo.png", "Obrigado pela preferência!");

        StoreProfile saved = service.update(profile, "admin");

        assertThat(saved).isEqualTo(profile);
        assertThat(store.get("store.cnpj").updatedBy()).isEqualTo("admin");
        // campo vazio vira string vazia no banco (config_value é NOT NULL) e volta como null
        assertThat(store.get("store.website").value()).isEmpty();
        assertThat(saved.website()).isNull();
    }

    @Test
    void update_blankClearsPreviousValue() {
        service.update(new StoreProfile("Mahal", null, null, null, null, null, null, null, null, null,
                null, null, null, null), "admin");

        StoreProfile cleared = service.update(new StoreProfile("  ", null, null, null, null, null, null,
                null, null, null, null, null, null, null), "admin");

        assertThat(cleared.tradeName()).isNull();
    }

    @Test
    void profile_rejectsOversizedField() {
        assertThatThrownBy(() -> new StoreProfile("x".repeat(StoreProfile.MAX_FIELD + 1), null, null, null,
                null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
