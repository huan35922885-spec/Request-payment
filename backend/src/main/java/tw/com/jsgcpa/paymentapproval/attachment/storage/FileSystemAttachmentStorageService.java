package tw.com.jsgcpa.paymentapproval.attachment.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tw.com.jsgcpa.paymentapproval.attachment.config.AttachmentStorageProperties;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;
import tw.com.jsgcpa.paymentapproval.attachment.validation.ValidatedAttachmentFile;

@Service
public class FileSystemAttachmentStorageService
        implements AttachmentStorageService {

    private final AttachmentStorageProperties properties;
    private final AttachmentStorageKeyGenerator keyGenerator;

    public FileSystemAttachmentStorageService(
            AttachmentStorageProperties properties,
            AttachmentStorageKeyGenerator keyGenerator
    ) {
        this.properties = properties;
        this.keyGenerator = keyGenerator;
    }

    @Override
    public StoredAttachmentFile store(
            Long paymentRequestId,
            ValidatedAttachmentFile file
    ) {
        if (file == null) {
            throw storageError(
                    "ATTACHMENT_STORAGE_WRITE_FAILED",
                    "Validated attachment file must not be null"
            );
        }

        String storageKey = keyGenerator.generate(
                paymentRequestId,
                file.canonicalExtension()
        );
        Path root = absoluteRoot();
        Path target = resolveWithinRoot(root, storageKey);
        Path temporaryFile = null;

        try {
            Files.createDirectories(target.getParent());
            temporaryFile = Files.createTempFile(
                    target.getParent(),
                    ".attachment-",
                    ".tmp"
            );
            Files.write(
                    temporaryFile,
                    file.content(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            moveAtomically(temporaryFile, target);
            temporaryFile = null;

            String storedFilename = target.getFileName().toString();
            return new StoredAttachmentFile(
                    storedFilename,
                    storageKey,
                    file.fileSize(),
                    file.detectedContentType()
            );
        } catch (IOException exception) {
            throw storageError(
                    "ATTACHMENT_STORAGE_WRITE_FAILED",
                    "Attachment file could not be stored",
                    exception
            );
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    @Override
    public InputStream load(String storagePath) {
        Path path = resolveWithinRoot(absoluteRoot(), storagePath);
        try {
            Path realRoot = absoluteRoot().toRealPath();
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(realRoot)
                    || !Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS)) {
                throw storageError(
                        "ATTACHMENT_STORAGE_READ_FAILED",
                        "Attachment storage path is not a regular file"
                );
            }
            return Files.newInputStream(realPath, StandardOpenOption.READ);
        } catch (AttachmentStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw storageError(
                    "ATTACHMENT_STORAGE_READ_FAILED",
                    "Attachment file could not be loaded",
                    exception
            );
        }
    }

    @Override
    public long size(String storagePath) {
        Path path = resolveWithinRoot(absoluteRoot(), storagePath);
        try {
            Path realRoot = absoluteRoot().toRealPath();
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(realRoot)
                    || !Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS)) {
                throw storageError(
                        "ATTACHMENT_STORAGE_READ_FAILED",
                        "Attachment storage path is not a regular file"
                );
            }
            return Files.size(realPath);
        } catch (AttachmentStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw storageError(
                    "ATTACHMENT_STORAGE_READ_FAILED",
                    "Attachment file size could not be read",
                    exception
            );
        }
    }

    @Override
    public PreparedAttachmentDeletion prepareDelete(String storagePath) {
        Path root = absoluteRoot();
        Path original = resolveWithinRoot(root, storagePath);
        if (original.equals(root)) {
            throw storageError(
                    "ATTACHMENT_STORAGE_PATH_INVALID",
                    "Attachment storage path must identify a file"
            );
        }

        try {
            Path realRoot = root.toRealPath();
            Path realOriginal = original.toRealPath();
            if (!realOriginal.startsWith(realRoot)
                    || !Files.isRegularFile(
                            realOriginal,
                            LinkOption.NOFOLLOW_LINKS
                    )) {
                throw storageError(
                        "ATTACHMENT_STORAGE_DELETE_FAILED",
                        "Attachment storage path is not a regular file"
                );
            }

            for (int attempt = 0; attempt < 3; attempt++) {
                Path prepared = root.resolve(
                        ".attachment-delete-"
                                + UUID.randomUUID()
                                + ".deleting"
                ).normalize();
                if (Files.exists(prepared, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try {
                    moveAtomically(original, prepared);
                    return new PreparedAttachmentDeletion(
                            root.relativize(original).toString().replace('\\', '/'),
                            root.relativize(prepared).toString().replace('\\', '/'),
                            true
                    );
                } catch (java.nio.file.FileAlreadyExistsException exception) {
                    // A UUID collision is harmless; retry with a new target.
                }
            }
            throw storageError(
                    "ATTACHMENT_STORAGE_DELETE_FAILED",
                    "Attachment file could not be prepared for deletion"
            );
        } catch (AttachmentStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw storageError(
                    "ATTACHMENT_STORAGE_DELETE_FAILED",
                    "Attachment file could not be prepared for deletion",
                    exception
            );
        }
    }

    @Override
    public void restore(PreparedAttachmentDeletion preparedDeletion) {
        if (preparedDeletion == null || !preparedDeletion.fileOriginallyExisted()) {
            return;
        }
        Path root = absoluteRoot();
        Path original = resolveWithinRoot(
                root,
                preparedDeletion.originalRelativePath()
        );
        Path prepared = resolveWithinRoot(
                root,
                preparedDeletion.preparedRelativePath()
        );
        if (original.equals(root) || prepared.equals(root)) {
            throw storageError(
                    "ATTACHMENT_STORAGE_DELETE_FAILED",
                    "Prepared attachment deletion path is invalid"
            );
        }

        try {
            if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                throw storageError(
                        "ATTACHMENT_STORAGE_DELETE_FAILED",
                        "Original attachment path already exists"
                );
            }
            Path realRoot = root.toRealPath();
            Path realPrepared = prepared.toRealPath();
            if (!realPrepared.startsWith(realRoot)
                    || !Files.isRegularFile(
                            realPrepared,
                            LinkOption.NOFOLLOW_LINKS
                    )) {
                throw storageError(
                        "ATTACHMENT_STORAGE_DELETE_FAILED",
                        "Prepared attachment is not a regular file"
                );
            }
            Files.createDirectories(original.getParent());
            moveAtomically(prepared, original);
        } catch (AttachmentStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw storageError(
                    "ATTACHMENT_STORAGE_DELETE_FAILED",
                    "Prepared attachment deletion could not be restored",
                    exception
            );
        }
    }

    @Override
    public void commitDelete(PreparedAttachmentDeletion preparedDeletion) {
        if (preparedDeletion == null || !preparedDeletion.fileOriginallyExisted()) {
            return;
        }
        Path root = absoluteRoot();
        Path prepared = resolveWithinRoot(
                root,
                preparedDeletion.preparedRelativePath()
        );
        if (prepared.equals(root)) {
            throw storageError(
                    "ATTACHMENT_STORAGE_DELETE_FAILED",
                    "Prepared attachment deletion path is invalid"
            );
        }
        if (!Files.exists(prepared, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try {
            Path realRoot = root.toRealPath();
            Path realPrepared = prepared.toRealPath();
            if (!realPrepared.startsWith(realRoot)
                    || !Files.isRegularFile(
                            realPrepared,
                            LinkOption.NOFOLLOW_LINKS
                    )) {
                throw storageError(
                        "ATTACHMENT_STORAGE_DELETE_FAILED",
                        "Prepared attachment is not a regular file"
                );
            }
            Files.deleteIfExists(realPrepared);
        } catch (AttachmentStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw storageError(
                    "ATTACHMENT_STORAGE_DELETE_FAILED",
                    "Prepared attachment could not be permanently deleted",
                    exception
            );
        }
    }

    @Override
    public void delete(String storagePath) {
        Path path = resolveWithinRoot(absoluteRoot(), storagePath);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try {
            Path realRoot = absoluteRoot().toRealPath();
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(realRoot)
                    || !Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS)) {
                throw storageError(
                        "ATTACHMENT_STORAGE_DELETE_FAILED",
                        "Attachment storage path is not a regular file"
                );
            }
            Files.deleteIfExists(realPath);
        } catch (AttachmentStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw storageError(
                    "ATTACHMENT_STORAGE_DELETE_FAILED",
                    "Attachment file could not be deleted",
                    exception
            );
        }
    }

    @Override
    public boolean exists(String storagePath) {
        Path path = resolveWithinRoot(absoluteRoot(), storagePath);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            Path realRoot = absoluteRoot().toRealPath();
            Path realPath = path.toRealPath();
            return realPath.startsWith(realRoot)
                    && Files.isRegularFile(realPath, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            return false;
        }
    }

    private Path absoluteRoot() {
        if (properties.getStorageRoot() == null) {
            throw storageError(
                    "ATTACHMENT_STORAGE_PATH_INVALID",
                    "Attachment storage root is not configured"
            );
        }
        return properties.getStorageRoot().toAbsolutePath().normalize();
    }

    private Path resolveWithinRoot(Path root, String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw storageError(
                    "ATTACHMENT_STORAGE_PATH_INVALID",
                    "Attachment storage path must not be blank"
            );
        }

        Path relativePath;
        try {
            relativePath = Path.of(storagePath);
        } catch (Exception exception) {
            throw storageError(
                    "ATTACHMENT_STORAGE_PATH_INVALID",
                    "Attachment storage path is invalid",
                    exception
            );
        }
        if (relativePath.isAbsolute()) {
            throw storageError(
                    "ATTACHMENT_STORAGE_PATH_INVALID",
                    "Attachment storage path must be relative"
            );
        }

        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw storageError(
                    "ATTACHMENT_STORAGE_PATH_INVALID",
                    "Attachment storage path escapes the storage root"
            );
        }
        return resolved;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // The original storage error is more useful to the caller.
        }
    }

    private AttachmentStorageException storageError(
            String code,
            String message
    ) {
        return new AttachmentStorageException(code, message);
    }

    private AttachmentStorageException storageError(
            String code,
            String message,
            Throwable cause
    ) {
        return new AttachmentStorageException(code, message, cause);
    }
}
