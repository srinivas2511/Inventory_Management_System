package com.mfg.ims.document.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores content under a root directory taken from configuration ({@code ims.documents.storage-root}, env
 * {@code DOCUMENTS_STORAGE_ROOT}). Object names are server-generated ({@code yyyy/MM/uuid}); a key is validated
 * against that exact shape and re-checked to resolve inside the root, so no client-supplied text can reach the
 * file system.
 */
@Component
public class LocalFileDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileDocumentStorage.class);
    private static final Pattern KEY = Pattern.compile("[0-9]{4}/[0-9]{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path root;

    public LocalFileDocumentStorage(@Value("${ims.documents.storage-root:./data/documents}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(InputStream content, long maxBytes) throws IOException {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String key = "%04d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID());
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");

        MessageDigest digest = sha256();
        long size = 0;
        try (OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = content.read(buffer)) != -1) {
                size += read;
                if (size > maxBytes) {
                    throw new ContentTooLargeException(maxBytes);
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        return new StoredObject(key, size, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete stored object {}: {}", storageKey, e.toString());
        }
    }

    Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
