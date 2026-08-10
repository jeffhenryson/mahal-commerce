package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.domain.model.storage.ImageFormat;
import com.cernecommerce.core.ports.out.storage.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Armazenamento em S3, com URL pública direta — o byte nunca passa pela aplicação na leitura.
 *
 * <p>Generalizado a partir do antigo {@code S3AvatarStorageAdapter}. O {@code keyPrefix} separa
 * os tipos de arquivo dentro do mesmo bucket ({@code avatars/}, {@code product-images/}), o que
 * permite uma política de ciclo de vida distinta por prefixo sem precisar de dois buckets.</p>
 */
public class S3FileStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageAdapter.class);

    private final S3Client s3;
    private final String bucket;
    private final String keyPrefix;
    private final String publicUrlBase;

    public S3FileStorageAdapter(S3Client s3, String bucket, String keyPrefix, String publicUrlBase) {
        this.s3 = s3;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
        this.publicUrlBase = publicUrlBase;
    }

    @Override
    public String save(byte[] bytes, String extension) {
        String filename = UUID.randomUUID() + "." + extension;
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(keyPrefix + filename)
                    .contentType(ImageFormat.contentTypeOf(extension))
                    .cacheControl("public, max-age=31536000, immutable")
                    .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw new IllegalStateException("Falha ao salvar no S3: " + keyPrefix + filename, e);
        }
        return filename;
    }

    @Override
    public Optional<InputStream> load(String filename) {
        try {
            return Optional.of(s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(keyPrefix + filename).build()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            log.warn("s3.load.failed key={}", keyPrefix + filename, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String filename) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket).key(keyPrefix + filename).build());
        } catch (S3Exception e) {
            log.warn("s3.delete.failed key={}", keyPrefix + filename, e);
        }
    }

    @Override
    public Optional<String> getPublicUrl(String filename) {
        return Optional.of(publicUrlBase + "/" + keyPrefix + filename);
    }
}
