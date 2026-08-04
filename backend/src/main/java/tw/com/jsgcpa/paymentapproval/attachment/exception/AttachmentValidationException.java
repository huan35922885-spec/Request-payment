package tw.com.jsgcpa.paymentapproval.attachment.exception;

public class AttachmentValidationException extends RuntimeException {

    private final String code;

    public AttachmentValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AttachmentValidationException(
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
