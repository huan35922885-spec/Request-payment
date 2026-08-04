package tw.com.jsgcpa.paymentapproval.attachment.storage;

/**
 * A filesystem deletion prepared for the surrounding database transaction.
 * Both paths are storage-root-relative and contain no absolute filesystem path.
 */
public record PreparedAttachmentDeletion(
        String originalRelativePath,
        String preparedRelativePath,
        boolean fileOriginallyExisted
) {
}
