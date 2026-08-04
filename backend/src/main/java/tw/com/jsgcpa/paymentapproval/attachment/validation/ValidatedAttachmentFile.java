package tw.com.jsgcpa.paymentapproval.attachment.validation;

public record ValidatedAttachmentFile(
        String safeOriginalFilename,
        String detectedContentType,
        String canonicalExtension,
        long fileSize,
        byte[] content
) {

    public ValidatedAttachmentFile {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
