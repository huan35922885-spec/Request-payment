package tw.com.jsgcpa.paymentapproval.master.admin.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReplaceExpensePriceSettingRequest(
        @NotBlank
        @Size(max = 100)
        String priceName,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount,

        @NotNull
        LocalDate effectiveFrom,

        @NotNull
        @PositiveOrZero
        Long version,

        @NotBlank
        @Size(max = 500)
        String reason
) {
    public ReplaceExpensePriceSettingRequest {
        priceName = priceName == null ? null : priceName.strip();
        reason = reason == null ? null : reason.strip();
    }
}
