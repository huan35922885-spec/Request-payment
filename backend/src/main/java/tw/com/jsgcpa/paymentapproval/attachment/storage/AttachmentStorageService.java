package tw.com.jsgcpa.paymentapproval.attachment.storage;

import java.io.InputStream;

import tw.com.jsgcpa.paymentapproval.attachment.validation.ValidatedAttachmentFile;

public interface AttachmentStorageService {

    StoredAttachmentFile store(
            Long paymentRequestId,
            ValidatedAttachmentFile file
    );

    InputStream load(String storagePath);

    long size(String storagePath);

    PreparedAttachmentDeletion prepareDelete(String storagePath);

    void restore(PreparedAttachmentDeletion preparedDeletion);

    void commitDelete(PreparedAttachmentDeletion preparedDeletion);

    void delete(String storagePath);

    boolean exists(String storagePath);
}
