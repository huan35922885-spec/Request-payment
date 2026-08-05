package tw.com.jsgcpa.paymentapproval.master.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ExpenseTypeVersionRequest(
        @NotNull
        @PositiveOrZero
        Long version
) {
}
