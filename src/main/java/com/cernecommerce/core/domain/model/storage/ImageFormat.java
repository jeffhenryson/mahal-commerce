package com.cernecommerce.core.domain.model.storage;

import java.util.Optional;

/**
 * Formatos de imagem aceitos em upload, detectados pelos <i>magic bytes</i> do próprio conteúdo.
 *
 * <p>A detecção é pelo conteúdo e não pela extensão do arquivo enviado nem pelo
 * {@code Content-Type} do multipart — os dois são texto controlado por quem faz o upload, então
 * confiar neles deixaria entrar qualquer coisa renomeada para {@code .jpg}. O que se grava no
 * disco é a extensão derivada daqui, nunca a que veio no request.</p>
 *
 * <p>Extraído de {@code AvatarService} quando o upload de imagem de produto passou a precisar da
 * mesma validação. Fica em {@code core.domain} e sem nenhuma dependência de Spring ou de servlet,
 * de propósito: é regra de negócio, não detalhe de transporte.</p>
 */
public enum ImageFormat {

    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG  = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    // WebP não tem assinatura contígua: bytes 0-3 = "RIFF", bytes 8-11 = "WEBP".
    private static final byte[] MAGIC_RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] MAGIC_WEBP = {0x57, 0x45, 0x42, 0x50};

    private final String extension;
    private final String contentType;

    ImageFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * Identifica o formato pelo conteúdo. Retorna vazio quando os bytes não correspondem a nenhum
     * formato aceito — cabe a cada chamador traduzir isso na sua própria exceção de domínio, e é
     * por isso que aqui não se lança nada.
     */
    public static Optional<ImageFormat> detect(byte[] bytes) {
        if (bytes == null) return Optional.empty();
        if (startsWith(bytes, 0, MAGIC_JPEG)) return Optional.of(JPEG);
        if (startsWith(bytes, 0, MAGIC_PNG))  return Optional.of(PNG);
        if (startsWith(bytes, 0, MAGIC_RIFF) && startsWith(bytes, 8, MAGIC_WEBP)) return Optional.of(WEBP);
        return Optional.empty();
    }

    /**
     * Content-type a partir de uma extensão já gravada, para servir o arquivo de volta.
     * Extensão desconhecida vira {@code application/octet-stream} — o navegador baixa em vez de
     * renderizar, que é o comportamento seguro para conteúdo não identificado.
     */
    public static String contentTypeOf(String extension) {
        if (extension == null) return "application/octet-stream";
        String normalized = extension.toLowerCase();
        for (ImageFormat format : values()) {
            if (format.extension.equals(normalized)) return format.contentType;
        }
        // "jpeg" não é a extensão que gravamos, mas chega em arquivos legados do storage.
        if ("jpeg".equals(normalized)) return JPEG.contentType;
        return "application/octet-stream";
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }
}
