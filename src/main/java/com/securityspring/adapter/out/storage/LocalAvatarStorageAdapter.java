package com.securityspring.adapter.out.storage;

import com.securityspring.core.ports.out.storage.AvatarStoragePort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public class LocalAvatarStorageAdapter implements AvatarStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalAvatarStorageAdapter.class);

    private final Path storageDir;

    public LocalAvatarStorageAdapter(Path storageDir) {
        this.storageDir = storageDir;
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storageDir);
        log.info("avatar.storage.dir={}", storageDir.toAbsolutePath());
    }

    @Override
    public String save(byte[] bytes, String extension) {
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.write(storageDir.resolve(filename), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao salvar avatar: " + filename, e);
        }
        return filename;
    }

    @Override
    public Optional<InputStream> load(String filename) {
        Path file = storageDir.resolve(filename).normalize();
        if (!file.startsWith(storageDir)) return Optional.empty(); // path traversal guard
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(Files.newInputStream(file));
        } catch (IOException e) {
            log.warn("avatar.load.failed filename={}", filename, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String filename) {
        try {
            Files.deleteIfExists(storageDir.resolve(filename).normalize());
        } catch (IOException e) {
            log.warn("avatar.delete.failed filename={}", filename, e);
        }
    }
}
