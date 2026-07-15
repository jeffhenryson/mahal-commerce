package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

public class S3AvatarStorageAdapter implements AvatarStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3AvatarStorageAdapter.class);
    private static final String PREFIX = "avatars/";

    private final S3Client s3;
    private final String bucket;
    private final String publicUrlBase;

    public S3AvatarStorageAdapter(S3Client s3, String bucket, String publicUrlBase) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicUrlBase = publicUrlBase;
    }

    @Override
    public String save(byte[] bytes, String extension) {
        String filename = UUID.randomUUID() + "." + extension;
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(PREFIX + filename)
                    .contentType(contentType(extension))
                    .cacheControl("public, max-age=31536000, immutable")
                    .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw new IllegalStateException("Falha ao salvar avatar no S3: " + filename, e);
        }
        return filename;
    }

    @Override
    public Optional<InputStream> load(String filename) {
        try {
            return Optional.of(s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(PREFIX + filename).build()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            log.warn("avatar.load.failed filename={}", filename, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String filename) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket).key(PREFIX + filename).build());
        } catch (S3Exception e) {
            log.warn("avatar.delete.failed filename={}", filename, e);
        }
    }

    @Override
    public Optional<String> getPublicUrl(String filename) {
        return Optional.of(publicUrlBase + "/" + PREFIX + filename);
    }

    private static String contentType(String extension) {
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
