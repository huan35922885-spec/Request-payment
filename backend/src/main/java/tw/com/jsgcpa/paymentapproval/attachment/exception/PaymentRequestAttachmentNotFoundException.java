package tw.com.jsgcpa.paymentapproval.attachment.exception;

public class PaymentRequestAttachmentNotFoundException extends RuntimeException {

    public PaymentRequestAttachmentNotFoundException() {
        super("找不到附件");
    }
}
