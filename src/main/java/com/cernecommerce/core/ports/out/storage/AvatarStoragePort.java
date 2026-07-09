package com.cernecommerce.core.ports.out.storage;

import java.io.InputStream;

public interface AvatarStoragePort {
    /** Salva os bytes e retorna o filename gerado (UUID + extensão). */
    String save(byte[] bytes, String extension);

    /** Carrega o arquivo. Retorna empty se não existir. */
    java.util.Optional<InputStream> load(String filename);

    /** Remove o arquivo. No-op se não existir. */
    void delete(String filename);

    /**
     * URL pública direta para o arquivo (ex.: S3, CDN).
     * Retorna empty para armazenamento local — neste caso o controller serve os bytes via API.
     */
    default java.util.Optional<String> getPublicUrl(String filename) {
        return java.util.Optional.empty();
    }
}
