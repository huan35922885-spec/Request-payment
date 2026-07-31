package tw.com.jsgcpa.paymentapproval.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SubmitPaymentDraftRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
