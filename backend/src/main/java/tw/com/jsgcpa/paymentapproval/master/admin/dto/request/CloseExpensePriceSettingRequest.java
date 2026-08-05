package tw.com.jsgcpa.paymentapproval.master.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CloseExpensePriceSettingRequest(
        @NotNull
        LocalDate effectiveTo,

        @NotNull
        @PositiveOrZero
        Long version,

        @NotBlank
        @Size(max = 500)
        String reason
) {
    public CloseExpensePriceSettingRequest {
        reason = reason == null ? null : reason.strip();
    }
}
