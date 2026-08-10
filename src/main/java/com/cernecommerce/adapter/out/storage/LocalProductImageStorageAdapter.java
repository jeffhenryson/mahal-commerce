package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.ports.out.storage.ProductImageStoragePort;

import java.nio.file.Path;

/**
 * Imagens de produto em disco local. Todo o comportamento vem de
 * {@link LocalFileStorageAdapter}.
 */
public class LocalProductImageStorageAdapter extends LocalFileStorageAdapter
        implements ProductImageStoragePort {

    public LocalProductImageStorageAdapter(Path storageDir) {
        super(storageDir, "product-image");
    }
}
