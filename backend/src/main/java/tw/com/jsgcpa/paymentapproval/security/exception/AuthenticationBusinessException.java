package tw.com.jsgcpa.paymentapproval.security.exception;

public class AuthenticationBusinessException extends RuntimeException {

    private final String code;

    public AuthenticationBusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
