package tw.com.jsgcpa.paymentapproval.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;

class AttachmentStorageKeyGeneratorTest {

    private final AttachmentStorageKeyGenerator generator =
            new AttachmentStorageKeyGenerator();

    @Test
    void generatesRelativeUuidStorageKeyWithCanonicalExtension() {
        String key = generator.generate(42L, "PDF");

        assertTrue(key.matches(
                "payment-requests/42/[0-9a-f-]{36}\\.pdf"
        ));
        assertTrue(!key.startsWith("/"));
    }

    @Test
    void rejectsInvalidPaymentRequestId() {
        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> generator.generate(0L, "pdf")
        );

        assertEquals("ATTACHMENT_STORAGE_PATH_INVALID", exception.getCode());
    }

    @Test
    void rejectsPathLikeExtension() {
        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> generator.generate(42L, "../pdf")
        );

        assertEquals("ATTACHMENT_STORAGE_PATH_INVALID", exception.getCode());
    }
}
