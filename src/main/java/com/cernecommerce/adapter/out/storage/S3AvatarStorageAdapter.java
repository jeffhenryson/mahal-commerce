package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Avatares em S3, sob o prefixo {@code avatars/}. Todo o comportamento vem de
 * {@link S3FileStorageAdapter}.
 */
public class S3AvatarStorageAdapter extends S3FileStorageAdapter implements AvatarStoragePort {

    private static final String PREFIX = "avatars/";

    public S3AvatarStorageAdapter(S3Client s3, String bucket, String publicUrlBase) {
        super(s3, bucket, PREFIX, publicUrlBase);
    }
}
