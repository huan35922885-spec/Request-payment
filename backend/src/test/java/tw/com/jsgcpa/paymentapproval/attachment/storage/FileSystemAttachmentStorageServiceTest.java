package tw.com.jsgcpa.paymentapproval.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import tw.com.jsgcpa.paymentapproval.attachment.config.AttachmentStorageProperties;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;
import tw.com.jsgcpa.paymentapproval.attachment.validation.ValidatedAttachmentFile;

class FileSystemAttachmentStorageServiceTest {

    Path temporaryDirectory;

    private FileSystemAttachmentStorageService storageService;

    @BeforeEach
    void setUp() throws IOException {
        temporaryDirectory = Files.createTempDirectory("attachment-storage-test-");
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

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(temporaryDirectory);
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
    void rejectsStoreWhenParentDirectorySymlinkEscapesRoot() throws IOException {
        Path testDirectory = Files.createTempDirectory("attachment-symlink-test-");
        try {
            Path root = testDirectory.resolve("attachments");
            Path outside = testDirectory.resolve("outside");
            Path paymentRequests = root.resolve("payment-requests");
            Path escapedParent = paymentRequests.resolve("14");
            Files.createDirectories(paymentRequests);
            Files.createDirectories(outside);

            try {
                Files.createSymbolicLink(escapedParent, outside);
            } catch (UnsupportedOperationException | SecurityException exception) {
                Files.deleteIfExists(escapedParent);
                assumeTrue(false, "symbolic links are not supported by this test environment");
            } catch (AccessDeniedException exception) {
                Files.deleteIfExists(escapedParent);
                assumeTrue(false, "symbolic link creation is not permitted by this test environment");
            } catch (FileSystemException exception) {
                String message = exception.toString().toLowerCase(Locale.ROOT);
                boolean permissionDenied = message.contains("permission")
                        || message.contains("access denied")
                        || message.contains("special privilege")
                        || message.contains("特殊權限");
                if (permissionDenied) {
                    Files.deleteIfExists(escapedParent);
                    assumeTrue(false, "symbolic link creation is not permitted by this test environment");
                }
                throw exception;
            }

            AttachmentStorageProperties properties = new AttachmentStorageProperties();
            properties.setStorageRoot(root);
            properties.setMaxFileSize(DataSize.ofMegabytes(10));
            properties.setAllowedContentTypes(Set.of("application/pdf"));
            properties.setAllowedExtensions(Set.of("pdf"));
            storageService = new FileSystemAttachmentStorageService(
                    properties,
                    new AttachmentStorageKeyGenerator()
            );

            byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46};
            ValidatedAttachmentFile file = new ValidatedAttachmentFile(
                    "invoice.pdf",
                    "application/pdf",
                    "pdf",
                    content.length,
                    content
            );

            AttachmentStorageException exception = assertThrows(
                    AttachmentStorageException.class,
                    () -> storageService.store(14L, file)
            );

            assertEquals("ATTACHMENT_STORAGE_PATH_INVALID", exception.getCode());
            try (var files = Files.list(outside)) {
                assertTrue(files.findAny().isEmpty(),
                        "storage must not write outside the configured root");
            }
            assertNoTemporaryFilesRemain(testDirectory);
        } finally {
            deleteRecursively(testDirectory);
        }
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
        assertNoTemporaryFilesRemain(temporaryDirectory);
    }

    private void assertNoTemporaryFilesRemain(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            assertTrue(paths.noneMatch(path -> path.getFileName() != null
                    && path.getFileName().toString().startsWith(".attachment-")));
        }
    }

    private void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            List<Path> pathsToDelete = paths
                    .sorted((left, right) -> Integer.compare(
                            right.getNameCount(), left.getNameCount()))
                    .toList();
            for (Path path : pathsToDelete) {
                Files.deleteIfExists(path);
            }
        }
    }
}
