package tw.com.jsgcpa.paymentapproval.attachment.exception;

public class PaymentRequestAttachmentDeleteException extends RuntimeException {

    private final String code;

    public PaymentRequestAttachmentDeleteException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
