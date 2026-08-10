package com.cernecommerce.core.domain.model.storage;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ImageFormatTest {

    private static byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11, 0x22};
    }

    private static byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x11};
    }

    private static byte[] webp() {
        // "RIFF" + 4 bytes de tamanho (irrelevantes) + "WEBP"
        return new byte[] {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
    }

    @Test
    void detecta_jpeg_pelos_magic_bytes() {
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.detect(jpeg()));
        assertEquals("jpg", ImageFormat.JPEG.extension());
    }

    @Test
    void detecta_png_pelos_magic_bytes() {
        assertEquals(Optional.of(ImageFormat.PNG), ImageFormat.detect(png()));
        assertEquals("png", ImageFormat.PNG.extension());
    }

    @Test
    void detecta_webp_pela_assinatura_nao_contigua_riff_webp() {
        assertEquals(Optional.of(ImageFormat.WEBP), ImageFormat.detect(webp()));
        assertEquals("webp", ImageFormat.WEBP.extension());
    }

    @Test
    void riff_sem_webp_no_offset_8_nao_e_aceito() {
        // Um AVI também começa com "RIFF" — é exatamente o caso que a checagem do offset 8 pega.
        byte[] avi = {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x41, 0x56, 0x49, 0x20};
        assertTrue(ImageFormat.detect(avi).isEmpty());
    }

    @Test
    void conteudo_nao_reconhecido_volta_vazio_em_vez_de_lancar() {
        // Quem traduz "não reconheci" em erro é cada chamador, com a exceção do seu domínio.
        assertTrue(ImageFormat.detect("não sou uma imagem".getBytes()).isEmpty());
    }

    @Test
    void bytes_nulos_ou_curtos_demais_voltam_vazio_sem_estourar_indice() {
        assertTrue(ImageFormat.detect(null).isEmpty());
        assertTrue(ImageFormat.detect(new byte[0]).isEmpty());
        assertTrue(ImageFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8}).isEmpty());
        // Prefixo RIFF válido mas curto demais para ter o "WEBP" do offset 8.
        assertTrue(ImageFormat.detect(new byte[] {0x52, 0x49, 0x46, 0x46, 0x00}).isEmpty());
    }

    @Test
    void content_type_resolve_a_extensao_gravada() {
        assertEquals("image/jpeg", ImageFormat.contentTypeOf("jpg"));
        assertEquals("image/png", ImageFormat.contentTypeOf("png"));
        assertEquals("image/webp", ImageFormat.contentTypeOf("webp"));
    }

    @Test
    void content_type_aceita_jpeg_legado_e_ignora_caixa() {
        // "jpeg" não é a extensão que gravamos, mas chega em arquivos antigos do storage.
        assertEquals("image/jpeg", ImageFormat.contentTypeOf("jpeg"));
        assertEquals("image/png", ImageFormat.contentTypeOf("PNG"));
    }

    @Test
    void extensao_desconhecida_ou_nula_vira_octet_stream() {
        // O navegador baixa em vez de renderizar — comportamento seguro para conteúdo não identificado.
        assertEquals("application/octet-stream", ImageFormat.contentTypeOf("exe"));
        assertEquals("application/octet-stream", ImageFormat.contentTypeOf(""));
        assertEquals("application/octet-stream", ImageFormat.contentTypeOf(null));
    }
}
