package tw.com.jsgcpa.paymentapproval.master.admin.exception;

public class ExpensePriceSettingAdminBusinessException extends RuntimeException {

    private final String code;

    public ExpensePriceSettingAdminBusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
