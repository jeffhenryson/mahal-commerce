package com.cernecommerce.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressão do bug de binding descoberto ao generalizar o storage para a imagem de produto:
 * {@code avatar.storage.dir} não chegava a {@link AvatarProperties} porque o campo era
 * {@code storageDir} de nível único, e o binder trata o ponto como separador de nível. Na
 * prática, {@code AVATAR_STORAGE_DIR} era ignorado e os avatares iam sempre para
 * {@code ./uploads/avatars} — invisível porque o valor de hml/prod é igual ao default.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "avatar.storage.dir=./target/test-uploads/avatars",
        "avatar.max-size-bytes=999999"
})
class AvatarPropertiesTest {

    @Autowired
    private AvatarProperties properties;

    @Test
    void storage_dir_configurado_chega_ao_bean() {
        assertEquals("./target/test-uploads/avatars", properties.getStorageDir());
    }

    @Test
    void storage_type_mantem_o_default_quando_nao_configurado() {
        assertEquals("local", properties.getStorageType());
    }

    @Test
    void chaves_de_nivel_unico_seguem_bindando() {
        assertEquals(999999L, properties.getMaxSizeBytes());
    }
}
