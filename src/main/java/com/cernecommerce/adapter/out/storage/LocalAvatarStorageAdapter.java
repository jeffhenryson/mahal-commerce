package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;

import java.nio.file.Path;

/**
 * Avatares em disco local. Todo o comportamento vem de {@link LocalFileStorageAdapter} — esta
 * classe só amarra o adapter genérico à porta específica, para o wiring manual distinguir os
 * beans.
 */
public class LocalAvatarStorageAdapter extends LocalFileStorageAdapter implements AvatarStoragePort {

    public LocalAvatarStorageAdapter(Path storageDir) {
        super(storageDir, "avatar");
    }
}
