package com.securityspring.adapter.out.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3AvatarStorageAdapterTest {

    private S3Client s3;
    private S3AvatarStorageAdapter adapter;

    private static final String BUCKET = "my-bucket";
    private static final String PUBLIC_URL = "https://cdn.example.com";

    @BeforeEach
    void setup() {
        s3 = mock(S3Client.class);
        adapter = new S3AvatarStorageAdapter(s3, BUCKET, PUBLIC_URL);
    }

    // ── save ────────────────────────────────────────────────────────────────

    @Test
    void save_envia_putObject_com_bucket_key_e_contentType_corretos() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String filename = adapter.save(new byte[]{1, 2, 3}, "png");

        assertThat(filename).endsWith(".png");
        verify(s3).putObject(argThat((PutObjectRequest req) ->
                req.bucket().equals(BUCKET)
                && req.key().equals("avatars/" + filename)
                && req.contentType().equals("image/png")
                && req.cacheControl().equals("public, max-age=31536000, immutable")
        ), any(RequestBody.class));
    }

    @Test
    void save_retorna_nome_com_extensao_jpg() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String filename = adapter.save(new byte[]{1}, "jpg");

        assertThat(filename).endsWith(".jpg");
        verify(s3).putObject(argThat((PutObjectRequest req) ->
                req.contentType().equals("image/jpeg")
        ), any(RequestBody.class));
    }

    @Test
    void save_lanca_IllegalStateException_quando_s3_falha() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").build());

        assertThatThrownBy(() -> adapter.save(new byte[]{1}, "webp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falha ao salvar avatar no S3");
    }

    // ── load ────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void load_retorna_inputStream_quando_objeto_existe() {
        ResponseInputStream<GetObjectResponse> stream =
                mock(ResponseInputStream.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(stream);

        Optional<InputStream> result = adapter.load("avatar.png");

        assertThat(result).isPresent();
        verify(s3).getObject(argThat((GetObjectRequest req) ->
                req.bucket().equals(BUCKET) && req.key().equals("avatars/avatar.png")));
    }

    @Test
    void load_retorna_empty_quando_chave_nao_existe() {
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        Optional<InputStream> result = adapter.load("nao-existe.png");

        assertThat(result).isEmpty();
    }

    @Test
    void load_retorna_empty_quando_s3_falha() {
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("InternalError").build());

        Optional<InputStream> result = adapter.load("avatar.png");

        assertThat(result).isEmpty();
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_envia_deleteObject_com_key_correta() {
        adapter.delete("avatar.png");

        verify(s3).deleteObject(argThat((DeleteObjectRequest req) ->
                req.bucket().equals(BUCKET) && req.key().equals("avatars/avatar.png")));
    }

    @Test
    void delete_suprime_excecao_s3() {
        doThrow(S3Exception.builder().message("Forbidden").build())
                .when(s3).deleteObject(any(DeleteObjectRequest.class));

        // should not throw
        adapter.delete("avatar.png");
    }

    // ── getPublicUrl ────────────────────────────────────────────────────────

    @Test
    void getPublicUrl_retorna_url_com_prefixo_correto() {
        Optional<String> url = adapter.getPublicUrl("abc.png");

        assertThat(url).hasValue("https://cdn.example.com/avatars/abc.png");
    }

    // ── contentType mapping ─────────────────────────────────────────────────

    @Test
    void save_usa_contentType_webp_para_extensao_webp() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        adapter.save(new byte[]{1}, "webp");

        verify(s3).putObject(argThat((PutObjectRequest req) ->
                req.contentType().equals("image/webp")
        ), any(RequestBody.class));
    }

    @Test
    void save_usa_octet_stream_para_extensao_desconhecida() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        adapter.save(new byte[]{1}, "bmp");

        verify(s3).putObject(argThat((PutObjectRequest req) ->
                req.contentType().equals("application/octet-stream")
        ), any(RequestBody.class));
    }
}
