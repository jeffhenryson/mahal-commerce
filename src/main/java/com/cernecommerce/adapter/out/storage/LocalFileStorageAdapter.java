package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.ports.out.storage.FileStoragePort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Armazenamento em disco local. Adequado a instância única ou a volume compartilhado
 * (NFS/EFS) — com múltiplas instâncias sem volume compartilhado, o arquivo enviado a uma
 * instância não é encontrado pelas outras, e aí o caminho correto é S3.
 *
 * <p>Generalizado a partir do antigo {@code LocalAvatarStorageAdapter}. O {@code label} entra
 * só nas mensagens de log, para "qual upload falhou" continuar legível depois que dois tipos
 * de arquivo passaram a usar a mesma classe.</p>
 */
public class LocalFileStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    private final Path storageDir;
    private final String label;

    public LocalFileStorageAdapter(Path storageDir, String label) {
        this.storageDir = storageDir.toAbsolutePath().normalize();
        this.label = label;
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storageDir);
        log.info("{}.storage.dir={}", label, storageDir);
    }

    @Override
    public String save(byte[] bytes, String extension) {
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.write(storageDir.resolve(filename), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao salvar " + label + ": " + filename, e);
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
            log.warn("{}.load.failed filename={}", label, filename, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String filename) {
        Path file = storageDir.resolve(filename).normalize();
        if (!file.startsWith(storageDir)) return; // path traversal guard
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("{}.delete.failed filename={}", label, filename, e);
        }
    }
}
