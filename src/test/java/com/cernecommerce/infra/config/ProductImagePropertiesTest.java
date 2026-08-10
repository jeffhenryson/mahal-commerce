package com.cernecommerce.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prova que as chaves {@code product.image.storage.*} realmente chegam ao bean.
 *
 * <p>Não é teste de ornamento: o binder do Spring Boot trata o ponto como separador de nível, e
 * uma chave {@code product.image.storage.dir} <b>não</b> casaria com um campo {@code storageDir}
 * de nível único — bindaria em silêncio no default, e só apareceria em produção como imagem
 * gravada no diretório errado. É por isso que {@code storage} é uma classe aninhada. Este teste
 * trava essa decisão contra uma "simplificação" futura.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "product.image.storage.type=local",
        "product.image.storage.dir=./target/test-uploads/product-images",
        "product.image.base-url=https://cdn.exemplo.test",
        "product.image.max-size-bytes=1234567"
})
class ProductImagePropertiesTest {

    @Autowired
    private ProductImageProperties properties;

    @Test
    void chaves_aninhadas_de_storage_sao_bindadas() {
        assertEquals("local", properties.getStorage().getType());
        assertEquals("./target/test-uploads/product-images", properties.getStorage().getDir());
    }

    @Test
    void chaves_de_nivel_unico_sao_bindadas() {
        assertEquals("https://cdn.exemplo.test", properties.getBaseUrl());
        assertEquals(1234567L, properties.getMaxSizeBytes());
    }
}
