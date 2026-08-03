package tw.com.jsgcpa.paymentapproval.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CashierReviewPaymentRequest(
        @NotNull
        @PositiveOrZero
        Long version,

        @Size(max = 2000)
        String comment
) {
}
