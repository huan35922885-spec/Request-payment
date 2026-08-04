package tw.com.jsgcpa.paymentapproval.attachment.exception;

public class AttachmentStorageException extends RuntimeException {

    private final String code;

    public AttachmentStorageException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AttachmentStorageException(
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
