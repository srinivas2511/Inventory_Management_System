package com.mfg.ims.doccore.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentStorageService {

    private final String storagePath;

    public AttachmentStorageService(@Value("${ims.attachments.storage-path:./data/attachments}") String storagePath) {
        this.storagePath = storagePath;
    }

    public String store(Long companyId, String entityType, Long entityId, MultipartFile file) throws IOException {
        String ext = "";
        String orig = file.getOriginalFilename();
        if (orig != null && orig.contains(".")) {
            ext = orig.substring(orig.lastIndexOf('.'));
        }
        String key = companyId + "/" + entityType.toLowerCase() + "/" + entityId + "/" + UUID.randomUUID() + ext;
        Path target = Paths.get(storagePath, key);
        Files.createDirectories(target.getParent());
        Files.copy(file.getInputStream(), target);
        return key;
    }

    public InputStream open(String storageKey) throws IOException {
        Path path = Paths.get(storagePath, storageKey);
        return Files.newInputStream(path);
    }

    public void delete(String storageKey) throws IOException {
        Path path = Paths.get(storagePath, storageKey);
        Files.deleteIfExists(path);
    }
}
