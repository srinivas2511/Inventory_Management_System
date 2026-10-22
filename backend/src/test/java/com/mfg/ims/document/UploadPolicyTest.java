package com.mfg.ims.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.document.service.UploadPolicy;

class UploadPolicyTest {

    private static final byte[] PDF = { '%', 'P', 'D', 'F', '-', '1', '.', '7' };
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A };
    private static final byte[] ZIP = { 'P', 'K', 3, 4, 0, 0 };
    private static final byte[] EXE = { 'M', 'Z', (byte) 0x90, 0 };

    private SystemSettingService settings;
    private UploadPolicy policy;

    @BeforeEach
    void setUp() {
        settings = mock(SystemSettingService.class);
        when(settings.get(eq("documents.allowed_extensions"), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(settings.getInt(eq("documents.max_size_mb"), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> inv.getArgument(1));
        policy = new UploadPolicy(settings);
    }

    @Test
    void acceptsAllowedTypesAndChoosesTheMimeTypeItself() {
        assertThat(policy.validate("coiling.pdf", PDF)).isEqualTo("application/pdf");
        assertThat(policy.validate("photo.PNG", PNG)).isEqualTo("image/png");
        assertThat(policy.validate("plan.docx", ZIP))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void formatsWithoutASignatureAreAcceptedByExtensionAlone() {
        assertThat(policy.validate("part.dwg", new byte[] { 1, 2, 3 })).isEqualTo("application/octet-stream");
        assertThat(policy.validate("notes.txt", "hello".getBytes())).isEqualTo("text/plain");
    }

    @Test
    void rejectsExtensionsOutsideTheAllowlist() {
        for (String name : new String[] { "run.exe", "page.html", "image.svg", "script.js", "archive.zip", "noext",
                "trailingdot.", ".hidden" }) {
            assertThatThrownBy(() -> policy.validate(name, PDF))
                    .as(name)
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED));
        }
    }

    @Test
    void rejectsContentThatContradictsItsExtension() {
        assertThatThrownBy(() -> policy.validate("invoice.pdf", EXE)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> policy.validate("picture.png", PDF)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> policy.validate("short.pdf", new byte[] { '%' }))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void doubleExtensionsAreJudgedByTheLastOne() {
        assertThatThrownBy(() -> policy.validate("invoice.pdf.exe", PDF)).isInstanceOf(BusinessRuleException.class);
        assertThat(policy.validate("backup.exe.pdf", PDF)).isEqualTo("application/pdf");
    }

    @Test
    void theAllowlistIsASettingAndRespectsItsValue() {
        when(settings.get(eq("documents.allowed_extensions"), anyString())).thenReturn(" PDF , txt ");
        assertThat(policy.allowedExtensions()).containsExactlyInAnyOrder("pdf", "txt");
        assertThatThrownBy(() -> policy.validate("photo.png", PNG)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void sizeLimitComesFromTheSettingInMegabytes() {
        assertThat(policy.maxBytes()).isEqualTo(25L * 1024 * 1024);
        when(settings.getInt(eq("documents.max_size_mb"), org.mockito.ArgumentMatchers.anyInt())).thenReturn(2);
        assertThat(policy.maxBytes()).isEqualTo(2L * 1024 * 1024);
        when(settings.getInt(eq("documents.max_size_mb"), org.mockito.ArgumentMatchers.anyInt())).thenReturn(0);
        assertThat(policy.maxBytes()).isEqualTo(1024L * 1024);
    }

    @Test
    void sanitizedFilenamesCannotCarryPathsOrControlCharacters() {
        assertThat(UploadPolicy.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(UploadPolicy.sanitizeFilename("C:\\Users\\x\\drawing.pdf")).isEqualTo("drawing.pdf");
        assertThat(UploadPolicy.sanitizeFilename("a\r\nb\0c.pdf")).isEqualTo("abc.pdf");
        assertThat(UploadPolicy.sanitizeFilename("we<ird>\"na|me?.pdf")).isEqualTo("we_ird__na_me_.pdf");
        assertThat(UploadPolicy.sanitizeFilename("...hidden..pdf")).isEqualTo("hidden.pdf");
        assertThat(UploadPolicy.sanitizeFilename("")).isEqualTo("file");
        assertThat(UploadPolicy.sanitizeFilename(null)).isEqualTo("file");
        assertThat(UploadPolicy.sanitizeFilename("..")).isEqualTo("file");
    }

    @Test
    void longFilenamesAreShortenedButKeepTheirExtension() {
        String name = UploadPolicy.sanitizeFilename("x".repeat(400) + ".pdf");
        assertThat(name.length()).isLessThanOrEqualTo(150);
        assertThat(name).endsWith(".pdf");
    }

    @Test
    void extensionIsTakenFromTheLastPathSegment() {
        assertThat(UploadPolicy.extension("dir.v2/readme")).isEmpty();
        assertThat(UploadPolicy.extension("dir/Report.PDF")).isEqualTo("pdf");
    }
}
