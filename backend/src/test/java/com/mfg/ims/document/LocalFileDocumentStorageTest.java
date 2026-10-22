package com.mfg.ims.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mfg.ims.document.storage.DocumentStorage.ContentTooLargeException;
import com.mfg.ims.document.storage.DocumentStorage.StoredObject;
import com.mfg.ims.document.storage.LocalFileDocumentStorage;

class LocalFileDocumentStorageTest {

    @TempDir Path root;
    private LocalFileDocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileDocumentStorage(root.toString());
    }

    @Test
    void storesContentWithItsSizeAndSha256AndReadsItBack() throws Exception {
        byte[] content = "spring wire drawing rev B".getBytes(StandardCharsets.UTF_8);
        StoredObject stored = storage.store(new ByteArrayInputStream(content), 1024);

        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.sha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)));
        assertThat(stored.storageKey()).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}");
        try (InputStream in = storage.open(stored.storageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void theStorageKeyIsServerGeneratedSoTwoUploadsNeverCollide() throws Exception {
        StoredObject a = storage.store(new ByteArrayInputStream(new byte[] { 1 }), 10);
        StoredObject b = storage.store(new ByteArrayInputStream(new byte[] { 1 }), 10);
        assertThat(a.storageKey()).isNotEqualTo(b.storageKey());
    }

    @Test
    void contentOverTheLimitIsRejectedAndNothingIsLeftBehind() throws Exception {
        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(new byte[100]), 99))
                .isInstanceOf(ContentTooLargeException.class);
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void contentExactlyAtTheLimitIsAccepted() throws Exception {
        StoredObject stored = storage.store(new ByteArrayInputStream(new byte[99]), 99);
        assertThat(stored.sizeBytes()).isEqualTo(99);
    }

    @Test
    void aFailingStreamLeavesNoPartialFile() {
        InputStream broken = new InputStream() {
            private int n;

            @Override
            public int read() throws IOException {
                if (n++ > 10) {
                    throw new IOException("disk gone");
                }
                return 7;
            }
        };
        assertThatThrownBy(() -> storage.store(broken, 1024)).isInstanceOf(IOException.class);
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void keysThatAreNotServerGeneratedAreRefusedSoNoPathCanEscapeTheRoot() throws Exception {
        Files.writeString(root.getParent().resolve("outside.txt"), "secret");
        for (String key : new String[] { "../outside.txt", "2026/10/../../outside.txt", "/etc/passwd",
                "..\\outside.txt", "2026/10/not-a-uuid", "", "2026/10/00000000-0000-0000-0000-000000000000/../x",
                "2026/10/00000000-0000-0000-0000-000000000000.part" }) {
            assertThatThrownBy(() -> storage.open(key)).as("open " + key).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> storage.open(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesTheObjectAndIgnoresMissingOrInvalidKeys() throws Exception {
        StoredObject stored = storage.store(new ByteArrayInputStream(new byte[] { 1, 2 }), 10);
        storage.delete(stored.storageKey());
        assertThatThrownBy(() -> storage.open(stored.storageKey())).isInstanceOf(java.nio.file.NoSuchFileException.class);

        storage.delete(stored.storageKey());
        storage.delete("../../nope");
        storage.delete(null);
    }
}
