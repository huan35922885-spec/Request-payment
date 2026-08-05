package tw.com.jsgcpa.paymentapproval.master.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DeactivateExpenseTypeRequest(
        @NotBlank
        @Size(max = 500)
        String reason,

        @NotNull
        @PositiveOrZero
        Long version
) {

    public DeactivateExpenseTypeRequest {
        reason = reason == null ? null : reason.strip();
    }
}
