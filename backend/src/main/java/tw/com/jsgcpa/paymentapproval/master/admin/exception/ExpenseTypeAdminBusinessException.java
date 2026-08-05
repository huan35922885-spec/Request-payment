package tw.com.jsgcpa.paymentapproval.master.admin.exception;

public class ExpenseTypeAdminBusinessException extends RuntimeException {

    private final String code;

    public ExpenseTypeAdminBusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
