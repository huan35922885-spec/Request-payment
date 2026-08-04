package tw.com.jsgcpa.paymentapproval.attachment.storage;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;

@Component
public class AttachmentStorageKeyGenerator {

    public String generate(Long paymentRequestId, String canonicalExtension) {
        if (paymentRequestId == null || paymentRequestId <= 0) {
            throw new AttachmentStorageException(
                    "ATTACHMENT_STORAGE_PATH_INVALID",
                    "Payment request id must be greater than zero"
            );
        }
        if (canonicalExtension == null
                || !canonicalExtension.matches("[a-zA-Z0-9]+")) {
            throw new AttachmentStorageException(
                    "ATTACHMENT_STORAGE_PATH_INVALID",
                    "Attachment extension is invalid"
            );
        }

        String extension = canonicalExtension.toLowerCase(Locale.ROOT);
        String storedFilename = UUID.randomUUID() + "." + extension;
        return "payment-requests/" + paymentRequestId + "/" + storedFilename;
    }
}
