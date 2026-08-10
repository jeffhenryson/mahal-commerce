package com.cernecommerce.core.ports.out.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Armazenamento de arquivos binários, indiferente ao que o arquivo significa.
 *
 * <p>Nasceu como {@code AvatarStoragePort} e foi generalizado quando a imagem de produto passou a
 * precisar exatamente do mesmo contrato (gravar com nome gerado, ler, apagar, e opcionalmente
 * expor URL pública). As sub-interfaces marcadoras — {@link AvatarStoragePort} e
 * {@link ProductImageStoragePort} — não acrescentam método nenhum: existem para o wiring manual
 * de {@code CoreBeanConfig} conseguir distinguir os dois beans, já que o projeto não usa
 * {@code @Qualifier}.</p>
 */
public interface FileStoragePort {

    /** Salva os bytes e retorna o filename gerado (UUID + extensão). */
    String save(byte[] bytes, String extension);

    /** Carrega o arquivo. Retorna empty se não existir. */
    Optional<InputStream> load(String filename);

    /** Remove o arquivo. No-op se não existir. */
    void delete(String filename);

    /**
     * URL pública direta para o arquivo (ex.: S3, CDN).
     * Retorna empty para armazenamento local — neste caso o controller serve os bytes via API.
     */
    default Optional<String> getPublicUrl(String filename) {
        return Optional.empty();
    }
}
