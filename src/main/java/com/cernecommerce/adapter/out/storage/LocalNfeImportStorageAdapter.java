package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.ports.out.storage.NfeImportStoragePort;

import java.nio.file.Path;

/** XML de NF-e em disco local. Todo o comportamento vem de {@link LocalFileStorageAdapter}. */
public class LocalNfeImportStorageAdapter extends LocalFileStorageAdapter implements NfeImportStoragePort {

    public LocalNfeImportStorageAdapter(Path storageDir) {
        super(storageDir, "nfe-import");
    }
}
