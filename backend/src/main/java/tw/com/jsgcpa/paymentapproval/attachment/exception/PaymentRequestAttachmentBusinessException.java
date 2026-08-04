package tw.com.jsgcpa.paymentapproval.attachment.exception;

public class PaymentRequestAttachmentBusinessException extends RuntimeException {

    private final String code;

    public PaymentRequestAttachmentBusinessException(
            String code,
            String message
    ) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
