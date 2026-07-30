package tw.com.jsgcpa.paymentapproval.payment.exception;

public class PaymentDraftBusinessException extends RuntimeException {

    private final String code;

    public PaymentDraftBusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
