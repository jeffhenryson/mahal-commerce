package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.ports.out.storage.ProductImageStoragePort;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Imagens de produto em S3, sob o prefixo {@code product-images/} — separado de
 * {@code avatars/} para permitir política de ciclo de vida distinta no mesmo bucket.
 */
public class S3ProductImageStorageAdapter extends S3FileStorageAdapter
        implements ProductImageStoragePort {

    private static final String PREFIX = "product-images/";

    public S3ProductImageStorageAdapter(S3Client s3, String bucket, String publicUrlBase) {
        super(s3, bucket, PREFIX, publicUrlBase);
    }
}
