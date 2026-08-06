package com.cernecommerce.adapter.out.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalAvatarStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalAvatarStorageAdapter adapter;

    @BeforeEach
    void setup() throws IOException {
        adapter = new LocalAvatarStorageAdapter(tempDir);
        adapter.init();
    }

    @Test
    void init_createsDirectory() {
        assertThat(Files.exists(tempDir)).isTrue();
    }

    @Test
    void saveAndLoad_success() throws Exception {
        byte[] content = "avatar_data".getBytes();
        String filename = adapter.save(content, "png");
        
        assertThat(filename).endsWith(".png");
        
        Optional<InputStream> loaded = adapter.load(filename);
        assertThat(loaded).isPresent();
        
        byte[] readContent = loaded.get().readAllBytes();
        assertThat(readContent).isEqualTo(content);
    }

    @Test
    void load_nonExistent_returnsEmpty() {
        Optional<InputStream> loaded = adapter.load("missing.png");
        assertThat(loaded).isEmpty();
    }

    @Test
    void load_pathTraversal_returnsEmpty() {
        Optional<InputStream> loaded = adapter.load("../outside.png");
        assertThat(loaded).isEmpty();
    }

    @Test
    void delete_removesFile() throws Exception {
        String filename = adapter.save("data".getBytes(), "png");
        
        adapter.delete(filename);
        
        assertThat(adapter.load(filename)).isEmpty();
    }
    
    @Test
    void delete_nonExistent_doesNotThrow() {
        adapter.delete("missing.png");
    }
}
