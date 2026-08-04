package tw.com.jsgcpa.paymentapproval.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import tw.com.jsgcpa.paymentapproval.attachment.config.AttachmentStorageProperties;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;
import tw.com.jsgcpa.paymentapproval.attachment.validation.ValidatedAttachmentFile;

class FileSystemAttachmentStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    private FileSystemAttachmentStorageService storageService;

    @BeforeEach
    void setUp() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setStorageRoot(temporaryDirectory.resolve("attachments"));
        properties.setMaxFileSize(DataSize.ofMegabytes(10));
        properties.setAllowedContentTypes(Set.of("application/pdf"));
        properties.setAllowedExtensions(Set.of("pdf"));
        storageService = new FileSystemAttachmentStorageService(
                properties,
                new AttachmentStorageKeyGenerator()
        );
    }

    @Test
    void storesLoadsChecksAndDeletesUsingRelativeStoragePath() throws IOException {
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
        ValidatedAttachmentFile file = new ValidatedAttachmentFile(
                "invoice.pdf",
                "application/pdf",
                "pdf",
                content.length,
                content
        );

        StoredAttachmentFile stored = storageService.store(14L, file);

        assertTrue(stored.relativeStoragePath()
                .matches("payment-requests/14/[0-9a-f-]{36}\\.pdf"));
        assertFalse(Path.of(stored.relativeStoragePath()).isAbsolute());
        assertTrue(storageService.exists(stored.relativeStoragePath()));
        assertEquals(content.length, storageService.size(
                stored.relativeStoragePath()
        ));
        try (InputStream input = storageService.load(
                stored.relativeStoragePath()
        )) {
            assertArrayEquals(content, input.readAllBytes());
        }

        storageService.delete(stored.relativeStoragePath());

        assertFalse(storageService.exists(stored.relativeStoragePath()));
        storageService.delete(stored.relativeStoragePath());
        assertNoTemporaryFilesRemain();
    }

    @Test
    void rejectsStoragePathEscapingRoot() {
        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> storageService.load("../outside.pdf")
        );

        assertTrue(exception.getCode().equals(
                "ATTACHMENT_STORAGE_PATH_INVALID"
        ));
    }

    @Test
    void rejectsAbsoluteStoragePath() {
        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> storageService.delete(
                        temporaryDirectory.resolve("outside.pdf").toString()
                )
        );

        assertTrue(exception.getCode().equals(
                "ATTACHMENT_STORAGE_PATH_INVALID"
        ));
    }

    @Test
    void missingFileIsNotReportedAsExisting() {
        assertFalse(storageService.exists(
                "payment-requests/14/missing.pdf"
        ));
    }

    @Test
    void preparesRestoresAndCommitsDeletionWithoutExposingAbsolutePath()
            throws IOException {
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46};
        ValidatedAttachmentFile file = new ValidatedAttachmentFile(
                "invoice.pdf",
                "application/pdf",
                "pdf",
                content.length,
                content
        );
        StoredAttachmentFile stored = storageService.store(14L, file);

        PreparedAttachmentDeletion prepared = storageService.prepareDelete(
                stored.relativeStoragePath()
        );

        assertEquals(stored.relativeStoragePath(), prepared.originalRelativePath());
        assertTrue(prepared.preparedRelativePath().endsWith(".deleting"));
        assertFalse(Path.of(prepared.preparedRelativePath()).isAbsolute());
        assertFalse(prepared.preparedRelativePath().contains("invoice"));
        assertFalse(storageService.exists(stored.relativeStoragePath()));
        assertTrue(Files.exists(
                temporaryDirectory.resolve("attachments")
                        .resolve(prepared.preparedRelativePath())
        ));

        storageService.restore(prepared);
        assertTrue(storageService.exists(stored.relativeStoragePath()));
        try (InputStream input = storageService.load(stored.relativeStoragePath())) {
            assertArrayEquals(content, input.readAllBytes());
        }

        PreparedAttachmentDeletion second = storageService.prepareDelete(
                stored.relativeStoragePath()
        );
        storageService.commitDelete(second);
        assertFalse(storageService.exists(stored.relativeStoragePath()));
        assertNoTemporaryFilesRemain();
    }

    @Test
    void missingBinaryFailsPreparationAndKeepsMetadataCallerSafe() {
        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> storageService.prepareDelete(
                        "payment-requests/14/missing.pdf"
                )
        );

        assertEquals("ATTACHMENT_STORAGE_DELETE_FAILED", exception.getCode());
    }

    @Test
    void rejectsRootPathForPreparedDeletion() {
        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> storageService.prepareDelete(".")
        );

        assertEquals("ATTACHMENT_STORAGE_PATH_INVALID", exception.getCode());
    }

    @Test
    void restoreDoesNotOverwriteAnExistingOriginal() throws IOException {
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46};
        StoredAttachmentFile stored = storageService.store(
                14L,
                new ValidatedAttachmentFile(
                        "invoice.pdf",
                        "application/pdf",
                        "pdf",
                        content.length,
                        content
                )
        );
        PreparedAttachmentDeletion prepared = storageService.prepareDelete(
                stored.relativeStoragePath()
        );
        Path original = temporaryDirectory.resolve("attachments")
                .resolve(stored.relativeStoragePath());
        Files.createDirectories(original.getParent());
        Files.write(original, new byte[]{0x01});

        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> storageService.restore(prepared)
        );

        assertEquals("ATTACHMENT_STORAGE_DELETE_FAILED", exception.getCode());
        assertArrayEquals(new byte[]{0x01}, Files.readAllBytes(original));
    }

    private void assertNoTemporaryFilesRemain() throws IOException {
        if (!Files.exists(temporaryDirectory)) {
            return;
        }
        try (var paths = Files.walk(temporaryDirectory)) {
            assertTrue(paths.noneMatch(path -> path.getFileName() != null
                    && path.getFileName().toString().startsWith(".attachment-")));
        }
    }
}
