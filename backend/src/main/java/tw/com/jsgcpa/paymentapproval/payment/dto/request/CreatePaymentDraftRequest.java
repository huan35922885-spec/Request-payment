package tw.com.jsgcpa.paymentapproval.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

public record CreatePaymentDraftRequest(
        @NotNull
        @Positive
        Long applicantId,

        @NotNull
        @Positive
        Long companyId,

        @NotNull
        @Positive
        Long customerId,

        @NotNull
        RequestCategory requestCategory,

        @NotBlank
        @Size(max = 2000)
        String reason,

        @NotEmpty
        @Valid
        List<CreatePaymentDraftItemRequest> items
) {
}
