package tw.com.jsgcpa.paymentapproval.attachment.storage;

public record StoredAttachmentFile(
        String storedFilename,
        String relativeStoragePath,
        long fileSize,
        String contentType
) {
}
