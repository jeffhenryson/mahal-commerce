package com.cernecommerce.core.domain.model.config;

/**
 * Dados da loja impressos no cabeçalho e no rodapé do cupom de venda (não fiscal).
 *
 * <p>Mora em {@code system_config} com chaves {@code store.*} — são poucos campos, sem histórico e
 * lidos sempre juntos, então uma tabela própria não pagaria o custo. Todos os campos são opcionais:
 * loja sem perfil preenchido imprime o cupom só com o que houver.</p>
 */
public record StoreProfile(
        String tradeName,
        String legalName,
        String cnpj,
        String stateRegistration,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode,
        String phone,
        String instagram,
        String website,
        String logoUrl,
        String receiptFooter) {

    /** Limite por campo — o cupom térmico tem 48 colunas; o rodapé pode quebrar em várias linhas. */
    public static final int MAX_FIELD = 300;

    public StoreProfile {
        tradeName = clean(tradeName, "tradeName");
        legalName = clean(legalName, "legalName");
        cnpj = clean(cnpj, "cnpj");
        stateRegistration = clean(stateRegistration, "stateRegistration");
        addressLine1 = clean(addressLine1, "addressLine1");
        addressLine2 = clean(addressLine2, "addressLine2");
        city = clean(city, "city");
        state = clean(state, "state");
        zipCode = clean(zipCode, "zipCode");
        phone = clean(phone, "phone");
        instagram = clean(instagram, "instagram");
        website = clean(website, "website");
        logoUrl = clean(logoUrl, "logoUrl");
        receiptFooter = clean(receiptFooter, "receiptFooter");
    }

    public static StoreProfile empty() {
        return new StoreProfile(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private static String clean(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > MAX_FIELD) {
            throw new IllegalArgumentException(field + " excede " + MAX_FIELD + " caracteres");
        }
        return v;
    }
}
