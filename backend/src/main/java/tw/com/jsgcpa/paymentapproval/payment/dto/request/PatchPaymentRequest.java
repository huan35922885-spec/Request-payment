package tw.com.jsgcpa.paymentapproval.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;

public record PatchPaymentRequest(
        @NotNull
        @PositiveOrZero
        Long version,

        @NotNull
        OffsetDateTime paidAt,

        PaymentMethod paymentMethod,

        @Size(max = 100)
        String paymentReference,

        String paymentNote
) {
}
