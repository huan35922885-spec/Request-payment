package tw.com.jsgcpa.paymentapproval.common.api;

public record FieldValidationError(
        String field,
        String message
) {
}
