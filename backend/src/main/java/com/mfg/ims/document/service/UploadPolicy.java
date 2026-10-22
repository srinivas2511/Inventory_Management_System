package com.mfg.ims.document.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.settings.SystemSettingService;

/**
 * Decides whether an upload is acceptable: extension allowlist (a setting), a content signature check for the formats
 * that have one, a server-chosen MIME type (the client's declared type is never trusted or stored) and a sanitised
 * display filename. Size is enforced while streaming, against {@link #maxBytes()}.
 */
@Component
public class UploadPolicy {

    static final String DEFAULT_EXTENSIONS = "pdf,png,jpg,jpeg,gif,txt,csv,docx,xlsx,pptx,dwg,dxf,step,stp";

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private static final Map<String, byte[]> SIGNATURE = Map.of(
            "pdf", new byte[] { '%', 'P', 'D', 'F', '-' },
            "png", new byte[] { (byte) 0x89, 'P', 'N', 'G' },
            "jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF },
            "jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF },
            "gif", new byte[] { 'G', 'I', 'F', '8' },
            "docx", new byte[] { 'P', 'K', 3, 4 },
            "xlsx", new byte[] { 'P', 'K', 3, 4 },
            "pptx", new byte[] { 'P', 'K', 3, 4 });

    private static final int MAX_NAME = 150;

    private final SystemSettingService settings;

    public UploadPolicy(SystemSettingService settings) {
        this.settings = settings;
    }

    public long maxBytes() {
        return Math.max(1, settings.getInt("documents.max_size_mb", 25)) * 1024L * 1024L;
    }

    /** Validates the file and returns the MIME type to store. Throws FILE_TYPE_NOT_ALLOWED otherwise. */
    public String validate(String originalFilename, byte[] head) {
        String ext = extension(originalFilename);
        if (ext.isEmpty() || !allowedExtensions().contains(ext)) {
            throw new BusinessRuleException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "Files of type ." + (ext.isEmpty() ? "(none)" : ext) + " are not accepted");
        }
        byte[] signature = SIGNATURE.get(ext);
        if (signature != null && !startsWith(head, signature)) {
            throw new BusinessRuleException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "The file content does not match its ." + ext + " extension");
        }
        return MIME.getOrDefault(ext, "application/octet-stream");
    }

    public Set<String> allowedExtensions() {
        return Arrays.stream(settings.get("documents.allowed_extensions", DEFAULT_EXTENSIONS).split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Lower-case extension after the last dot of the final path segment, or "" when there is none. */
    public static String extension(String filename) {
        String name = lastSegment(filename);
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * A filename that is safe to store and to put in a Content-Disposition header: no path, no control characters, only
     * letters, digits and a few punctuation marks, bounded in length, with the extension kept.
     */
    public static String sanitizeFilename(String original) {
        String name = lastSegment(original).replaceAll("\\p{Cntrl}", "").trim();
        name = name.replaceAll("[^A-Za-z0-9._ ()\\-]", "_").replaceAll("\\.{2,}", ".").replaceAll("^\\.+", "");
        if (name.isBlank()) {
            return "file";
        }
        if (name.length() > MAX_NAME) {
            String ext = extension(name);
            String stem = ext.isEmpty() ? name : name.substring(0, name.length() - ext.length() - 1);
            int keep = MAX_NAME - (ext.isEmpty() ? 0 : ext.length() + 1);
            name = stem.substring(0, Math.max(1, Math.min(stem.length(), keep))) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name;
    }

    private static String lastSegment(String filename) {
        if (filename == null) {
            return "";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash < 0 ? filename : filename.substring(slash + 1);
    }

    private static boolean startsWith(byte[] head, byte[] prefix) {
        if (head == null || head.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (head[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
