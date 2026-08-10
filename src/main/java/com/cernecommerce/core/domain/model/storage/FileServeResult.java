package com.cernecommerce.core.domain.model.storage;

/**
 * Como um arquivo armazenado deve ser devolvido ao cliente.
 *
 * <p>A decisão mora no domínio e não no controller porque depende do backend de armazenamento:
 * com S3/CDN existe URL pública e o melhor é redirecionar (o byte nunca passa pela aplicação);
 * com armazenamento local não existe URL pública e a própria API serve os bytes.</p>
 *
 * <p>Generalizado a partir do antigo {@code AvatarServeResult} quando a imagem de produto passou
 * a precisar do mesmo contrato.</p>
 */
public sealed interface FileServeResult {

    /** Há URL pública (S3/CDN) — responder 308 para lá. */
    record Redirect(String url) implements FileServeResult {}

    /** Armazenamento local — a própria API devolve os bytes. */
    record LocalFile(byte[] bytes, String extension) implements FileServeResult {}

    /** Arquivo inexistente ou ilegível. */
    record NotFound() implements FileServeResult {}
}
