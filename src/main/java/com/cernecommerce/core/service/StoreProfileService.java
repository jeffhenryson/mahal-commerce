package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.config.StoreProfile;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.ports.in.StoreProfileUseCase;
import com.cernecommerce.core.ports.out.SystemConfigPort;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Lê e grava o {@link StoreProfile} em {@code system_config}, uma chave {@code store.*} por campo.
 * Campo vazio é gravado como string vazia ({@code config_value} é NOT NULL) e lido de volta como
 * {@code null}.
 */
public class StoreProfileService implements StoreProfileUseCase {

    private static final Map<String, Function<StoreProfile, String>> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("store.trade-name", StoreProfile::tradeName);
        FIELDS.put("store.legal-name", StoreProfile::legalName);
        FIELDS.put("store.cnpj", StoreProfile::cnpj);
        FIELDS.put("store.state-registration", StoreProfile::stateRegistration);
        FIELDS.put("store.address-line1", StoreProfile::addressLine1);
        FIELDS.put("store.address-line2", StoreProfile::addressLine2);
        FIELDS.put("store.city", StoreProfile::city);
        FIELDS.put("store.state", StoreProfile::state);
        FIELDS.put("store.zip-code", StoreProfile::zipCode);
        FIELDS.put("store.phone", StoreProfile::phone);
        FIELDS.put("store.instagram", StoreProfile::instagram);
        FIELDS.put("store.website", StoreProfile::website);
        FIELDS.put("store.logo-url", StoreProfile::logoUrl);
        FIELDS.put("store.receipt-footer", StoreProfile::receiptFooter);
    }

    private final SystemConfigPort configPort;

    public StoreProfileService(SystemConfigPort configPort) {
        this.configPort = configPort;
    }

    @Override
    public StoreProfile get() {
        return new StoreProfile(
                read("store.trade-name"), read("store.legal-name"), read("store.cnpj"),
                read("store.state-registration"), read("store.address-line1"), read("store.address-line2"),
                read("store.city"), read("store.state"), read("store.zip-code"), read("store.phone"),
                read("store.instagram"), read("store.website"), read("store.logo-url"),
                read("store.receipt-footer"));
    }

    @Override
    public StoreProfile update(StoreProfile profile, String updatedBy) {
        Instant now = Instant.now();
        FIELDS.forEach((key, getter) -> {
            String value = getter.apply(profile);
            configPort.save(new SystemConfig(key, value == null ? "" : value, now, updatedBy));
        });
        return get();
    }

    private String read(String key) {
        return configPort.findByKey(key).map(SystemConfig::value).orElse(null);
    }
}
