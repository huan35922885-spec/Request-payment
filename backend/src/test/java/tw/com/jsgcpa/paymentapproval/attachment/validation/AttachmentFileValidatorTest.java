package tw.com.jsgcpa.paymentapproval.attachment.validation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import tw.com.jsgcpa.paymentapproval.attachment.config.AttachmentStorageProperties;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentValidationException;

class AttachmentFileValidatorTest {

    private AttachmentFileValidator validator;

    @BeforeEach
    void setUp() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setStorageRoot(Path.of("data/attachments"));
        properties.setMaxFileSize(DataSize.ofMegabytes(10));
        properties.setAllowedContentTypes(Set.of(
                "application/pdf",
                "image/jpeg",
                "image/png"
        ));
        properties.setAllowedExtensions(Set.of(
                "pdf",
                "jpg",
                "jpeg",
                "png"
        ));
        validator = new AttachmentFileValidator(properties);
    }

    @Test
    void acceptsPdfWhenExtensionContentTypeAndSignatureMatch() {
        byte[] content = new byte[]{
                0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37
        };

        ValidatedAttachmentFile result = validator.validate(
                new MockMultipartFile(
                        "file",
                        "invoice.pdf",
                        "application/pdf",
                        content
                )
        );

        assertEquals("invoice.pdf", result.safeOriginalFilename());
        assertEquals("application/pdf", result.detectedContentType());
        assertEquals("pdf", result.canonicalExtension());
        assertEquals(content.length, result.fileSize());
        assertArrayEquals(content, result.content());
    }

    @Test
    void acceptsJpegWithJpegExtensionAndCanonicalizesToJpg() {
        ValidatedAttachmentFile result = validator.validate(
                new MockMultipartFile(
                        "file",
                        "photo.jpeg",
                        "image/jpeg",
                        new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
                )
        );

        assertEquals("jpg", result.canonicalExtension());
        assertEquals("image/jpeg", result.detectedContentType());
    }

    @Test
    void rejectsFilenamePathTraversal() {
        AttachmentValidationException exception = assertThrows(
                AttachmentValidationException.class,
                () -> validator.validate(new MockMultipartFile(
                        "file",
                        "../invoice.pdf",
                        "application/pdf",
                        new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}
                ))
        );

        assertEquals("ATTACHMENT_FILENAME_INVALID", exception.getCode());
    }

    @Test
    void rejectsDoubleExtension() {
        AttachmentValidationException exception = assertThrows(
                AttachmentValidationException.class,
                () -> validator.validate(new MockMultipartFile(
                        "file",
                        "invoice.pdf.exe",
                        "application/pdf",
                        new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}
                ))
        );

        assertEquals("ATTACHMENT_EXTENSION_NOT_ALLOWED", exception.getCode());
    }

    @Test
    void rejectsContentTypeAndSignatureMismatch() {
        AttachmentValidationException exception = assertThrows(
                AttachmentValidationException.class,
                () -> validator.validate(new MockMultipartFile(
                        "file",
                        "image.png",
                        "image/png",
                        new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
                ))
        );

        assertEquals(
                "ATTACHMENT_CONTENT_SIGNATURE_INVALID",
                exception.getCode()
        );
    }

    @Test
    void rejectsUnsupportedContentType() {
        AttachmentValidationException exception = assertThrows(
                AttachmentValidationException.class,
                () -> validator.validate(new MockMultipartFile(
                        "file",
                        "page.html",
                        "text/html",
                        "<html>".getBytes()
                ))
        );

        assertEquals(
                "ATTACHMENT_CONTENT_TYPE_NOT_ALLOWED",
                exception.getCode()
        );
    }

    @Test
    void rejectsEmptyFile() {
        AttachmentValidationException exception = assertThrows(
                AttachmentValidationException.class,
                () -> validator.validate(new MockMultipartFile(
                        "file",
                        "empty.pdf",
                        "application/pdf",
                        new byte[0]
                ))
        );

        assertEquals("ATTACHMENT_FILE_EMPTY", exception.getCode());
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setStorageRoot(Path.of("data/attachments"));
        properties.setMaxFileSize(DataSize.ofBytes(5));
        properties.setAllowedContentTypes(Set.of("application/pdf"));
        properties.setAllowedExtensions(Set.of("pdf"));
        AttachmentFileValidator smallFileValidator =
                new AttachmentFileValidator(properties);

        AttachmentValidationException exception = assertThrows(
                AttachmentValidationException.class,
                () -> smallFileValidator.validate(new MockMultipartFile(
                        "file",
                        "large.pdf",
                        "application/pdf",
                        new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00}
                ))
        );

        assertEquals("ATTACHMENT_FILE_TOO_LARGE", exception.getCode());
    }
}
