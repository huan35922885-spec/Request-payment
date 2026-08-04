package tw.com.jsgcpa.paymentapproval.attachment.validation;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import tw.com.jsgcpa.paymentapproval.attachment.config.AttachmentStorageProperties;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentValidationException;

@Component
public class AttachmentFileValidator {

    private static final int MAX_FILENAME_BYTES = 255;

    private final AttachmentStorageProperties properties;

    public AttachmentFileValidator(AttachmentStorageProperties properties) {
        this.properties = properties;
    }

    public ValidatedAttachmentFile validate(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw validationError(
                    "ATTACHMENT_FILE_EMPTY",
                    "Attachment file must not be empty"
            );
        }

        String safeOriginalFilename = validateFilename(
                multipartFile.getOriginalFilename()
        );
        byte[] content = readContent(multipartFile);
        long fileSize = content.length;

        if (fileSize <= 0) {
            throw validationError(
                    "ATTACHMENT_FILE_EMPTY",
                    "Attachment file must not be empty"
            );
        }
        if (fileSize > properties.getMaxFileSize().toBytes()) {
            throw validationError(
                    "ATTACHMENT_FILE_TOO_LARGE",
                    "Attachment file exceeds the configured size limit"
            );
        }

        String declaredContentType = normalizeContentType(
                multipartFile.getContentType()
        );
        if (!properties.getAllowedContentTypes().contains(declaredContentType)) {
            throw validationError(
                    "ATTACHMENT_CONTENT_TYPE_NOT_ALLOWED",
                    "Attachment content type is not allowed"
            );
        }

        String requestedExtension = extensionOf(safeOriginalFilename);
        if (!properties.getAllowedExtensions().contains(requestedExtension)) {
            throw validationError(
                    "ATTACHMENT_EXTENSION_NOT_ALLOWED",
                    "Attachment extension is not allowed"
            );
        }

        DetectedAttachmentFileType detectedType = detect(content);
        if (detectedType == null
                || !properties.getAllowedContentTypes().contains(
                        detectedType.getContentType()
                )
                || !detectedType.getContentType().equals(declaredContentType)
                || !extensionMatches(
                        requestedExtension,
                        detectedType.getCanonicalExtension()
                )) {
            throw validationError(
                    "ATTACHMENT_CONTENT_SIGNATURE_INVALID",
                    "Attachment content signature does not match its metadata"
            );
        }

        return new ValidatedAttachmentFile(
                safeOriginalFilename,
                detectedType.getContentType(),
                detectedType.getCanonicalExtension(),
                fileSize,
                content
        );
    }

    private String validateFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw validationError(
                    "ATTACHMENT_FILENAME_INVALID",
                    "Attachment filename must not be blank"
            );
        }
        if (originalFilename.indexOf('\0') >= 0
                || originalFilename.indexOf('\r') >= 0
                || originalFilename.indexOf('\n') >= 0
                || originalFilename.indexOf('/') >= 0
                || originalFilename.indexOf('\\') >= 0
                || originalFilename.contains("..")) {
            throw validationError(
                    "ATTACHMENT_FILENAME_INVALID",
                    "Attachment filename contains an invalid path component"
            );
        }

        String safeFilename = originalFilename.trim();
        if (safeFilename.isEmpty()
                || safeFilename.getBytes(StandardCharsets.UTF_8).length
                > MAX_FILENAME_BYTES) {
            throw validationError(
                    "ATTACHMENT_FILENAME_INVALID",
                    "Attachment filename is invalid or too long"
            );
        }
        return safeFilename;
    }

    private byte[] readContent(MultipartFile multipartFile) {
        try {
            return multipartFile.getBytes();
        } catch (Exception exception) {
            throw new AttachmentValidationException(
                    "ATTACHMENT_FILE_READ_FAILED",
                    "Attachment file could not be read",
                    exception
            );
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null
                ? ""
                : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            throw validationError(
                    "ATTACHMENT_EXTENSION_NOT_ALLOWED",
                    "Attachment filename must have an extension"
            );
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean extensionMatches(
            String requestedExtension,
            String canonicalExtension
    ) {
        return canonicalExtension.equals(requestedExtension)
                || "jpg".equals(canonicalExtension)
                && "jpeg".equals(requestedExtension);
    }

    private DetectedAttachmentFileType detect(byte[] content) {
        for (DetectedAttachmentFileType type : DetectedAttachmentFileType.values()) {
            if (type.matches(content)) {
                return type;
            }
        }
        return null;
    }

    private AttachmentValidationException validationError(
            String code,
            String message
    ) {
        return new AttachmentValidationException(code, message);
    }
}
